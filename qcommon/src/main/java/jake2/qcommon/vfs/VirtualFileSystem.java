package jake2.qcommon.vfs;

/**
 * Core read-focused virtual file system contract shared across modules.
 */
public interface VirtualFileSystem {
    void configure(VfsConfig config);

    default VfsLookupResult resolve(String logicalPath) {
        return resolve(logicalPath, false);
    }

    VfsLookupResult resolve(String logicalPath, boolean gameDataOnly);

    VfsResult<byte[]> loadBytes(String logicalPath);

    VfsResult<VfsReadableHandle> openRead(String logicalPath, VfsOpenOptions options);

    default boolean exists(String logicalPath) {
        return exists(logicalPath, false);
    }

    boolean exists(String logicalPath, boolean gameDataOnly);

    void setGameMod(String gameMod);

    VfsResult<String> mountPackage(String packagePath);

    VfsResult<Void> unmount(String mountId);

    void rebuildIndex(RebuildScope scope);
}
