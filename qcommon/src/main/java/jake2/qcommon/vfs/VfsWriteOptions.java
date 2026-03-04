package jake2.qcommon.vfs;

/**
 * Write-open options for writable VFS operations.
 */
public record VfsWriteOptions(boolean truncateExisting, boolean append) {
    public static final VfsWriteOptions TRUNCATE = new VfsWriteOptions(true, false);
    public static final VfsWriteOptions APPEND = new VfsWriteOptions(false, true);
}

