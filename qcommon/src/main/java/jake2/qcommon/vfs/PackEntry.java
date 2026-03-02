package jake2.qcommon.vfs;

/**
 * One indexed file entry inside a package container.
 */
public final class PackEntry {
    public final String normalizedPath;
    public final String displayPath;
    public final long offset;
    public final long length;

    public PackEntry(String normalizedPath, String displayPath, long offset, long length) {
        this.normalizedPath = normalizedPath;
        this.displayPath = displayPath;
        this.offset = offset;
        this.length = length;
    }
}

