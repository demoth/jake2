package jake2.qcommon.vfs;

/**
 * Shared read-side VFS owner for active engine code.
 *
 * <p>`FS` still configures and bridges legacy compatibility APIs, but active
 * read paths should use this class instead of the deprecated filesystem facade.
 */
public final class EngineVfs {
    private static final DefaultVirtualFileSystem VFS = new DefaultVirtualFileSystem();

    private EngineVfs() {
    }

    public static VirtualFileSystem shared() {
        return VFS;
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
