package jake2.qcommon.vfs;

/**
 * Flattened index entry for one virtual path.
 */
public record VfsEntry(
        String normalizedPath,
        String displayPath,
        VfsLayer layer,
        int layerOrder,
        VfsSource source,
        long size,
        long modifiedTimeMillis
) {
}

