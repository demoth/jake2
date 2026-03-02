package jake2.qcommon.vfs;

/**
 * Stream-open options for VFS entries.
 */
public final class VfsOpenOptions {
    public static final VfsOpenOptions DEFAULT = new VfsOpenOptions(false);

    /**
     * If true, open operation should refuse package-backed entries.
     */
    public final boolean realFilesOnly;

    public VfsOpenOptions(boolean realFilesOnly) {
        this.realFilesOnly = realFilesOnly;
    }
}

