package jake2.qcommon.vfs;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Immutable snapshot of VFS index/mount counters for diagnostics.
 */
public final class VfsSnapshot {
    public final int totalEntries;
    public final Map<VfsLayer, Integer> entryCountByLayer;
    public final Map<VfsLayer, Integer> mountCountByLayer;

    public VfsSnapshot(
            int totalEntries,
            Map<VfsLayer, Integer> entryCountByLayer,
            Map<VfsLayer, Integer> mountCountByLayer
    ) {
        this.totalEntries = totalEntries;
        this.entryCountByLayer = Collections.unmodifiableMap(new EnumMap<>(entryCountByLayer));
        this.mountCountByLayer = Collections.unmodifiableMap(new EnumMap<>(mountCountByLayer));
    }
}

