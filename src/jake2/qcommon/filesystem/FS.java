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

import jake2.Defines;
import jake2.Globals;
import jake2.game.Cmd;
import jake2.game.cvar_t;
import jake2.qcommon.CDAudio;
import jake2.qcommon.Cbuf;
import jake2.qcommon.Com;
import jake2.qcommon.Cvar;
import jake2.sys.Sys;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * FS
 * 
 * @author cwei
 */
public final class FS extends Globals {

    /*
     * ==================================================
     * 
     * QUAKE FILESYSTEM
     * 
     * ==================================================
     */

    public static class packfile_t {
        static final int SIZE = 64;

        static final int NAME_SIZE = 56;

        String name; // char name[56]

        int filepos, filelen;

        public String toString() {
            return name + " [ length: " + filelen + " pos: " + filepos + " ]";
        }
    }

    public static class pack_t {
        String filename;

        RandomAccessFile handle;
        
        ByteBuffer backbuffer;

        int numfiles;

        Hashtable files; // with packfile_t entries
    }

    private static String fs_gamedir;

    private static String fs_userdir;

    private static cvar_t fs_basedir;

    private static cvar_t fs_cddir;

    public static cvar_t fs_gamedirvar;

    public static class filelink_t {
        String from;

        int fromlength;

        String to;
    }

    // with filelink_t entries
    private static List fs_links = new LinkedList();

    public static class searchpath_t {
        String filename;

        pack_t pack; // only one of filename or pack will be used

        searchpath_t next;
    }

    private static searchpath_t fs_searchpaths;

    // without gamedirs
    private static searchpath_t fs_base_searchpaths;

    /*
     * All of Quake's data access is through a hierchal file system, but the
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

    /*
     * FCloseFile
     * 
     * For some reason, other dll's can't just call fclose() on files returned
     * by FS_FOpenFile...
     */
    public static void FCloseFile(RandomAccessFile file) throws IOException {
        file.close();
    }

    public static void FCloseFile(InputStream stream) throws IOException {
        stream.close();
    }

    public static int FileLength(String filename) {
        searchpath_t search;
        String netpath;
        pack_t pak;
        filelink_t link;

        file_from_pak = 0;

        // check for links first
        for (Iterator it = fs_links.iterator(); it.hasNext();) {
            link = (filelink_t) it.next();

            if (filename.regionMatches(0, link.from, 0, link.fromlength)) {
                netpath = link.to + filename.substring(link.fromlength);
                File file = new File(netpath);
                if (file.canRead()) {
                    Com.DPrintf("link file: " + netpath + '\n');
                    return (int) file.length();
                }
                return -1;
            }
        }

        // search through the path, one element at a time

        for (search = fs_searchpaths; search != null; search = search.next) {
            // is the element a pak file?
            if (search.pack != null) {
                // look through all the pak file elements
                pak = search.pack;
                filename = filename.toLowerCase();
                packfile_t entry = (packfile_t) pak.files.get(filename);

                if (entry != null) {
                    // found it!
                    file_from_pak = 1;
                    Com.DPrintf("PackFile: " + pak.filename + " : " + filename
                            + '\n');
                    // open a new file on the pakfile
                    File file = new File(pak.filename);
                    if (!file.canRead()) {
                        Com.Error(Defines.ERR_FATAL, "Couldn't reopen "
                                + pak.filename);
                    }
                    return entry.filelen;
                }
            } else {
                // check a file in the directory tree
                netpath = search.filename + '/' + filename;

                File file = new File(netpath);
                if (!file.canRead())
                    continue;

                Com.DPrintf("FindFile: " + netpath + '\n');

                return (int) file.length();
            }
        }
        Com.DPrintf("FindFile: can't find " + filename + '\n');
        return -1;
    }

    public static int file_from_pak = 0;

    /*
     * FOpenFile
     * 
     * Finds the file in the search path. returns a RadomAccesFile. Used for
     * streaming data out of either a pak file or a seperate file.
     */
    public static RandomAccessFile FOpenFile(String filename)
            throws IOException {
        searchpath_t search;
        String netpath;
        pack_t pak;
        filelink_t link;
        File file = null;

        file_from_pak = 0;

        // check for links first
        for (Iterator it = fs_links.iterator(); it.hasNext();) {
            link = (filelink_t) it.next();

            //			if (!strncmp (filename, link->from, link->fromlength))
            if (filename.regionMatches(0, link.from, 0, link.fromlength)) {
                netpath = link.to + filename.substring(link.fromlength);
                file = new File(netpath);
                if (file.canRead()) {
                    //Com.DPrintf ("link file: " + netpath +'\n');
                    return new RandomAccessFile(file, "r");
                }
                return null;
            }
        }

        //
        // search through the path, one element at a time
        //
        for (search = fs_searchpaths; search != null; search = search.next) {
            // is the element a pak file?
            if (search.pack != null) {
                // look through all the pak file elements
                pak = search.pack;
                filename = filename.toLowerCase();
                packfile_t entry = (packfile_t) pak.files.get(filename);

                if (entry != null) {
                    // found it!
                    file_from_pak = 1;
                    //Com.DPrintf ("PackFile: " + pak.filename + " : " +
                    // filename + '\n');
                    file = new File(pak.filename);
                    if (!file.canRead())
                        Com.Error(Defines.ERR_FATAL, "Couldn't reopen "
                                + pak.filename);
                    if (pak.handle == null || !pak.handle.getFD().valid()) {
                        // hold the pakfile handle open
                        pak.handle = new RandomAccessFile(pak.filename, "r");
                    }
                    // open a new file on the pakfile

                    RandomAccessFile raf = new RandomAccessFile(file, "r");
                    raf.seek(entry.filepos);

                    return raf;
                }
            } else {
                // check a file in the directory tree
                netpath = search.filename + '/' + filename;

                file = new File(netpath);
                if (!file.canRead())
                    continue;

                //Com.DPrintf("FindFile: " + netpath +'\n');

                return new RandomAccessFile(file, "r");
            }
        }
        //Com.DPrintf ("FindFile: can't find " + filename + '\n');
        return null;
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
            		CDAudio.Stop();
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
     * Filename are reletive to the quake search path a null buffer will just
     * return the file content as byte[]
     */
    public static byte[] LoadFile(String path) {
        RandomAccessFile file;

        byte[] buf = null;
        int len = 0;

        // TODO hack for bad strings (fuck \0)
        int index = path.indexOf('\0');
        if (index != -1)
            path = path.substring(0, index);

        // look for it in the filesystem or pack files
        len = FileLength(path);

        if (len < 1)
            return null;

        try {
            file = FOpenFile(path);
            //Read(buf = new byte[len], len, h);
            buf = new byte[len];
            file.readFully(buf);
            file.close();
        } catch (IOException e) {
            Com.Error(Defines.ERR_FATAL, e.toString());
        }
        return buf;
    }

    /*
     * LoadMappedFile
     * 
     * Filename are reletive to the quake search path a null buffer will just
     * return the file content as ByteBuffer (memory mapped)
     */
    public static ByteBuffer LoadMappedFile(String filename) {
        searchpath_t search;
        String netpath;
        pack_t pak;
        filelink_t link;
        File file = null;

        int fileLength = 0;
        FileChannel channel = null;
        FileInputStream input = null;
        ByteBuffer buffer = null;

        file_from_pak = 0;

        try {
            // check for links first
            for (Iterator it = fs_links.iterator(); it.hasNext();) {
                link = (filelink_t) it.next();

                if (filename.regionMatches(0, link.from, 0, link.fromlength)) {
                    netpath = link.to + filename.substring(link.fromlength);
                    file = new File(netpath);
                    if (file.canRead()) {
                        input = new FileInputStream(file);
                        channel = input.getChannel();
                        fileLength = (int) channel.size();
                        buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0,
                                fileLength);
                        input.close();
                        return buffer;
                    }
                    return null;
                }
            }

            //
            // search through the path, one element at a time
            //
            for (search = fs_searchpaths; search != null; search = search.next) {
                // is the element a pak file?
                if (search.pack != null) {
                    // look through all the pak file elements
                    pak = search.pack;
                    filename = filename.toLowerCase();
                    packfile_t entry = (packfile_t) pak.files.get(filename);

                    if (entry != null) {
                        // found it!
                        file_from_pak = 1;
                        //Com.DPrintf ("PackFile: " + pak.filename + " : " +
                        // filename + '\n');
                        file = new File(pak.filename);
                        if (!file.canRead())
                            Com.Error(Defines.ERR_FATAL, "Couldn't reopen "
                                    + pak.filename);
                        if (pak.handle == null || !pak.handle.getFD().valid()) {
                            // hold the pakfile handle open
                            pak.handle = new RandomAccessFile(pak.filename, "r");
                        }
                        // open a new file on the pakfile
                        if (pak.backbuffer == null) {
                            channel = pak.handle.getChannel();
                            pak.backbuffer = channel.map(
                                    FileChannel.MapMode.READ_ONLY, 0,
                                    pak.handle.length());
                            channel.close();
                        }
                        pak.backbuffer.position(entry.filepos);
                        buffer = pak.backbuffer.slice();
                        buffer.limit(entry.filelen);
                        return buffer;
                    }
                } else {
                    // check a file in the directory tree
                    netpath = search.filename + '/' + filename;

                    file = new File(netpath);
                    if (!file.canRead())
                        continue;

                    //Com.DPrintf("FindFile: " + netpath +'\n');
                    input = new FileInputStream(file);
                    channel = input.getChannel();
                    fileLength = (int) channel.size();
                    buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0,
                            fileLength);
                    input.close();
                    return buffer;
                }
            }
        } catch (Exception e) {
        }
        try {
            if (input != null)
                input.close();
            else if (channel != null && channel.isOpen())
                channel.close();
        } catch (IOException ioe) {
        }
        return null;
    }

    /*
     * FreeFile
     */
    public static void FreeFile(byte[] buffer) {
        buffer = null;
    }

    private static final int IDPAKHEADER = (('K' << 24) + ('C' << 16) + ('A' << 8) + 'P');

    static class dpackheader_t {
        int ident; // IDPAKHEADER

        int dirofs;

        int dirlen;
    }

    private static final int MAX_FILES_IN_PACK = 4096;

    // buffer for C-Strings char[56]
    private static byte[] tmpText = new byte[packfile_t.NAME_SIZE];

    /*
     * LoadPackFile
     * 
     * Takes an explicit (not game tree related) path to a pak file.
     * 
     * Loads the header and directory, adding the files at the beginning of the
     * list so they override previous pack files.
     */
    private static pack_t LoadPackFile(String packfile) {

        dpackheader_t header;
        Hashtable newfiles;
        RandomAccessFile file;
        int numpackfiles = 0;
        pack_t pack = null;
        //		unsigned checksum;
        //
        try {
        	file = new RandomAccessFile(packfile, "r");
        	FileChannel fc = file.getChannel();
            ByteBuffer packhandle = fc.map(FileChannel.MapMode.READ_ONLY, 0, file.length());
            packhandle.order(ByteOrder.LITTLE_ENDIAN);
 
            fc.close();
            
            if (packhandle == null || packhandle.limit() < 1)
                return null;
            //
            header = new dpackheader_t();
            header.ident = packhandle.getInt();
            header.dirofs = packhandle.getInt();
            header.dirlen = packhandle.getInt();

            if (header.ident != IDPAKHEADER)
                Com.Error(Defines.ERR_FATAL, packfile + " is not a packfile");

            numpackfiles = header.dirlen / packfile_t.SIZE;

            if (numpackfiles > MAX_FILES_IN_PACK)
                Com.Error(Defines.ERR_FATAL, packfile + " has " + numpackfiles
                        + " files");

            newfiles = new Hashtable(numpackfiles);

            packhandle.position(header.dirofs);

            // parse the directory
            packfile_t entry = null;

            for (int i = 0; i < numpackfiles; i++) {
                packhandle.get(tmpText);

                entry = new packfile_t();
                entry.name = new String(tmpText).trim();
                entry.filepos = packhandle.getInt();
                entry.filelen = packhandle.getInt();

                newfiles.put(entry.name.toLowerCase(), entry);
            }

        } catch (IOException e) {
            Com.DPrintf(e.getMessage() + '\n');
            return null;
        }

        pack = new pack_t();
        pack.filename = new String(packfile);
        pack.handle = file;
        pack.numfiles = numpackfiles;
        pack.files = newfiles;

        Com.Printf("Added packfile " + packfile + " (" + numpackfiles
                + " files)\n");

        return pack;
    }

    /*
     * AddGameDirectory
     * 
     * Sets fs_gamedir, adds the directory to the head of the path, then loads
     * and adds pak1.pak pak2.pak ...
     */
    private static void AddGameDirectory(String dir) {
        int i;
        searchpath_t search;
        pack_t pak;
        String pakfile;

        fs_gamedir = new String(dir);

        //
        // add the directory to the search path
        // ensure fs_userdir is first in searchpath
        search = new searchpath_t();
        search.filename = new String(dir);
        if (fs_searchpaths != null) {
            search.next = fs_searchpaths.next;
            fs_searchpaths.next = search;
        } else {
            fs_searchpaths = search;
        }

        //
        // add any pak files in the format pak0.pak pak1.pak, ...
        //
        for (i = 0; i < 10; i++) {
            pakfile = dir + "/pak" + i + ".pak";
            if (!(new File(pakfile).canRead()))
                continue;

            pak = LoadPackFile(pakfile);
            if (pak == null)
                continue;

            search = new searchpath_t();
            search.pack = pak;
            search.filename = "";
            search.next = fs_searchpaths;
            fs_searchpaths = search;
        }
    }

    /*
     * Gamedir
     * 
     * Called to find where to write a file (demos, savegames, etc)
     * this is modified to <user.home>/.jake2 
     */
    public static String Gamedir() {
        return (fs_userdir != null) ? fs_userdir : Globals.BASEDIRNAME;
    }

    /*
     * BaseGamedir
     * 
     * Called to find where to write a downloaded file
     */
    public static String BaseGamedir() {
        return (fs_gamedir != null) ? fs_gamedir : Globals.BASEDIRNAME;
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
            name = fs_basedir.string + '/' + Globals.BASEDIRNAME
                    + "/autoexec.cfg";
        }

        int canthave = Defines.SFF_SUBDIR | Defines.SFF_HIDDEN
                | Defines.SFF_SYSTEM;

        if (Sys.FindAll(name, 0, canthave) != null) {
            Cbuf.AddText("exec autoexec.cfg\n");
        }
    }

    /*
     * SetGamedir
     * 
     * Sets the gamedir and path to a different directory.
     */
    public static void SetGamedir(String dir) {
        searchpath_t next;

        if (dir.indexOf("..") != -1 || dir.indexOf("/") != -1
                || dir.indexOf("\\") != -1 || dir.indexOf(":") != -1) {
            Com.Printf("Gamedir should be a single filename, not a path\n");
            return;
        }

        //
        // free up any current game dir info
        //
        while (fs_searchpaths != fs_base_searchpaths) {
            if (fs_searchpaths.pack != null) {
                try {
                    fs_searchpaths.pack.handle.close();
                } catch (IOException e) {
                    Com.DPrintf(e.getMessage() + '\n');
                }
                // clear the hashtable
                fs_searchpaths.pack.files.clear();
                fs_searchpaths.pack.files = null;
                fs_searchpaths.pack = null;
            }
            next = fs_searchpaths.next;
            fs_searchpaths = null;
            fs_searchpaths = next;
        }

        //
        // flush all data, so it will be forced to reload
        //
        if ((Globals.dedicated != null) && (Globals.dedicated.value == 0.0f))
            Cbuf.AddText("vid_restart\nsnd_restart\n");

        fs_gamedir = fs_basedir.string + '/' + dir;

        if (dir.equals(Globals.BASEDIRNAME) || (dir.length() == 0)) {
            Cvar.FullSet("gamedir", "", CVAR_SERVERINFO | CVAR_NOSET);
            Cvar.FullSet("game", "", CVAR_LATCH | CVAR_SERVERINFO);
        } else {
            Cvar.FullSet("gamedir", dir, CVAR_SERVERINFO | CVAR_NOSET);
            if (fs_cddir.string != null && fs_cddir.string.length() > 0)
                AddGameDirectory(fs_cddir.string + '/' + dir);

            AddGameDirectory(fs_basedir.string + '/' + dir);
        }
    }

    /*
     * Link_f
     * 
     * Creates a filelink_t
     */
    private static void Link_f() {
        filelink_t entry = null;

        if (Cmd.Argc() != 3) {
            Com.Printf("USAGE: link <from> <to>\n");
            return;
        }

        // see if the link already exists
        for (Iterator it = fs_links.iterator(); it.hasNext();) {
            entry = (filelink_t) it.next();

            if (entry.from.equals(Cmd.Argv(1))) {
                if (Cmd.Argv(2).length() < 1) {
                    // delete it
                    it.remove();
                    return;
                }
                entry.to = new String(Cmd.Argv(2));
                return;
            }
        }

        // create a new link if the <to> is not empty
        if (Cmd.Argv(2).length() > 0) {
            entry = new filelink_t();
            entry.from = new String(Cmd.Argv(1));
            entry.fromlength = entry.from.length();
            entry.to = new String(Cmd.Argv(2));
            fs_links.add(entry);
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

    /*
     * Dir_f
     */
    private static void Dir_f() {
        String path = null;
        String findname = null;
        String wildcard = "*.*";
        String[] dirnames;

        if (Cmd.Argc() != 1) {
            wildcard = Cmd.Argv(1);
        }

        while ((path = NextPath(path)) != null) {
            String tmp = findname;

            findname = path + '/' + wildcard;

            if (tmp != null)
                tmp.replaceAll("\\\\", "/");

            Com.Printf("Directory of " + findname + '\n');
            Com.Printf("----\n");

            dirnames = ListFiles(findname, 0, 0);

            if (dirnames != null) {
                int index = 0;
                for (int i = 0; i < dirnames.length; i++) {
                    if ((index = dirnames[i].lastIndexOf('/')) > 0) {
                        Com.Printf(dirnames[i].substring(index + 1, dirnames[i]
                                .length()) + '\n');
                    } else {
                        Com.Printf(dirnames[i] + '\n');
                    }
                }
            }

            Com.Printf("\n");
        }
    }

    /*
     * Path_f
     */
    private static void Path_f() {

        searchpath_t s;
        filelink_t link;

        Com.Printf("Current search path:\n");
        for (s = fs_searchpaths; s != null; s = s.next) {
            if (s == fs_base_searchpaths)
                Com.Printf("----------\n");
            if (s.pack != null)
                Com.Printf(s.pack.filename + " (" + s.pack.numfiles
                        + " files)\n");
            else
                Com.Printf(s.filename + '\n');
        }

        Com.Printf("\nLinks:\n");
        for (Iterator it = fs_links.iterator(); it.hasNext();) {
            link = (filelink_t) it.next();
            Com.Printf(link.from + " : " + link.to + '\n');
        }
    }

    /*
     * NextPath
     * 
     * Allows enumerating all of the directories in the search path
     */
    public static String NextPath(String prevpath) {
        searchpath_t s;
        String prev;

        if (prevpath == null || prevpath.length() == 0)
            return fs_gamedir;

        prev = fs_gamedir;
        for (s = fs_searchpaths; s != null; s = s.next) {
            if (s.pack != null)
                continue;

            if (prevpath == prev)
                return s.filename;

            prev = s.filename;
        }

        return null;
    }

    /*
     * InitFilesystem
     */
    public static void InitFilesystem() {
        Cmd.AddCommand("path", (List<String> args) -> Path_f());
        Cmd.AddCommand("link", (List<String> args) -> Link_f());
        Cmd.AddCommand("dir", (List<String> args) -> Dir_f());

        fs_userdir = System.getProperty("user.home") + "/.jake2";
        FS.CreatePath(fs_userdir + "/");
        FS.AddGameDirectory(fs_userdir);

        //
        // basedir <path>
        // allows the game to run from outside the data tree
        //
        fs_basedir = Cvar.Get("basedir", ".", CVAR_NOSET);

        //
        // cddir <path>
        // Logically concatenates the cddir after the basedir for
        // allows the game to run from outside the data tree
        //

        setCDDir();

        //
        // start up with baseq2 by default
        //
        AddGameDirectory(fs_basedir.string + '/' + Globals.BASEDIRNAME);

        // any set gamedirs will be freed up to here
        markBaseSearchPaths();

        // check for game override
        fs_gamedirvar = Cvar.Get("game", "", CVAR_LATCH | CVAR_SERVERINFO);

        if (fs_gamedirvar.string.length() > 0)
            SetGamedir(fs_gamedirvar.string);
    }

    /**
     * set baseq2 directory
     */
    public static void setCDDir() {
        fs_cddir = Cvar.Get("cddir", "", CVAR_ARCHIVE);
        if (fs_cddir.string.length() > 0)
            AddGameDirectory(fs_cddir.string);
    }
    
    public static void markBaseSearchPaths() {
        // any set gamedirs will be freed up to here
        fs_base_searchpaths = fs_searchpaths;
    }

    //	RAFAEL
    /*
     * Developer_searchpath
     */
    public static int Developer_searchpath(int who) {

        // PMM - warning removal
        //	 char *start;
        searchpath_t s;

        for (s = fs_searchpaths; s != null; s = s.next) {
            if (s.filename.indexOf("xatrix") != -1)
                return 1;

            if (s.filename.indexOf("rogue") != -1)
                return 2;
        }

        return 0;
    }
}