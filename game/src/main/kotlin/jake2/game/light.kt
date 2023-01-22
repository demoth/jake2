package jake2.game

import jake2.game.adapters.SuperAdapter.Companion.registerUse
import jake2.qcommon.Defines

/**
 * QUAKED light (0 1 0) (-8 -8 -8) (8 8 8) START_OFF Non-displayed light.
 * Default light value is 300. Default style is 0. If targeted, will toggle
 * between on and off. Default _cone value is 10 (used to set size of light
 * for spotlights)
 */

private const val START_OFF = 1
fun light(self: SubgameEntity, game: GameExportsImpl) {
    // no targeted lights in deathmatch, because they cause global messages
    if (self.targetname == null || game.gameCvars.deathmatch.value != 0f) {
        game.freeEntity(self)
        return
    }
    if (self.style >= 32) {
        self.use = lightUse
        val lightValue = if (self.hasSpawnFlag(START_OFF)) "a" else "m"
        game.gameImports.configstring(Defines.CS_LIGHTS + self.style, lightValue)
    }
}

private val lightUse = registerUse("lightUse") { self, other, activator, game ->
    if (self.hasSpawnFlag(START_OFF)) {
        game.gameImports.configstring(Defines.CS_LIGHTS + self.style, "m")
        self.unsetSpawnFlag(START_OFF)
    } else {
        game.gameImports.configstring(Defines.CS_LIGHTS + self.style, "a")
        self.setSpawnFlag(START_OFF)
    }
}


// fixme: not lights but just simple static models
/*
 * QUAKED light_mine1 (0 1 0) (-2 -2 -12) (2 2 12)
 */
fun lightMine1(ent: SubgameEntity, game: GameExportsImpl) {
    ent.movetype = GameDefines.MOVETYPE_NONE
    ent.solid = Defines.SOLID_BBOX
    ent.s.modelindex = game.gameImports.modelindex("models/objects/minelite/light1/tris.md2")
    game.gameImports.linkentity(ent)
}

/*
 * QUAKED light_mine2 (0 1 0) (-2 -2 -12) (2 2 12)
 */
fun lightMine2(ent: SubgameEntity, game: GameExportsImpl) {
    ent.movetype = GameDefines.MOVETYPE_NONE
    ent.solid = Defines.SOLID_BBOX
    ent.s.modelindex = game.gameImports.modelindex("models/objects/minelite/light2/tris.md2")
    game.gameImports.linkentity(ent)
}
