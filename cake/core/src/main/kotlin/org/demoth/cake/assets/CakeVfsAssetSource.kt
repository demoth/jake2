package org.demoth.cake.assets

import com.badlogic.gdx.files.FileHandle
import jake2.qcommon.vfs.DefaultVirtualFileSystem
import jake2.qcommon.vfs.VfsConfig
import jake2.qcommon.vfs.VfsSourceType
import java.nio.file.Files
import java.nio.file.Path

/**
 * Thin Cake-side adapter over qcommon VFS read APIs.
 *
 * It resolves game-data assets from basedir/mod layers and materializes package entries
 * to temporary files so libGDX loaders can consume standard [FileHandle]s.
 */
internal class CakeVfsAssetSource {
    private val vfs = DefaultVirtualFileSystem()
    private val extractedPackageFiles = mutableMapOf<String, Path>()

    private var configuredBasedir: String? = null
    private var configuredGameMod: String? = null
    private var configuredCaseSensitive: Boolean = false
    private var initialized = false

    @Synchronized
    fun configure(basedir: String?, gameMod: String?, caseSensitive: Boolean) {
        val normalizedBasedir = basedir?.takeIf { it.isNotBlank() }
        val normalizedGameMod = gameMod?.takeIf { it.isNotBlank() }
        if (
            initialized &&
            configuredBasedir == normalizedBasedir &&
            configuredGameMod == normalizedGameMod &&
            configuredCaseSensitive == caseSensitive
        ) {
            return
        }

        configuredBasedir = normalizedBasedir
        configuredGameMod = normalizedGameMod
        configuredCaseSensitive = caseSensitive

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
            ),
        )
        initialized = true
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
