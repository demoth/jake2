package org.demoth.cake.audio

import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import jake2.qcommon.Defines

/**
 * Listener transform snapshot for the current render frame.
 *
 * Vectors are expected to represent the game camera orientation (right-handed basis derived from
 * `forward x up`), but they do not need to be immutable; the backend copies values on `beginFrame`.
 */
data class ListenerState(
    val position: Vector3,
    val forward: Vector3,
    val up: Vector3,
)

/**
 * Override key for entity-scoped channels (`CHAN_WEAPON`, `CHAN_VOICE`, etc.).
 *
 * Channel `0` (`CHAN_AUTO`) is intentionally excluded from keyed override behavior.
 */
data class AudioChannelKey(
    val entityIndex: Int,
    val channel: Int,
)

/**
 * Unified playback request used by Cake sound call sites.
 *
 * Channel and time-offset fields are included early so packet semantics can be implemented
 * incrementally without changing all callers again.
 *
 * `entityIndex` follows server entity numbering (`1..MAX_EDICTS`); `0` means unbound.
 * If `origin == null` and `entityIndex > 0`, backend may resolve origin dynamically each frame.
 */
data class SoundPlaybackRequest(
    val sound: Sound,
    val baseVolume: Float = 1f,
    val attenuation: Float = Defines.ATTN_NONE.toFloat(),
    val origin: Vector3? = null,
    val entityIndex: Int = 0,
    val channel: Int = Defines.CHAN_AUTO,
    val timeOffsetSeconds: Float = 0f,
)

/**
 * Desired per-frame loop state for one replicated entity (`entity_state.sound`).
 *
 * This is a declarative input: callers provide the full active set each frame; omitted entities
 * are treated as loop-stop requests by the backend.
 */
data class EntityLoopSoundRequest(
    val entityIndex: Int,
    val sound: Sound,
    val baseVolume: Float = 1f,
    val attenuation: Float = Defines.ATTN_STATIC.toFloat(),
)

/**
 * Centralized gameplay audio facade used by Cake runtime systems.
 *
 * Call sequence per render frame:
 * `beginFrame(listener)` -> zero or more `play(...)` / one `syncEntityLoopingSounds(...)` -> `endFrame()`.
 */
interface CakeAudioSystem : Disposable {
    /**
     * Starts audio-frame processing and updates listener state used for spatialization.
     */
    fun beginFrame(listener: ListenerState)

    /**
     * Plays a one-shot (or channel-bound) sound request.
     */
    fun play(request: SoundPlaybackRequest)

    /**
     * Synchronizes looped entity sounds for the current frame.
     *
     * Implementations treat [requests] as authoritative for this frame.
     */
    fun syncEntityLoopingSounds(requests: Collection<EntityLoopSoundRequest>)

    /**
     * Ends audio-frame processing and flushes deferred playback work.
     */
    fun endFrame()

    /**
     * Stops all active/pending sounds and clears runtime tracking state.
     */
    fun stopAll()
}
