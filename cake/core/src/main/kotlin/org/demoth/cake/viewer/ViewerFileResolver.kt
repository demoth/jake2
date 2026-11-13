package org.demoth.cake.viewer

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.loaders.FileHandleResolver
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.GdxRuntimeException
import jake2.qcommon.vfs.DefaultVirtualFileSystem
import jake2.qcommon.vfs.VfsConfig
import jake2.qcommon.vfs.VfsEntry
import jake2.qcommon.vfs.VfsSourceType
import org.demoth.cake.assets.CakeVfsAssetSource
import org.demoth.cake.ui.playerModelSkinPaths
import org.demoth.cake.ui.playerSkinName
import jake2.qcommon.filesystem.IDALIASHEADER
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.File
import java.nio.file.Path
import java.util.LinkedHashSet

/**
 * Lightweight resolver for the standalone viewer.
 *
 * Lookup order:
 * 1. classpath/internal assets (viewer shaders, palette, icons)
 * 2. opened file folder and its parents via local VFS (for local model/map bundles)
 * 3. optional basedir/mod fallback via Cake VFS adapter (same convention as the game client)
 * Folder mode uses one VFS game root for both listing and dependencies, plus selected-preview sibling lookup.
 * Archive mode resolves only bundled classpath assets and entries in the selected archive.
 */
class ViewerFileResolver(
    openedFilePath: String? = null,
    private var basedir: String? = null,
    gamemod: String? = null,
) : org.demoth.cake.assets.TextureCandidateResolver {

    private var openedFileDirectoryPath = openedFilePath?.let { File(it).absoluteFile.parentFile?.absolutePath } ?: "."
    private var openedFileDirectory = Gdx.files.absolute(openedFileDirectoryPath)
    private val localVfs = DefaultVirtualFileSystem()
    private val gameDataVfs = CakeVfsAssetSource()
    private var archiveContext = false
    private var directoryContext = false
    private var previewDirectory = ""

    init {
        if (openedFilePath != null) configure(openedFilePath, basedir, gamemod)
    }

    /**
     * Changes the resource context while preserving the resolver used by asset loaders.
     * Call only after unloading the previous preview and finishing all asset-loading work.
     * This does not invalidate AssetManager's cached assets.
     */
    fun configure(openedFilePath: String, basedir: String? = null, gamemod: String? = null) {
        archiveContext = false
        directoryContext = false
        this.basedir = basedir
        openedFileDirectoryPath = File(openedFilePath).absoluteFile.parentFile?.absolutePath ?: "."
        openedFileDirectory = Gdx.files.absolute(openedFileDirectoryPath)
        localVfs.configure(
            VfsConfig(
                null,
                "baseq2",
                null,
                false,
                true,
                false,
                openedDirectoryAncestors().map { Path.of(it.path()) },
                setOf("pak", "pk2", "pk3", "pkz", "zip"),
            ),
        )
        gameDataVfs.configure(basedir = basedir, gameMod = gamemod, caseSensitive = false)
    }

    /** Changes to an isolated archive context under the same loading/unloading rules as [configure]. */
    fun configureArchive(archivePath: String): List<VfsEntry> {
        val entries = gameDataVfs.configureArchive(archivePath)
        archiveContext = true
        directoryContext = false
        return entries
    }

    /** Listing and preview dependencies share this single folder VFS snapshot. */
    fun configureDirectory(directory: String): List<VfsEntry> {
        val entries = gameDataVfs.configureDirectory(directory)
        archiveContext = false
        directoryContext = true
        previewDirectory = ""
        basedir = null
        return entries
    }

    /** Reuse sibling lookup for extracted model bundles without changing the mounted roots. */
    internal fun preparePreview(path: String) {
        if (directoryContext) previewDirectory = path.substringBeforeLast('/', "")
    }

    /** Discover external body skins without changing resolver context or loading any textures. */
    internal fun playerSkinPaths(modelPath: String): List<String>? {
        if (archiveContext) return playerModelSkinPaths(modelPath, gameDataVfs.listEntries().map { it.normalizedPath })
        if (directoryContext) {
            val paths = gameDataVfs.listEntries().map { it.normalizedPath }
            playerModelSkinPaths(modelPath, paths)?.let { return it }
            if (!modelPath.substringAfterLast('/').equals("tris.md2", ignoreCase = true)) return null
            val header = gameDataVfs.resolve(modelPath)?.read()?.use { it.readNBytes(24) } ?: return null
            if (!isSkinlessMd2(header)) return null
            val directory = modelPath.substringBeforeLast('/', "")
            return paths.filter { it.substringBeforeLast('/', "") == directory &&
                playerSkinName(it.substringAfterLast('/')) != null }.sorted()
        }
        val model = File(modelPath)
        if (!model.isAbsolute || !model.name.equals("tris.md2", ignoreCase = true)) return null
        val normalized = model.absolutePath.replace('\\', '/')
        val logical = Regex("(?:^|/)(players/[^/]+/tris\\.md2)$", RegexOption.IGNORE_CASE)
            .find(normalized)?.groupValues?.get(1)
        if (logical == null) {
            // An extracted player folder has no players/ prefix. Do not override embedded monster skins.
            val header = model.inputStream().use { it.readNBytes(24) }
            if (!isSkinlessMd2(header)) return null
        }
        val siblings = model.parentFile.listFiles().orEmpty()
            .filter { it.isFile && playerSkinName(it.name) != null }.map { it.absolutePath }
        val fallback = if (logical != null && basedir != null)
            playerModelSkinPaths(logical, gameDataVfs.listEntries().map { it.normalizedPath }).orEmpty()
        else emptyList()
        return (siblings + fallback).distinctBy { it.substringAfterLast('/').lowercase() }
            .sortedBy { it.substringAfterLast('/').lowercase() }
    }

    override fun textureCandidates(): List<String> = if (archiveContext || directoryContext) {
        gameDataVfs.listEntries().map { it.normalizedPath }
    } else {
        localVfs.listEntries().flatMap { entry ->
            val logical = entry.normalizedPath
            if (entry.source.type == VfsSourceType.DIRECTORY)
                listOf(logical, entry.source.containerPath.resolve(entry.source.entryPath).toAbsolutePath().toString())
            else listOf(logical)
        }.plus(gameDataVfs.listEntries().map { it.normalizedPath }).distinct()
    }

    override fun resolve(fileName: String): FileHandle? {
        // Loose previews accept absolute CLI paths; archive dependencies use logical paths.
        val absoluteCandidate = File(fileName)
        if (archiveContext && absoluteCandidate.isAbsolute) return null
        if (absoluteCandidate.isAbsolute) {
            val absoluteHandle = Gdx.files.absolute(absoluteCandidate.path)
            if (absoluteHandle.exists()) {
                return absoluteHandle
            }
        }

        val normalized = fileName.replace('\\', '/')
        val roots = if (archiveContext || directoryContext) listOf(Gdx.files.classpath(""))
            else listOf(Gdx.files.classpath(""), Gdx.files.internal(""))
        resolveCaseSensitive(roots, fileName)?.let { return it }
        resolveCaseInsensitive(roots, normalized.lowercase())?.let { return it }

        if (archiveContext) return gameDataVfs.resolve(fileName)
        if (directoryContext) {
            gameDataVfs.resolve(fileName)?.let { return it }
            val sibling = normalized.substringAfterLast('/')
            return gameDataVfs.resolve(if (previewDirectory.isEmpty()) sibling else "$previewDirectory/$sibling")
        }

        resolveFromLocalVfs(fileName)?.let { return it }
        if (basedir != null) {
            gameDataVfs.resolve(fileName)?.let { return it }
        }

        // Some MD2 files store full game-relative paths while assets are next to tris.md2.
        // todo: verify this
        val fileNameOnly = normalized.substringAfterLast('/')
        if (fileNameOnly != normalized) {
            openedFileDirectory.child(fileNameOnly).takeIf { it.exists() }?.let { return it }
            findCaseInsensitive(openedFileDirectory, fileNameOnly.lowercase())?.let { return it }
        }

        return null
    }

    private fun resolveCaseSensitive(roots: List<FileHandle>, fileName: String): FileHandle? {
        for (root in roots) {
            val resolved = root.child(fileName)
            if (resolved.exists()) {
                return resolved
            }
        }
        return null
    }

    private fun isSkinlessMd2(header: ByteArray): Boolean {
        if (header.size != 24) return false
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        return buffer.getInt(0) == IDALIASHEADER && buffer.getInt(4) == 8 && buffer.getInt(20) == 0
    }

    private fun resolveCaseInsensitive(roots: List<FileHandle>, relativePath: String): FileHandle? {
        for (root in roots) {
            val resolved = findCaseInsensitive(root, relativePath)
            if (resolved != null && resolved.exists()) {
                return resolved
            }
        }
        return null
    }

    private fun findCaseInsensitive(root: FileHandle, relativePath: String): FileHandle? {
        val parts = relativePath.split('/').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null

        var current = root
        for ((index, part) in parts.withIndex()) {
            if (!current.exists()) return null
            if (index < parts.lastIndex && !current.isDirectory) return null

            val entries = try {
                current.list()
            } catch (_: GdxRuntimeException) {
                return null
            }
            val next = entries.firstOrNull { it.name().equals(part, ignoreCase = true) } ?: return null
            current = next
        }
        return current
    }

    private fun resolveFromLocalVfs(fileName: String): FileHandle? {
        val lookup = localVfs.resolve(fileName)
        if (!lookup.found || lookup.entry.source.type != VfsSourceType.DIRECTORY) {
            return null
        }
        val sourcePath = lookup.entry.source.containerPath.resolve(lookup.entry.source.entryPath)
        val file = sourcePath.toFile()
        if (!file.exists() || !file.canRead()) {
            return null
        }
        return FileHandle(file)
    }

    private fun openedDirectoryAncestors(maxDepth: Int = 2): List<FileHandle> {
        val roots = LinkedHashSet<String>()
        var current: File? = File(openedFileDirectoryPath)
        var depth = 0
        val basedirBoundary = basedir?.let { File(it).absoluteFile.normalize().absolutePath }
        while (current != null && depth < maxDepth) {
            roots.add(current.absolutePath)
            if (basedirBoundary != null && current.absoluteFile.normalize().absolutePath == basedirBoundary) {
                break
            }
            current = current.parentFile
            depth++
        }
        return roots.map { Gdx.files.absolute(it) }
    }
}
