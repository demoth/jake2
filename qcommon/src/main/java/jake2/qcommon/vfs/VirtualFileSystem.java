package jake2.qcommon.vfs;

import java.util.List;

/**
 * Core read-focused virtual file system contract shared across modules.
 */
public interface VirtualFileSystem {
    void configure(VfsConfig config);

    default VfsLookupResult resolve(String logicalPath) {
        return resolve(logicalPath, false);
    }

    VfsLookupResult resolve(String logicalPath, boolean gameDataOnly);

    /**
     * Immutable snapshot of resolved files, sorted by normalized logical path.
     * Contains one winning entry per path, including its size and source metadata.
     * Later mount or configuration changes do not modify a returned snapshot.
     */
    List<VfsEntry> listEntries();

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
