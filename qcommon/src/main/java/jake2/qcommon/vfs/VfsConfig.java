package jake2.qcommon.vfs;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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
        Set<String> supportedPackExtensions
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
        this.basedir = basedir;
        this.baseGame = baseGame;
        this.gameMod = gameMod;
        this.serverMode = serverMode;
        this.enableEngineFallback = enableEngineFallback;
        this.caseSensitive = caseSensitive;
        this.extraRoots = extraRoots == null ? Collections.emptyList() : Collections.unmodifiableList(extraRoots);
        this.supportedPackExtensions = supportedPackExtensions == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(supportedPackExtensions);
    }
}

