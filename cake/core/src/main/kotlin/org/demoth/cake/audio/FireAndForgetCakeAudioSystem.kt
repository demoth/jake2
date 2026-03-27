package org.demoth.cake.audio

import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.math.Vector3
import jake2.qcommon.Defines
import jake2.qcommon.exec.Cvar
import kotlin.math.ceil

/**
 * Transitional backend for Cake audio.
 *
 * Keeps existing centralized dispatch while modeling Quake2-style channel behavior more closely:
 * - explicit entity channels override the same `(entity, channel)`,
 * - loops and one-shots share one voice pool,
 * - saturated playback steals the shortest-lived eligible voice instead of silently dropping gameplay sounds.
 */
class FireAndForgetCakeAudioSystem(
    /**
     * Returns current client time in milliseconds. Used for delayed-start scheduling.
     */
    private val currentTimeMsProvider: () -> Int,
    /**
     * Resolves current world origin for a server entity index (`1..MAX_EDICTS`).
     */
    private val entityOriginProvider: (Int) -> Vector3? = { null },
    /**
     * Returns the local player entity index (`playernum + 1`) when known.
     */
    private val localPlayerEntityIndexProvider: () -> Int? = { null },
) : CakeAudioSystem {

    private data class PendingPlayback(
        val startTimeMs: Int,
        val request: SoundPlaybackRequest,
    )

    private sealed interface ActiveVoice {
        val sound: Sound
        val soundId: Long
        val entityIndex: Int
        val channel: Int
        var endTimeMs: Int
    }

    private data class ActivePlayback(
        override val sound: Sound,
        override val soundId: Long,
        val request: SoundPlaybackRequest,
        override val channel: Int,
        override var endTimeMs: Int,
    ) : ActiveVoice {
        override val entityIndex: Int = request.entityIndex
    }

    private data class ActiveLoopPlayback(
        override val sound: Sound,
        override val soundId: Long,
        var request: EntityLoopSoundRequest,
        override var endTimeMs: Int,
    ) : ActiveVoice {
        override val entityIndex: Int = request.entityIndex
        override val channel: Int = Defines.CHAN_AUTO
    }

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
    private val activeVoices = mutableListOf<ActiveVoice>()
    private val knownSounds = mutableSetOf<Sound>()
    private val effectsVolume = Cvar.getInstance().Get(
        "s_volume",
        "0.7",
        Defines.CVAR_ARCHIVE or Defines.CVAR_OPTIONS,
        "Effects volume"
    )
    private val maxVoices = Cvar.getInstance().Get(
        "s_maxvoices",
        "32",
        Defines.CVAR_ARCHIVE or Defines.CVAR_OPTIONS,
        "Maximum simultaneous Cake gameplay voices"
    )

    /**
     * Updates listener orientation/position and respatializes currently active channels/loops.
     */
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
        pruneExpiredVoices()
        respatializeActiveVoices()
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

    override fun syncEntityLoopingSounds(requests: Collection<EntityLoopSoundRequest>) {
        pruneExpiredVoices()
        val now = currentTimeMsProvider()
        val seenEntities = mutableSetOf<Int>()

        requests.forEach { request ->
            if (request.entityIndex <= 0) {
                return@forEach
            }

            seenEntities += request.entityIndex
            val existing = activeVoices
                .filterIsInstance<ActiveLoopPlayback>()
                .firstOrNull { it.entityIndex == request.entityIndex }

            if (existing != null && existing.sound === request.sound) {
                existing.request = request
                existing.endTimeMs = computeLoopEndTimeMs(request.sound, now)
                val spatial = calculateLoopSpatial(request)
                existing.sound.setPan(existing.soundId, spatial.pan, spatial.volume)
                return@forEach
            }

            if (existing != null) {
                stopVoice(existing)
            }

            startLoop(request, now)?.let { started ->
                activeVoices += started
            }
        }

        val iterator = activeVoices.iterator()
        while (iterator.hasNext()) {
            val voice = iterator.next()
            if (voice is ActiveLoopPlayback && voice.entityIndex !in seenEntities) {
                stopVoice(voice, iterator)
            }
        }
    }

    override fun endFrame() {
        flushDueSounds()
        pruneExpiredVoices()
    }

    override fun stopAll() {
        pending.clear()
        knownSounds.forEach { sound -> sound.stop() }
        knownSounds.clear()
        activeVoices.clear()
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
        pruneExpiredVoices()
        val origin = resolveOrigin(request)
        val spatial = calculateSpatial(request, origin)
        if (spatial.volume <= 0f) {
            return
        }

        val now = currentTimeMsProvider()
        val channel = normalizedChannel(request.channel)
        val replaced = if (channel != Defines.CHAN_AUTO) {
            activeVoices.firstOrNull { it !is ActiveLoopPlayback && it.entityIndex == request.entityIndex && it.channel == channel }
        } else {
            null
        }

        if (replaced != null) {
            stopVoice(replaced)
        } else if (activeVoices.size >= configuredMaxVoices()) {
            val candidate = pickReplacementCandidate(request.entityIndex, now) ?: return
            stopVoice(candidate)
        }

        val voice = startOneShot(request, channel, spatial, now)
        if (voice != null) {
            activeVoices += voice
            return
        }

        val fallback = pickReplacementCandidate(request.entityIndex, now) ?: return
        if (fallback !== replaced) {
            stopVoice(fallback)
        }
        startOneShot(request, channel, spatial, now)?.let { activeVoices += it }
    }

    private fun startOneShot(
        request: SoundPlaybackRequest,
        channel: Int,
        spatial: SpatialParams,
        now: Int,
    ): ActivePlayback? {
        val soundId = request.sound.play(spatial.volume, 1f, spatial.pan)
        if (soundId < 0L) {
            return null
        }
        knownSounds += request.sound
        return ActivePlayback(
            sound = request.sound,
            soundId = soundId,
            request = request,
            channel = channel,
            endTimeMs = computeOneShotEndTimeMs(request.sound, now)
        )
    }

    private fun startLoop(request: EntityLoopSoundRequest, now: Int): ActiveLoopPlayback? {
        val spatial = calculateLoopSpatial(request)
        if (spatial.volume <= 0f) {
            return null
        }

        // Keep already-running loops stable under saturation. One-shots are still allowed
        // to steal loop voices, but loop sync itself should not churn ambience every frame.
        if (activeVoices.size >= configuredMaxVoices()) {
            return null
        }

        val soundId = request.sound.loop(spatial.volume, 1f, spatial.pan)
        if (soundId < 0L) {
            return null
        }

        knownSounds += request.sound
        return ActiveLoopPlayback(
            sound = request.sound,
            soundId = soundId,
            request = request,
            endTimeMs = computeLoopEndTimeMs(request.sound, now)
        )
    }

    private fun respatializeActiveVoices() {
        activeVoices.forEach { voice ->
            when (voice) {
                is ActivePlayback -> {
                    val origin = resolveOrigin(voice.request)
                    val spatial = calculateSpatial(voice.request, origin)
                    voice.sound.setPan(voice.soundId, spatial.pan, spatial.volume)
                }

                is ActiveLoopPlayback -> {
                    val spatial = calculateLoopSpatial(voice.request)
                    voice.sound.setPan(voice.soundId, spatial.pan, spatial.volume)
                }
            }
        }
    }

    private fun pruneExpiredVoices() {
        val now = currentTimeMsProvider()
        val iterator = activeVoices.iterator()
        while (iterator.hasNext()) {
            val voice = iterator.next()
            if (voice !is ActiveLoopPlayback && voice.endTimeMs <= now) {
                iterator.remove()
            }
        }
    }

    private fun stopVoice(voice: ActiveVoice, iterator: MutableIterator<ActiveVoice>? = null) {
        voice.sound.stop(voice.soundId)
        if (iterator != null) {
            iterator.remove()
        } else {
            activeVoices.remove(voice)
        }
    }

    private fun pickReplacementCandidate(newEntityIndex: Int, now: Int): ActiveVoice? {
        var best: ActiveVoice? = null
        var shortestRemainingLife = Int.MAX_VALUE

        activeVoices.forEach { candidate ->
            if (isProtectedLocalPlayerVoice(candidate, newEntityIndex)) {
                return@forEach
            }
            val remainingLife = candidate.endTimeMs - now
            if (best == null || remainingLife < shortestRemainingLife) {
                best = candidate
                shortestRemainingLife = remainingLife
            }
        }

        return best
    }

    private fun isProtectedLocalPlayerVoice(candidate: ActiveVoice, newEntityIndex: Int): Boolean {
        val localPlayerEntityIndex = localPlayerEntityIndexProvider() ?: return false
        return candidate.entityIndex == localPlayerEntityIndex && newEntityIndex != localPlayerEntityIndex
    }

    private fun resolveOrigin(request: SoundPlaybackRequest): Vector3? {
        request.origin?.let { return it }
        if (request.entityIndex > 0) {
            return entityOriginProvider(request.entityIndex)
        }
        return null
    }

    private fun calculateSpatial(request: SoundPlaybackRequest, origin: Vector3?): SpatialParams {
        val baseVolume = (request.baseVolume * effectsVolume.value).coerceIn(0f, 1f)
        if (baseVolume <= 0f) {
            return SpatialParams(volume = 0f, pan = 0f)
        }
        if (isNonSpatial(request.attenuation, request.entityIndex)) {
            return SpatialParams(volume = baseVolume, pan = 0f)
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

    private fun calculateLoopSpatial(request: EntityLoopSoundRequest): SpatialParams {
        val baseVolume = (request.baseVolume * effectsVolume.value).coerceIn(0f, 1f)
        if (baseVolume <= 0f) {
            return SpatialParams(volume = 0f, pan = 0f)
        }
        if (isNonSpatial(request.attenuation, request.entityIndex)) {
            return SpatialParams(volume = baseVolume, pan = 0f)
        }
        val origin = entityOriginProvider(request.entityIndex)
            ?: return SpatialParams(volume = baseVolume, pan = 0f)

        val spatialScale = SpatialSoundAttenuation.calculate(origin, listenerPosition, request.attenuation)
        val volume = (baseVolume * spatialScale).coerceIn(0f, 1f)
        val pan = calculatePan(origin)
        return SpatialParams(volume = volume, pan = pan)
    }

    private fun isNonSpatial(attenuation: Float, entityIndex: Int): Boolean {
        if (attenuation <= 0f) {
            return true
        }
        val localPlayerEntityIndex = localPlayerEntityIndexProvider() ?: return false
        return entityIndex > 0 && entityIndex == localPlayerEntityIndex
    }

    private fun calculatePan(origin: Vector3): Float {
        tempDirection.set(origin).sub(listenerPosition)
        if (tempDirection.isZero) {
            return 0f
        }
        tempDirection.nor()
        return listenerRight.dot(tempDirection).coerceIn(-1f, 1f)
    }

    private fun computeOneShotEndTimeMs(sound: Sound, now: Int): Int {
        return now + (SoundDurationRegistry.durationMs(sound) ?: UNKNOWN_DURATION_MS)
    }

    private fun computeLoopEndTimeMs(sound: Sound, now: Int): Int {
        return now + (SoundDurationRegistry.durationMs(sound) ?: UNKNOWN_DURATION_MS)
    }

    private fun configuredMaxVoices(): Int {
        return maxVoices.value.toInt().coerceIn(1, 128)
    }

    private fun normalizedChannel(channel: Int): Int {
        return channel and 7
    }

    private fun SoundPlaybackRequest.snapshotForQueue(): SoundPlaybackRequest {
        val originSnapshot = this.origin?.let(::Vector3)
        return this.copy(origin = originSnapshot)
    }

    private companion object {
        const val UNKNOWN_DURATION_MS = 1000
    }
}
