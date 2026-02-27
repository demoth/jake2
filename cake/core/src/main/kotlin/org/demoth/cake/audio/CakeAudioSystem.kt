package org.demoth.cake.audio

import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import jake2.qcommon.Defines

data class ListenerState(
    val position: Vector3,
    val forward: Vector3,
    val up: Vector3,
)

data class AudioChannelKey(
    val entityIndex: Int,
    val channel: Int,
)

/**
 * Unified playback request used by Cake sound call sites.
 *
 * Channel and time-offset fields are included early so packet semantics can be implemented
 * incrementally without changing all callers again.
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

interface CakeAudioSystem : Disposable {
    fun beginFrame(listener: ListenerState)
    fun play(request: SoundPlaybackRequest)
    fun endFrame()
    fun stopAll()
}
