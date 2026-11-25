package jake2.game

import jake2.game.adapters.SuperAdapter.Companion.registerThink
import jake2.qcommon.Defines
import jake2.qcommon.util.Math3D

/**
 * QUAKED info_player_start (1 0 0) (-16 -16 -24) (16 16 32)
 * The normal starting point for a level.
 */
fun infoPlayerStart(self: GameEntity, game: GameExportsImpl) {
    val mapName = game.level.mapname.orEmpty().lowercase()

    // map hack
    if (mapName == "security" && game.gameCvars.coop.typedValue) {
        // invoke one of our gross, ugly, disgusting hacks
        self.think.action = createCoopStartHack
        self.think.nextTime = game.level.time + Defines.FRAMETIME
    }

    // map hack
    if (mapName in unnamedStartEntrances) {
        // Call function to hack unnamed spawn points
        self.think.action = createUnnamedStart
        self.think.nextTime = game.level.time + Defines.FRAMETIME
    }
}

private val unnamedStartEntrances = mapOf(
    "mine1" to "mintro",
    "mine2" to "mine1",
    "mine3" to "mine2a",
    "mine4" to "mine3",
    "power2" to "power1",
    "waste1" to "power2",
    "waste2" to "waste1",
    "city2" to "city2nl"
)

/*
 * Some maps have no unnamed (e.g. generic)
 * info_player_start. This is no problem in
 * normal gameplay, but if the map is loaded
 * via console there is a huge chance that
 * the player will spawn in the wrong point.
 * Therefore create an unnamed info_player_start
 * at the correct point.
 */
private val createUnnamedStart = registerThink("SP_CreateUnnamedSpawn") { self, game ->
    val entrance = unnamedStartEntrances[game.level.mapname.lowercase()] ?: return@registerThink true
    if (entrance == self.targetname) {
        game.spawnOptional()?.apply {
            classname = "info_player_start"
            Math3D.VectorCopy(self.s.origin, s.origin)
            s.angles[1] = self.s.angles[1]
        }
    }
    true
}

private val mapsToFixCoop = setOf(
    "jail2",
    "jail4",
    "mintro",
    "mine1",
    "mine2",
    "mine3",
    "mine4",
    "lab",
    "boss1",
    "fact1",
    "fact3",
    "waste1", // really?
    "biggun",
    "space",
    "command",
    "power2",
    "strike",
    "city2"
)

/**
 * QUAKED info_player_coop (1 0 1) (-16 -16 -24) (16 16 32)
 * A potential spawning position for coop games.
 */
fun infoPlayerCoop(self: GameEntity, game: GameExportsImpl) {
    if (!game.gameCvars.coop.typedValue) {
        game.freeEntity(self)
        return
    }
    // map hack
    if (game.level.mapname.orEmpty().lowercase() in mapsToFixCoop) {
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
    for (spot in game.findEntities { it.classname == "info_player_start" }) {
        if (spot.targetname == null)
            continue
        Math3D.VectorSubtract(self.s.origin, spot.s.origin, d)
        if (Math3D.VectorLength(d) < 550) {
            self.targetname = spot.targetname
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
fun infoPlayerDeathmatch(self: GameEntity, game: GameExportsImpl) {
    if (!game.gameCvars.deathmatch.typedValue) {
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
fun infoNotNull(self: GameEntity, game: GameExportsImpl) {
    Math3D.VectorCopy(self.s.origin, self.absmin)
    Math3D.VectorCopy(self.s.origin, self.absmax)
}
