package jake2.qcommon.vfs;

/**
 * Stream-open options for VFS entries.
 *
 * @param realFilesOnly If true, open operation should refuse package-backed entries.
 */
public record VfsOpenOptions(boolean realFilesOnly) {
    public static final VfsOpenOptions DEFAULT = new VfsOpenOptions(false);
}

