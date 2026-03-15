package jake2.qcommon.vfs;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Shared read-side VFS owner for active engine code.
 *
 * <p>Active engine code should use this class instead of depending on a legacy
 * filesystem facade or compatibility wrapper.
 */
public final class EngineVfs {
    private static final DefaultVirtualFileSystem VFS = new DefaultVirtualFileSystem();
    private static volatile VfsConfig config;

    private EngineVfs() {
    }

    public static VirtualFileSystem shared() {
        return VFS;
    }

    public static void configure(VfsConfig newConfig) {
        config = newConfig;
        VFS.configure(newConfig);
    }

    public static boolean isInitialized() {
        return config != null;
    }

    public static void setGameMod(String gameMod) {
        if (config == null) {
            return;
        }
        config = new VfsConfig(
                config.basedir(),
                config.baseGame(),
                gameMod,
                config.serverMode(),
                config.enableEngineFallback(),
                config.caseSensitive(),
                config.extraRoots(),
                config.supportedPackExtensions()
        );
        VFS.setGameMod(gameMod);
    }

    public static void setCaseSensitive(boolean caseSensitive) {
        if (config == null || config.caseSensitive() == caseSensitive) {
            return;
        }
        configure(new VfsConfig(
                config.basedir(),
                config.baseGame(),
                config.gameMod(),
                config.serverMode(),
                config.enableEngineFallback(),
                caseSensitive,
                config.extraRoots(),
                config.supportedPackExtensions()
        ));
    }

    public static byte[] loadBytes(String logicalPath) {
        if (logicalPath == null || logicalPath.isBlank()) {
            return null;
        }
        try {
            VfsResult<byte[]> result = VFS.loadBytes(logicalPath, VfsLookupOptions.DEFAULT);
            return result.success() ? result.value() : null;
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    public static boolean exists(String logicalPath) {
        if (logicalPath == null || logicalPath.isBlank()) {
            return false;
        }
        try {
            return VFS.exists(logicalPath, VfsLookupOptions.DEFAULT);
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    public static List<String> debugResolvedFiles() {
        if (!isInitialized()) {
            return List.of();
        }
        return VFS.debugResolvedFiles();
    }

    public static List<String> debugMounts() {
        if (!isInitialized()) {
            return List.of();
        }
        return VFS.debugMounts();
    }

    public static List<String> debugOverrides() {
        if (!isInitialized()) {
            return List.of();
        }
        return VFS.debugOverrides();
    }

    public static List<String> debugFilesMatching(String wildcard) {
        String effective = (wildcard == null || wildcard.isBlank()) ? "*.*" : wildcard;
        Pattern matcher = compileWildcard(effective);
        List<String> matches = debugResolvedFiles().stream()
                .filter(entry -> matcher.matcher(entry).matches())
                .toList();
        return matches.isEmpty() ? List.of() : matches;
    }

    public static VfsResult<List<String>> debugFilesInPack(String packName) {
        if (!isInitialized()) {
            return VfsResult.fail("VFS is not initialized.");
        }
        return VFS.debugFilesInPack(packName);
    }

    public static VfsResult<String> mountPackage(String packagePath) {
        if (!isInitialized()) {
            return VfsResult.fail("VFS is not initialized.");
        }
        return VFS.mountPackage(packagePath);
    }

    public static VfsResult<Void> unmount(String mountId) {
        if (!isInitialized()) {
            return VfsResult.fail("VFS is not initialized.");
        }
        return VFS.unmount(mountId);
    }

    public static void rebuildIndex(RebuildScope scope) {
        if (isInitialized()) {
            VFS.rebuildIndex(scope);
        }
    }

    private static Pattern compileWildcard(String wildcard) {
        String normalized = wildcard.replace('\\', '/');
        StringBuilder regex = new StringBuilder(normalized.length() * 2);
        regex.append('^');
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c == '*') {
                regex.append("[^/]*");
            } else if (c == '?') {
                regex.append("[^/]");
            } else if (".[]{}()+-^$|\\".indexOf(c) >= 0) {
                regex.append('\\').append(c);
            } else {
                regex.append(c);
            }
        }
        regex.append('$');
        return Pattern.compile(regex.toString());
    }

    public static boolean isFromPack(String logicalPath) {
        if (logicalPath == null || logicalPath.isBlank()) {
            return false;
        }
        try {
            VfsLookupResult lookup = VFS.resolve(logicalPath, VfsLookupOptions.DEFAULT);
            return lookup.found() && lookup.entry().source().fromPack();
        } catch (IllegalStateException ignored) {
            return false;
        }
    }
}
