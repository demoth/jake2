package org.demoth.cake.assets

import com.badlogic.gdx.files.FileHandle
import jake2.qcommon.vfs.DefaultVirtualFileSystem
import jake2.qcommon.vfs.VfsConfig
import jake2.qcommon.vfs.VfsEntry
import jake2.qcommon.vfs.VfsPackageSelection
import jake2.qcommon.vfs.VfsPackOrder
import jake2.qcommon.vfs.VfsSourceType
import java.nio.file.Files
import java.nio.file.Path

/**
 * Thin Cake-side adapter over qcommon VFS read APIs.
 *
 * It resolves game-data assets from basedir/mod layers and materializes package entries
 * to temporary files so libGDX loaders can consume standard [FileHandle]s.
 * CIN files use stream-backed handles to avoid extracting entire movies for previews.
 */
internal class CakeVfsAssetSource {
    private var vfs = DefaultVirtualFileSystem()
    private val extractedPackageFiles = mutableMapOf<String, Path>()

    private var configuredBasedir: String? = null
    private var configuredGameMod: String? = null
    private var configuredCaseSensitive: Boolean = false
    private var initialized = false
    private var inspectionContext = false
    private var configuredSelection = jake2.qcommon.vfs.VfsPackageSelection.AUTOMATIC

    @Synchronized
    fun configure(basedir: String?, gameMod: String?, caseSensitive: Boolean,
                  selection: jake2.qcommon.vfs.VfsPackageSelection = jake2.qcommon.vfs.VfsPackageSelection.AUTOMATIC) {
        val normalizedBasedir = basedir?.takeIf { it.isNotBlank() }
        val normalizedGameMod = gameMod?.takeIf { it.isNotBlank() }
        if (
            initialized &&
            !inspectionContext &&
            configuredBasedir == normalizedBasedir &&
            configuredGameMod == normalizedGameMod &&
            configuredCaseSensitive == caseSensitive && configuredSelection == selection
        ) {
            return
        }

        configuredSelection = selection
        configuredBasedir = normalizedBasedir
        configuredGameMod = normalizedGameMod
        configuredCaseSensitive = caseSensitive
        inspectionContext = false

        clearExtractedPackageFiles()

        val basePath = normalizedBasedir?.let { runCatching { Path.of(it) }.getOrNull() }
        vfs.configure(
            VfsConfig(
                basePath,
                "baseq2",
                normalizedGameMod,
                false,
                false,
                caseSensitive,
                emptyList(),
                setOf("pak", "pk2", "pk3", "pkz", "zip"),
                selection,
            ),
        )
        initialized = true
    }

    /**
     * Isolated inspection: the selected archive overrides the profile's numbered baseq2 PAKs.
     * Loose files and other optional archives are excluded. Returns only the selected archive's
     * entries for browsing, while resolution and texture candidate lookup can use baseline assets.
     * Omitting basedir retains archive-only inspection. Failure leaves the previous context intact.
     */
    @Synchronized
    fun configureArchive(archivePath: String, basedir: String? = null): List<VfsEntry> {
        val archiveVfs = DefaultVirtualFileSystem()
        archiveVfs.configure(
            VfsConfig(
                null, "baseq2", null, false, false, false, emptyList(),
                setOf("pak", "pk2", "pk3", "pkz", "zip"),
            ),
        )
        val selected = Path.of(archivePath).toAbsolutePath().normalize()
        val baseDirectory = basedir?.takeIf { it.isNotBlank() }?.let { Path.of(it).resolve("baseq2") }
        if (baseDirectory != null && Files.isDirectory(baseDirectory)) {
            val order = VfsPackOrder()
            Files.list(baseDirectory).use { files ->
                files.filter { Files.isRegularFile(it) && VfsPackageSelection.isNumberedPak(it.fileName.toString()) }
                    .sorted { a, b -> order.compare(a.toString(), b.toString()) }
                    .forEach { basePak ->
                        // The selected archive may itself be a numbered base PAK. Mount it last.
                        if (basePak.toAbsolutePath().normalize() != selected) {
                            val mounted = archiveVfs.mountPackage(basePak.toString())
                            require(mounted.success) { mounted.error ?: "Could not mount base archive: $basePak" }
                        }
                    }
            }
        }
        val mounted = archiveVfs.mountPackage(selected.toString())
        require(mounted.success) { mounted.error ?: "Could not mount archive: $archivePath" }
        val entries = archiveVfs.listEntries().filter { it.source.containerPath == selected }
        clearExtractedPackageFiles()
        vfs = archiveVfs
        inspectionContext = true
        initialized = true
        return entries
    }

    /** Browse a folder using the same loose/package indexing and precedence as a profile game root. */
    @Synchronized
    fun configureDirectory(directory: String): List<VfsEntry> {
        val root = Path.of(directory).toAbsolutePath().normalize()
        require(Files.isDirectory(root) && Files.isReadable(root)) { "Folder is missing or unreadable: $root" }
        val folderVfs = DefaultVirtualFileSystem()
        folderVfs.configure(VfsConfig(
            root, ".", null, false, false, false, emptyList(),
            setOf("pak", "pk2", "pk3", "pkz", "zip"),
        ))
        val entries = folderVfs.listEntries()
        clearExtractedPackageFiles()
        vfs = folderVfs
        inspectionContext = true
        initialized = true
        return entries
    }

    /** Open profile content directly, retaining the VFS handle until the caller closes the stream. */
    @Synchronized
    fun openContent(path: String): java.io.InputStream? {
        val result = vfs.openRead(path, jake2.qcommon.vfs.VfsOpenOptions.DEFAULT)
        if (!result.success) return null
        val handle = result.value ?: return null
        val input = try { handle.inputStream() } catch (error: java.io.IOException) {
            try { handle.close() } catch (closeError: java.io.IOException) { error.addSuppressed(closeError) }
            throw error
        }
        return object : java.io.FilterInputStream(input) {
            override fun close() = handle.close()
        }
    }

    @Synchronized
    fun resolve(path: String): FileHandle? {
        val lookup = vfs.resolve(path)
        if (!lookup.found) {
            return null
        }

        return when (lookup.entry.source.type) {
            VfsSourceType.DIRECTORY -> {
                val sourcePath = lookup.entry.source.containerPath.resolve(lookup.entry.source.entryPath)
                val file = sourcePath.toFile()
                if (!file.exists() || !file.canRead()) return null
                FileHandle(file)
            }

            VfsSourceType.PACKAGE_ENTRY -> {
                if (path.endsWith(".cin", ignoreCase = true)) {
                    // CIN consumers stream sequentially; extracting the whole movie defeats thumbnails.
                    val source = vfs
                    val size = lookup.entry.size
                    return object : FileHandle(path) {
                        override fun exists() = true
                        override fun length() = size
                        override fun read(): java.io.InputStream {
                            val result = source.openRead(path, jake2.qcommon.vfs.VfsOpenOptions.DEFAULT)
                            val handle = checkNotNull(result.value) { "Could not open cinematic: $path" }
                            return object : java.io.FilterInputStream(handle.inputStream()) {
                                override fun close() = handle.close()
                            }
                        }
                    }
                }

                val cacheKey = buildString {
                    append(lookup.entry.source.containerPath.toAbsolutePath().normalize())
                    append('|')
                    append(lookup.entry.normalizedPath)
                    append('|')
                    append(lookup.entry.modifiedTimeMillis)
                }
                val cached = extractedPackageFiles[cacheKey]
                if (cached != null && Files.isRegularFile(cached)) {
                    return FileHandle(cached.toFile())
                }

                val bytesResult = vfs.loadBytes(path)
                if (!bytesResult.success || bytesResult.value == null) {
                    return null
                }
                val temp = createTempAssetFile(path, bytesResult.value)
                extractedPackageFiles[cacheKey] = temp
                FileHandle(temp.toFile())
            }

            else -> null
        }
    }

    @Synchronized
    fun isInitialized(): Boolean = initialized

    @Synchronized
    fun listEntries(): List<VfsEntry> = vfs.listEntries()

    @Synchronized
    fun debugResolvedFiles(): List<String> {
        if (!initialized) return emptyList()
        return vfs.debugResolvedFiles()
    }

    @Synchronized
    fun debugMounts(): List<String> {
        if (!initialized) return emptyList()
        return vfs.debugMounts()
    }

    @Synchronized
    fun debugOverrides(): List<String> {
        if (!initialized) return emptyList()
        return vfs.debugOverrides()
    }

    @Synchronized
    private fun clearExtractedPackageFiles() {
        extractedPackageFiles.values.forEach {
            runCatching { Files.deleteIfExists(it) }
        }
        extractedPackageFiles.clear()
    }

    private fun createTempAssetFile(logicalPath: String, bytes: ByteArray): Path {
        val suffix = logicalPath.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.let { ".$it" } ?: ".bin"
        val baseName = logicalPath.substringAfterLast('/').substringBeforeLast('.', "").ifBlank { "asset" }
        val safePrefix = (baseName.take(20).ifBlank { "asset" } + "-")
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
        val temp = Files.createTempFile("cake-vfs-$safePrefix", suffix)
        temp.toFile().deleteOnExit()
        Files.write(temp, bytes)
        return temp
    }
}
