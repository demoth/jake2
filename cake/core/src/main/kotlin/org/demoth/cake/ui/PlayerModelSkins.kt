package org.demoth.cake.ui

/** Shared selectable player-skin convention; icons and auxiliary textures are not body skins. */
internal fun playerSkinName(fileName: String): String? {
    val name = fileName.lowercase()
    if (!name.endsWith(".pcx")) return null
    val skin = name.removeSuffix(".pcx")
    return skin.takeUnless { it.isEmpty() || it.endsWith("_i") || it in setOf("weapon", "disguise") }
}

/** Null means an ordinary MD2; an empty list means a player model with no available skins. */
internal fun playerModelSkinPaths(modelPath: String, paths: List<String>): List<String>? {
    val model = modelPath.replace('\\', '/')
    if (!model.matches(Regex("players/[^/]+/tris\\.md2", RegexOption.IGNORE_CASE))) return null
    val directory = model.substringBeforeLast('/')
    return paths.filter { path ->
        val normalized = path.replace('\\', '/')
        normalized.substringBeforeLast('/', "").equals(directory, ignoreCase = true) &&
            playerSkinName(normalized.substringAfterLast('/')) != null
    }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
}
