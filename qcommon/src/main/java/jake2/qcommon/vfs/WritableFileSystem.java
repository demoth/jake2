package jake2.qcommon.vfs;

/**
 * Writable counterpart for save/config/screenshot persistence.
 */
public interface WritableFileSystem {
    VfsResult<VfsWritableHandle> openWrite(String logicalPath, VfsWriteOptions options);

    VfsResult<VfsReadableHandle> openReadReal(String logicalPath, VfsOpenOptions options);

    String resolveWritePath(String logicalPath);

    String writeRoot();
}

