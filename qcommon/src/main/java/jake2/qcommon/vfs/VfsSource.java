package jake2.qcommon.vfs;

import java.nio.file.Path;

/**
 * Physical location metadata for a virtual entry.
 */
public record VfsSource(
        VfsSourceType type,
        Path containerPath,
        String entryPath,
        String packType,
        boolean fromPack,
        boolean protectedPack
) {
}

