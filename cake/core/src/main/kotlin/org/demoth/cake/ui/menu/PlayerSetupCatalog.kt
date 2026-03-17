package org.demoth.cake.ui.menu

data class PlayerSetupCatalog(
    val models: List<String>,
    val skinsByModel: Map<String, List<String>>,
) {
    fun availableSkins(model: String): List<String> {
        val normalizedModel = model.trim().lowercase()
        return skinsByModel[normalizedModel]
            ?: skinsByModel[DEFAULT_MODEL]
            ?: listOf(DEFAULT_SKIN)
    }

    fun normalize(form: PlayerSetupFormState): PlayerSetupFormState {
        val resolvedModel = when {
            models.isEmpty() -> DEFAULT_MODEL
            models.contains(form.model.trim().lowercase()) -> form.model.trim().lowercase()
            else -> models.first()
        }
        val skins = availableSkins(resolvedModel)
        val resolvedSkin = when {
            skins.isEmpty() -> DEFAULT_SKIN
            skins.contains(form.skin.trim().lowercase()) -> form.skin.trim().lowercase()
            else -> skins.first()
        }
        return form.copy(
            model = resolvedModel,
            skin = resolvedSkin,
            hand = form.hand.coerceIn(0, 2),
        )
    }

    companion object {
        private const val DEFAULT_MODEL = "male"
        private const val DEFAULT_SKIN = "grunt"
        private val NON_SELECTABLE_SKINS = setOf("weapon", "disguise")

        fun fromResolvedFiles(paths: List<String>): PlayerSetupCatalog {
            val modelDirs = mutableSetOf<String>()
            val skinsByModel = linkedMapOf<String, MutableSet<String>>()

            for (rawPath in paths) {
                val path = rawPath.replace('\\', '/').lowercase()
                val parts = path.split('/')
                if (parts.size != 3 || parts.first() != "players") {
                    continue
                }

                val model = parts[1]
                val fileName = parts[2]
                if (fileName == "tris.md2") {
                    modelDirs += model
                    continue
                }

                if (!fileName.endsWith(".pcx")) {
                    continue
                }

                val skinName = fileName.removeSuffix(".pcx")
                if (skinName.endsWith("_i")) {
                    continue
                }
                if (skinName in NON_SELECTABLE_SKINS) {
                    continue
                }

                skinsByModel.getOrPut(model) { linkedSetOf() }.add(skinName)
            }

            val models = modelDirs
                .filter { skinsByModel[it]?.isNotEmpty() == true }
                .sortedWith(compareBy<String> {
                    when (it) {
                        "male" -> 0
                        "female" -> 1
                        else -> 2
                    }
                }.thenBy { it })

            if (models.isEmpty()) {
                return fallback()
            }

            val finalizedSkins = models.associateWith { model ->
                skinsByModel[model].orEmpty().sorted()
            }
            return PlayerSetupCatalog(models = models, skinsByModel = finalizedSkins)
        }

        fun fallback(): PlayerSetupCatalog = PlayerSetupCatalog(
            models = listOf(DEFAULT_MODEL),
            skinsByModel = mapOf(DEFAULT_MODEL to listOf(DEFAULT_SKIN)),
        )
    }
}
