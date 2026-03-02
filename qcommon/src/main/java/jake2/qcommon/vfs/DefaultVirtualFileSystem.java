package jake2.qcommon.vfs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Default read-side VFS implementation.
 *
 * Layer precedence follows Q2PRO-style search-path ordering:
 * mod loose, mod pack, base loose, base pack, engine fallback.
 */
public class DefaultVirtualFileSystem implements VirtualFileSystem {
    private VfsConfig config;
    private VfsPathNormalizer normalizer;
    private final VfsPackOrder packOrder = new VfsPackOrder();

    private final List<LooseMount> looseMounts = new ArrayList<>();
    private final List<PackageMount> packageMounts = new ArrayList<>();
    private final Map<Path, PackageMount> packageMountByPath = new HashMap<>();
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

        if (lookup.entry.source.type == VfsSourceType.DIRECTORY) {
            Path sourcePath = lookup.entry.source.containerPath.resolve(lookup.entry.source.entryPath);
            try {
                return VfsResult.ok(new PathReadableHandle(sourcePath, lookup.entry.size));
            } catch (IOException e) {
                return VfsResult.fail("Failed to open resource: " + e.getMessage());
            }
        }

        if (lookup.entry.source.type == VfsSourceType.PACKAGE_ENTRY) {
            PackageMount mount = packageMountByPath.get(lookup.entry.source.containerPath);
            if (mount == null) {
                return VfsResult.fail("Package is no longer mounted: " + lookup.entry.source.containerPath);
            }
            PackEntry packEntry = mount.entriesByNormalizedPath.get(lookup.entry.normalizedPath);
            if (packEntry == null) {
                return VfsResult.fail("Package entry not found: " + logicalPath);
            }
            try {
                InputStream stream = mount.reader.openEntry(packEntry);
                return VfsResult.ok(new StreamReadableHandle(stream, packEntry.length));
            } catch (IOException e) {
                return VfsResult.fail("Failed to open package entry: " + e.getMessage());
            }
        }

        return VfsResult.fail("Unsupported source type: " + lookup.entry.source.type);
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
        ensureConfigured();
        if (packagePath == null || packagePath.isBlank()) {
            return VfsResult.fail("Package path is empty");
        }

        Path resolvedPath = resolvePackagePath(packagePath);
        if (resolvedPath == null) {
            return VfsResult.fail("Invalid package path: " + packagePath);
        }
        if (!Files.isRegularFile(resolvedPath)) {
            return VfsResult.fail("Package file not found: " + packagePath);
        }
        if (!isSupportedPackFile(resolvedPath)) {
            return VfsResult.fail("Unsupported package extension: " + packagePath);
        }
        if (packageMountByPath.containsKey(resolvedPath)) {
            return VfsResult.fail("Package is already mounted: " + packagePath);
        }

        VfsLayer layer = detectRuntimeMountLayer(resolvedPath);
        int priority = nextRuntimeMountPriority(layer);
        VfsResult<PackageMount> mountResult = createPackageMount(layer, resolvedPath, priority);
        if (!mountResult.success) {
            return VfsResult.fail(mountResult.error);
        }

        // New runtime mounts are inserted at layer head to make hot content immediately visible.
        insertPackageMount(mountResult.value, true);
        rebuildIndexes();
        return VfsResult.ok(mountResult.value.id);
    }

    @Override
    public synchronized VfsResult<Void> unmount(String mountId) {
        ensureConfigured();
        if (mountId == null || mountId.isBlank()) {
            return VfsResult.fail("Mount id is empty");
        }

        PackageMount target = null;
        for (PackageMount mount : packageMounts) {
            if (mount.id.equals(mountId)) {
                target = mount;
                break;
            }
        }
        if (target == null) {
            return VfsResult.fail("Mount not found: " + mountId);
        }

        packageMounts.remove(target);
        packageMountByPath.remove(target.packagePath);
        rebuildIndexes();
        return VfsResult.ok(null);
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
        for (PackageMount mount : packageMounts) {
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
        rebuildPackageMounts();
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

    private void rebuildPackageMounts() {
        packageMounts.clear();
        packageMountByPath.clear();

        final Path basedir = config.basedir;
        final String baseGame = sanitizeSegment(config.baseGame);
        final String gameMod = sanitizeSegment(config.gameMod);

        if (basedir != null && gameMod != null) {
            addPackageMountsFromDirectory(VfsLayer.MOD_PACK, basedir.resolve(gameMod));
        }
        if (basedir != null && baseGame != null) {
            addPackageMountsFromDirectory(VfsLayer.BASE_PACK, basedir.resolve(baseGame));
        }
    }

    private void addPackageMountsFromDirectory(VfsLayer layer, Path root) {
        if (root == null || !Files.isDirectory(root)) {
            return;
        }

        List<Path> packageFiles = listPackageFiles(root);
        int priority = 0;
        for (Path packageFile : packageFiles) {
            VfsResult<PackageMount> mountResult = createPackageMount(layer, packageFile, priority++);
            if (!mountResult.success) {
                continue;
            }
            insertPackageMount(mountResult.value, false);
        }
    }

    private List<Path> listPackageFiles(Path directory) {
        try (var stream = Files.list(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(this::isSupportedPackFile)
                    .sorted((a, b) -> packOrder.compare(fileName(a), fileName(b)))
                    .map(path -> path.toAbsolutePath().normalize())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private VfsResult<PackageMount> createPackageMount(VfsLayer layer, Path packagePath, int priority) {
        Path normalizedPath = packagePath.toAbsolutePath().normalize();
        final PackReader reader;
        try {
            reader = createPackReader(normalizedPath);
        } catch (IOException e) {
            return VfsResult.fail("Failed to read package " + normalizedPath + ": " + e.getMessage());
        }

        Map<String, PackEntry> entriesByPath = new HashMap<>();
        for (PackEntry entry : reader.entries()) {
            entriesByPath.putIfAbsent(entry.normalizedPath, entry);
        }

        long modifiedTime = 0L;
        try {
            modifiedTime = Files.getLastModifiedTime(normalizedPath).toMillis();
        } catch (IOException ignored) {
            // Keep zero when metadata lookup fails.
        }

        PackageMount mount = new PackageMount(
                "pack-" + UUID.randomUUID(),
                layer,
                normalizedPath,
                priority,
                reader,
                entriesByPath,
                modifiedTime
        );
        return VfsResult.ok(mount);
    }

    private void insertPackageMount(PackageMount mount, boolean prependWithinLayer) {
        if (prependWithinLayer) {
            int index = firstIndexForLayer(mount.layer);
            packageMounts.add(index, mount);
        } else {
            packageMounts.add(mount);
        }
        packageMountByPath.put(mount.packagePath, mount);
    }

    private int firstIndexForLayer(VfsLayer layer) {
        for (int i = 0; i < packageMounts.size(); i++) {
            if (packageMounts.get(i).layer == layer) {
                return i;
            }
        }
        return packageMounts.size();
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
        for (PackageMount mount : packageMounts) {
            indexPackageMount(mount);
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

    private void indexPackageMount(PackageMount mount) {
        Map<String, VfsEntry> layerMap = perLayerIndex.get(mount.layer);
        for (PackEntry packEntry : mount.entriesByNormalizedPath.values()) {
            if (layerMap.containsKey(packEntry.normalizedPath)) {
                continue;
            }
            VfsSource source = new VfsSource(
                    VfsSourceType.PACKAGE_ENTRY,
                    mount.packagePath,
                    packEntry.displayPath,
                    mount.reader.type(),
                    true,
                    isProtectedPack(mount.packagePath)
            );
            VfsEntry entry = new VfsEntry(
                    packEntry.normalizedPath,
                    packEntry.displayPath,
                    mount.layer,
                    mount.priority,
                    source,
                    packEntry.length,
                    mount.modifiedTimeMillis
            );
            layerMap.put(packEntry.normalizedPath, entry);
        }
    }

    private PackReader createPackReader(Path packagePath) throws IOException {
        String extension = extension(packagePath);
        if ("pak".equals(extension)) {
            return new PakPackReader(packagePath, config.caseSensitive);
        }
        if ("pk2".equals(extension) || "pk3".equals(extension) || "pkz".equals(extension) || "zip".equals(extension)) {
            return new ZipPackReader(packagePath, config.caseSensitive);
        }
        throw new IOException("Package format backend is not implemented yet for extension: " + extension);
    }

    private boolean isSupportedPackFile(Path path) {
        String extension = extension(path);
        if (extension == null) {
            return false;
        }
        for (String supported : config.supportedPackExtensions) {
            String candidate = supported == null ? "" : supported.trim().toLowerCase();
            if (candidate.startsWith(".")) {
                candidate = candidate.substring(1);
            }
            if (extension.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private Path resolvePackagePath(String packagePath) {
        Path path = Path.of(packagePath);
        if (!path.isAbsolute() && config.basedir != null) {
            path = config.basedir.resolve(path);
        }
        return path.toAbsolutePath().normalize();
    }

    private VfsLayer detectRuntimeMountLayer(Path packagePath) {
        Path absolute = packagePath.toAbsolutePath().normalize();
        Path modRoot = rootPathForSegment(config.gameMod);
        if (modRoot != null && absolute.startsWith(modRoot)) {
            return VfsLayer.MOD_PACK;
        }
        Path baseRoot = rootPathForSegment(config.baseGame);
        if (baseRoot != null && absolute.startsWith(baseRoot)) {
            return VfsLayer.BASE_PACK;
        }
        return sanitizeSegment(config.gameMod) != null ? VfsLayer.MOD_PACK : VfsLayer.BASE_PACK;
    }

    private Path rootPathForSegment(String segment) {
        String sanitized = sanitizeSegment(segment);
        if (config.basedir == null || sanitized == null) {
            return null;
        }
        return config.basedir.resolve(sanitized).toAbsolutePath().normalize();
    }

    private int nextRuntimeMountPriority(VfsLayer layer) {
        int min = Integer.MAX_VALUE;
        boolean found = false;
        for (PackageMount mount : packageMounts) {
            if (mount.layer == layer) {
                found = true;
                min = Math.min(min, mount.priority);
            }
        }
        return found ? min - 1 : 0;
    }

    private static boolean isProtectedPack(Path packagePath) {
        String name = fileName(packagePath).toLowerCase();
        if (!name.startsWith("pak")) {
            return false;
        }
        int i = 3;
        if (i >= name.length() || !Character.isDigit(name.charAt(i))) {
            return false;
        }
        while (i < name.length() && Character.isDigit(name.charAt(i))) {
            i++;
        }
        return i < name.length() && name.charAt(i) == '.';
    }

    private static String extension(Path path) {
        String name = fileName(path);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return null;
        }
        return name.substring(dot + 1).toLowerCase();
    }

    private static String fileName(Path path) {
        Path fileName = path.getFileName();
        return fileName == null ? "" : fileName.toString();
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

    private static final class PackageMount {
        final String id;
        final VfsLayer layer;
        final Path packagePath;
        final int priority;
        final PackReader reader;
        final Map<String, PackEntry> entriesByNormalizedPath;
        final long modifiedTimeMillis;

        PackageMount(
                String id,
                VfsLayer layer,
                Path packagePath,
                int priority,
                PackReader reader,
                Map<String, PackEntry> entriesByNormalizedPath,
                long modifiedTimeMillis
        ) {
            this.id = id;
            this.layer = layer;
            this.packagePath = packagePath;
            this.priority = priority;
            this.reader = reader;
            this.entriesByNormalizedPath = entriesByNormalizedPath;
            this.modifiedTimeMillis = modifiedTimeMillis;
        }
    }
}
