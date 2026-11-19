package jake2.qcommon.vfs;

import jake2.qcommon.Com;
import jake2.qcommon.exec.Cmd;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Shared registration for VFS diagnostics commands.
 *
 * Commands:
 * - {@code fs_files}
 * - {@code fs_find <regex>}
 * - {@code fs_mounts}
 * - {@code fs_overrides}
 * - {@code fs_mount <packagePath>}
 * - {@code fs_unmount <mountId>}
 * - {@code fs_rebuild [full|mod|base|pack]}
 */
public final class VfsDebugCommands {
    private VfsDebugCommands() {
    }

    public interface Provider {
        boolean isInitialized();

        List<String> resolvedFiles();

        List<String> mounts();

        List<String> overrides();

        default VfsResult<String> mountPackage(String packagePath) {
            return VfsResult.fail("Runtime package mount is not supported by this provider.");
        }

        default VfsResult<Void> unmountPackage(String mountId) {
            return VfsResult.fail("Runtime package unmount is not supported by this provider.");
        }

        default void rebuildIndex(RebuildScope scope) {
        }
    }

    public static void register(Provider provider) {
        Objects.requireNonNull(provider, "provider");
        Cmd.AddCommand("fs_files", true, args -> printResolvedFiles(provider));
        Cmd.AddCommand("fs_find", true, "List indexed VFS files whose path matches a regex", args -> findResolvedFiles(provider, args));
        Cmd.AddCommand("fs_mounts", true, args -> printMounts(provider));
        Cmd.AddCommand("fs_overrides", true, args -> printOverrides(provider));
        Cmd.AddCommand("fs_mount", true, args -> mountPackage(provider, args));
        Cmd.AddCommand("fs_unmount", true, args -> unmountPackage(provider, args));
        Cmd.AddCommand("fs_rebuild", true, args -> rebuild(provider, args));
    }

    private static void printResolvedFiles(Provider provider) {
        if (!provider.isInitialized()) {
            Com.Print("VFS debug provider is not initialized.");
            return;
        }
        List<String> files = provider.resolvedFiles();
        if (files.isEmpty()) {
            Com.Print("No VFS files indexed.");
            return;
        }
        for (String file : files) {
            Com.Print(file);
        }
        Com.Print("Total resolved files: " + files.size());
    }

    private static void findResolvedFiles(Provider provider, List<String> args) {
        if (!provider.isInitialized()) {
            Com.Print("VFS debug provider is not initialized.");
            return;
        }
        if (args.size() < 2) {
            Com.Print("Usage: fs_find <regex>");
            return;
        }

        String regex = Cmd.getArguments(args);
        final Pattern pattern;
        try {
            pattern = Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            Com.Warn("Invalid regex for fs_find: " + e.getDescription());
            return;
        }

        List<String> matches = provider.resolvedFiles().stream()
                .filter(file -> pattern.matcher(file).find())
                .toList();
        if (matches.isEmpty()) {
            Com.Print("No VFS files matched regex: " + regex);
            return;
        }

        for (String match : matches) {
            Com.Print(match);
        }
        Com.Print("Total matched files: " + matches.size());
    }

    private static void printMounts(Provider provider) {
        if (!provider.isInitialized()) {
            Com.Print("VFS debug provider is not initialized.");
            return;
        }
        List<String> mounts = provider.mounts();
        if (mounts.isEmpty()) {
            Com.Print("No VFS mounts available.");
            return;
        }
        for (String mount : mounts) {
            Com.Print(mount);
        }
        Com.Print("Total mounts: " + mounts.size());
    }

    private static void printOverrides(Provider provider) {
        if (!provider.isInitialized()) {
            Com.Print("VFS debug provider is not initialized.");
            return;
        }
        List<String> overrides = provider.overrides();
        if (overrides.isEmpty()) {
            Com.Print("No VFS overrides detected.");
            return;
        }
        for (String line : overrides) {
            Com.Print(line);
        }
        Com.Print("Total overridden paths: " + overrides.size());
    }

    private static void mountPackage(Provider provider, List<String> args) {
        if (!provider.isInitialized()) {
            Com.Print("VFS debug provider is not initialized.");
            return;
        }
        if (args.size() < 2) {
            Com.Print("Usage: fs_mount <packagePath>");
            return;
        }
        String packagePath = args.get(1);
        VfsResult<String> result = provider.mountPackage(packagePath);
        if (!result.success()) {
            Com.Warn("fs_mount failed: " + result.error());
            return;
        }
        Com.Print("Mounted package: " + packagePath + " (id=" + result.value() + ")");
    }

    private static void unmountPackage(Provider provider, List<String> args) {
        if (!provider.isInitialized()) {
            Com.Print("VFS debug provider is not initialized.");
            return;
        }
        if (args.size() < 2) {
            Com.Print("Usage: fs_unmount <mountId>");
            return;
        }
        String mountId = args.get(1);
        VfsResult<Void> result = provider.unmountPackage(mountId);
        if (!result.success()) {
            Com.Warn("fs_unmount failed: " + result.error());
            return;
        }
        Com.Print("Unmounted package id=" + mountId);
    }

    private static void rebuild(Provider provider, List<String> args) {
        if (!provider.isInitialized()) {
            Com.Print("VFS debug provider is not initialized.");
            return;
        }
        RebuildScope scope = parseScope(args);
        if (scope == null) {
            Com.Print("Usage: fs_rebuild [full|mod|base|pack]");
            return;
        }
        provider.rebuildIndex(scope);
        Com.Print("VFS rebuild completed for scope " + scope);
    }

    private static RebuildScope parseScope(List<String> args) {
        if (args.size() < 2) {
            return RebuildScope.FULL;
        }
        String raw = args.get(1).toLowerCase();
        if ("full".equals(raw)) {
            return RebuildScope.FULL;
        }
        if ("mod".equals(raw) || "mod_only".equals(raw)) {
            return RebuildScope.MOD_ONLY;
        }
        if ("base".equals(raw) || "base_only".equals(raw)) {
            return RebuildScope.BASE_ONLY;
        }
        if ("pack".equals(raw) || "pack_only".equals(raw)) {
            return RebuildScope.PACK_ONLY;
        }
        return null;
    }
}
