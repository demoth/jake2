/*
 * FS.java
 * Copyright (C) 2003
 */
/*
 Copyright (C) 1997-2001 Id Software, Inc.

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation; either version 2
 of the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

 See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

 */
package jake2.qcommon.filesystem;

import jake2.qcommon.Com;
import jake2.qcommon.Defines;
import jake2.qcommon.Globals;
import jake2.qcommon.exec.Cbuf;
import jake2.qcommon.exec.Cmd;
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.exec.cvar_t;
import jake2.qcommon.sys.Sys;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/*
 * All of Quake's data access is through a hierarchical file system, but the
 * contents of the file system can be transparently merged from several
 * sources.
 *
 * The "base directory" is the path to the directory holding the quake.exe
 * and all game directories. The sys_* files pass this to host_init in
 * quakeparms_t->basedir. This can be overridden with the "-basedir" command
 * line parm to allow code debugging in a different directory. The base
 * directory is only used during filesystem initialization.
 *
 * The "game directory" is the first tree on the search path and directory
 * that all generated files (savegames, screenshots, demos, config files)
 * will be saved to. This can be overridden with the "-game" command line
 * parameter. The game directory can never be changed while quake is
 * executing. This is a precacution against having a malicious server
 * instruct clients to write files over areas they shouldn't.
 *
 */
public final class FS extends Globals {

    /*
     * ==================================================
     * 
     * QUAKE FILESYSTEM
     * 
     * ==================================================
     */

    /**
     * Represents a file inside a PAK archive
     */
    static class packfile_t {
        static final int SIZE = 64;

        static final int NAME_SIZE = 56;

        // char name[56]
        final String name;
        final int filepos;
        final int filelen;

        packfile_t(String name, int filepos, int filelen) {
            this.name = name;
            this.filepos = filepos;
            this.filelen = filelen;
        }

        public String toString() {
            return name + " [ length: " + filelen + " pos: " + filepos + " ]";
        }
    }

    static class pack_t {
        final String filename;
        final int numfiles;
        final Map<String, packfile_t> files;

        RandomAccessFile handle;
        
        ByteBuffer mappedFileChannel;


        pack_t(String filename, int numfiles, Map<String, packfile_t> files) {
            this.filename = filename;
            this.numfiles = numfiles;
            this.files = files;
        }

        void closeQuietly() {
            try {
                handle.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public String toString() {
            return filename + " (" + numfiles + ')';
        }
    }

    private static String fs_gamedir;

    private static String fs_userdir;

    private static cvar_t fs_basedir;

    private static cvar_t fs_cddir;
    private static cvar_t fs_casesensitive;

    public static cvar_t fs_gamedirvar;
    private static VfsBackedFileSystem fs_vfsCompat;

    static class filelink_t {
        final String from;

        String to;

        filelink_t(String from, String to) {
            this.from = from;
            this.to = to;
        }

        @Override
        public String toString() {
            return "filelink_t{" +
                    "from='" + from + '\'' +
                    ", to='" + to + '\'' +
                    '}';
        }
    }

    private static List<filelink_t> fs_links = new LinkedList<>();

    static class SearchPath {
        // only one of filename or pack will be used
        final String filename;
        final pack_t pack;
        // all entries with 'true' values will be freed after change of 'game' cvar
        final boolean isGame;

        SearchPath(String filename, pack_t pack, boolean isGame) {
            this.filename = filename;
            this.pack = pack;
            this.isGame = isGame;
        }

        @Override
        public String toString() {
            return pack != null ? pack.toString() : filename;
        }
    }
    private static final Deque<SearchPath> searchPaths = new ArrayDeque<>();


    /*
     * CreatePath
     * 
     * Creates any directories needed to store the given filename.
     */
    public static void CreatePath(String path) {
        int index = path.lastIndexOf('/');
        // -1 if not found and 0 means write to root
        if (index > 0) {
            File f = new File(path.substring(0, index));
            if (!f.mkdirs() && !f.isDirectory()) {
                Com.Printf("can't create path \"" + path + '"' + "\n");
            }
        }
    }

    public static boolean FileExists(String filename) {
        File linkedFile = resolveLinkedFile(filename);
        if (linkedFile != null) {
            return true;
        }
        syncVfsCaseSensitivity();
        if (fs_vfsCompat != null && fs_vfsCompat.exists(filename)) {
            return true;
        }
        try {
            RandomAccessFile raf = FOpenFile(filename);
            if (raf != null) {
                raf.close();
                return true;
            } else return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * FOpenFile
     * 
     * Finds the file in the search path. returns a RadomAccesFile. Used for
     * streaming data out of either a pak file or a separate file.
     *
     * @return null in case of error
     */
    public static QuakeFile FOpenFile(String filename) {


        try {
            // used for tools
            // todo: unify with other code
            if (filename.startsWith("/")) {
                final File file = new File(filename);
                return new QuakeFile(file, "r", false, file.length());
            }

            // check for links first
            File linkedFile = resolveLinkedFile(filename);
            if (linkedFile != null) {
                return new QuakeFile(linkedFile, "r", false, linkedFile.length());
            }

            syncVfsCaseSensitivity();
            if (fs_vfsCompat != null) {
                QuakeFile vfsFile = fs_vfsCompat.openFile(filename);
                if (vfsFile != null) {
                    return vfsFile;
                }
            }

            //
            // search through the path, one element at a time
            //
            for (SearchPath searchPath : searchPaths) {
                if (searchPath.pack != null) {
                    packfile_t fileInPak = searchPath.pack.files.get(filename.toLowerCase());

                    if (fileInPak != null) {
                        // found it!
                        File file = new File(searchPath.pack.filename);
                        if (!file.canRead())
                            Com.Error(Defines.ERR_FATAL, "Couldn't reopen " + searchPath.pack.filename + '\n');
                        if (searchPath.pack.handle == null || !searchPath.pack.handle.getFD().valid()) {
                            // hold the pakfile handle open
                            searchPath.pack.handle = new RandomAccessFile(searchPath.pack.filename, "r");
                        }
                        // open a new file on the pakfile

                        QuakeFile qf = new QuakeFile(file, "r", true, fileInPak.filelen);
                        qf.seek(fileInPak.filepos);
                        return qf;
                    }

                } else {
                    File file = new File(searchPath.filename + '/' + filename);
                    if (file.exists() && file.canRead()) {
                        return new QuakeFile(file, "r", false, file.length());
                    }
                }
            }
        } catch (Exception e) {
            Com.Printf("Could not open the file " + filename + " due to " + e.getMessage() + '\n');
        }
        return null;
    }

    /**
     * Opens a readable file using the full FS search policy.
     *
     * This is the preferred read-path entry point for compatibility call sites that
     * historically instantiated {@link QuakeFile} directly with mode {@code "r"}.
     */
    public static QuakeFile OpenReadFile(String filename) throws FileNotFoundException {
        QuakeFile file = FOpenFile(filename);
        if (file == null) {
            throw new FileNotFoundException("Resource not found: " + filename);
        }
        return file;
    }

    /**
     * Opens a writable file and ensures parent directories exist first.
     *
     * This centralizes write-path handling while preserving legacy QuakeFile semantics.
     */
    public static QuakeFile OpenWriteFile(String filename) throws FileNotFoundException {
        CreatePath(filename);
        return new QuakeFile(filename, "rw");
    }

    // read in blocks of 64k
    private static final int MAX_READ = 0x10000;

    /**
     * Read
     * 
     * Properly handles partial reads
     */
    public static void Read(byte[] buffer, int len, RandomAccessFile f) {

        int offset = 0;
        int read = 0;
        int tries = 0;
        // read in chunks for progress bar
        int remaining = len;
        int block;

        while (remaining != 0) {
            block = Math.min(remaining, MAX_READ);
            try {
                read = f.read(buffer, offset, block);
            } catch (IOException e) {
                Com.Error(Defines.ERR_FATAL, e.toString());
            }

            if (read == 0) {
            	
            	// we might have been trying to read from a CD
            	if (tries == 0)
            	{
            		tries = 1;
            		// todo: check if this hack is requried (does anyone has CD these days?)
            		// CDAudio.Stop();
            	} else {
            		Com.Error(Defines.ERR_FATAL, "FS_Read: 0 bytes read");
            	}

            } else if (read == -1) {
                Com.Error(Defines.ERR_FATAL, "FS_Read: -1 bytes read");
            }
            //
            // do some progress bar thing here...
            //
            remaining -= read;
            offset += read;
        }
    }

    /*
     * LoadFile
     * 
     * Filename are relative to the quake search path a null buffer will just
     * return the file content as byte[]
     */
    public static byte[] LoadFile(String path) {
        // TODO hack for bad strings (fuck \0)
        int index = path.indexOf('\0');
        if (index != -1)
            path = path.substring(0, index);

        File linkedFile = resolveLinkedFile(path);
        if (linkedFile != null) {
            try {
                return Files.readAllBytes(linkedFile.toPath());
            } catch (IOException e) {
                Com.Error(Defines.ERR_FATAL, e.toString());
                return null;
            }
        }

        syncVfsCaseSensitivity();
        if (fs_vfsCompat != null) {
            byte[] bytes = fs_vfsCompat.loadFile(path);
            if (bytes != null) {
                return bytes;
            }
        }

        try {
            QuakeFile file = FOpenFile(path);
            if (file == null) {
                if (!isVfsCaseSensitive()) {
                    file = FOpenFile(path.toLowerCase());
                    if (file != null) {
                        // some mods mess up the case
                        Com.Printf("Found file by lowercase: " + path + "\n");
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }
            }
            return file.toBytes();
        } catch (IOException e) {
            Com.Error(Defines.ERR_FATAL, e.toString());
            return null;
        }
    }

    /**
     * Filename are relative to the quake search path a null buffer will just
     * return the file content as ByteBuffer (memory mapped)
     *
     * Used for big files like cinematics
     */
    public static ByteBuffer LoadMappedFile(String filename) {
        File linkedFile = resolveLinkedFile(filename);
        if (linkedFile != null) {
            try (FileInputStream input = new FileInputStream(linkedFile);
                 FileChannel channel = input.getChannel()) {
                int fileLength = (int) channel.size();
                return channel.map(FileChannel.MapMode.READ_ONLY, 0, fileLength);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        syncVfsCaseSensitivity();
        if (fs_vfsCompat != null) {
            ByteBuffer mapped = fs_vfsCompat.loadMappedFile(filename);
            if (mapped != null) {
                return mapped;
            }
        }

        try {
            //
            // search through the path, one element at a time
            //
            for (SearchPath search : searchPaths) {
                // is the element a pak file?
                if (search.pack != null) {
                    // look through all the pak file elements
                    packfile_t entry = search.pack.files.get(filename.toLowerCase());

                    if (entry != null) {
                        // found it!
                        //Com.DPrintf ("PackFile: " + pak.filename + " : " +
                        // filename + '\n');
                        File file = new File(search.pack.filename);
                        if (!file.canRead())
                            Com.Error(Defines.ERR_FATAL, "Couldn't reopen "
                                    + search.pack.filename);
                        if (search.pack.handle == null || !search.pack.handle.getFD().valid()) {
                            // hold the pakfile handle open
                            search.pack.handle = new RandomAccessFile(search.pack.filename, "r");
                        }
                        // open a new file on the pakfile
                        if (search.pack.mappedFileChannel == null) {
                            try (FileChannel channel = search.pack.handle.getChannel()) {
                                search.pack.mappedFileChannel = channel.map(FileChannel.MapMode.READ_ONLY, 0, search.pack.handle.length());
                            }
                        }
                        search.pack.mappedFileChannel.position(entry.filepos);
                        ByteBuffer buffer = search.pack.mappedFileChannel.slice();
                        buffer.limit(entry.filelen);
                        return buffer;
                    }
                } else {
                    // check a file in the directory tree

                    File file = new File(search.filename + '/' + filename);
                    if (!file.canRead())
                        continue;

                    //Com.DPrintf("FindFile: " + netpath +'\n');
                    try (FileInputStream input = new FileInputStream(file);
                         FileChannel channel = input.getChannel()) {

                        int fileLength = (int) channel.size();
                        return channel.map(FileChannel.MapMode.READ_ONLY, 0, fileLength);

                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static final int IDPAKHEADER = (('K' << 24) + ('C' << 16) + ('A' << 8) + 'P');

    static class dpackheader_t {
        final int ident; // IDPAKHEADER
        final int dirofs;
        final int dirlen;

        dpackheader_t(int ident, int dirofs, int dirlen) {
            this.ident = ident;
            this.dirofs = dirofs;
            this.dirlen = dirlen;
        }
    }

    private static final int MAX_FILES_IN_PACK = 4096;

    /**
     *
     * Takes an explicit (not game tree related) path to a pak file.
     * 
     * Loads the header and directory, adding the files at the beginning of the
     * list so they override previous pack files.
     */
    private static pack_t LoadPackFile(String packfile) {

        dpackheader_t header;
        Map<String, packfile_t> newfiles;
        RandomAccessFile file;
        int numpackfiles;
        //		unsigned checksum;
        //
        try {
        	file = new RandomAccessFile(packfile, "r");
        	FileChannel fc = file.getChannel();
            ByteBuffer packhandle = fc.map(FileChannel.MapMode.READ_ONLY, 0, file.length());
            packhandle.order(ByteOrder.LITTLE_ENDIAN);
 
            fc.close();
            
            if (packhandle.limit() < 1)
                return null;

            header = new dpackheader_t(packhandle.getInt(), packhandle.getInt(), packhandle.getInt());

            if (header.ident != IDPAKHEADER)
                Com.Error(Defines.ERR_FATAL, packfile + " is not a packfile");

            numpackfiles = header.dirlen / packfile_t.SIZE;

            if (numpackfiles > MAX_FILES_IN_PACK)
                Com.Error(Defines.ERR_FATAL, packfile + " has " + numpackfiles + " files");

            newfiles = new HashMap<>(numpackfiles);

            packhandle.position(header.dirofs);

            // parse the directory

            byte[] name = new byte[packfile_t.NAME_SIZE];

            for (int i = 0; i < numpackfiles; i++) {
                packhandle.get(name);
                packfile_t entry = new packfile_t(new String(name).trim(), packhandle.getInt(), packhandle.getInt());
                newfiles.put(entry.name.toLowerCase(), entry);
            }

        } catch (IOException e) {
            Com.DPrintf("jake2.qcommon.filesystem.FS.LoadPackFile" + e.getMessage() + '\n');
            return null;
        }

        pack_t pack = new pack_t(packfile, numpackfiles, newfiles);
        pack.handle = file;

        Com.Printf("Added packfile " + packfile + " (" + numpackfiles + " files)\n");

        return pack;
    }

    /**
     *
     * Sets fs_gamedir, adds the directory to the head of the path, then loads
     * and adds pak2.pak, pak1.pak, pak0.pak ...
     *
     * @param isGame true for mod directories, false for baseq2
     */
    private static void AddGameDirectory(String dir, boolean isGame) {
        if (!isGame)
            searchPaths.add(new SearchPath(dir, null, false));

        fs_gamedir = dir;
        // add the directory to the search path
        // ensure fs_userdir is first in searchpath

        // todo add custom packs (like cool_models.pk3) before pak0.pak

        //
        // add any pak files in the format pak0.pak pak1.pak, ...
        //
        int maxPacks = 9;
        for (int i = maxPacks; i >= 0; i--) {
            int pakIndex = isGame ? maxPacks - i : i;
            String pakfile = dir + "/pak" + pakIndex + ".pak";
            if (!(new File(pakfile).canRead()))
                continue;

            pack_t pak = LoadPackFile(pakfile);
            if (pak == null)
                continue;

            if (isGame)
                searchPaths.push(new SearchPath(dir, pak, true));
            else
                searchPaths.add(new SearchPath(dir, pak, false));
        }

        if (isGame)
            searchPaths.push(new SearchPath(dir, null, true));
    }

    /**
     * Called to find where to write a file (demos, savegames, etc)
     * this is modified to `user.home`/.jake2/`game_name`
     */
    public static String getWriteDir() {
        return (fs_userdir != null) ? fs_userdir : Globals.BASEQ2;
    }

    private static void setWriteDir(String gameName, boolean isGame) {
        fs_userdir = System.getProperty("user.home") + "/.jake2/" + gameName;
        FS.CreatePath(fs_userdir + "/");
        if (isGame)
            AddGameDirectory(fs_userdir, true);
    }

    /*
     * ExecAutoexec
     */
    public static void ExecAutoexec() {
        String dir = fs_userdir;

        String name;
        if (dir != null && dir.length() > 0) {
            name = dir + "/autoexec.cfg";
        } else {
            name = fs_basedir.string + '/' + Globals.BASEQ2 + "/autoexec.cfg";
        }

        int canthave = Defines.SFF_SUBDIR | Defines.SFF_HIDDEN
                | Defines.SFF_SYSTEM;

        if (Sys.FindAll(name, 0, canthave) != null) {
            Cbuf.AddText("exec autoexec.cfg\n");
        }
    }

    /**
     *
     * Sets the gamedir and path to a different directory.
     *
     * used when game cvar is changed
     */
    public static void SetGamedir(String gameName) {

        if (gameName.contains("..") || gameName.contains("/") || gameName.contains("\\") || gameName.contains(":")) {
            Com.Printf("Gamedir should be a single filename, not a path\n");
            return;
        }


        //
        // free up any current game dir info
        //
        searchPaths.removeIf(path -> {
            if (path.isGame) {
                if (path.pack != null)
                    path.pack.closeQuietly();
                return true;
            } else return false;
        });

        //
        // flush all data, so it will be forced to reload
        //
        if ((Globals.dedicated != null) && (Globals.dedicated.value == 0.0f)) {
            Cbuf.AddText("vid_restart");
            Cbuf.AddText("snd_restart");
        }

        fs_gamedir = fs_basedir.string + '/' + gameName;

        if (gameName.equals(Globals.BASEQ2) || gameName.isEmpty()) {
            Cvar.getInstance().FullSet("gamedir", "", CVAR_SERVERINFO | CVAR_NOSET);
            Cvar.getInstance().FullSet("game", "", CVAR_LATCH | CVAR_SERVERINFO);
        } else {
            Cvar.getInstance().FullSet("gamedir", gameName, CVAR_SERVERINFO | CVAR_NOSET);
            if (fs_cddir.string != null && fs_cddir.string.length() > 0)
                AddGameDirectory(fs_cddir.string + '/' + gameName, true);

            AddGameDirectory(fs_basedir.string + '/' + gameName, true);
        }

        setWriteDir(gameName, true);
        updateVfsCompat(gameName);
    }

    /*
     * Link_f
     * 
     * Creates a filelink_t
     */
    private static void Link_f(List<String> args) {

        if (args.size() != 3) {
            Com.Printf("USAGE: link <from> <to>\n");
            return;
        }

        // see if the link already exists
        String from = args.get(1);
        String to = args.get(2);
        for (Iterator<filelink_t> it = fs_links.iterator(); it.hasNext();) {
            filelink_t entry = it.next();

            if (entry.from.equals(from)) {
                if (to.isEmpty()) {
                    // delete it
                    it.remove();
                    return;
                }
                entry.to = to;
                return;
            }
        }

        // create a new link if the <to> is not empty
        if (!to.isEmpty()) {
            fs_links.add(new filelink_t(from, to));
        }
    }

    /*
     * ListFiles
     */
    public static String[] ListFiles(String findname, int musthave, int canthave) {
        String[] list = null;

        File[] files = Sys.FindAll(findname, musthave, canthave);

        if (files != null) {
            list = new String[files.length];
            for (int i = 0; i < files.length; i++) {
                list[i] = files[i].getPath();
            }
        }

        return list;
    }

    /**
     * dir command as in MS-DOS - shows contents of the directory
     * @param args wildcard, can contain a subpath also, like 'dir players/cyborg/*.*'.
     *
     * TODO: support dir of pak files
     * TODO: support links
     */
    private static void Dir_f(List<String> args) {
        //
        String wildcard = "*.*";

        if (args.size() >= 2) {
            wildcard = args.get(1);
        }

        if (fs_vfsCompat != null) {
            syncVfsCaseSensitivity();
            List<String> matches = fs_vfsCompat.debugFilesMatching(wildcard);
            if (!matches.isEmpty()) {
                Com.Printf("Directory of " + wildcard + '\n');
                for (String match : matches) {
                    Com.Printf("  " + match + '\n');
                }
                Com.Printf("\n");
                return;
            }
        }

        for (SearchPath s : searchPaths) {
            if (s.pack != null)
                continue;
            String path = s.filename;

            String findname = path + '/' + wildcard;

            String[] dirnames = ListFiles(findname, 0, 0);

            if (dirnames != null && dirnames.length > 0) {
                Com.Printf("Directory of " + findname + '\n');
                for (String dirname : dirnames) {
                    int index;
                    if ((index = dirname.lastIndexOf('/')) > 0) {
                        Com.Printf("  " + dirname.substring(index + 1) + '\n');
                    } else {
                        Com.Printf("  " + dirname + '\n');
                    }
                }
                Com.Printf("\n");

            }
        }
    }

    /**
     * Shows all current search paths and links
     */
    private static void Path_f() {
        if (fs_vfsCompat != null) {
            syncVfsCaseSensitivity();
            List<String> mounts = fs_vfsCompat.debugMounts();
            if (!mounts.isEmpty()) {
                for (String mount : mounts) {
                    Com.Printf(mount + "\n");
                }
            } else {
                Com.Printf("No VFS mounts available.\n");
            }
        } else {
            for (SearchPath s : searchPaths) {
                Com.Printf(s.toString() + "\n");
            }
        }

        if (!fs_links.isEmpty()) {
            Com.Printf("\nLinks:\n");
            for (filelink_t link : fs_links) {
                Com.Printf(link.from + " : " + link.to + '\n');
            }
        }
    }

    /**
     * Prints all resolved VFS files in ascending order.
     */
    private static void FsFiles_f(List<String> args) {
        if (fs_vfsCompat == null) {
            Com.Printf("VFS compatibility layer is not initialized.\n");
            return;
        }
        syncVfsCaseSensitivity();
        List<String> files = fs_vfsCompat.debugResolvedFiles();
        if (files.isEmpty()) {
            Com.Printf("No VFS files indexed.\n");
            return;
        }
        for (String file : files) {
            Com.Printf(file + "\n");
        }
        Com.Printf("Total resolved files: " + files.size() + "\n");
    }

    /**
     * Prints mounted VFS roots/packages in effective priority order.
     */
    private static void FsMounts_f(List<String> args) {
        if (fs_vfsCompat == null) {
            Com.Printf("VFS compatibility layer is not initialized.\n");
            return;
        }
        syncVfsCaseSensitivity();
        List<String> mounts = fs_vfsCompat.debugMounts();
        if (mounts.isEmpty()) {
            Com.Printf("No VFS mounts available.\n");
            return;
        }
        for (String mount : mounts) {
            Com.Printf(mount + "\n");
        }
        Com.Printf("Total mounts: " + mounts.size() + "\n");
    }

    /**
     * Prints logical paths that are present in more than one source.
     */
    private static void FsOverrides_f(List<String> args) {
        if (fs_vfsCompat == null) {
            Com.Printf("VFS compatibility layer is not initialized.\n");
            return;
        }
        syncVfsCaseSensitivity();
        List<String> overrides = fs_vfsCompat.debugOverrides();
        if (overrides.isEmpty()) {
            Com.Printf("No VFS overrides detected.\n");
            return;
        }
        for (String line : overrides) {
            Com.Printf(line + "\n");
        }
        Com.Printf("Total overridden paths: " + overrides.size() + "\n");
    }

    /**
     * Allows enumerating all of the directories in the search path
     */
    public static String NextPath(String prevpath) {
        if (fs_vfsCompat != null) {
            syncVfsCaseSensitivity();
            List<String> roots = fs_vfsCompat.debugLooseMountRoots();
            if (!roots.isEmpty()) {
                if (prevpath == null || prevpath.length() == 0) {
                    return roots.get(0);
                }

                for (int i = 0; i < roots.size() - 1; i++) {
                    if (prevpath.equals(roots.get(i))) {
                        return roots.get(i + 1);
                    }
                }
                return null;
            }
        }

        if (prevpath == null || prevpath.length() == 0)
            return fs_gamedir;

        String dir = fs_gamedir;
        for (SearchPath s : searchPaths) {
            if (s.pack != null)
                continue;

            if (prevpath.equals(dir))
                return s.filename;

            dir = s.filename;
        }

        return null;
    }

    /*
     * InitFilesystem
     */
    public static void InitFilesystem() {
        Cmd.AddCommand("path", (List<String> args) -> Path_f());
        Cmd.AddCommand("link", FS::Link_f);
        Cmd.AddCommand("dir", FS::Dir_f);
        Cmd.AddCommand("ls", FS::Dir_f);
        Cmd.AddCommand("fs_files", FS::FsFiles_f);
        Cmd.AddCommand("fs_mounts", FS::FsMounts_f);
        Cmd.AddCommand("fs_overrides", FS::FsOverrides_f);

        // by default all saves, screenshots etc go to ~/.jake2/baseq2
        // overridden by 'game' cvar, i.e xatrix saves to go ~/.jake2/xatrix
        setWriteDir(Globals.BASEQ2, false);

        FS.AddGameDirectory(fs_userdir, false);

        //
        // basedir <path>
        // allows the game to run from outside the data tree
        //
        if (new File("./baseq2").exists()) {
            fs_basedir = Cvar.getInstance().Get("basedir", ".", CVAR_NOSET);
        } else {
            var autoDetectedBasedir = autodetectBasedir();
            fs_basedir = Cvar.getInstance().Get("basedir", autoDetectedBasedir, CVAR_NOSET);
        }

        //
        // cddir <path>
        // Logically concatenates the cddir after the basedir for
        // allows the game to run from outside the data tree
        //

        setCDDir();

        //
        // start up with baseq2 by default
        //
        AddGameDirectory(fs_basedir.string + '/' + Globals.BASEQ2, false);

        // check for game override
        fs_gamedirvar = Cvar.getInstance().Get("game", "", CVAR_LATCH | CVAR_SERVERINFO);
        fs_casesensitive = Cvar.getInstance().Get("fs_casesensitive", "0", CVAR_ARCHIVE);

        if (!fs_gamedirvar.string.isEmpty())
            SetGamedir(fs_gamedirvar.string);

        initVfsCompat();
    }

    private static String autodetectBasedir() {
        // linux
        var home = System.getProperty("user.home");
        if (home != null && !home.isBlank()) {
            var steamLinuxPath = Paths.get(home, ".steam", "steam", "steamapps", "common", "Quake 2").toFile();
            if (steamLinuxPath.exists()) {
                System.out.println("Auto-detected steam q2 installation at :" + steamLinuxPath);
                return steamLinuxPath.getAbsolutePath();
            }

            // Mac
            var macSteamPath = Paths.get(home, "Library", "Application Support", "Steam", "steamapps", "common", "Quake 2").toFile();
            if (macSteamPath.exists()) {
                System.out.println("Auto-detected steam q2 installation at :" + macSteamPath);
                return macSteamPath.getAbsolutePath();
            }
        }

        // windows 32
        var windowsProgramFiles = Paths.get("c:", "Program Files (x86)", "Steam", "steamapps", "common", "Quake 2").toFile();
        if (windowsProgramFiles.exists()) {
            System.out.println("Auto-detected steam q2 installation at :" + windowsProgramFiles);
            return windowsProgramFiles.getAbsolutePath();
        }

        // windows 64
        var windowsProgramFiles64 = Paths.get("c:", "Program Files", "Steam", "steamapps", "common", "Quake 2").toFile();
        if (windowsProgramFiles64.exists()) {
            System.out.println("Auto-detected steam q2 installation at :" + windowsProgramFiles64);
            return windowsProgramFiles64.getAbsolutePath();
        }


        return ".";
    }

    /**
     * set baseq2 directory
     */
    public static void setCDDir() {
        fs_cddir = Cvar.getInstance().Get("cddir", "", CVAR_ARCHIVE);
        if (fs_cddir.string.length() > 0)
            AddGameDirectory(fs_cddir.string, false);
    }

    private static void initVfsCompat() {
        String gameMod = null;
        if (fs_gamedirvar != null && !fs_gamedirvar.string.isEmpty()) {
            gameMod = fs_gamedirvar.string;
        }
        fs_vfsCompat = new VfsBackedFileSystem();
        fs_vfsCompat.configure(
                Paths.get(fs_basedir.string),
                Globals.BASEQ2,
                gameMod,
                true,
                isVfsCaseSensitive()
        );
    }

    private static void updateVfsCompat(String gameName) {
        if (fs_vfsCompat == null) {
            return;
        }
        if (gameName == null || gameName.isEmpty() || gameName.equals(Globals.BASEQ2)) {
            fs_vfsCompat.setGameMod(null);
        } else {
            fs_vfsCompat.setGameMod(gameName);
        }
    }

    private static boolean isVfsCaseSensitive() {
        return fs_casesensitive != null && fs_casesensitive.value != 0.0f;
    }

    private static void syncVfsCaseSensitivity() {
        if (fs_vfsCompat == null || fs_casesensitive == null) {
            return;
        }
        boolean desired = isVfsCaseSensitive();
        if (fs_vfsCompat.isCaseSensitive() != desired) {
            fs_vfsCompat.setCaseSensitive(desired);
        }
    }

    private static File resolveLinkedFile(String filename) {
        if (filename == null) {
            return null;
        }
        for (filelink_t link : fs_links) {
            if (filename.startsWith(link.from)) {
                File file = new File(link.to + filename.substring(link.from.length()));
                if (file.canRead()) {
                    return file;
                }
            }
        }
        return null;
    }
    
    //	RAFAEL
    /*
     * Developer_searchpath
     */
    public static int Developer_searchpath() {
        if (fs_vfsCompat != null) {
            syncVfsCaseSensitivity();
            for (String root : fs_vfsCompat.debugLooseMountRoots()) {
                String normalized = root.replace('\\', '/').toLowerCase(Locale.ROOT);
                if (normalized.contains("/xatrix"))
                    return 1;
                if (normalized.contains("/rogue"))
                    return 2;
            }
        }

        for (SearchPath s: searchPaths) {
            if (s.filename.contains("xatrix"))
                return 1;

            if (s.filename.contains("rogue"))
                return 2;
        }

        return 0;
    }
}
