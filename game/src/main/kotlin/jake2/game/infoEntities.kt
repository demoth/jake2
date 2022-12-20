package jake2.game

import jake2.game.adapters.SuperAdapter.Companion.registerThink
import jake2.qcommon.Defines
import jake2.qcommon.util.Math3D

/**
 * QUAKED info_player_start (1 0 0) (-16 -16 -24) (16 16 32)
 * The normal starting point for a level.
 */
fun infoPlayerStart(self: SubgameEntity, game: GameExportsImpl) {
    if (game.gameCvars.coop.value == 0f)
        return
    if ("security".equals(game.level.mapname, true)) {
        // invoke one of our gross, ugly, disgusting hacks
        self.think.action = createCoopStartHack
        self.think.nextTime = game.level.time + Defines.FRAMETIME
    }
}

private val mapsToFixCoop = setOf(
    "jail2",
    "jail4",
    "mine1",
    "mine2",
    "mine3",
    "mine4",
    "lab",
    "boss1",
    "fact3",
    "biggun",
    "space",
    "command",
    "power2",
    "strike"
)

/**
 * QUAKED info_player_coop (1 0 1) (-16 -16 -24) (16 16 32)
 * A potential spawning position for coop games.
 */
fun infoPlayerCoop(self: SubgameEntity, game: GameExportsImpl) {
    if (game.gameCvars.coop.value == 0f) {
        game.freeEntity(self)
        return
    }
    if (game.level.mapname in mapsToFixCoop) {
        // invoke one of our gross, ugly, disgusting hacks
        self.think.action = fixCoopStartHack
        self.think.nextTime = game.level.time + Defines.FRAMETIME
    }
}

/*
 * The ugly as hell coop spawnpoint fixup function.
 * While coop was planed by id, it wasn't part of
 * the initial release and added later with patch
 * to version 2.00. The spawnpoints in some maps
 * were SNAFU, some have wrong targets and some
 * no name at all. Fix this by matching the coop
 * spawnpoint target names to the nearest named
 * single player spot.
 */
private val fixCoopStartHack = registerThink("SP_FixCoopSpots") { self, game ->
    val d = floatArrayOf(0f, 0f, 0f)
    var es: EdictIterator? = null
    while (true) {
        es = GameBase.G_Find(es, GameBase.findByClassName, "info_player_start", game)
        if (es == null)
            return@registerThink true
        val spot = es.o
        if (spot.targetname == null)
            continue
        Math3D.VectorSubtract(self.s.origin, spot.s.origin, d)
        if (Math3D.VectorLength(d) < 384) {
            if (self.targetname == null || self.targetname.equals(spot.targetname, true)) {
                self.targetname = spot.targetname
            }
            return@registerThink true
        }
    }
    true
}

/*
 * Some maps have no coop spawnpoints at
 * all. Add these by injecting entities
 * into the map where they should have
 * been
 */
private val createCoopStartHack = registerThink("SP_CreateCoopSpots") { _, game ->
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

/**
 * QUAKED info_player_deathmatch (1 0 1) (-16 -16 -24) (16 16 32)
 * A potential spawning position for deathmatch games.
 */
fun infoPlayerDeathmatch(self: SubgameEntity, game: GameExportsImpl) {
    if (game.gameCvars.deathmatch.value == 0f) {
        game.freeEntity(self)
        return
    }
    // deathmatch (re)spawnpoints have a distinct model
    miscTeleporterDest(self, game)
}


/**
 * QUAKED info_notnull (0 0.5 0) (-4 -4 -4) (4 4 4)
 * Used as a positional target for lightning.
 */
fun infoNotNull(self: SubgameEntity, game: GameExportsImpl) {
    Math3D.VectorCopy(self.s.origin, self.absmin)
    Math3D.VectorCopy(self.s.origin, self.absmax)
}
