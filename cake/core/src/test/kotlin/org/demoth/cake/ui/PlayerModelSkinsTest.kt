package org.demoth.cake.ui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.demoth.cake.ui.menu.PlayerSetupCatalog

class PlayerModelSkinsTest {
    @Test
    fun `matching stays in the player directory and shares setup exclusions`() {
        val paths = listOf("players/male/tris.md2", "players/male/z.pcx", "players/male/a.pcx",
            "players/male/a_i.pcx", "players/male/weapon.pcx", "players/male/disguise.pcx",
            "players/male/sub/other.pcx", "players/female/a.pcx", "players/male/readme.txt")
        assertEquals(listOf("players/male/a.pcx", "players/male/z.pcx"),
            playerModelSkinPaths("players/male/tris.md2", paths))
        assertEquals(listOf("a", "z"), PlayerSetupCatalog.fromResolvedFiles(paths).availableSkins("male"))
        assertNull(playerModelSkinPaths("models/monsters/soldier/tris.md2", paths))
        assertNull(playerModelSkinPaths("players/male/weapon.md2", paths))
        assertEquals(emptyList<String>(), playerModelSkinPaths("players/robot/tris.md2", paths))
    }

    @Test
    fun `matching preserves resolvable paths and does not cap the choices`() {
        val paths = (0..15).map { "Players/Male/skin%02d.PCX".format(it) }
        assertEquals(paths, playerModelSkinPaths("players/male/TRIS.MD2", paths))
    }
}
