package org.demoth.cake.ui.menu

import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.find
import org.demoth.cake.assets.CakeFileResolver

/** Player identity settings and the selectable models/skins from the active content.
 * Form validation belongs to MenuController; profile persistence belongs to the caller.
 */
class PlayerSetup(
    private val fileResolver: CakeFileResolver,
    private val cvars: Cvar = Cvar.getInstance(),
) {
    private var cachedPlayerSetupCatalog: PlayerSetupCatalog? = null

    fun invalidateCatalog() {
        cachedPlayerSetupCatalog = null
    }

    fun catalog(): PlayerSetupCatalog {
        cachedPlayerSetupCatalog?.let { return it }
        val catalog = if (fileResolver.isVfsInitialized()) {
            PlayerSetupCatalog.fromResolvedFiles(fileResolver.debugResolvedFiles())
        } else {
            PlayerSetupCatalog.fallback()
        }
        cachedPlayerSetupCatalog = catalog
        return catalog
    }

    fun currentForm(): PlayerSetupFormState {
        val name = cvars.find<String>("name").typedValue.ifBlank { "unnamed" }
        val password = cvars.find<String>("password").typedValue
        val skinValue = cvars.find<String>("skin").typedValue.ifBlank { "male/grunt" }
        val split = skinValue.split('/', limit = 2)
        val rawModel = split.firstOrNull().orEmpty().ifBlank { "male" }
        val rawSkin = split.getOrNull(1).orEmpty().ifBlank { "grunt" }
        val hand = cvars.find<Int>("hand").typedValue
        return catalog().normalize(
            PlayerSetupFormState(
                name = name,
                password = password,
                model = rawModel,
                skin = rawSkin,
                hand = hand,
            ),
        )
    }

    fun apply(form: PlayerSetupFormState): PlayerSetupFormState {
        val normalized = catalog().normalize(
            form.copy(
                name = form.name.trim().ifBlank { "unnamed" },
                password = form.password.trim(),
            ),
        )
        val skinValue = "${normalized.model}/${normalized.skin}"
        val genderValue = deriveGenderFromSkin(skinValue)

        cvars.find<String>("name").set(normalized.name)
        cvars.find<String>("password").set(normalized.password)
        cvars.find<String>("skin").set(skinValue)
        cvars.find<Int>("hand").set(normalized.hand)
        cvars.find<String>("gender").set(genderValue)
        return normalized
    }

    private fun deriveGenderFromSkin(skinValue: String): String {
        return when {
            skinValue.startsWith("male", ignoreCase = true) || skinValue.startsWith(
                "cyborg",
                ignoreCase = true
            ) -> "male"

            skinValue.startsWith("female", ignoreCase = true) || skinValue.startsWith(
                "crackhor",
                ignoreCase = true
            ) -> "female"

            else -> "none"
        }
    }

}
