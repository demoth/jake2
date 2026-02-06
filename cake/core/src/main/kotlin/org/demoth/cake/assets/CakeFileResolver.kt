package org.demoth.cake.assets

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.loaders.FileHandleResolver
import com.badlogic.gdx.files.FileHandle
import jake2.qcommon.Com


/**
 * This class is responsible for finding where a certain asset should be loaded from.
 * It doesn't load or cache anything.
 *
 * [basedir] Game installation directory, should be an absolute path
 * [basemod] Base game mod, default is "baseq2"
 * [gamemod] Game mod override like rogue or xatrix, has higher priority than the basemod
 * todo: implement resolvers for pak files
 * todo: implement indexing of pak files
 * todo: impelment setters for basedir/gamemod, with reindexing of pak files
 */
class CakeFileResolver(
    var basedir: String? = null,
    var gamemod: String? = null
) : FileHandleResolver {

    // tothink: do we need to be able to override this? like for q1 it will be id1
    private val basemod: String = "baseq2"

    /**
     * look for files in the following order:
     * 1. classpath (java classpath)
     * 2. internal (assets folder)
     * 3. list of mounted locations:
     *      3.1 basedir/gamemod/
     *      3.2 basedir/gamemod/other_pak_files
     *      3.3 basedir/gamemod/pak\d+.pak files
     * 4. same but for the basemod folder
     *      4.1 basedir/basemod/
     *      4.2 basedir/basemod/other_pak_files
     *      4.3 basedir/basemod/pak\d+.pak files
     */
    override fun resolve(fileName: String): FileHandle? {
        // first try to resolve the file matching the case
        val file = resolveInternal(fileName)
        if (file != null) return file
        // fallback to case-insensitive lookup using libgdx FileHandle API
        val caseInsensitive = resolveCaseInsensitive(fileName)
        if (caseInsensitive != null) {
            Com.Warn("Resource $fileName was found with different case")
            return caseInsensitive
        }
        Com.Warn("Resource $fileName was not found")
        return null
    }

    private fun resolveInternal(fileName: String): FileHandle? {
        // bootstrap assets - not overridable
        var file = Gdx.files.classpath(fileName)
        if (file.exists()) return file

        // engine "assets" folder
        file = Gdx.files.internal(fileName)
        if (file.exists()) return file

        if (basedir != null) {
            // check mod override
            if (gamemod != null) {
                file = Gdx.files.absolute("$basedir/$gamemod/$fileName")
                if (file.exists()) return file

                // todo: check the game mod pak files
            }
            // fallback to the base mod
            file = Gdx.files.absolute("$basedir/$basemod/$fileName")
            if (file.exists()) return file

            // todo: check the basemod pak files
        }
        return null

    }

    private fun resolveCaseInsensitive(fileName: String): FileHandle? {
        val normalized = fileName.replace('\\', '/')

        // classpath
        findCaseInsensitive(Gdx.files.classpath(""), normalized)?.let { if (it.exists()) return it }

        // engine "assets" folder
        findCaseInsensitive(Gdx.files.internal(""), normalized)?.let { if (it.exists()) return it }

        if (basedir != null) {
            // check mod override
            if (gamemod != null) {
                findCaseInsensitive(
                    Gdx.files.absolute("$basedir/$gamemod"),
                    normalized
                )?.let { if (it.exists()) return it }

                // todo: check the game mod pak files
            }
            // fallback to the base mod
            findCaseInsensitive(
                Gdx.files.absolute("$basedir/$basemod"),
                normalized
            )?.let { if (it.exists()) return it }

            // todo: check the basemod pak files
        }
        return null
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

            val next = current.list().firstOrNull { it.name().equals(part, ignoreCase = true) }
                ?: return null
            current = next
        }
        return current
    }
}
