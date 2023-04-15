package jake2.game

import jake2.game.adapters.SuperAdapter.Companion.registerThink
import jake2.game.adapters.SuperAdapter.Companion.registerUse
import jake2.game.components.Earthquake
import jake2.game.components.LightRamp
import jake2.game.components.addComponent
import jake2.game.components.getComponent
import jake2.qcommon.Defines
import jake2.qcommon.Globals
import jake2.qcommon.network.MulticastTypes
import jake2.qcommon.network.messages.server.PointTEMessage
import jake2.qcommon.network.messages.server.SplashTEMessage
import jake2.qcommon.trace_t
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
    if (self.hasSpawnFlag(LOOPED_ON) || self.hasSpawnFlag(LOOPED_OFF)) { // looping sound toggles
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
    if (game.skipForDeathmatch(self)) return

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
    if (game.skipForDeathmatch(self)) return

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

/**
 * QUAKED target_blaster (1 0 0) (-8 -8 -8) (8 8 8) 
 * NOTRAIL
 * NOEFFECTS
 * Fires a blaster bolt in the set direction when triggered.
 *
 * dmg default is 15 speed default is 1000
 */
private const val NO_TRAIL = 1
private const val NO_EFFECTS = 2
fun targetBlaster(self: SubgameEntity, game: GameExportsImpl) {
    self.use = targetBlasterUse
    GameBase.G_SetMovedir(self.s.angles, self.movedir)
    self.noise_index = game.gameImports.soundindex("weapons/laser2.wav")
    if (self.dmg == 0)
        self.dmg = 15
    if (self.speed == 0f)
        self.speed = 1000f
    self.svflags = Defines.SVF_NOCLIENT
}

private val targetBlasterUse = registerUse("use_target_blaster") { self, _, _, game ->

    // fixme
    val effect: Int = if (self.hasSpawnFlag(NO_EFFECTS))
        0
    else if (self.hasSpawnFlag(NO_TRAIL))
        Defines.EF_HYPERBLASTER
    else
        Defines.EF_BLASTER

    GameWeapon.fire_blaster(
        self, self.s.origin, self.movedir, self.dmg, self.speed.toInt(),
        Defines.EF_BLASTER, 
        self.hasSpawnFlag(NO_TRAIL), // fixed?
        game
    )

    game.gameImports.sound(self, Defines.CHAN_VOICE, self.noise_index, 1f, Defines.ATTN_NORM.toFloat(), 0f)
}

/**
 * QUAKED target_earthquake (1 0 0) (-8 -8 -8) (8 8 8)
 * When triggered, this initiates a level-wide earthquake.
 * All players and monsters are affected.
 *
 * "speed" severity of the quake (default:200)
 *
 * "count" duration of the quake in seconds (default:5)
 */
fun targetEarthquake(self: SubgameEntity, game: GameExportsImpl) {
    if (self.targetname == null)
        game.gameImports.dprintf("untargeted ${self.classname} at ${Lib.vtos(self.s.origin)}\n")

    self.addComponent(Earthquake(
        game.gameImports.soundindex("world/quake.wav"),
        if (self.count != 0) self.count.toFloat() else 5f,
        if (self.speed != 0f) self.speed else 200f
    ))


    self.svflags = self.svflags or Defines.SVF_NOCLIENT
    self.think.action = targetEarthquakeThink
    // nextThink is set when used
    self.use = targetEarthquakeUse
}

private val targetEarthquakeUse = registerUse("target_earthquake_use") { self, other, activator, game ->
    val earthquake: Earthquake = self.getComponent() ?: return@registerUse
    earthquake.stopTime = game.level.time + earthquake.duration
    self.think.nextTime = game.level.time + Defines.FRAMETIME
    self.activator = activator
    earthquake.soundTime = 0f
}

private val targetEarthquakeThink = registerThink("target_earthquake_think") { self, game ->
    val earthquake: Earthquake = self.getComponent() ?: return@registerThink false

    if (earthquake.soundTime < game.level.time) {
        game.gameImports.positioned_sound(
            self.s.origin, self,
            Defines.CHAN_AUTO, earthquake.soundIndex, 1.0f,
            Defines.ATTN_NONE.toFloat(), 0f
        )
        earthquake.soundTime = game.level.time + 0.5f
    }

    for (i in 1 until game.num_edicts) {
        val entity = game.g_edicts[i]
        if (!entity.inuse)
            continue
        if (entity.client == null)
            continue
        if (entity.groundentity == null)
            continue
        entity.groundentity = null
        entity.velocity[0] += Lib.crandom() * 150
        entity.velocity[1] += Lib.crandom() * 150
        entity.velocity[2] = earthquake.magnitude * (100.0f / entity.mass)
    }

    if (game.level.time < earthquake.stopTime)
        self.think.nextTime = game.level.time + Defines.FRAMETIME

    true
}

/**
 * QUAKED target_help (1 0 1) (-16 -16 -24) (16 16 24)
 * HELP1 - if sets the primary objective, else - secondary objective
 * When fired, the "message" key becomes the current personal computer string,
 * and the  message light will be set on all clients status bars.
 */
private const val HELP1 = 1
fun targetHelp(self: SubgameEntity, game: GameExportsImpl) {
    if (game.skipForDeathmatch(self)) return

    if (self.message == null) {
        game.gameImports.dprintf("${self.classname} with no message at ${Lib.vtos(self.s.origin)}\n")
        game.freeEntity(self)
        return
    }
    self.use = targetHelpUse
}

private val targetHelpUse = registerUse("Use_Target_Help") { self, _, _, game ->
    if (self.hasSpawnFlag(HELP1))
        game.game.helpmessage1 = self.message
    else
        game.game.helpmessage2 = self.message

    game.game.helpchanged++
}

/**
 * QUAKED target_lightramp (0 .5 .8) (-8 -8 -8) (8 8 8)
 * TOGGLE
 *
 * Gradually changes intensity (aka style) of a targeted light.
 * speed - How many seconds the ramping will take
 * "message" two letter string: (starting lightlevel, ending lightlevel)
 */
private const val LIGHTRAMP_TOGGLE = 1
fun targetLightramp(self: SubgameEntity, game: GameExportsImpl) {
    if (game.skipForDeathmatch(self)) return

    // expect 2 letters between 'a' and 'z'
    if (self.message?.length != 2
        || self.message[0] !in 'a'..'z'
        || self.message[1] !in 'a'..'z'
        || self.message[0] == self.message[1]
    ) {
        game.gameImports.dprintf("target_lightramp has bad ramp (${self.message}) at ${Lib.vtos(self.s.origin)}\n")
        game.freeEntity(self)
        return
    }

    if (self.target == null) {
        game.gameImports.dprintf("${self.classname} with no target at ${Lib.vtos(self.s.origin)}\n")
        game.freeEntity(self)
        return
    }
    self.svflags = self.svflags or Defines.SVF_NOCLIENT
    self.use = targetLightrampUse
    self.think.action = targetLightrampThink
    // think.nextTime is set when used

    self.addComponent(LightRamp(
        self.message[0].code - 'a'.code,
        self.message[1].code - 'a'.code,
        self.speed,
        game.level.time + self.speed,
        self.hasSpawnFlag(LIGHTRAMP_TOGGLE)
    ))

}

private val targetLightrampThink = registerThink("target_lightramp_think") { self, game ->
    val lightRamp: LightRamp = self.getComponent() ?: return@registerThink false
    if (lightRamp.targetTime < game.level.time) {
        val newValue = lightRamp.update(Defines.FRAMETIME)
        // fixme: don't call if not changed
        game.gameImports.configstring(Defines.CS_LIGHTS + lightRamp.targetLightStyle, ('a'.code + newValue).toChar().toString())
        self.think.nextTime = game.level.time + Defines.FRAMETIME
    } else {
        if (lightRamp.toggleable) {
            lightRamp.toggle(game.level.time)
        }
    }
    true
}

private val targetLightrampUse = registerUse("target_lightramp_use") { self, _, _, game ->
    val lightRamp: LightRamp = self.getComponent() ?: return@registerUse
    // find a target by name
    if (lightRamp.targetLightStyle == -1) {
        // check all the targets
        var es: EdictIterator? = null
        while (true) {
            es = GameBase.G_Find(es, GameBase.findByTargetName, self.target, game)
            if (es == null)
                break
            val entity = es.o
            if ("light" == entity.classname) {
                lightRamp.targetLightStyle = entity.style
            } else {
                game.gameImports.dprintf(self.classname + " at "+ Lib.vtos(self.s.origin))
                game.gameImports.dprintf(("target " + self.target + " ("+ entity.classname + " at " + Lib.vtos(entity.s.origin)+ ") is not a light\n"))
            }
        }
        // couldn't find a target
        if (lightRamp.targetLightStyle == -1) {
            game.gameImports.dprintf((self.classname + " target " + self.target + " not found at " + Lib.vtos(self.s.origin) + "\n"))
            game.freeEntity(self)
            return@registerUse
        }
    }
    // start the ramping
    targetLightrampThink.think(self, game)

}

/**
 * QUAKED target_character (0 0 1) ?
 * Used with target_string [jake2.game.TargetEntitiesKt.targetString] - must be on same "team"
 *
 * "count" is position in the string (starts at 1)
 */
fun targetCharacter(self: SubgameEntity, game: GameExportsImpl) {
    self.movetype = GameDefines.MOVETYPE_PUSH
    game.gameImports.setmodel(self, self.model)
    self.solid = Defines.SOLID_BSP
    self.s.frame = 12
    game.gameImports.linkentity(self)
}

/*
 * QUAKED target_string (0 0 1) (-8 -8 -8) (8 8 8)
 */
fun targetString(self: SubgameEntity, game: GameExportsImpl) {
    if (self.message == null)
        self.message = ""
    self.use = targetStringUse
}

private val targetStringUse = registerUse("target_string_use") { self, _, _, _ ->
    val length = self.message.length
    var e = self.teammaster
    while (e != null) {
        if (e.count == 0) {
            e = e.teamchain
            continue
        }
        val index = e.count - 1
        if (index >= length) {
            e.s.frame = 12
            e = e.teamchain
            continue
        }
        e.s.frame = when (val c = self.message[index]) {
            in '0'..'9' -> c.code - '0'.code
            '-' -> 10
            ':' -> 11
            else -> 12
        }
        e = e.teamchain
    }

}

/**
 * QUAKED target_changelevel (1 0 0) (-8 -8 -8) (8 8 8)
 *
 * Changes level to "map" when fired
 */
fun targetChangelevel(self: SubgameEntity, game: GameExportsImpl) {
    if (self.map == null) {
        game.gameImports.dprintf("target_changelevel with no map at " + Lib.vtos(self.s.origin) + "\n")
        game.freeEntity(self)
        return
    }

    // ugly hack because *SOMEBODY* screwed up their map
    if(self.map == "fact3" && game.level.mapname == "fact1")
        self.map = "fact3\$secret1"

    self.use = targetChangelevelUse
    self.svflags = Defines.SVF_NOCLIENT
}

private val targetChangelevelUse = registerUse("use_target_changelevel") { self, other, activator, game ->
    if (game.level.intermissiontime != 0f)
        return@registerUse  // already activated


    if (game.gameCvars.deathmatch.value == 0f && game.gameCvars.coop.value == 0f) {
        if (game.g_edicts[1].health <= 0)
            return@registerUse
    }

    // if noexit, do a ton of damage to other
    if (game.gameCvars.deathmatch.value != 0f
        && game.gameCvars.dmflags.value.toInt() and Defines.DF_ALLOW_EXIT == 0 && other !== game.g_edicts[0] /* world */) {
        GameCombat.T_Damage(
            other, self, self, Globals.vec3_origin,
            other!!.s.origin, Globals.vec3_origin,
            10 * other.max_health, 1000, 0, GameDefines.MOD_EXIT, game
        )
        return@registerUse
    }

    // if multiplayer, let everyone know who hit the exit
    if (game.gameCvars.deathmatch.value != 0f) {
        if (activator != null) {
            val activatorClient = activator.client
            if (activatorClient != null)
                game.gameImports.bprintf(Defines.PRINT_HIGH, "${activatorClient.pers.netname} exited the level.\n")
        }
    }

    // if going to a new unit, clear cross triggers
    if (self.map.indexOf('*') > -1)
        game.game.serverflags = game.game.serverflags and Defines.SFL_CROSS_TRIGGER_MASK.inv()

    PlayerHud.BeginIntermission(self, game)
}

/**
 * QUAKED target_crosslevel_trigger (.5 .5 .5) (-8 -8 -8) (8 8 8) trigger1
 * trigger2 trigger3 trigger4 trigger5 trigger6 trigger7 trigger8
 *
 * Set's it's spawnflag to the serverflags register.
 *
 * Once this trigger is touched/used, any trigger_crosslevel_target with the same
 * trigger number is automatically used when a level is started within the same unit.
 * It is OK to check multiple triggers.
 * Message, delay, target, and killtarget also work.
 */
fun targetCrosslevelTrigger(self: SubgameEntity, game: GameExportsImpl) {
    self.svflags = Defines.SVF_NOCLIENT
    self.use = targetCrosslevelTriggerUse
}

private val targetCrosslevelTriggerUse = registerUse("trigger_crosslevel_trigger_use") { self, _, _, game ->
    game.game.serverflags = game.game.serverflags or self.spawnflags
    game.freeEntity(self)
}

/**
 * QUAKED target_crosslevel_target (.5 .5 .5) (-8 -8 -8) (8 8 8) trigger1
 * trigger2 trigger3 trigger4 trigger5 trigger6 trigger7 trigger8
 *
 * Triggered by a trigger_crosslevel elsewhere within a unit.
 * If multiple triggers are checked, all must be true.
 * Delay, target and killtarget also work.
 *
 * "delay" delay before using targets if the trigger has been activated
 * (default 1)
 */
fun targetCrosslevelTarget(self: SubgameEntity, game: GameExportsImpl) {
    if (self.delay == 0f)
        self.delay = 1f
    self.svflags = Defines.SVF_NOCLIENT
    self.think.action = targetCrosslevelTargetThink
    self.think.nextTime = game.level.time + self.delay
}

private val targetCrosslevelTargetThink = registerThink("target_crosslevel_target_think") { self, game ->
    if (self.spawnflags == Defines.SFL_CROSS_TRIGGER_MASK and game.game.serverflags and self.spawnflags) {
        GameUtil.G_UseTargets(self, self, game)
        game.freeEntity(self)
    }
    true
}

/**
 * QUAKED target_laser (0 .5 .8) (-8 -8 -8) (8 8 8)
 * START_ON
 * RED
 * GREEN
 * BLUE
 * YELLOW
 * ORANGE
 * FAT
 * When triggered, fires a laser.
 * You can either set a target or a direction.
 */
private const val START_ON = 1
private const val RED = 2
private const val GREEN = 4
private const val BLUE = 8
private const val YELLOW = 16
private const val ORANGE = 32
private const val FAT = 64

// fixme: find a better name
private const val LASER_SPARK_FLAG = Int.MIN_VALUE // highest bit
fun targetLaser(self: SubgameEntity, game: GameExportsImpl) {
    // let everything else get spawned before we start firing
    self.think.action = laserInit
    self.think.nextTime = game.level.time + 1
}

private val laserInit = registerThink("target_laser_start") { self, game ->
    self.movetype = GameDefines.MOVETYPE_NONE
    self.solid = Defines.SOLID_NOT
    self.s.renderfx = self.s.renderfx or (Defines.RF_BEAM or Defines.RF_TRANSLUCENT)
    self.s.modelindex = 1 // must be non-zero

    // set the beam diameter
    if (self.hasSpawnFlag(FAT))
        self.s.frame = 16
    else
        self.s.frame = 4

    // set the color
    if (self.hasSpawnFlag(RED))
        self.s.skinnum = -0xd0d0f10
    else if (self.hasSpawnFlag(GREEN))
        self.s.skinnum = -0x2f2e2d2d
    else if (self.hasSpawnFlag(BLUE))
        self.s.skinnum = -0xc0c0e0f
    else if (self.hasSpawnFlag(YELLOW))
        self.s.skinnum = -0x23222121
    else if (self.hasSpawnFlag(ORANGE))
        self.s.skinnum = -0x1f1e1d1d

    // target or direction
    if (self.enemy == null) {
        if (self.target != null) {
            val target = GameBase.G_Find(null, GameBase.findByTargetName, self.target, game)
            if (target != null) {
                self.enemy = target.o
            } else {
                game.gameImports.dprintf("${self.classname} at ${Lib.vtos(self.s.origin)}: ${self.target} is a bad target\n")
                // fixme: free such entity?
            }
        } else {
            GameBase.G_SetMovedir(self.s.angles, self.movedir)
        }
    }

    self.use = laserUse
    self.think.action = laserThink


    if (self.dmg == 0)
        self.dmg = 1

    Math3D.VectorSet(self.mins, -8f, -8f, -8f)
    Math3D.VectorSet(self.maxs, 8f, 8f, 8f)
    game.gameImports.linkentity(self)

    if (self.hasSpawnFlag(START_ON))
        laserOn(self, game)
    else
        laserOff(self)

    true
}

private val laserUse = registerUse("target_laser_use") { self, other, activator, game ->
    self.activator = activator
    if (self.hasSpawnFlag(START_ON))
        laserOff(self)
    else
        laserOn(self, game)
}

fun laserOn(self: SubgameEntity, game: GameExportsImpl) {
    if (self.activator == null)
        self.activator = self
    self.setSpawnFlag(START_ON)
    self.setSpawnFlag(LASER_SPARK_FLAG)
    self.svflags = self.svflags and Defines.SVF_NOCLIENT.inv()
    laserThink.think(self, game)
}

fun laserOff(self: SubgameEntity) {
    self.unsetSpawnFlag(START_ON)
    self.svflags = self.svflags or Defines.SVF_NOCLIENT
    self.think.nextTime = 0f
}

/**
 * Fires a raycast check until hit an entity that is immune to damage
 */
private val laserThink = registerThink("target_laser_think") { self, game ->
    val sparksCount = if (self.hasSpawnFlag(LASER_SPARK_FLAG)) 8 else 4

    if (self.enemy != null) {
        val lastMoveDir = floatArrayOf(0f, 0f, 0f)
        Math3D.VectorCopy(self.movedir, lastMoveDir)
        val point = floatArrayOf(0f, 0f, 0f)
        Math3D.VectorMA(self.enemy.absmin, 0.5f, self.enemy.size, point)
        Math3D.VectorSubtract(point, self.s.origin, self.movedir)
        Math3D.VectorNormalize(self.movedir)
        if (!Math3D.VectorEquals(self.movedir, lastMoveDir)) {
            self.setSpawnFlag(LASER_SPARK_FLAG)
        }
    }

    var ignore = self
    val start = floatArrayOf(0f, 0f, 0f)
    Math3D.VectorCopy(self.s.origin, start)
    val end = floatArrayOf(0f, 0f, 0f)
    Math3D.VectorMA(start, 2048f, self.movedir, end)
    var tr: trace_t
    while (true) {
        tr = game.gameImports.trace(
            start,
            null,
            null,
            end,
            ignore,
            Defines.CONTENTS_SOLID or Defines.CONTENTS_MONSTER or Defines.CONTENTS_DEADMONSTER
        )
        val target = tr.ent as SubgameEntity

        // hurt it if we can
        if (target.takedamage != 0 && 0 == target.flags and GameDefines.FL_IMMUNE_LASER)
            GameCombat.T_Damage(
            target, self, self.activator,
            self.movedir, tr.endpos, Globals.vec3_origin,
            self.dmg, 1, DamageFlags.DAMAGE_ENERGY,
            GameDefines.MOD_TARGET_LASER, game
        )

        // if we hit something that's not a monster or player or is
        // immune to lasers, we're done
        if (target.svflags and Defines.SVF_MONSTER == 0 && target.client == null) {
            if (self.hasSpawnFlag(LASER_SPARK_FLAG)) {
                self.unsetSpawnFlag(LASER_SPARK_FLAG)
                game.gameImports.multicastMessage(tr.endpos, SplashTEMessage(Defines.TE_LASER_SPARKS, sparksCount, tr.endpos, tr.plane.normal, self.s.skinnum), MulticastTypes.MULTICAST_PVS)
            }
            break
        }
        ignore = target
        Math3D.VectorCopy(tr.endpos, start)
    }

    Math3D.VectorCopy(tr.endpos, self.s.old_origin)

    self.think.nextTime = game.level.time + Defines.FRAMETIME

    true
}
