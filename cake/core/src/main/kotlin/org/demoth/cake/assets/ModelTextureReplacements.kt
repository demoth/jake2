package org.demoth.cake.assets

import com.badlogic.gdx.assets.loaders.FileHandleResolver
import jake2.qcommon.Defines.CVAR_ARCHIVE
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.CvarTypes
import jake2.qcommon.exec.find

/** Candidate paths retain the order supplied by the content VFS. */
interface TextureCandidateResolver : FileHandleResolver {
    fun textureCandidates(): List<String>
}

object ModelTextureReplacements {
    const val CVAR = "r_texture_replacements"

    fun register() = Cvar.getInstance().register(CVAR, CvarTypes.BOOLEAN, true, CVAR_ARCHIVE,
        "Use same-name JPG/JPEG replacements for model skins (applies when models are reloaded)")

    fun reset() {
        register()
        Cvar.getInstance().find<Boolean>(CVAR).forceSet(true)
    }

    fun enabled(): Boolean = !Cvar.getInstance().isRegistered(CVAR) || Cvar.getInstance().find<Boolean>(CVAR).typedValue

    internal fun resolve(path: String, resolver: FileHandleResolver): String =
        if (enabled() && resolver is TextureCandidateResolver) select(path, resolver.textureCandidates()) else path

    internal fun select(path: String, candidates: List<String>): String {
        val stem = path.replace('\\', '/').substringBeforeLast('.')
        return candidates.firstOrNull {
            !it.equals(path, ignoreCase = true) &&
                it.substringBeforeLast('.').equals(stem, ignoreCase = true) &&
                it.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg")
        } ?: path
    }
}
