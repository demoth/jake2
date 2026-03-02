package org.demoth.cake.modelviewer

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.loaders.FileHandleResolver
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.GdxRuntimeException
import jake2.qcommon.vfs.DefaultVirtualFileSystem
import jake2.qcommon.vfs.VfsConfig
import jake2.qcommon.vfs.VfsLookupOptions
import jake2.qcommon.vfs.VfsSourceType
import org.demoth.cake.assets.CakeVfsAssetSource
import java.io.File
import java.nio.file.Path
import java.util.LinkedHashSet

/**
 * Lightweight resolver for the standalone model viewer.
 *
 * Lookup order:
 * 1. classpath/internal assets (viewer shaders, palette, icons)
 * 2. opened file folder and its parents via local VFS (for local model/map bundles)
 * 3. optional basedir/mod fallback via Cake VFS adapter (same convention as the game client)
 */
class ModelViewerFileResolver(
    openedFilePath: String,
    private val basedir: String? = null,
    private val gamemod: String? = null,
) : FileHandleResolver {

    private val openedFileDirectoryPath = File(openedFilePath).absoluteFile.parentFile?.absolutePath ?: "."
    private val openedFileDirectory = Gdx.files.absolute(openedFileDirectoryPath)
    private val localVfs = DefaultVirtualFileSystem()
    private val gameDataVfs = CakeVfsAssetSource()

    init {
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

    override fun resolve(fileName: String): FileHandle? {
        // Absolute paths can come from CLI args and should always be accepted.
        val absoluteCandidate = File(fileName)
        if (absoluteCandidate.isAbsolute) {
            val absoluteHandle = Gdx.files.absolute(absoluteCandidate.path)
            if (absoluteHandle.exists()) {
                return absoluteHandle
            }
        }

        val normalized = fileName.replace('\\', '/')
        val roots = listOf(Gdx.files.classpath(""), Gdx.files.internal(""))
        resolveCaseSensitive(roots, fileName)?.let { return it }
        resolveCaseInsensitive(roots, normalized.lowercase())?.let { return it }

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
        val lookup = localVfs.resolve(fileName, VfsLookupOptions.DEFAULT)
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
