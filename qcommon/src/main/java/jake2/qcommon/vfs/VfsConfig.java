package jake2.qcommon.vfs;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Runtime configuration for a VFS instance.
 */
public record VfsConfig(
        Path basedir,
        String baseGame,
        String gameMod,
        boolean serverMode,
        boolean enableEngineFallback,
        boolean caseSensitive,
        List<Path> extraRoots,
        Set<String> supportedPackExtensions,
        VfsPackageSelection packageSelection
) {
    public VfsConfig(
            Path basedir,
            String baseGame,
            String gameMod,
            boolean serverMode,
            boolean enableEngineFallback,
            boolean caseSensitive,
            List<Path> extraRoots,
            Set<String> supportedPackExtensions
    ) {
        this(basedir, baseGame, gameMod, serverMode, enableEngineFallback,
                caseSensitive, extraRoots, supportedPackExtensions, VfsPackageSelection.AUTOMATIC);
    }

    public VfsConfig {
        extraRoots = extraRoots == null ? Collections.emptyList() : List.copyOf(extraRoots);
        supportedPackExtensions = supportedPackExtensions == null
                ? Collections.emptySet()
                : Set.copyOf(supportedPackExtensions);
        packageSelection = java.util.Objects.requireNonNull(packageSelection, "packageSelection");
    }
}

