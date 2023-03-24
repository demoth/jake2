package jake2.game

import jake2.game.adapters.SuperAdapter.Companion.registerUse
import jake2.game.components.ComponentType
import jake2.game.components.Light
import jake2.game.components.getComponent
import jake2.qcommon.Defines

/**
 * QUAKED light (0 1 0) (-8 -8 -8) (8 8 8)
 * START_OFF
 * Non-displayed light.
 * Default light value is 300.
 * Default style is 0. If targeted, will toggle between on and off (will have a dedicated light style).
 * Default _cone value is 10 (used to set size of light for spotlights)
 */
private const val START_OFF = 1
fun light(self: SubgameEntity, game: GameExportsImpl) {
    // no targeted lights in deathmatch, because they cause global messages
    if (game.skipForDeathmatch(self)) return

    if (self.targetname == null) {
        // useless
        game.freeEntity(self)
        return
    }

    val light = Light(self.style, !self.hasSpawnFlag(START_OFF))
    self.components[ComponentType.Light] = light

    if (light.lightStyle >= 32) {
        self.use = lightUse
        light.sendLightValue(game)
    }
}

private val lightUse = registerUse("lightUse") { self, _, _, game ->
    val light: Light = self.getComponent(ComponentType.Light) ?: return@registerUse
    light.switchedOn = !light.switchedOn
    light.sendLightValue(game)
}


/*
 * QUAKED light_mine1 (0 1 0) (-2 -2 -12) (2 2 12)
 */
fun lightMine1(ent: SubgameEntity, game: GameExportsImpl) {
    staticModel(ent, game, "models/objects/minelite/light1/tris.md2")
}

/*
 * QUAKED light_mine2 (0 1 0) (-2 -2 -12) (2 2 12)
 */
fun lightMine2(ent: SubgameEntity, game: GameExportsImpl) {
    staticModel(ent, game, "models/objects/minelite/light2/tris.md2")
}

// todo: expose this to the editor with as separate classname (misc_model?) and set model from a property
// check how it is implemented in other engines
fun staticModel(ent: SubgameEntity, game: GameExportsImpl, model: String) {
    ent.movetype = GameDefines.MOVETYPE_NONE
    ent.solid = Defines.SOLID_BBOX
    ent.s.modelindex = game.gameImports.modelindex(model)
    game.gameImports.linkentity(ent)
}
