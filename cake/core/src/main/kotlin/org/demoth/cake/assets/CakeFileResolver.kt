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
        val file = resolveInternal(fileName)
        if (file != null) return file
        // maybe add a cvar switch to disable/enable lowercase file resolving?
        val lowercase = resolveInternal(fileName.lowercase())
        if (lowercase != null) {
            Com.Warn("Resource $fileName was found with lowercase")
            return lowercase
        }
        Com.Warn("Resource $fileName was not found")
        return null
    }

    private fun resolveInternal(fileName: String): FileHandle? {
        // bootstrap assets - not overridable
        var file = Gdx.files.classpath(fileName)
        if (file.exists()) return file

        // "assets" folder
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
}