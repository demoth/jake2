package jake2.game

import jake2.game.adapters.SuperAdapter.Companion.registerThink
import jake2.game.adapters.SuperAdapter.Companion.registerUse
import jake2.qcommon.Defines
import jake2.qcommon.network.MulticastTypes
import jake2.qcommon.network.messages.server.PointTEMessage
import jake2.qcommon.network.messages.server.SplashTEMessage
import jake2.qcommon.util.Lib
import jake2.qcommon.util.Math3D

/**
 * QUAKED target_temp_entity (1 0 0) (-8 -8 -8) (8 8 8) 
 * Fire an origin based temp entity event to the clients. 
 * "style" type byte, see [jake2.qcommon.network.messages.server.PointTEMessage.SUBTYPES]
 */
fun targetTempEntity(self: SubgameEntity, game: GameExportsImpl) {
    self.use = tempEntityUse
}

private val tempEntityUse = registerUse("Use_Target_Tent") { self, _, _, game ->  
    game.gameImports.multicastMessage(self.s.origin, PointTEMessage(self.style, self.s.origin), MulticastTypes.MULTICAST_PVS);
}

/**
 * QUAKED target_speaker (1 0 0) (-8 -8 -8) (8 8 8) 
 * looped-on 
 * looped-off
 * reliable 
 * "noise" wav file to play 
 * "attenuation" -1 = none, send to whole level 
 * 1 = normal fighting sounds 
 * 2 = idle sound level 
 * 3 = ambient sound level 
 * "volume" 0.0 to 1.0 (1.0 default)
 *
 * Normal sounds play each time the target is used. The reliable flag can be
 * set for crucial voiceovers.
 *
 * Looped sounds are always atten 3 / vol 1, and the use function toggles it on/off. 
 * Multiple identical looping sounds will just increase volume without any speed cost.
 */
private const val LOOPED_ON = 1
private const val LOOPED_OFF = 2
private const val RELIABLE = 4
fun targetSpeaker(self: SubgameEntity, game: GameExportsImpl) {
    if (self.st.noise == null) {
        game.gameImports.dprintf("target_speaker with no noise set at ${Lib.vtos(self.s.origin)}\n")
        return
    }
    val noise: String = if (self.st.noise.contains(".wav")) self.st.noise else "${self.st.noise}.wav"
    self.noise_index = game.gameImports.soundindex(noise)
    if (self.volume == 0f)
        self.volume = 1.0f

    if (self.attenuation == 0f)
        self.attenuation = 1.0f
    else if (self.attenuation == -1f) // use -1 because 0 defaults to 1
        self.attenuation = 0f

    // check for prestarted looping sound
    if (self.hasSpawnFlag(LOOPED_ON))
        self.s.sound = self.noise_index
    self.use = targetSpeakerUse

    // must link the entity, so we get areas and clusters so
    // the server can determine who to send updates to
    game.gameImports.linkentity(self)
}

private val targetSpeakerUse = registerUse("Use_Target_Speaker") { self, _, _, game ->
    if (self.hasSpawnFlag(LOOPED_ON) && self.hasSpawnFlag(LOOPED_OFF)) { // looping sound toggles
        if (self.s.sound != 0)
            self.s.sound = 0 // turn it off
        else 
            self.s.sound = self.noise_index // start it
    } else { // normal sound
        val chan: Int = if (self.hasSpawnFlag(RELIABLE)) Defines.CHAN_VOICE or Defines.CHAN_RELIABLE else Defines.CHAN_VOICE
        // use a positioned_sound, because this entity won't normally be
        // sent to any clients because it is invisible
        game.gameImports.positioned_sound(
            self.s.origin, self, chan,
            self.noise_index, self.volume, self.attenuation, 0f
        )
    }

}

/**
 * QUAKED target_explosion (1 0 0) (-8 -8 -8) (8 8 8) 
 * Spawns an explosion temporary entity when used.
 *
 * "delay" wait this long before going off "dmg" how much radius damage
 * should be done, defaults to 0
 */
fun targetExplosion(self: SubgameEntity, game: GameExportsImpl) {
    self.use = targetExplosionUse
    self.svflags = Defines.SVF_NOCLIENT
}

private val targetExplosionUse = registerUse("use_target_explosion") { self, _, activator, game ->
    self.activator = activator

    if (self.delay != 0f) {
        self.think.action = targetExplosionExplode
        self.think.nextTime = game.level.time + self.delay
    } else {
        targetExplosionExplode.think(self, game) // BOOM!
    }
}

private val targetExplosionExplode = registerThink("target_explosion_explode") { self, game ->
    game.gameImports.multicastMessage(self.s.origin, PointTEMessage(Defines.TE_EXPLOSION1, self.s.origin), MulticastTypes.MULTICAST_PHS)

    GameCombat.T_RadiusDamage(self, self.activator, self.dmg.toFloat(), null,
        (self.dmg + 40).toFloat(), GameDefines.MOD_EXPLOSIVE, game)

    val delay = self.delay
    self.delay = 0f
    GameUtil.G_UseTargets(self, self.activator, game)
    self.delay = delay
    true
}

/**
 * QUAKED target_secret (1 0 1) (-8 -8 -8) (8 8 8)
 * Counts a secret found.
 * These are single use targets.
 */
fun targetSecret(self: SubgameEntity, game: GameExportsImpl) {
    if (game.gameCvars.deathmatch.value != 0f) { // auto-remove for deathmatch
        game.freeEntity(self)
        return
    }
    self.use = targetSecretUse
    if (self.st.noise == null)
        self.st.noise = "misc/secret.wav"
    self.noise_index = game.gameImports.soundindex(self.st.noise)
    self.svflags = Defines.SVF_NOCLIENT
    game.level.total_secrets++

    // map bug hack
    if ("mine3" == game.level.mapname
        && self.s.origin[0] == 280f
        && self.s.origin[1] == -2048f
        && self.s.origin[2] == -624f
    ) {
        self.message = "You have found a secret area."
    }
}

private val targetSecretUse = registerUse("use_target_explosion") { self, _, activator, game ->
    game.gameImports.sound(self, Defines.CHAN_VOICE, self.noise_index, 1f, Defines.ATTN_NORM.toFloat(), 0f)

    game.level.found_secrets++

    GameUtil.G_UseTargets(self, activator, game)
    game.freeEntity(self)
}

/**
 * QUAKED target_goal (1 0 1) (-8 -8 -8) (8 8 8) Counts a goal completed.
 * These are single use targets.
 */
fun targetGoal(self: SubgameEntity, game: GameExportsImpl) {
    if (game.gameCvars.deathmatch.value != 0f) { // auto-remove for deathmatch
        game.freeEntity(self)
        return
    }
    self.use = targetGoalUse
    if (self.st.noise == null)
        self.st.noise = "misc/secret.wav"
    self.noise_index = game.gameImports.soundindex(self.st.noise)
    self.svflags = Defines.SVF_NOCLIENT
    game.level.total_goals++
}

private val targetGoalUse = registerUse("use_target_goal") { self, _, activator, gameExports ->
    gameExports.gameImports.sound(self, Defines.CHAN_VOICE, self.noise_index, 1f, Defines.ATTN_NORM.toFloat(), 0f)

    gameExports.level.found_goals++

    if (gameExports.level.found_goals == gameExports.level.total_goals)
        gameExports.gameImports.configstring(Defines.CS_CDTRACK,"0")

    GameUtil.G_UseTargets(self, activator, gameExports)
    gameExports.freeEntity(self)
}

/**
 * QUAKED target_splash (1 0 0) (-8 -8 -8) (8 8 8) Creates a particle splash
 * effect when used.
 *
 * Set "sounds" to one of the following: 
 * 1) sparks 2) blue water 3) brown water 4) slime 5) lava 6) blood
 *
 * "count" how many pixels in the splash (32 by default)
 * "dmg" if set, does a radius damage at this location when it splashes useful for lava/sparks
 */
fun targetSplash(self: SubgameEntity, game: GameExportsImpl) {
    self.use = targetSplashUse
    GameBase.G_SetMovedir(self.s.angles, self.movedir)
    if (0 == self.count)
        self.count = 32
    self.svflags = Defines.SVF_NOCLIENT
}

private val targetSplashUse = registerUse("use_target_splash") { self, _, activator, gameExports ->
    gameExports.gameImports.multicastMessage(
        self.s.origin,
        SplashTEMessage(Defines.TE_SPLASH, self.count, self.s.origin, self.movedir, self.sounds),
        MulticastTypes.MULTICAST_PVS
    )

    if (self.dmg != 0)
        GameCombat.T_RadiusDamage(
            self,
            activator,
            self.dmg.toFloat(),
            null,
            (self.dmg + 40).toFloat(),
            GameDefines.MOD_SPLASH,
            gameExports
        )
}

/**
 * QUAKED target_spawner (1 0 0) (-8 -8 -8) (8 8 8) 
 * Set target to the type of entity you want spawned. 
 * Useful for spawning monsters and gibs in the factory levels.
 *
 * For monsters: Set direction to the facing you want it to have.
 *
 * For gibs: Set direction if you want it moving and speed how fast it
 * should be moving otherwise it will just be dropped
 */
fun targetSpawner(self: SubgameEntity, game: GameExportsImpl) {
    self.use = targetSpawnerUse
    self.svflags = Defines.SVF_NOCLIENT
    if (self.speed != 0f) {
        GameBase.G_SetMovedir(self.s.angles, self.movedir)
        Math3D.VectorScale(self.movedir, self.speed, self.movedir)
    }
}

private val targetSpawnerUse = registerUse("use_target_spawner") { self, _, _, game ->
    val ent: SubgameEntity = game.G_Spawn()
    ent.classname = self.target
    Math3D.VectorCopy(self.s.origin, ent.s.origin)
    Math3D.VectorCopy(self.s.angles, ent.s.angles)
    GameSpawn.ED_CallSpawn(ent, game)
    game.gameImports.unlinkentity(ent)
    GameUtil.KillBox(ent, game)
    game.gameImports.linkentity(ent)
    if (self.speed != 0f)
        Math3D.VectorCopy(self.movedir, ent.velocity)
}

