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
 */
class CakeFileResolver(
    var basedir: String? = null,
    var basemod: String = "baseq2",
    var gamemod: String? = null
) : FileHandleResolver {
    // todo: implement resolvers for pak files

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
    override fun resolve(fileName: String?): FileHandle? {
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

                // todo: check the pak files
            }
            // fallback to the base mod
            file = Gdx.files.absolute("$basedir/$basemod/$fileName")
            if (file.exists()) return file

            // todo: check the pak files
        }

        Com.Warn("Resource $fileName was not found")
        return null
    }
}