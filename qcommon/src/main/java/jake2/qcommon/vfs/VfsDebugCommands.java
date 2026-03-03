package jake2.qcommon.vfs;

import jake2.qcommon.Com;
import jake2.qcommon.exec.Cmd;

import java.util.List;
import java.util.Objects;

/**
 * Shared registration for VFS diagnostics commands.
 *
 * Commands:
 * - {@code fs_files}
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
        Cmd.AddCommand("fs_mounts", true, args -> printMounts(provider));
        Cmd.AddCommand("fs_overrides", true, args -> printOverrides(provider));
        Cmd.AddCommand("fs_mount", true, args -> mountPackage(provider, args));
        Cmd.AddCommand("fs_unmount", true, args -> unmountPackage(provider, args));
        Cmd.AddCommand("fs_rebuild", true, args -> rebuild(provider, args));
    }

    private static void printResolvedFiles(Provider provider) {
        if (!provider.isInitialized()) {
            Com.Printf("VFS debug provider is not initialized.\n");
            return;
        }
        List<String> files = provider.resolvedFiles();
        if (files.isEmpty()) {
            Com.Printf("No VFS files indexed.\n");
            return;
        }
        for (String file : files) {
            Com.Printf(file + "\n");
        }
        Com.Printf("Total resolved files: " + files.size() + "\n");
    }

    private static void printMounts(Provider provider) {
        if (!provider.isInitialized()) {
            Com.Printf("VFS debug provider is not initialized.\n");
            return;
        }
        List<String> mounts = provider.mounts();
        if (mounts.isEmpty()) {
            Com.Printf("No VFS mounts available.\n");
            return;
        }
        for (String mount : mounts) {
            Com.Printf(mount + "\n");
        }
        Com.Printf("Total mounts: " + mounts.size() + "\n");
    }

    private static void printOverrides(Provider provider) {
        if (!provider.isInitialized()) {
            Com.Printf("VFS debug provider is not initialized.\n");
            return;
        }
        List<String> overrides = provider.overrides();
        if (overrides.isEmpty()) {
            Com.Printf("No VFS overrides detected.\n");
            return;
        }
        for (String line : overrides) {
            Com.Printf(line + "\n");
        }
        Com.Printf("Total overridden paths: " + overrides.size() + "\n");
    }

    private static void mountPackage(Provider provider, List<String> args) {
        if (!provider.isInitialized()) {
            Com.Printf("VFS debug provider is not initialized.\n");
            return;
        }
        if (args.size() < 2) {
            Com.Printf("Usage: fs_mount <packagePath>\n");
            return;
        }
        String packagePath = args.get(1);
        VfsResult<String> result = provider.mountPackage(packagePath);
        if (!result.success) {
            Com.Printf("fs_mount failed: " + result.error + "\n");
            return;
        }
        Com.Printf("Mounted package: " + packagePath + " (id=" + result.value + ")\n");
    }

    private static void unmountPackage(Provider provider, List<String> args) {
        if (!provider.isInitialized()) {
            Com.Printf("VFS debug provider is not initialized.\n");
            return;
        }
        if (args.size() < 2) {
            Com.Printf("Usage: fs_unmount <mountId>\n");
            return;
        }
        String mountId = args.get(1);
        VfsResult<Void> result = provider.unmountPackage(mountId);
        if (!result.success) {
            Com.Printf("fs_unmount failed: " + result.error + "\n");
            return;
        }
        Com.Printf("Unmounted package id=" + mountId + "\n");
    }

    private static void rebuild(Provider provider, List<String> args) {
        if (!provider.isInitialized()) {
            Com.Printf("VFS debug provider is not initialized.\n");
            return;
        }
        RebuildScope scope = parseScope(args);
        if (scope == null) {
            Com.Printf("Usage: fs_rebuild [full|mod|base|pack]\n");
            return;
        }
        provider.rebuildIndex(scope);
        Com.Printf("VFS rebuild completed for scope " + scope + "\n");
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
