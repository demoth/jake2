package org.demoth.cake.audio

import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.math.Vector3
import jake2.qcommon.Defines
import kotlin.math.ceil

/**
 * Transitional backend for Cake audio.
 *
 * Keeps existing fire-and-forget behavior while introducing:
 * - centralized sound dispatch,
 * - optional delayed start (`timeOffsetSeconds`),
 * - basic channel override keys (`entityIndex`, `channel`).
 */
class FireAndForgetCakeAudioSystem(
    private val currentTimeMsProvider: () -> Int,
    private val entityOriginProvider: (Int) -> Vector3? = { null },
) : CakeAudioSystem {

    private data class PendingPlayback(
        val startTimeMs: Int,
        val request: SoundPlaybackRequest,
    )

    private data class ActivePlayback(
        val sound: Sound,
        val soundId: Long,
        val request: SoundPlaybackRequest,
    )

    private data class SpatialParams(
        val volume: Float,
        val pan: Float,
    )

    private val listenerPosition = Vector3()
    private val listenerForward = Vector3(0f, 1f, 0f)
    private val listenerUp = Vector3(0f, 0f, 1f)
    private val listenerRight = Vector3(1f, 0f, 0f)
    private val tempDirection = Vector3()
    private val pending = mutableListOf<PendingPlayback>()
    private val keyedChannels = mutableMapOf<AudioChannelKey, ActivePlayback>()
    private val knownSounds = mutableSetOf<Sound>()

    override fun beginFrame(listener: ListenerState) {
        listenerPosition.set(listener.position)
        listenerForward.set(listener.forward)
        listenerUp.set(listener.up)
        listenerRight.set(listener.forward).crs(listener.up)
        if (listenerRight.isZero) {
            listenerRight.set(1f, 0f, 0f)
        } else {
            listenerRight.nor()
        }
        flushDueSounds()
        respatializeActiveChannels()
    }

    override fun play(request: SoundPlaybackRequest) {
        if (request.timeOffsetSeconds > 0f) {
            val now = currentTimeMsProvider()
            val delayMs = ceil(request.timeOffsetSeconds * 1000f).toInt().coerceAtLeast(0)
            pending += PendingPlayback(now + delayMs, request.snapshotForQueue())
            return
        }
        playNow(request)
    }

    override fun endFrame() {
        flushDueSounds()
    }

    override fun stopAll() {
        pending.clear()
        knownSounds.forEach { sound ->
            sound.stop()
        }
        knownSounds.clear()
        keyedChannels.clear()
    }

    override fun dispose() {
        stopAll()
    }

    private fun flushDueSounds() {
        if (pending.isEmpty()) {
            return
        }
        val now = currentTimeMsProvider()
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            val pendingPlayback = iterator.next()
            if (pendingPlayback.startTimeMs <= now) {
                playNow(pendingPlayback.request)
                iterator.remove()
            }
        }
    }

    private fun playNow(request: SoundPlaybackRequest) {
        val origin = resolveOrigin(request)
        val spatial = calculateSpatial(request, origin)
        if (spatial.volume <= 0f) {
            return
        }
        val key = channelKeyFor(request)
        if (key != null) {
            keyedChannels.remove(key)?.let { existing ->
                existing.sound.stop(existing.soundId)
            }
        }

        val soundId = request.sound.play(spatial.volume, 1f, spatial.pan)
        if (soundId < 0L) {
            return
        }
        knownSounds += request.sound
        if (key != null) {
            keyedChannels[key] = ActivePlayback(
                sound = request.sound,
                soundId = soundId,
                request = request,
            )
        }
    }

    private fun respatializeActiveChannels() {
        keyedChannels.values.forEach { playback ->
            val origin = resolveOrigin(playback.request)
            val spatial = calculateSpatial(playback.request, origin)
            playback.sound.setPan(playback.soundId, spatial.pan, spatial.volume)
        }
    }

    private fun resolveOrigin(request: SoundPlaybackRequest): Vector3? {
        request.origin?.let { return it }
        if (request.entityIndex > 0) {
            return entityOriginProvider(request.entityIndex)
        }
        return null
    }

    private fun calculateSpatial(request: SoundPlaybackRequest, origin: Vector3?): SpatialParams {
        val baseVolume = request.baseVolume.coerceIn(0f, 1f)
        if (baseVolume <= 0f) {
            return SpatialParams(volume = 0f, pan = 0f)
        }
        if (origin == null) {
            return SpatialParams(volume = baseVolume, pan = 0f)
        }
        val spatialScale = if (request.attenuation > 0f) {
            SpatialSoundAttenuation.calculate(origin, listenerPosition, request.attenuation)
        } else {
            1f
        }
        val volume = (baseVolume * spatialScale).coerceIn(0f, 1f)
        val pan = calculatePan(origin)
        return SpatialParams(volume = volume, pan = pan)
    }

    private fun calculatePan(origin: Vector3): Float {
        tempDirection.set(origin).sub(listenerPosition)
        if (tempDirection.isZero) {
            return 0f
        }
        tempDirection.nor()
        return listenerRight.dot(tempDirection).coerceIn(-1f, 1f)
    }

    private fun channelKeyFor(request: SoundPlaybackRequest): AudioChannelKey? {
        if (request.entityIndex <= 0) {
            return null
        }
        val channel = request.channel and 7
        if (channel == Defines.CHAN_AUTO) {
            return null
        }
        return AudioChannelKey(request.entityIndex, channel)
    }

    private fun SoundPlaybackRequest.snapshotForQueue(): SoundPlaybackRequest {
        val originSnapshot = this.origin?.let(::Vector3)
        return this.copy(origin = originSnapshot)
    }
}
