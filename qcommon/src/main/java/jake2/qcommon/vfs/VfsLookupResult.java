package jake2.qcommon.vfs;

/**
 * Lookup result for one logical path.
 */
public record VfsLookupResult(boolean found, VfsEntry entry) {

    public static VfsLookupResult found(VfsEntry entry) {
        return new VfsLookupResult(true, entry);
    }

    public static VfsLookupResult missing() {
        return new VfsLookupResult(false, null);
    }
}

