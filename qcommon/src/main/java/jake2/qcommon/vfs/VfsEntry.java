package jake2.qcommon.vfs;

/**
 * Flattened index entry for one virtual path.
 */
public final class VfsEntry {
    public final String normalizedPath;
    public final String displayPath;
    public final VfsLayer layer;
    public final int layerOrder;
    public final VfsSource source;
    public final long size;
    public final long modifiedTimeMillis;

    public VfsEntry(
            String normalizedPath,
            String displayPath,
            VfsLayer layer,
            int layerOrder,
            VfsSource source,
            long size,
            long modifiedTimeMillis
    ) {
        this.normalizedPath = normalizedPath;
        this.displayPath = displayPath;
        this.layer = layer;
        this.layerOrder = layerOrder;
        this.source = source;
        this.size = size;
        this.modifiedTimeMillis = modifiedTimeMillis;
    }
}

