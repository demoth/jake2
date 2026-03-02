package jake2.qcommon.vfs;

/**
 * Read-time lookup options.
 */
public final class VfsLookupOptions {
    public static final VfsLookupOptions DEFAULT = new VfsLookupOptions(false);

    /**
     * If true, the implementation may skip fallback layers and only check mounted game data.
     */
    public final boolean gameDataOnly;

    public VfsLookupOptions(boolean gameDataOnly) {
        this.gameDataOnly = gameDataOnly;
    }
}

