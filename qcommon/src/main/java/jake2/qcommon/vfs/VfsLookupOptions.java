package jake2.qcommon.vfs;

/**
 * Read-time lookup options.
 *
 * @param gameDataOnly If true, the implementation may skip fallback layers and only check mounted game data.
 */
public record VfsLookupOptions(boolean gameDataOnly) {
    public static final VfsLookupOptions DEFAULT = new VfsLookupOptions(false);
}

