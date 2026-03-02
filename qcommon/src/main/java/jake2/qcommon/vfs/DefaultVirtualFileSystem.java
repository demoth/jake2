package jake2.qcommon.vfs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Default read-side VFS implementation.
 *
 * This step implements loose-directory mounts and O(1) flattened lookup index.
 * Package mounts are introduced in a later phase.
 */
public class DefaultVirtualFileSystem implements VirtualFileSystem {
    private VfsConfig config;
    private VfsPathNormalizer normalizer;

    private final List<LooseMount> looseMounts = new ArrayList<>();
    private final EnumMap<VfsLayer, Map<String, VfsEntry>> perLayerIndex = new EnumMap<>(VfsLayer.class);
    private final Map<String, VfsEntry> flattenedIndex = new HashMap<>();

    @Override
    public synchronized void configure(VfsConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.normalizer = new VfsPathNormalizer(config.caseSensitive);
        rebuildAll();
    }

    @Override
    public synchronized VfsLookupResult resolve(String logicalPath, VfsLookupOptions options) {
        ensureConfigured();

        String normalized = normalizer.normalizeOrNull(logicalPath);
        if (normalized == null) {
            return VfsLookupResult.missing();
        }

        VfsEntry entry = flattenedIndex.get(normalized);
        if (entry == null) {
            return VfsLookupResult.missing();
        }

        VfsLookupOptions effective = options == null ? VfsLookupOptions.DEFAULT : options;
        if (effective.gameDataOnly && entry.layer == VfsLayer.ENGINE_FALLBACK) {
            return VfsLookupResult.missing();
        }

        return VfsLookupResult.found(entry);
    }

    @Override
    public synchronized VfsResult<byte[]> loadBytes(String logicalPath, VfsLookupOptions options) {
        VfsResult<VfsReadableHandle> opened = openRead(logicalPath, VfsOpenOptions.DEFAULT);
        if (!opened.success) {
            return VfsResult.fail(opened.error);
        }

        try (VfsReadableHandle handle = opened.value; InputStream stream = handle.inputStream()) {
            return VfsResult.ok(stream.readAllBytes());
        } catch (IOException e) {
            return VfsResult.fail("Failed to load bytes: " + e.getMessage());
        }
    }

    @Override
    public synchronized VfsResult<VfsReadableHandle> openRead(String logicalPath, VfsOpenOptions options) {
        VfsLookupResult lookup = resolve(logicalPath, VfsLookupOptions.DEFAULT);
        if (!lookup.found) {
            return VfsResult.fail("Resource not found: " + logicalPath);
        }

        VfsOpenOptions effective = options == null ? VfsOpenOptions.DEFAULT : options;
        if (effective.realFilesOnly && lookup.entry.source.type != VfsSourceType.DIRECTORY) {
            return VfsResult.fail("Resource is not a real file: " + logicalPath);
        }

        Path sourcePath = lookup.entry.source.containerPath.resolve(lookup.entry.source.entryPath);
        try {
            return VfsResult.ok(new FileReadableHandle(sourcePath, lookup.entry.size));
        } catch (IOException e) {
            return VfsResult.fail("Failed to open resource: " + e.getMessage());
        }
    }

    @Override
    public synchronized boolean exists(String logicalPath, VfsLookupOptions options) {
        return resolve(logicalPath, options).found;
    }

    @Override
    public synchronized void setGameMod(String gameMod) {
        ensureConfigured();
        config = new VfsConfig(
                config.basedir,
                config.baseGame,
                gameMod,
                config.serverMode,
                config.enableEngineFallback,
                config.caseSensitive,
                config.extraRoots,
                config.supportedPackExtensions
        );
        rebuildAll();
    }

    @Override
    public synchronized VfsResult<String> mountPackage(String packagePath) {
        // Implemented in a later phase alongside pack readers.
        return VfsResult.fail("Package mounting is not implemented yet: " + packagePath);
    }

    @Override
    public synchronized VfsResult<Void> unmount(String mountId) {
        // Implemented in a later phase when explicit package mounts exist.
        return VfsResult.fail("Unmount is not implemented yet: " + mountId);
    }

    @Override
    public synchronized void rebuildIndex(RebuildScope scope) {
        ensureConfigured();
        // Scoped rebuild will be introduced later; for now keep deterministic full rebuild.
        rebuildAll();
    }

    @Override
    public synchronized VfsSnapshot snapshot() {
        ensureConfigured();
        EnumMap<VfsLayer, Integer> entryCounts = new EnumMap<>(VfsLayer.class);
        EnumMap<VfsLayer, Integer> mountCounts = new EnumMap<>(VfsLayer.class);
        for (VfsLayer layer : VfsLayer.values()) {
            entryCounts.put(layer, perLayerIndex.get(layer).size());
            mountCounts.put(layer, 0);
        }
        for (LooseMount mount : looseMounts) {
            mountCounts.put(mount.layer, mountCounts.get(mount.layer) + 1);
        }
        return new VfsSnapshot(flattenedIndex.size(), entryCounts, mountCounts);
    }

    private void ensureConfigured() {
        if (config == null || normalizer == null) {
            throw new IllegalStateException("VFS is not configured");
        }
    }

    private void rebuildAll() {
        rebuildLooseMounts();
        rebuildIndexes();
    }

    private void rebuildLooseMounts() {
        looseMounts.clear();
        int priority = 0;

        final Path basedir = config.basedir;
        final String baseGame = sanitizeSegment(config.baseGame);
        final String gameMod = sanitizeSegment(config.gameMod);

        if (basedir != null && gameMod != null) {
            addLooseMountIfDirectory(VfsLayer.MOD_LOOSE, basedir.resolve(gameMod), priority++);
        }

        if (basedir != null && baseGame != null) {
            addLooseMountIfDirectory(VfsLayer.BASE_LOOSE, basedir.resolve(baseGame), priority++);
        }

        if (!config.serverMode && config.enableEngineFallback) {
            for (Path extraRoot : config.extraRoots) {
                addLooseMountIfDirectory(VfsLayer.ENGINE_FALLBACK, extraRoot, priority++);
            }
        }
    }

    private void addLooseMountIfDirectory(VfsLayer layer, Path root, int priority) {
        if (root == null) {
            return;
        }
        if (!Files.isDirectory(root)) {
            return;
        }
        looseMounts.add(new LooseMount("loose-" + UUID.randomUUID(), layer, root, priority));
    }

    private void rebuildIndexes() {
        perLayerIndex.clear();
        for (VfsLayer layer : VfsLayer.values()) {
            perLayerIndex.put(layer, new HashMap<>());
        }

        for (LooseMount mount : looseMounts) {
            indexLooseMount(mount);
        }

        flattenedIndex.clear();
        for (VfsLayer layer : VfsLayer.values()) {
            Map<String, VfsEntry> layerEntries = perLayerIndex.get(layer);
            for (Map.Entry<String, VfsEntry> entry : layerEntries.entrySet()) {
                flattenedIndex.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
    }

    private void indexLooseMount(LooseMount mount) {
        try {
            Files.walk(mount.root).filter(Files::isRegularFile).forEach(path -> {
                Path relativePath = mount.root.relativize(path);
                String relative = relativePath.toString().replace('\\', '/');
                String normalized = normalizer.normalizeOrNull(relative);
                if (normalized == null) {
                    return;
                }
                Map<String, VfsEntry> layerMap = perLayerIndex.get(mount.layer);
                if (layerMap.containsKey(normalized)) {
                    return;
                }
                try {
                    VfsSource source = new VfsSource(
                            VfsSourceType.DIRECTORY,
                            mount.root,
                            relative,
                            null,
                            false,
                            false
                    );
                    VfsEntry vfsEntry = new VfsEntry(
                            normalized,
                            relative,
                            mount.layer,
                            mount.priority,
                            source,
                            Files.size(path),
                            Files.getLastModifiedTime(path).toMillis()
                    );
                    layerMap.put(normalized, vfsEntry);
                } catch (IOException ignored) {
                    // Broken file entry should not fail full index build.
                }
            });
        } catch (IOException ignored) {
            // Mount may become unreadable at runtime; skip it from this index pass.
        }
    }

    private static String sanitizeSegment(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String sanitized = value.trim().replace('\\', '/');
        if (sanitized.contains("..") || sanitized.contains("/") || sanitized.contains(":")) {
            return null;
        }
        return sanitized;
    }

    private static final class LooseMount {
        final String id;
        final VfsLayer layer;
        final Path root;
        final int priority;

        LooseMount(String id, VfsLayer layer, Path root, int priority) {
            this.id = id;
            this.layer = layer;
            this.root = root;
            this.priority = priority;
        }
    }

    private static final class FileReadableHandle implements VfsReadableHandle {
        private final InputStream stream;
        private final long size;

        FileReadableHandle(Path path, long size) throws IOException {
            this.stream = Files.newInputStream(path);
            this.size = size;
        }

        @Override
        public InputStream inputStream() {
            return stream;
        }

        @Override
        public long size() {
            return size;
        }

        @Override
        public void close() throws IOException {
            stream.close();
        }
    }
}
