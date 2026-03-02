package jake2.qcommon.filesystem;

import jake2.qcommon.vfs.DefaultVirtualFileSystem;
import jake2.qcommon.vfs.VfsConfig;
import jake2.qcommon.vfs.VfsLookupOptions;
import jake2.qcommon.vfs.VfsResult;
import jake2.qcommon.vfs.VirtualFileSystem;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;

/**
 * Thin compatibility wrapper around the new common VFS for legacy FS call sites.
 *
 * This keeps migration incremental: legacy filesystem remains as fallback while
 * read-heavy calls can already use Q2PRO-style layered lookup from {@code jake2.qcommon.vfs}.
 */
public final class VfsBackedFileSystem {
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("pak", "pk2", "pk3", "pkz", "zip");

    private final VirtualFileSystem vfs;
    private Path basedir;
    private String baseGame;
    private boolean serverMode;
    private boolean caseSensitive;

    public VfsBackedFileSystem() {
        this(new DefaultVirtualFileSystem());
    }

    VfsBackedFileSystem(VirtualFileSystem vfs) {
        this.vfs = vfs;
    }

    public void configure(Path basedir, String baseGame, String gameMod, boolean serverMode, boolean caseSensitive) {
        this.basedir = basedir;
        this.baseGame = baseGame;
        this.serverMode = serverMode;
        this.caseSensitive = caseSensitive;

        vfs.configure(new VfsConfig(
                basedir,
                baseGame,
                gameMod,
                serverMode,
                false,
                caseSensitive,
                Collections.emptyList(),
                SUPPORTED_EXTENSIONS
        ));
    }

    public void setGameMod(String gameMod) {
        if (basedir == null) {
            return;
        }
        vfs.configure(new VfsConfig(
                basedir,
                baseGame,
                gameMod,
                serverMode,
                false,
                caseSensitive,
                Collections.emptyList(),
                SUPPORTED_EXTENSIONS
        ));
    }

    public byte[] loadFile(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        VfsResult<byte[]> result = vfs.loadBytes(path, VfsLookupOptions.DEFAULT);
        if (!result.success) {
            return null;
        }
        return result.value;
    }

    public boolean exists(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        return vfs.exists(path, VfsLookupOptions.DEFAULT);
    }
}
