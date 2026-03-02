package jake2.qcommon.filesystem;

import jake2.qcommon.vfs.DefaultVirtualFileSystem;
import jake2.qcommon.vfs.PackEntry;
import jake2.qcommon.vfs.PakPackReader;
import jake2.qcommon.vfs.VfsConfig;
import jake2.qcommon.vfs.VfsLookupResult;
import jake2.qcommon.vfs.VfsLookupOptions;
import jake2.qcommon.vfs.VfsSourceType;
import jake2.qcommon.vfs.VfsResult;
import jake2.qcommon.vfs.VirtualFileSystem;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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
    private String gameMod;
    private boolean serverMode;
    private boolean caseSensitive;
    private final Map<Path, Map<String, PackEntry>> pakEntryCache = new HashMap<>();

    public VfsBackedFileSystem() {
        this(new DefaultVirtualFileSystem());
    }

    VfsBackedFileSystem(VirtualFileSystem vfs) {
        this.vfs = vfs;
    }

    public void configure(Path basedir, String baseGame, String gameMod, boolean serverMode, boolean caseSensitive) {
        this.basedir = basedir;
        this.baseGame = baseGame;
        this.gameMod = gameMod;
        this.serverMode = serverMode;
        this.caseSensitive = caseSensitive;
        this.pakEntryCache.clear();

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
        this.gameMod = gameMod;
        this.pakEntryCache.clear();
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

    public void setCaseSensitive(boolean caseSensitive) {
        if (this.caseSensitive == caseSensitive) {
            return;
        }
        this.caseSensitive = caseSensitive;
        this.pakEntryCache.clear();
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

    public boolean isCaseSensitive() {
        return caseSensitive;
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

    /**
     * Opens a loose file through the VFS index.
     * Package entries intentionally return {@code null} in this phase and are handled by legacy FS fallback.
     */
    public QuakeFile openFile(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }

        VfsLookupResult lookup = vfs.resolve(path, VfsLookupOptions.DEFAULT);
        if (!lookup.found || lookup.entry.source.type != VfsSourceType.DIRECTORY) {
            if (!lookup.found || lookup.entry.source.type != VfsSourceType.PACKAGE_ENTRY) {
                return null;
            }
            if (!"pak".equals(lookup.entry.source.packType)) {
                return null;
            }
            return openPakEntry(lookup);
        }

        Path sourcePath = lookup.entry.source.containerPath.resolve(lookup.entry.source.entryPath);
        try {
            return new QuakeFile(sourcePath.toFile(), "r", false, lookup.entry.size);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    /**
     * Returns a read-only buffer for compatibility with legacy mapped-file call sites.
     * Loose files are memory-mapped directly, package entries are exposed as read-only heap buffers.
     */
    public ByteBuffer loadMappedFile(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }

        VfsLookupResult lookup = vfs.resolve(path, VfsLookupOptions.DEFAULT);
        if (!lookup.found) {
            return null;
        }

        if (lookup.entry.source.type == VfsSourceType.DIRECTORY) {
            Path sourcePath = lookup.entry.source.containerPath.resolve(lookup.entry.source.entryPath);
            try (FileInputStream input = new FileInputStream(sourcePath.toFile());
                 FileChannel channel = input.getChannel()) {
                return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
            } catch (IOException e) {
                return null;
            }
        }

        VfsResult<byte[]> result = vfs.loadBytes(path, VfsLookupOptions.DEFAULT);
        if (!result.success || result.value == null) {
            return null;
        }
        return ByteBuffer.wrap(result.value).asReadOnlyBuffer();
    }

    private QuakeFile openPakEntry(VfsLookupResult lookup) {
        Path packagePath = lookup.entry.source.containerPath.toAbsolutePath().normalize();
        Map<String, PackEntry> entries = pakEntryCache.computeIfAbsent(packagePath, this::readPakEntries);
        PackEntry packEntry = entries.get(lookup.entry.normalizedPath);
        if (packEntry == null) {
            return null;
        }
        try {
            QuakeFile file = new QuakeFile(packagePath.toFile(), "r", true, packEntry.length);
            file.seek(packEntry.offset);
            return file;
        } catch (IOException e) {
            return null;
        }
    }

    private Map<String, PackEntry> readPakEntries(Path packagePath) {
        try {
            PakPackReader reader = new PakPackReader(packagePath, caseSensitive);
            Map<String, PackEntry> entries = new HashMap<>();
            for (PackEntry entry : reader.entries()) {
                entries.putIfAbsent(entry.normalizedPath, entry);
            }
            return entries;
        } catch (IOException e) {
            return Collections.emptyMap();
        }
    }
}
