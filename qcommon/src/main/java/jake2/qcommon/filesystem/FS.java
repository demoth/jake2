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
import jake2.qcommon.vfs.VfsDebugCommands;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
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

    private static String fs_gamedir;

    private static String fs_userdir;

    private static cvar_t fs_basedir;

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
        if (filename == null || filename.isBlank()) {
            return false;
        }
        if (filename.startsWith("/")) {
            return new File(filename).canRead();
        }
        File linkedFile = resolveLinkedFile(filename);
        if (linkedFile != null) {
            return true;
        }
        syncVfsCaseSensitivity();
        return fs_vfsCompat != null && fs_vfsCompat.exists(filename);
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
        if (filename == null || filename.isBlank()) {
            return null;
        }

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

    /*
     * LoadFile
     * 
     * Filename are relative to the quake search path a null buffer will just
     * return the file content as byte[]
     */
    public static byte[] LoadFile(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        // TODO hack for bad strings (fuck \0)
        int index = path.indexOf('\0');
        if (index != -1)
            path = path.substring(0, index);

        if (path.startsWith("/")) {
            try {
                return Files.readAllBytes(Paths.get(path));
            } catch (IOException e) {
                return null;
            }
        }

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
            if (!isVfsCaseSensitive()) {
                bytes = fs_vfsCompat.loadFile(path.toLowerCase(Locale.ROOT));
                if (bytes != null) {
                    Com.Printf("Found file by lowercase: " + path + "\n");
                    return bytes;
                }
            }
        }
        return null;
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
            QuakeFile file = FOpenFile(filename);
            if (file == null) {
                return null;
            }
            byte[] data = file.toBytes();
            return ByteBuffer.wrap(data).asReadOnlyBuffer();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Called to find where to write a file (demos, savegames, etc)
     * this is modified to `user.home`/.jake2/`game_name`
     */
    public static String getWriteDir() {
        return (fs_userdir != null) ? fs_userdir : Globals.BASEQ2;
    }

    private static void setWriteDir(String gameName) {
        fs_userdir = System.getProperty("user.home") + "/.jake2/" + gameName;
        FS.CreatePath(fs_userdir + "/");
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
        }

        setWriteDir(gameName);
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

    /**
     * Legacy compatibility API used by deprecated old-client UI flows.
     * New code should use VFS snapshot/debug listings instead.
     */
    @Deprecated(forRemoval = true)
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
            Com.Printf("Directory of " + wildcard + '\n');
            for (String match : matches) {
                Com.Printf("  " + match + '\n');
            }
            if (matches.isEmpty()) {
                Com.Printf("  <no matches>\n");
            }
            Com.Printf("\n");
            return;
        }
        Com.Printf("VFS compatibility layer is not initialized.\n");
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
            Com.Printf("VFS compatibility layer is not initialized.\n");
        }

        if (!fs_links.isEmpty()) {
            Com.Printf("\nLinks:\n");
            for (filelink_t link : fs_links) {
                Com.Printf(link.from + " : " + link.to + '\n');
            }
        }
    }

    /**
     * Legacy compatibility API used by deprecated old-client UI flows.
     * New code should use VFS mount snapshots instead.
     */
    @Deprecated(forRemoval = true)
    public static String NextPath(String prevpath) {
        if (fs_vfsCompat == null) {
            return prevpath == null || prevpath.length() == 0 ? fs_gamedir : null;
        }
        syncVfsCaseSensitivity();
        List<String> roots = fs_vfsCompat.debugLooseMountRoots();
        if (roots.isEmpty()) {
            return prevpath == null || prevpath.length() == 0 ? fs_gamedir : null;
        }
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

    /*
     * InitFilesystem
     */
    public static void InitFilesystem() {
        Cmd.AddCommand("path", (List<String> args) -> Path_f());
        Cmd.AddCommand("link", FS::Link_f);
        Cmd.AddCommand("dir", FS::Dir_f);
        Cmd.AddCommand("ls", FS::Dir_f);
        VfsDebugCommands.register(new VfsDebugCommands.Provider() {
            @Override
            public boolean isInitialized() {
                return fs_vfsCompat != null;
            }

            @Override
            public List<String> resolvedFiles() {
                syncVfsCaseSensitivity();
                return fs_vfsCompat == null ? List.of() : fs_vfsCompat.debugResolvedFiles();
            }

            @Override
            public List<String> mounts() {
                syncVfsCaseSensitivity();
                return fs_vfsCompat == null ? List.of() : fs_vfsCompat.debugMounts();
            }

            @Override
            public List<String> overrides() {
                syncVfsCaseSensitivity();
                return fs_vfsCompat == null ? List.of() : fs_vfsCompat.debugOverrides();
            }
        });

        // by default all saves, screenshots etc go to ~/.jake2/baseq2
        // overridden by 'game' cvar, i.e xatrix saves to go ~/.jake2/xatrix
        setWriteDir(Globals.BASEQ2);

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

        fs_gamedir = fs_basedir.string + '/' + Globals.BASEQ2;

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
    
    /**
     * Legacy compatibility API used by deprecated old-client menu/render paths.
     * New code should infer behavior from current game/mod state directly.
     */
    @Deprecated(forRemoval = true)
    public static int Developer_searchpath() {
        if (fs_vfsCompat == null) {
            return 0;
        }
        syncVfsCaseSensitivity();
        for (String root : fs_vfsCompat.debugLooseMountRoots()) {
            String normalized = root.replace('\\', '/').toLowerCase(Locale.ROOT);
            if (normalized.contains("/xatrix"))
                return 1;
            if (normalized.contains("/rogue"))
                return 2;
        }

        return 0;
    }
}
