package jake2.qcommon.vfs;

/**
 * Defines how much of the virtual filesystem index should be rebuilt.
 */
public enum RebuildScope {
    FULL,
    MOD_ONLY,
    BASE_ONLY,
    PACK_ONLY
}

