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
 */
public final class VfsDebugCommands {
    private VfsDebugCommands() {
    }

    public interface Provider {
        boolean isInitialized();

        List<String> resolvedFiles();

        List<String> mounts();

        List<String> overrides();
    }

    public static void register(Provider provider) {
        Objects.requireNonNull(provider, "provider");
        Cmd.AddCommand("fs_files", true, args -> printResolvedFiles(provider));
        Cmd.AddCommand("fs_mounts", true, args -> printMounts(provider));
        Cmd.AddCommand("fs_overrides", true, args -> printOverrides(provider));
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
}
