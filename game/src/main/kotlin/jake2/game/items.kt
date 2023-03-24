package jake2.game

import jake2.game.components.ComponentType
import jake2.game.components.Medkit
import jake2.game.items.GameItems
import jake2.qcommon.Defines

/*
* QUAKED item_health (.3 .3 1) (-16 -16 -16) (16 16 16)
*/
fun itemHealthMedium(self: SubgameEntity, game: GameExportsImpl) {
    spawnHealth(self, game, "models/items/healing/medium/tris.md2", "items/n_health.wav", 10)
}

/*
 * QUAKED item_health_small (.3 .3 1) (-16 -16 -16) (16 16 16)
 */
fun itemHealthSmall(self: SubgameEntity, game: GameExportsImpl) {
    spawnHealth(self, game, "models/items/healing/stimpack/tris.md2", "items/s_health.wav", 2, true)
}

/*
 * QUAKED item_health_large (.3 .3 1) (-16 -16 -16) (16 16 16)
 */
fun itemHealthLarge(self: SubgameEntity, game: GameExportsImpl) {
    spawnHealth(self, game, "models/items/healing/large/tris.md2", "items/l_health.wav", 25)
}

/*
 * QUAKED item_health_mega (.3 .3 1) (-16 -16 -16) (16 16 16)
 */
fun itemHealthMega(self: SubgameEntity, game: GameExportsImpl) {
    spawnHealth(self, game, "models/items/mega_h/tris.md2", "items/m_health.wav", 100, true, true)
}


private fun spawnHealth(self: SubgameEntity, game: GameExportsImpl, model: String, sound: String, amount: Int, ignoreHealth: Boolean = false, timed: Boolean = false) {
    if (game.skipForDeathmatch(self)) return

    if (game.gameCvars.dmflags.value.toInt() and Defines.DF_NO_HEALTH != 0) {
        game.freeEntity(self)
        return
    }

    self.components[ComponentType.ItemHealth] = Medkit(amount, game.gameImports.soundindex(sound), ignoreHealth, timed)
    self.model = model

    GameItems.SpawnItem(self, GameItems.FindItem("Health", game), game)
}
