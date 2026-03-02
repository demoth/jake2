package jake2.qcommon.vfs;

import java.nio.file.Path;

/**
 * Physical location metadata for a virtual entry.
 */
public final class VfsSource {
    public final VfsSourceType type;
    public final Path containerPath;
    public final String entryPath;
    public final String packType;
    public final boolean fromPack;
    public final boolean protectedPack;

    public VfsSource(
            VfsSourceType type,
            Path containerPath,
            String entryPath,
            String packType,
            boolean fromPack,
            boolean protectedPack
    ) {
        this.type = type;
        this.containerPath = containerPath;
        this.entryPath = entryPath;
        this.packType = packType;
        this.fromPack = fromPack;
        this.protectedPack = protectedPack;
    }
}

