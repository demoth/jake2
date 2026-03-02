package jake2.qcommon.vfs;

/**
 * Core read-focused virtual file system contract shared across modules.
 */
public interface VirtualFileSystem {
    void configure(VfsConfig config);

    VfsLookupResult resolve(String logicalPath, VfsLookupOptions options);

    VfsResult<byte[]> loadBytes(String logicalPath, VfsLookupOptions options);

    VfsResult<VfsReadableHandle> openRead(String logicalPath, VfsOpenOptions options);

    boolean exists(String logicalPath, VfsLookupOptions options);

    void setGameMod(String gameMod);

    VfsResult<String> mountPackage(String packagePath);

    VfsResult<Void> unmount(String mountId);

    void rebuildIndex(RebuildScope scope);

    VfsSnapshot snapshot();
}

