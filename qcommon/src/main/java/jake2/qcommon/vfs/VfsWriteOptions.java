package jake2.qcommon.vfs;

/**
 * Write-open options for writable VFS operations.
 */
public final class VfsWriteOptions {
    public static final VfsWriteOptions TRUNCATE = new VfsWriteOptions(true, false);
    public static final VfsWriteOptions APPEND = new VfsWriteOptions(false, true);

    public final boolean truncateExisting;
    public final boolean append;

    public VfsWriteOptions(boolean truncateExisting, boolean append) {
        this.truncateExisting = truncateExisting;
        this.append = append;
    }
}

