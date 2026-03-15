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
import jake2.qcommon.Globals;
import jake2.qcommon.sys.Sys;
import jake2.qcommon.vfs.EngineFilesystemLifecycle;
import jake2.qcommon.vfs.EngineVfs;
import jake2.qcommon.vfs.EngineWriteRoot;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
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
 *
 * @deprecated Active code should use `VirtualFileSystem` / `WritableFileSystem`
 * directly. `FS` remains only as a legacy compatibility facade during migration.
 */
@Deprecated(forRemoval = true)
public final class FS extends Globals {

    /*
     * ==================================================
     * 
     * QUAKE FILESYSTEM
     * 
     * ==================================================
     */

    /*
     * CreatePath
     * 
     * Creates any directories needed to store the given filename.
     */
    public static void CreatePath(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        EngineWriteRoot.ensureParentDirectories(Path.of(path));
    }

    public static boolean FileExists(String filename) {
        EngineFilesystemLifecycle.syncVfsCaseSensitivity();
        return EngineVfs.exists(filename);
    }

    public static boolean IsFromPack(String filename) {
        EngineFilesystemLifecycle.syncVfsCaseSensitivity();
        return EngineVfs.isFromPack(filename);
    }

    /**
     * FOpenFile
     * 
     * Finds the file in the search path. returns a RadomAccesFile. Used for
     * streaming data out of either a pak file or a separate file.
     *
     * @return null in case of error
     */
    @Deprecated(forRemoval = true)
    public static QuakeFile FOpenFile(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }

        try {
            EngineFilesystemLifecycle.syncVfsCaseSensitivity();
            VfsBackedFileSystem compatFileSystem = EngineFilesystemLifecycle.compatFileSystem();
            if (compatFileSystem != null) {
                QuakeFile vfsFile = compatFileSystem.openFile(filename);
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
    @Deprecated(forRemoval = true)
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
    @Deprecated(forRemoval = true)
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

        EngineFilesystemLifecycle.syncVfsCaseSensitivity();
        byte[] bytes = EngineVfs.loadBytes(path);
        if (bytes != null) {
            return bytes;
        }
        if (!EngineFilesystemLifecycle.isVfsCaseSensitive()) {
            bytes = EngineVfs.loadBytes(path.toLowerCase(Locale.ROOT));
            if (bytes != null) {
                Com.Printf("Found file by lowercase: " + path + "\n");
                return bytes;
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
        EngineFilesystemLifecycle.syncVfsCaseSensitivity();
        VfsBackedFileSystem compatFileSystem = EngineFilesystemLifecycle.compatFileSystem();
        if (compatFileSystem != null) {
            ByteBuffer mapped = compatFileSystem.loadMappedFile(filename);
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
        return EngineWriteRoot.pathString();
    }

    /*
     * ExecAutoexec
     */
    public static void ExecAutoexec() {
        EngineFilesystemLifecycle.execAutoexec();
    }

    /**
     *
     * Sets the gamedir and path to a different directory.
     *
     * used when game cvar is changed
     */
    public static void SetGamedir(String gameName) {
        EngineFilesystemLifecycle.setGameDir(gameName);
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
     * Legacy compatibility API used by deprecated old-client UI flows.
     * New code should use VFS mount snapshots instead.
     */
    @Deprecated(forRemoval = true)
    public static String NextPath(String prevpath) {
        VfsBackedFileSystem compatFileSystem = EngineFilesystemLifecycle.compatFileSystem();
        if (compatFileSystem == null) {
            return prevpath == null || prevpath.length() == 0 ? EngineFilesystemLifecycle.currentGameDir() : null;
        }
        EngineFilesystemLifecycle.syncVfsCaseSensitivity();
        List<String> roots = compatFileSystem.debugLooseMountRoots();
        if (roots.isEmpty()) {
            return prevpath == null || prevpath.length() == 0 ? EngineFilesystemLifecycle.currentGameDir() : null;
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
        EngineFilesystemLifecycle.init();
    }

    /**
     * Legacy compatibility API used by deprecated old-client menu/render paths.
     * New code should infer behavior from current game/mod state directly.
     */
    @Deprecated(forRemoval = true)
    public static int Developer_searchpath() {
        VfsBackedFileSystem compatFileSystem = EngineFilesystemLifecycle.compatFileSystem();
        if (compatFileSystem == null) {
            return 0;
        }
        EngineFilesystemLifecycle.syncVfsCaseSensitivity();
        for (String root : compatFileSystem.debugLooseMountRoots()) {
            String normalized = root.replace('\\', '/').toLowerCase(Locale.ROOT);
            if (normalized.contains("/xatrix"))
                return 1;
            if (normalized.contains("/rogue"))
                return 2;
        }

        return 0;
    }
}
