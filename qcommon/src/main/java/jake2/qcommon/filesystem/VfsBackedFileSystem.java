package jake2.qcommon.filesystem;

import jake2.qcommon.vfs.DefaultVirtualFileSystem;
import jake2.qcommon.vfs.PackEntry;
import jake2.qcommon.vfs.PakPackReader;
import jake2.qcommon.vfs.VfsConfig;
import jake2.qcommon.vfs.VfsLookupResult;
import jake2.qcommon.vfs.VfsLookupOptions;
import jake2.qcommon.vfs.VfsOpenOptions;
import jake2.qcommon.vfs.VfsReadableHandle;
import jake2.qcommon.vfs.RebuildScope;
import jake2.qcommon.vfs.VfsSourceType;
import jake2.qcommon.vfs.VfsResult;
import jake2.qcommon.vfs.VirtualFileSystem;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Thin compatibility wrapper around the new common VFS for legacy FS call sites.
 *
 * This keeps migration incremental: legacy filesystem remains as fallback while
 * read-heavy calls can already use Q2PRO-style layered lookup from {@code jake2.qcommon.vfs}.
 *
 * @deprecated Transitional bridge from `FS` to the real VFS. Active code should
 * depend on `VirtualFileSystem` directly instead of going through this wrapper.
 */
@Deprecated(forRemoval = true)
public final class VfsBackedFileSystem {
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("pak", "pk2", "pk3", "pkz", "zip");

    private final VirtualFileSystem vfs;
    private Path basedir;
    private String baseGame;
    private String gameMod;
    private boolean serverMode;
    private boolean caseSensitive;
    private final Map<Path, Map<String, PackEntry>> pakEntryCache = new HashMap<>();
    private final Map<String, Path> packagedOpenCache = new HashMap<>();

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
        clearPackageCaches();

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
        clearPackageCaches();
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
        clearPackageCaches();
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
        if (!result.success()) {
            return null;
        }
        return result.value();
    }

    public boolean exists(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        return vfs.exists(path, VfsLookupOptions.DEFAULT);
    }

    public boolean isFromPack(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }

        VfsLookupResult lookup = vfs.resolve(path, VfsLookupOptions.DEFAULT);
        return lookup.found() && lookup.entry().source().fromPack();
    }

    public List<String> debugResolvedFiles() {
        if (vfs instanceof DefaultVirtualFileSystem) {
            DefaultVirtualFileSystem debugVfs = (DefaultVirtualFileSystem) vfs;
            return debugVfs.debugResolvedFiles();
        }
        return Collections.emptyList();
    }

    public List<String> debugMounts() {
        if (vfs instanceof DefaultVirtualFileSystem) {
            DefaultVirtualFileSystem debugVfs = (DefaultVirtualFileSystem) vfs;
            return debugVfs.debugMounts();
        }
        return Collections.emptyList();
    }

    public List<String> debugOverrides() {
        if (vfs instanceof DefaultVirtualFileSystem) {
            DefaultVirtualFileSystem debugVfs = (DefaultVirtualFileSystem) vfs;
            return debugVfs.debugOverrides();
        }
        return Collections.emptyList();
    }

    public VfsResult<String> mountPackage(String packagePath) {
        clearPackageCaches();
        return vfs.mountPackage(packagePath);
    }

    public VfsResult<Void> unmountPackage(String mountId) {
        clearPackageCaches();
        return vfs.unmount(mountId);
    }

    public void rebuildIndex(RebuildScope scope) {
        clearPackageCaches();
        vfs.rebuildIndex(scope);
    }

    /**
     * Returns loose mount roots (mod/base directories) in effective lookup order.
     */
    public List<String> debugLooseMountRoots() {
        if (vfs instanceof DefaultVirtualFileSystem) {
            DefaultVirtualFileSystem debugVfs = (DefaultVirtualFileSystem) vfs;
            return debugVfs.debugLooseMountRoots().stream().map(Path::toString).toList();
        }
        return Collections.emptyList();
    }

    /**
     * Returns resolved winner files matching a wildcard using Quake-style '*' and '?' segments.
     */
    public List<String> debugFilesMatching(String wildcard) {
        String effective = (wildcard == null || wildcard.isBlank()) ? "*.*" : wildcard;
        Pattern matcher = compileWildcard(effective);
        List<String> matches = new ArrayList<>();
        for (String entry : debugResolvedFiles()) {
            if (matcher.matcher(entry).matches()) {
                matches.add(entry);
            }
        }
        return matches;
    }

    /**
     * Returns all indexed logical files from the first mounted package matching {@code packName}.
     * Accepts either pack filename (e.g. {@code pak0.pak}) or full package path.
     */
    public VfsResult<List<String>> debugFilesInPack(String packName) {
        if (vfs instanceof DefaultVirtualFileSystem) {
            DefaultVirtualFileSystem debugVfs = (DefaultVirtualFileSystem) vfs;
            return debugVfs.debugFilesInPack(packName);
        }
        return VfsResult.fail("Pack listing is not supported by this VFS implementation.");
    }

    /**
     * Opens a loose file through the VFS index.
     * Package entries are opened via direct `.pak` offset access or extracted temp files for ZIP-based packs.
     */
    public QuakeFile openFile(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }

        VfsLookupResult lookup = vfs.resolve(path, VfsLookupOptions.DEFAULT);
        if (!lookup.found() || lookup.entry().source().type() != VfsSourceType.DIRECTORY) {
            if (!lookup.found() || lookup.entry().source().type() != VfsSourceType.PACKAGE_ENTRY) {
                return null;
            }
            if (!"pak".equals(lookup.entry().source().packType())) {
                return openNonPakEntry(lookup, path);
            }
            return openPakEntry(lookup);
        }

        Path sourcePath = lookup.entry().source().containerPath().resolve(lookup.entry().source().entryPath());
        try {
            return new QuakeFile(sourcePath.toFile(), "r", false, lookup.entry().size());
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
        if (!lookup.found()) {
            return null;
        }

        if (lookup.entry().source().type() == VfsSourceType.DIRECTORY) {
            Path sourcePath = lookup.entry().source().containerPath().resolve(lookup.entry().source().entryPath());
            try (FileInputStream input = new FileInputStream(sourcePath.toFile());
                 FileChannel channel = input.getChannel()) {
                return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
            } catch (IOException e) {
                return null;
            }
        }

        VfsResult<byte[]> result = vfs.loadBytes(path, VfsLookupOptions.DEFAULT);
        if (!result.success() || result.value() == null) {
            return null;
        }
        return ByteBuffer.wrap(result.value()).asReadOnlyBuffer();
    }

    private QuakeFile openPakEntry(VfsLookupResult lookup) {
        Path packagePath = lookup.entry().source().containerPath().toAbsolutePath().normalize();
        Map<String, PackEntry> entries = pakEntryCache.computeIfAbsent(packagePath, this::readPakEntries);
        PackEntry packEntry = entries.get(lookup.entry().normalizedPath());
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

    private QuakeFile openNonPakEntry(VfsLookupResult lookup, String logicalPath) {
        String cacheKey = lookup.entry().source().containerPath().toAbsolutePath().normalize()
                + "|" + lookup.entry().normalizedPath()
                + "|" + lookup.entry().modifiedTimeMillis();

        Path cached = packagedOpenCache.get(cacheKey);
        if (cached != null && Files.isRegularFile(cached)) {
            try {
                return new QuakeFile(cached.toFile(), "r", true, Files.size(cached));
            } catch (IOException e) {
                packagedOpenCache.remove(cacheKey);
            }
        }

        VfsResult<VfsReadableHandle> opened = vfs.openRead(logicalPath, VfsOpenOptions.DEFAULT);
        if (!opened.success() || opened.value() == null) {
            return null;
        }
        try (VfsReadableHandle handle = opened.value(); InputStream input = handle.inputStream()) {
            byte[] bytes = input.readAllBytes();
            Path temp = Files.createTempFile("jake2-vfs-", ".tmp");
            Files.write(temp, bytes);
            temp.toFile().deleteOnExit();
            packagedOpenCache.put(cacheKey, temp);
            return new QuakeFile(temp.toFile(), "r", true, bytes.length);
        } catch (IOException e) {
            return null;
        }
    }

    private void clearPackageCaches() {
        pakEntryCache.clear();
        for (Path path : packagedOpenCache.values()) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // Best-effort cleanup.
            }
        }
        packagedOpenCache.clear();
    }

    private static Pattern compileWildcard(String wildcard) {
        String normalized = wildcard.replace('\\', '/');
        StringBuilder regex = new StringBuilder(normalized.length() * 2);
        regex.append('^');
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c == '*') {
                regex.append("[^/]*");
            } else if (c == '?') {
                regex.append("[^/]");
            } else if (".[]{}()+-^$|\\".indexOf(c) >= 0) {
                regex.append('\\').append(c);
            } else {
                regex.append(c);
            }
        }
        regex.append('$');
        return Pattern.compile(regex.toString());
    }
}
