package jake2.qcommon.vfs;

import jake2.qcommon.Com;
import jake2.qcommon.Defines;
import jake2.qcommon.Globals;
import jake2.qcommon.exec.Cbuf;
import jake2.qcommon.exec.Cmd;
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.exec.cvar_t;
import jake2.qcommon.filesystem.VfsBackedFileSystem;
import jake2.qcommon.sys.Sys;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Shared lifecycle/bootstrap owner for engine filesystem setup.
 *
 * <p>This carries the remaining startup, gamedir, and compatibility bridge
 * state while `FS` is being reduced to a legacy facade.
 */
public final class EngineFilesystemLifecycle {
    private static String gameDirPath;
    private static String userDirPath;
    private static cvar_t baseDir;
    private static cvar_t caseSensitive;
    private static cvar_t gameDirVar;
    private static VfsBackedFileSystem compatFileSystem;

    private EngineFilesystemLifecycle() {
    }

    public static void init() {
        Cmd.AddCommand("path", args -> printPath());
        Cmd.AddCommand("dir", EngineFilesystemLifecycle::printDir);
        Cmd.AddCommand("ls", EngineFilesystemLifecycle::printDir);
        Cmd.AddCommand("packfiles", EngineFilesystemLifecycle::printPackFiles);
        Cmd.AddCommand("packdir", EngineFilesystemLifecycle::printPackFiles);
        VfsDebugCommands.register(new VfsDebugCommands.Provider() {
            @Override
            public boolean isInitialized() {
                return compatFileSystem != null;
            }

            @Override
            public List<String> resolvedFiles() {
                syncVfsCaseSensitivity();
                return compatFileSystem == null ? List.of() : compatFileSystem.debugResolvedFiles();
            }

            @Override
            public List<String> mounts() {
                syncVfsCaseSensitivity();
                return compatFileSystem == null ? List.of() : compatFileSystem.debugMounts();
            }

            @Override
            public List<String> overrides() {
                syncVfsCaseSensitivity();
                return compatFileSystem == null ? List.of() : compatFileSystem.debugOverrides();
            }

            @Override
            public VfsResult<String> mountPackage(String packagePath) {
                syncVfsCaseSensitivity();
                if (compatFileSystem == null) {
                    return VfsResult.fail("VFS compatibility layer is not initialized.");
                }
                return compatFileSystem.mountPackage(packagePath);
            }

            @Override
            public VfsResult<Void> unmountPackage(String mountId) {
                syncVfsCaseSensitivity();
                if (compatFileSystem == null) {
                    return VfsResult.fail("VFS compatibility layer is not initialized.");
                }
                return compatFileSystem.unmountPackage(mountId);
            }

            @Override
            public void rebuildIndex(RebuildScope scope) {
                syncVfsCaseSensitivity();
                if (compatFileSystem != null) {
                    compatFileSystem.rebuildIndex(scope);
                }
            }
        });

        setWriteDir(Globals.BASEQ2);

        if (new File("./baseq2").exists()) {
            baseDir = Cvar.getInstance().Get("basedir", ".", Defines.CVAR_NOSET);
        } else {
            baseDir = Cvar.getInstance().Get("basedir", autodetectBasedir(), Defines.CVAR_NOSET);
        }

        gameDirPath = baseDir.string + '/' + Globals.BASEQ2;
        gameDirVar = Cvar.getInstance().Get("game", "", Defines.CVAR_LATCH | Defines.CVAR_SERVERINFO);
        caseSensitive = Cvar.getInstance().Get("fs_casesensitive", "0", Defines.CVAR_ARCHIVE);

        if (!gameDirVar.string.isEmpty()) {
            setGameDir(gameDirVar.string);
        }

        initCompatFileSystem();
    }

    public static void execAutoexec() {
        String name;
        if (userDirPath != null && !userDirPath.isEmpty()) {
            name = userDirPath + "/autoexec.cfg";
        } else {
            name = baseDir.string + '/' + Globals.BASEQ2 + "/autoexec.cfg";
        }

        int canthave = Defines.SFF_SUBDIR | Defines.SFF_HIDDEN | Defines.SFF_SYSTEM;
        if (Sys.FindAll(name, 0, canthave) != null) {
            Cbuf.AddText("exec autoexec.cfg\n");
        }
    }

    public static void setGameDir(String gameName) {
        if (gameName.contains("..") || gameName.contains("/") || gameName.contains("\\") || gameName.contains(":")) {
            Com.Printf("Gamedir should be a single filename, not a path\n");
            return;
        }

        if ((Globals.dedicated != null) && (Globals.dedicated.value == 0.0f)) {
            Cbuf.AddText("vid_restart");
            Cbuf.AddText("snd_restart");
        }

        gameDirPath = baseDir.string + '/' + gameName;

        if (gameName.equals(Globals.BASEQ2) || gameName.isEmpty()) {
            Cvar.getInstance().FullSet("gamedir", "", Defines.CVAR_SERVERINFO | Defines.CVAR_NOSET);
            Cvar.getInstance().FullSet("game", "", Defines.CVAR_LATCH | Defines.CVAR_SERVERINFO);
        } else {
            Cvar.getInstance().FullSet("gamedir", gameName, Defines.CVAR_SERVERINFO | Defines.CVAR_NOSET);
        }

        setWriteDir(gameName);
        updateCompatGameMod(gameName);
    }

    public static VfsBackedFileSystem compatFileSystem() {
        return compatFileSystem;
    }

    public static String currentGameDir() {
        return gameDirPath;
    }

    public static boolean isVfsCaseSensitive() {
        return caseSensitive != null && caseSensitive.value != 0.0f;
    }

    public static void syncVfsCaseSensitivity() {
        if (compatFileSystem == null || caseSensitive == null) {
            return;
        }
        boolean desired = isVfsCaseSensitive();
        if (compatFileSystem.isCaseSensitive() != desired) {
            compatFileSystem.setCaseSensitive(desired);
        }
    }

    private static void setWriteDir(String gameName) {
        userDirPath = System.getProperty("user.home") + "/.jake2/" + gameName;
        EngineWriteRoot.setRoot(Path.of(userDirPath));
    }

    private static void initCompatFileSystem() {
        String gameMod = null;
        if (gameDirVar != null && !gameDirVar.string.isEmpty()) {
            gameMod = gameDirVar.string;
        }
        compatFileSystem = new VfsBackedFileSystem(EngineVfs.shared());
        compatFileSystem.configure(
                Paths.get(baseDir.string),
                Globals.BASEQ2,
                gameMod,
                true,
                isVfsCaseSensitive()
        );
    }

    private static void updateCompatGameMod(String gameName) {
        if (compatFileSystem == null) {
            return;
        }
        if (gameName == null || gameName.isEmpty() || gameName.equals(Globals.BASEQ2)) {
            compatFileSystem.setGameMod(null);
        } else {
            compatFileSystem.setGameMod(gameName);
        }
    }

    private static String autodetectBasedir() {
        String home = System.getProperty("user.home");
        if (home != null && !home.isBlank()) {
            File steamLinuxPath = Paths.get(home, ".steam", "steam", "steamapps", "common", "Quake 2").toFile();
            if (steamLinuxPath.exists()) {
                System.out.println("Auto-detected steam q2 installation at :" + steamLinuxPath);
                return steamLinuxPath.getAbsolutePath();
            }

            File macSteamPath = Paths.get(home, "Library", "Application Support", "Steam", "steamapps", "common", "Quake 2").toFile();
            if (macSteamPath.exists()) {
                System.out.println("Auto-detected steam q2 installation at :" + macSteamPath);
                return macSteamPath.getAbsolutePath();
            }
        }

        File windowsProgramFiles = Paths.get("c:", "Program Files (x86)", "Steam", "steamapps", "common", "Quake 2").toFile();
        if (windowsProgramFiles.exists()) {
            System.out.println("Auto-detected steam q2 installation at :" + windowsProgramFiles);
            return windowsProgramFiles.getAbsolutePath();
        }

        File windowsProgramFiles64 = Paths.get("c:", "Program Files", "Steam", "steamapps", "common", "Quake 2").toFile();
        if (windowsProgramFiles64.exists()) {
            System.out.println("Auto-detected steam q2 installation at :" + windowsProgramFiles64);
            return windowsProgramFiles64.getAbsolutePath();
        }

        return ".";
    }

    private static void printDir(List<String> args) {
        String wildcard = "*.*";
        if (args.size() >= 2) {
            wildcard = args.get(1);
        }

        if (compatFileSystem == null) {
            Com.Printf("VFS compatibility layer is not initialized.\n");
            return;
        }

        syncVfsCaseSensitivity();
        List<String> matches = compatFileSystem.debugFilesMatching(wildcard);
        Com.Printf("Directory of " + wildcard + '\n');
        for (String match : matches) {
            Com.Printf("  " + match + '\n');
        }
        if (matches.isEmpty()) {
            Com.Printf("  <no matches>\n");
        }
        Com.Printf("\n");
    }

    private static void printPackFiles(List<String> args) {
        if (args.size() != 2) {
            Com.Printf("USAGE: packfiles <packname>\n");
            return;
        }

        if (compatFileSystem == null) {
            Com.Printf("VFS compatibility layer is not initialized.\n");
            return;
        }

        syncVfsCaseSensitivity();
        String packName = args.get(1);
        VfsResult<List<String>> result = compatFileSystem.debugFilesInPack(packName);
        if (!result.success()) {
            Com.Printf(result.error() + "\n");
            return;
        }

        List<String> files = result.value();
        Com.Printf("Files in pack " + packName + "\n");
        for (String file : files) {
            Com.Printf("  " + file + "\n");
        }
        if (files.isEmpty()) {
            Com.Printf("  <pack is empty>\n");
        }
        Com.Printf("Total files in pack: " + files.size() + "\n\n");
    }

    private static void printPath() {
        if (compatFileSystem == null) {
            Com.Printf("VFS compatibility layer is not initialized.\n");
            return;
        }

        syncVfsCaseSensitivity();
        List<String> mounts = compatFileSystem.debugMounts();
        if (mounts.isEmpty()) {
            Com.Printf("No VFS mounts available.\n");
            return;
        }
        for (String mount : mounts) {
            Com.Printf(mount + "\n");
        }
    }
}
