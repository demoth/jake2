package jake2.qcommon.vfs;

/**
 * Lookup result for one logical path.
 */
public final class VfsLookupResult {
    public final boolean found;
    public final VfsEntry entry;

    private VfsLookupResult(boolean found, VfsEntry entry) {
        this.found = found;
        this.entry = entry;
    }

    public static VfsLookupResult found(VfsEntry entry) {
        return new VfsLookupResult(true, entry);
    }

    public static VfsLookupResult missing() {
        return new VfsLookupResult(false, null);
    }
}

