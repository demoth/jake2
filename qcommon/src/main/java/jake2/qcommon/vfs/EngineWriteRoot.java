package jake2.qcommon.vfs;

import jake2.qcommon.Com;
import jake2.qcommon.Globals;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shared write-root policy for active engine code.
 *
 * <p>This keeps save/config output location under an explicit VFS-owned policy
 * while preserving the current on-disk layout.
 */
public final class EngineWriteRoot {
    private static volatile Path root = Path.of(Globals.BASEQ2);

    private EngineWriteRoot() {
    }

    public static void setRoot(Path newRoot) {
        root = newRoot;
        ensureDirectories(root);
    }

    public static Path path() {
        return root;
    }

    public static String pathString() {
        return root.toString();
    }

    public static Path resolve(String relativePath) {
        return root.resolve(relativePath);
    }

    public static void ensureParentDirectories(Path path) {
        Path parent = path.getParent();
        if (parent != null) {
            ensureDirectories(parent);
        }
    }

    private static void ensureDirectories(Path path) {
        try {
            Files.createDirectories(path);
        } catch (Exception e) {
            Com.Printf("can't create path \"" + path + "\"\n");
        }
    }
}
