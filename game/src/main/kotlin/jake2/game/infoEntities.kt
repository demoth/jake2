package jake2.game

import jake2.game.adapters.SuperAdapter.Companion.registerThink
import jake2.qcommon.Defines

/**
 * QUAKED info_player_start (1 0 0) (-16 -16 -24) (16 16 32)
 * The normal starting point for a level.
 */
fun infoPlayerStart(self: SubgameEntity, game: GameExportsImpl) {
    if (game.gameCvars.coop.value == 0f)
        return
    if ("security".equals(game.level.mapname, true)) {
        // invoke one of our gross, ugly, disgusting hacks
        self.think.action = fixCoopStartHack
        self.think.nextTime = game.level.time + Defines.FRAMETIME
    }
}

/*
 * Some maps have no coop spawnpoints at
 * all. Add these by injecting entities
 * into the map where they should have
 * been
 */
private val fixCoopStartHack = registerThink("SP_CreateCoopSpots") { _, game ->
    if ("security".equals(game.level.mapname, true)) {
        game.G_Spawn().apply {
            classname = "info_player_coop"
            s.origin[0] = (188 - 64).toFloat()
            s.origin[1] = -164f
            s.origin[2] = 80f
            targetname = "jail3"
            s.angles[1] = 90f
        }
        game.G_Spawn().apply {
            classname = "info_player_coop"
            s.origin[0] = (188 + 64).toFloat()
            s.origin[1] = -164f
            s.origin[2] = 80f
            targetname = "jail3"
            s.angles[1] = 90f
        }
        game.G_Spawn().apply {
            classname = "info_player_coop"
            s.origin[0] = (188 + 128).toFloat()
            s.origin[1] = -164f
            s.origin[2] = 80f
            targetname = "jail3"
            s.angles[1] = 90f
        }
    }
    true
}
