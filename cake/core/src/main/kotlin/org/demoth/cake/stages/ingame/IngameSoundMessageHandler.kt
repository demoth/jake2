package org.demoth.cake.stages.ingame

import com.badlogic.gdx.math.Vector3
import jake2.qcommon.Com
import jake2.qcommon.Defines
import jake2.qcommon.Globals
import jake2.qcommon.network.messages.server.SoundMessage
import jake2.qcommon.network.messages.server.WeaponSoundMessage
import jake2.qcommon.util.Lib
import jake2.qcommon.util.Math3D
import org.demoth.cake.GameConfiguration
import org.demoth.cake.audio.CakeAudioSystem
import org.demoth.cake.audio.EntityLoopSoundRequest
import org.demoth.cake.audio.SoundPlaybackRequest
import org.demoth.cake.stages.ingame.effects.ClientEffectsSystem

/**
 * Owns ingame sound-message dispatch and entity-event sound side effects.
 *
 * Legacy references:
 * - `CL_parse.ParseStartSoundPacket` (`SoundMessage`)
 * - `CL_fx.ParseMuzzleFlash` (`WeaponSoundMessage`, MZ_* sounds + dlights)
 * - `CL_fx.EntityEvent` (packet-entity event sounds)
 */
internal class IngameSoundMessageHandler(
    private val gameConfig: GameConfiguration,
    private val entityManager: ClientEntityManager,
    private val effectsSystem: ClientEffectsSystem,
    private val audioSystem: CakeAudioSystem,
    private val dynamicLightSystem: DynamicLightSystem,
) {
    private val loopSoundRequests = mutableListOf<EntityLoopSoundRequest>()

    fun processSoundMessage(msg: SoundMessage) {
        val sound = gameConfig.getSound(msg.soundIndex, msg.entityIndex)
        if (sound != null) {
            val origin = resolveExplicitSoundOrigin(msg)
            audioSystem.play(
                SoundPlaybackRequest(
                    sound = sound,
                    baseVolume = msg.volume,
                    attenuation = msg.attenuation,
                    origin = origin,
                    entityIndex = msg.entityIndex,
                    channel = msg.sendchan,
                    timeOffsetSeconds = msg.timeOffset,
                )
            )
        } else {
            Com.Warn("sound ${msg.soundIndex} (${gameConfig.getSoundPath(msg.soundIndex)}) not found")
        }
    }

    /**
     * Play client-side entity event sounds derived from reconstructed packet entities.
     *
     * Supported events currently mirror the subset implemented in this module:
     * - `EV_ITEM_RESPAWN` -> `items/respawn1.wav` + item respawn particles
     * - `EV_FOOTSTEP` -> random `player/step1..4.wav`
     * - `EV_FALLSHORT` -> `player/land1.wav`
     * - `EV_FALL` -> variation-specific `fall2.wav`
     * - `EV_FALLFAR` -> variation-specific `fall1.wav`
     * - `EV_PLAYER_TELEPORT` -> `misc/tele1.wav` + teleport particles
     */
    fun playEntityEventSounds() {
        entityManager.forEachCurrentEntityState { state ->
            when (state.event) {
                Defines.EV_ITEM_RESPAWN -> {
                    val sound = gameConfig.getNamedSound("items/respawn1.wav") ?: return@forEachCurrentEntityState
                    playEntityEventSound(
                        sound = sound,
                        entityIndex = state.index,
                        attenuation = Defines.ATTN_IDLE.toFloat(),
                        channel = Defines.CHAN_WEAPON,
                    )
                    effectsSystem.emitItemRespawnEvent(state.index)
                }
                Defines.EV_FOOTSTEP -> {
                    val stepIndex = (Lib.rand().toInt() and 3) + 1
                    val sound = gameConfig.getNamedSound("player/step$stepIndex.wav") ?: return@forEachCurrentEntityState
                    playEntityEventSound(sound, state.index)
                }
                Defines.EV_FALLSHORT -> {
                    val sound = gameConfig.getNamedSound("player/land1.wav") ?: return@forEachCurrentEntityState
                    playEntityEventSound(sound, state.index)
                }
                Defines.EV_FALL -> {
                    val sound = gameConfig.playerConfiguration.getPlayerSound(state.index, "fall2.wav") ?: return@forEachCurrentEntityState
                    playEntityEventSound(sound, state.index)
                }
                Defines.EV_FALLFAR -> {
                    val sound = gameConfig.playerConfiguration.getPlayerSound(state.index, "fall1.wav") ?: return@forEachCurrentEntityState
                    playEntityEventSound(sound, state.index)
                }
                Defines.EV_PLAYER_TELEPORT -> {
                    val sound = gameConfig.getNamedSound("misc/tele1.wav") ?: return@forEachCurrentEntityState
                    playEntityEventSound(
                        sound = sound,
                        entityIndex = state.index,
                        attenuation = Defines.ATTN_IDLE.toFloat(),
                        channel = Defines.CHAN_WEAPON,
                    )
                    effectsSystem.emitPlayerTeleportEvent(state.index)
                }
            }
        }
    }

    /**
     * Handles `MZ_*` weapon events (sound + one-shot muzzle dynamic light + special login/logout burst).
     *
     * Legacy counterpart: `client/CL_fx.ParseMuzzleFlash`.
     */
    fun processWeaponSoundMessage(msg: WeaponSoundMessage) {
        if (msg.entityIndex !in 1 until Defines.MAX_EDICTS) {
            Com.Warn("Ignoring WeaponSoundMessage with invalid entity index ${msg.entityIndex}")
            return
        }
        // weapon type is stored in last 7 bits of the msg.type
        val weaponType = msg.type and 0x7F
        // the silenced flag is stored in the first bit
        val silenced = (msg.type and Defines.MZ_SILENCED) != 0
        val volume = if (silenced) 0.2f else 1f

        spawnWeaponMuzzleFlashLight(msg.entityIndex, weaponType, silenced)

        // login/out effects
        if (weaponType == Defines.MZ_LOGIN || weaponType == Defines.MZ_LOGOUT || weaponType == Defines.MZ_RESPAWN) {
            effectsSystem.emitLoginLogoutRespawnEvent(msg.entityIndex, weaponType)
        }

        when (weaponType) {
            Defines.MZ_BLASTER -> playWeaponSound(msg.entityIndex, "weapons/blastf1a.wav", volume)
            Defines.MZ_BLUEHYPERBLASTER, Defines.MZ_HYPERBLASTER -> {
                playWeaponSound(msg.entityIndex, "weapons/hyprbf1a.wav", volume)
            }
            Defines.MZ_MACHINEGUN -> {
                playWeaponSound(msg.entityIndex, randomMachineGunSound(), volume)
            }
            Defines.MZ_SHOTGUN -> {
                playWeaponSound(msg.entityIndex, "weapons/shotgf1b.wav", volume)
                playWeaponSound(
                    entityIndex = msg.entityIndex,
                    soundPath = "weapons/shotgr1b.wav",
                    volume = volume,
                    channel = Defines.CHAN_AUTO,
                    timeOffsetSeconds = 0.1f,
                )
            }
            Defines.MZ_SSHOTGUN -> playWeaponSound(msg.entityIndex, "weapons/sshotf1b.wav", volume)
            Defines.MZ_CHAINGUN1 -> {
                playWeaponSound(msg.entityIndex, randomMachineGunSound(), volume)
            }
            Defines.MZ_CHAINGUN2 -> {
                playWeaponSound(msg.entityIndex, randomMachineGunSound(), volume)
                playWeaponSound(
                    entityIndex = msg.entityIndex,
                    soundPath = randomMachineGunSound(),
                    volume = volume,
                    timeOffsetSeconds = 0.05f,
                )
            }
            Defines.MZ_CHAINGUN3 -> {
                playWeaponSound(msg.entityIndex, randomMachineGunSound(), volume)
                playWeaponSound(
                    entityIndex = msg.entityIndex,
                    soundPath = randomMachineGunSound(),
                    volume = volume,
                    timeOffsetSeconds = 0.033f,
                )
                playWeaponSound(
                    entityIndex = msg.entityIndex,
                    soundPath = randomMachineGunSound(),
                    volume = volume,
                    timeOffsetSeconds = 0.066f,
                )
            }
            Defines.MZ_RAILGUN -> playWeaponSound(msg.entityIndex, "weapons/railgf1a.wav", volume)
            Defines.MZ_ROCKET -> {
                playWeaponSound(msg.entityIndex, "weapons/rocklf1a.wav", volume)
                playWeaponSound(
                    entityIndex = msg.entityIndex,
                    soundPath = "weapons/rocklr1b.wav",
                    volume = volume,
                    channel = Defines.CHAN_AUTO,
                    timeOffsetSeconds = 0.1f,
                )
            }
            Defines.MZ_GRENADE -> {
                playWeaponSound(msg.entityIndex, "weapons/grenlf1a.wav", volume)
                playWeaponSound(
                    entityIndex = msg.entityIndex,
                    soundPath = "weapons/grenlr1b.wav",
                    volume = volume,
                    channel = Defines.CHAN_AUTO,
                    timeOffsetSeconds = 0.1f,
                )
            }
            Defines.MZ_BFG -> playWeaponSound(msg.entityIndex, "weapons/bfg__f1y.wav", volume)
            Defines.MZ_LOGIN, Defines.MZ_LOGOUT, Defines.MZ_RESPAWN -> {
                playWeaponSound(msg.entityIndex, "weapons/grenlf1a.wav", 1f)
            }
            Defines.MZ_PHALANX -> playWeaponSound(msg.entityIndex, "weapons/plasshot.wav", volume)
            Defines.MZ_IONRIPPER -> playWeaponSound(msg.entityIndex, "weapons/rippfire.wav", volume)
            Defines.MZ_ETF_RIFLE -> playWeaponSound(msg.entityIndex, "weapons/nail1.wav", volume)
            Defines.MZ_SHOTGUN2 -> playWeaponSound(msg.entityIndex, "weapons/shotg2.wav", volume)
            Defines.MZ_BLASTER2 -> playWeaponSound(msg.entityIndex, "weapons/blastf1a.wav", volume)
            Defines.MZ_TRACKER -> playWeaponSound(msg.entityIndex, "weapons/disint2.wav", volume)
            Defines.MZ_HEATBEAM, Defines.MZ_NUKE1, Defines.MZ_NUKE2, Defines.MZ_NUKE4, Defines.MZ_NUKE8, Defines.MZ_UNUSED -> {
                // Legacy path has no additional one-shot sound for these muzzleflash types.
            }
            else -> {
                // Keep backward-compatible fallback for unknown mod-specific muzzleflash values.
                val fallback = gameConfig.getWeaponSound(weaponType)
                if (fallback == null) {
                    Com.Warn("weapon sound $weaponType not found")
                } else {
                    audioSystem.play(
                        SoundPlaybackRequest(
                            sound = fallback,
                            baseVolume = volume,
                            entityIndex = msg.entityIndex,
                            channel = Defines.CHAN_WEAPON,
                            attenuation = Defines.ATTN_NORM.toFloat(),
                        )
                    )
                }
            }
        }
    }

    fun syncEntityLoopSounds() {
        loopSoundRequests.clear()
        entityManager.forEachCurrentEntityState { state ->
            val soundIndex = state.sound
            if (soundIndex <= 0) {
                return@forEachCurrentEntityState
            }
            val sound = gameConfig.getSound(soundIndex, state.index) ?: return@forEachCurrentEntityState
            loopSoundRequests += EntityLoopSoundRequest(
                entityIndex = state.index,
                sound = sound,
                attenuation = Defines.ATTN_STATIC.toFloat(),
            )
        }
        audioSystem.syncEntityLoopingSounds(loopSoundRequests)
    }

    private fun resolveExplicitSoundOrigin(msg: SoundMessage): Vector3? {
        val rawOrigin = msg.origin ?: return null
        return Vector3(rawOrigin[0], rawOrigin[1], rawOrigin[2])
    }

    /**
     * Play an event sound using legacy-normal attenuation from the emitting entity origin.
     */
    private fun playEntityEventSound(
        sound: com.badlogic.gdx.audio.Sound,
        entityIndex: Int,
        attenuation: Float = Defines.ATTN_NORM.toFloat(),
        channel: Int = Defines.CHAN_AUTO,
    ) {
        audioSystem.play(
            SoundPlaybackRequest(
                sound = sound,
                attenuation = attenuation,
                origin = entityManager.getEntityOrigin(entityIndex),
                entityIndex = entityIndex,
                channel = channel,
            )
        )
    }

    /**
     * Emits one-shot weapon muzzle dynamic lights for `MZ_*` events.
     *
     * Legacy counterpart: `client/CL_fx.ParseMuzzleFlash` (`CL_AllocDlight` + weapon-specific color/radius).
     */
    private fun spawnWeaponMuzzleFlashLight(entityIndex: Int, weaponType: Int, silenced: Boolean) {
        val origin = entityManager.getEntityOrigin(entityIndex) ?: return
        val angles = entityManager.getEntityAngles(entityIndex) ?: return

        val forward = FloatArray(3)
        val right = FloatArray(3)
        Math3D.AngleVectors(angles, forward, right, null)

        // Match classic muzzle offset from entity origin.
        origin.mulAdd(Vector3(forward[0], forward[1], forward[2]), WEAPON_MUZZLE_FORWARD_OFFSET)
        origin.mulAdd(Vector3(right[0], right[1], right[2]), WEAPON_MUZZLE_RIGHT_OFFSET)

        val profile = resolveWeaponMuzzleLightProfile(weaponType) ?: return
        val radiusBase = profile.radiusBase ?: if (silenced) {
            WEAPON_MUZZLE_SILENCED_RADIUS_BASE
        } else {
            WEAPON_MUZZLE_DEFAULT_RADIUS_BASE
        }
        val radius = radiusBase + Globals.rnd.nextInt(WEAPON_MUZZLE_RADIUS_JITTER + 1)

        dynamicLightSystem.spawnTransientLight(
            key = entityIndex,
            origin = origin,
            radius = radius,
            red = profile.red,
            green = profile.green,
            blue = profile.blue,
            lifetimeMs = profile.lifetimeMs,
            currentTimeMs = Globals.curtime,
        )
    }

    private fun resolveWeaponMuzzleLightProfile(weaponType: Int): WeaponMuzzleLightProfile? {
        return when (weaponType) {
            Defines.MZ_BLASTER,
            Defines.MZ_HYPERBLASTER,
            Defines.MZ_MACHINEGUN,
            Defines.MZ_SHOTGUN,
            Defines.MZ_SSHOTGUN,
            Defines.MZ_SHOTGUN2 -> WeaponMuzzleLightProfile(red = 1f, green = 1f, blue = 0f)
            Defines.MZ_HEATBEAM -> WeaponMuzzleLightProfile(red = 1f, green = 1f, blue = 0f, lifetimeMs = 100)

            Defines.MZ_BLUEHYPERBLASTER -> WeaponMuzzleLightProfile(red = 0f, green = 0f, blue = 1f)
            Defines.MZ_CHAINGUN1 -> WeaponMuzzleLightProfile(radiusBase = 200f, red = 1f, green = 0.25f, blue = 0f)
            Defines.MZ_CHAINGUN2 -> WeaponMuzzleLightProfile(radiusBase = 225f, red = 1f, green = 0.5f, blue = 0f, lifetimeMs = 100)
            Defines.MZ_CHAINGUN3 -> WeaponMuzzleLightProfile(radiusBase = 250f, red = 1f, green = 1f, blue = 0f, lifetimeMs = 100)
            Defines.MZ_RAILGUN -> WeaponMuzzleLightProfile(red = 0.5f, green = 0.5f, blue = 1f)
            Defines.MZ_ROCKET -> WeaponMuzzleLightProfile(red = 1f, green = 0.5f, blue = 0.2f)
            Defines.MZ_GRENADE -> WeaponMuzzleLightProfile(red = 1f, green = 0.5f, blue = 0f)
            Defines.MZ_BFG -> WeaponMuzzleLightProfile(red = 0f, green = 1f, blue = 0f)
            Defines.MZ_LOGIN -> WeaponMuzzleLightProfile(red = 0f, green = 1f, blue = 0f, lifetimeMs = LOGIN_EVENT_MUZZLE_LIGHT_LIFETIME_MS)
            Defines.MZ_LOGOUT -> WeaponMuzzleLightProfile(red = 1f, green = 0f, blue = 0f, lifetimeMs = LOGIN_EVENT_MUZZLE_LIGHT_LIFETIME_MS)
            Defines.MZ_RESPAWN -> WeaponMuzzleLightProfile(red = 1f, green = 1f, blue = 0f, lifetimeMs = LOGIN_EVENT_MUZZLE_LIGHT_LIFETIME_MS)
            Defines.MZ_PHALANX,
            Defines.MZ_IONRIPPER -> WeaponMuzzleLightProfile(red = 1f, green = 0.5f, blue = 0.5f)

            Defines.MZ_ETF_RIFLE -> WeaponMuzzleLightProfile(red = 0.9f, green = 0.7f, blue = 0f)
            Defines.MZ_BLASTER2 -> WeaponMuzzleLightProfile(red = 0f, green = 1f, blue = 0f)
            Defines.MZ_TRACKER -> WeaponMuzzleLightProfile(red = -1f, green = -1f, blue = -1f)
            Defines.MZ_NUKE1 -> WeaponMuzzleLightProfile(red = 1f, green = 0f, blue = 0f, lifetimeMs = 100)
            Defines.MZ_NUKE2 -> WeaponMuzzleLightProfile(red = 1f, green = 1f, blue = 0f, lifetimeMs = 100)
            Defines.MZ_NUKE4 -> WeaponMuzzleLightProfile(red = 0f, green = 0f, blue = 1f, lifetimeMs = 100)
            Defines.MZ_NUKE8 -> WeaponMuzzleLightProfile(red = 0f, green = 1f, blue = 1f, lifetimeMs = 100)
            Defines.MZ_UNUSED -> null
            else -> null
        }
    }

    private fun playWeaponSound(
        entityIndex: Int,
        soundPath: String,
        volume: Float,
        channel: Int = Defines.CHAN_WEAPON,
        timeOffsetSeconds: Float = 0f,
    ) {
        val sound = gameConfig.getNamedSound(soundPath)
        if (sound == null) {
            Com.Warn("weapon sound path $soundPath not found")
            return
        }
        audioSystem.play(
            SoundPlaybackRequest(
                sound = sound,
                baseVolume = volume,
                entityIndex = entityIndex,
                channel = channel,
                attenuation = Defines.ATTN_NORM.toFloat(),
                timeOffsetSeconds = timeOffsetSeconds,
            )
        )
    }

    private fun randomMachineGunSound(): String {
        val soundIndex = Globals.rnd.nextInt(5) + 1
        return "weapons/machgf${soundIndex}b.wav"
    }

    private data class WeaponMuzzleLightProfile(
        val red: Float,
        val green: Float,
        val blue: Float,
        val radiusBase: Float? = null,
        val lifetimeMs: Int = 0,
    )

    private companion object {
        private const val WEAPON_MUZZLE_FORWARD_OFFSET = 18f
        private const val WEAPON_MUZZLE_RIGHT_OFFSET = 16f
        private const val WEAPON_MUZZLE_DEFAULT_RADIUS_BASE = 200f
        private const val WEAPON_MUZZLE_SILENCED_RADIUS_BASE = 100f
        private const val WEAPON_MUZZLE_RADIUS_JITTER = 31
        private const val LOGIN_EVENT_MUZZLE_LIGHT_LIFETIME_MS = 1
    }
}
