package org.demoth.cake.modelviewer

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.loaders.FileHandleResolver
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.GdxRuntimeException
import java.io.File

/**
 * Lightweight resolver for the standalone model viewer.
 *
 * Lookup order:
 * 1. classpath/internal assets (viewer shaders, palette, icons)
 * 2. opened file folder (for ad-hoc local model bundles)
 * 3. optional basedir/mod fallback (same convention as the game client)
 */
class ModelViewerFileResolver(
    openedFilePath: String,
    private val basedir: String? = null,
    private val gamemod: String? = null,
) : FileHandleResolver {

    private val openedFileDirectory = Gdx.files.absolute(
        File(openedFilePath).absoluteFile.parentFile?.absolutePath ?: "."
    )

    private val basemod: String = "baseq2"

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
        val roots = mutableListOf<FileHandle>().apply {
            add(Gdx.files.classpath(""))
            add(Gdx.files.internal(""))
            add(openedFileDirectory)

            if (basedir != null) {
                if (gamemod != null) {
                    add(Gdx.files.absolute("$basedir/$gamemod"))
                }
                add(Gdx.files.absolute("$basedir/$basemod"))
            }
        }

        resolveCaseSensitive(roots, fileName)?.let { return it }
        resolveCaseInsensitive(roots, normalized.lowercase())?.let { return it }

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
}
