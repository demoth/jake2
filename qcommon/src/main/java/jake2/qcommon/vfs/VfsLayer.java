package jake2.qcommon.vfs;

/**
 * Ordered source layers for virtual path resolution.
 */
public enum VfsLayer {
    MOD_LOOSE,
    MOD_PACK,
    BASE_LOOSE,
    BASE_PACK,
    ENGINE_FALLBACK
}

