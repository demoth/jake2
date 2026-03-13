package org.demoth.cake.assets

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.loaders.FileHandleResolver
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.GdxRuntimeException
import jake2.qcommon.Com
import jake2.qcommon.Defines
import jake2.qcommon.exec.Cvar
import java.util.Locale


/**
 * This class is responsible for finding where a certain asset should be loaded from.
 * It doesn't load or cache anything.
 *
 * [basedir] Game installation directory, should be an absolute path
 * [gamemod] Game mod override like rogue or xatrix, has higher priority than the basemod (default baseq2)
 * [caseSensitive] strict lookup mode, default remains case-insensitive for compatibility
 */
class CakeFileResolver(
    basedir: String? = null,
    gamemod: String? = null,
    caseSensitive: Boolean = false,
) : FileHandleResolver {

    private val fsDebugLoaders = Cvar.getInstance().Get("fs_debug_loaders", "0", Defines.CVAR_ARCHIVE)
    private val vfsAssetSource = CakeVfsAssetSource()
    private val warnedMissing = LinkedHashSet<Pair<String, ResourceKind>>()

    var caseSensitive: Boolean = caseSensitive
        set(value) {
            field = value
            vfsAssetSource.configure(basedir = basedir, gameMod = gamemod, caseSensitive = field)
        }

    var basedir: String? = basedir
        set(value) {
            field = value
            vfsAssetSource.configure(basedir = field, gameMod = gamemod, caseSensitive = caseSensitive)
        }

    var gamemod: String? = gamemod
        set(value) {
            field = value
            vfsAssetSource.configure(basedir = basedir, gameMod = field, caseSensitive = caseSensitive)
        }

    init {
        vfsAssetSource.configure(basedir = this.basedir, gameMod = this.gamemod, caseSensitive = this.caseSensitive)
    }

    fun isVfsInitialized(): Boolean = vfsAssetSource.isInitialized()

    fun debugResolvedFiles(): List<String> = vfsAssetSource.debugResolvedFiles()

    fun debugMounts(): List<String> = vfsAssetSource.debugMounts()

    fun debugOverrides(): List<String> = vfsAssetSource.debugOverrides()

    /**
     * look for files in the following order:
     * 1. qcommon VFS game-data layers:
     *      1.1 basedir/gamemod loose
     *      1.2 basedir/gamemod packages (.pak/.pk2/.pk3/.pkz/.zip)
     *      1.3 basedir/baseq2 loose
     *      1.4 basedir/baseq2 packages
     * 2. classpath fallback (java classpath)
     * 3. internal fallback (engine assets folder)
     *
     * supports case-insensitive lookup by default (strict mode via [caseSensitive]).
     *
     * Synthetic key support:
     * - player MD2 variants use `<skinPath>|<modelPath>` for AssetManager cache separation.
     * - resolver ignores the prefix and resolves only `<modelPath>`.
     */
    override fun resolve(fileName: String): FileHandle? {
        // Variant keys can include skin prefix metadata (e.g. "<skin>|<model>"),
        // only the trailing model path should be resolved on disk.
        val rawPath = fileName.substringAfterLast('|')
        val path = normalizeLookupPath(rawPath)
        if (path == null) {
            return null
        }

        tryResolve(path)?.let { return it }

        val resourceKind = ResourceKind.fromPath(path) ?: return null
        if (resourceKind.tolerableMissing) {
            warnMissingOnce(path, resourceKind, "continuing without it")
            return null
        }

        if (fsDebugLoaders.value != 0f) {
            val fallbackPath = resourceKind.fallbackAssetPath
                ?: throw MissingResourceException(path, resourceKind, "resolver")
            warnMissingOnce(path, resourceKind, "using fallback '$fallbackPath'")
            return resolveBundledAsset(fallbackPath) ?: Gdx.files.internal(fallbackPath)
        }

        throw MissingResourceException(path, resourceKind, "resolver")
    }

    internal fun tryResolve(fileName: String): FileHandle? {
        val vfsResolved = vfsAssetSource.resolve(fileName)
        if (vfsResolved != null) {
            return vfsResolved
        }
        return resolveBundledAsset(fileName)
    }

    /**
     * Canonicalize a logical asset path while preserving game-root sandboxing.
     *
     * Quake2 content can reference sibling assets with parent segments
     * (for example `models/monsters/tank/../ctank/pain.pcx`).
     * We collapse such segments, but reject attempts to escape above root.
     */
    private fun normalizeLookupPath(rawPath: String): String? {
        val parts = rawPath.replace('\\', '/').split('/')
        val normalized = ArrayDeque<String>()

        for (part in parts) {
            if (part.isEmpty() || part == ".") {
                continue
            }
            if (part == "..") {
                if (normalized.isEmpty()) {
                    return null
                }
                normalized.removeLast()
                continue
            }
            normalized.addLast(part)
        }

        if (normalized.isEmpty()) {
            return null
        }
        return normalized.joinToString("/")
    }

    private fun resolveBundledAsset(fileName: String): FileHandle? {
        // SkyLoader uses synthetic keys like sky/<name>.sky; no physical file is required.
        val normalized = fileName.replace('\\', '/').lowercase(Locale.ROOT)
        if (normalized.startsWith("sky/") && normalized.endsWith(".sky")) {
            return Gdx.files.internal("") // return an empty file handle
        }

        // Fallback-only roots: bundled resources should not override mounted game content.
        val roots = listOf(Gdx.files.classpath(""), Gdx.files.internal(""))

        for (root in roots) {
            val exact = root.child(fileName)
            if (exact.exists()) return exact
            val caseInsensitive = findCaseInsensitive(root, normalized)
            if (caseInsensitive != null && caseInsensitive.exists()) {
                Com.Warn("Resource $fileName was found with different case")
                return caseInsensitive
            }
        }
        return null
    }

    private fun warnMissingOnce(path: String, resourceKind: ResourceKind, detail: String) {
        val key = path.lowercase(Locale.ROOT) to resourceKind
        if (warnedMissing.add(key)) {
            Com.Warn("Missing ${resourceKind.name.lowercase(Locale.ROOT)} resource '$path'; $detail")
        }
    }

    /**
     * Resolves a relative path under [root] by matching each path segment case-insensitively.
     * This exists to handle Quake2 assets whose on-disk casing does not match the requested path.
     */
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
                // Some backends (classpath root) do not support listing; skip this root.
                return null
            }
            val next = entries.firstOrNull { it.name().equals(part, ignoreCase = true) }
                ?: return null
            current = next
        }
        return current
    }
}
