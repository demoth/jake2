package org.demoth.cake.audio

import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.math.Vector3
import jake2.qcommon.Defines
import jake2.qcommon.exec.Cvar
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FireAndForgetCakeAudioSystemTest {

    private var nowMs = 0
    private val registeredSounds = mutableListOf<FakeSound>()

    @BeforeEach
    fun setUp() {
        Cvar.getInstance().Set("s_maxvoices", "32")
        Cvar.getInstance().Set("s_volume", "1")
    }

    @AfterEach
    fun tearDown() {
        registeredSounds.forEach(SoundDurationRegistry::unregister)
        registeredSounds.clear()
    }

    @Test
    fun gameplayOneShotStealsAmbientLoopWhenPoolIsFull() {
        Cvar.getInstance().Set("s_maxvoices", "1")
        val system = createSystem()
        val ambientLoop = registerFakeSound(durationMs = 3000)
        val weaponShot = registerFakeSound(durationMs = 200)

        system.syncEntityLoopingSounds(
            listOf(EntityLoopSoundRequest(entityIndex = 2, sound = ambientLoop, attenuation = Defines.ATTN_STATIC.toFloat()))
        )

        system.play(
            SoundPlaybackRequest(
                sound = weaponShot,
                entityIndex = 3,
                channel = Defines.CHAN_WEAPON,
                attenuation = Defines.ATTN_NORM.toFloat()
            )
        )

        assertEquals(listOf(1L), ambientLoop.stoppedIds)
        assertEquals(1, weaponShot.playCalls.size)
    }

    @Test
    fun loopSyncDoesNotThrashExistingLoopsWhenRequestsExceedPool() {
        Cvar.getInstance().Set("s_maxvoices", "1")
        val system = createSystem()
        val firstLoop = registerFakeSound(durationMs = 1500)
        val secondLoop = registerFakeSound(durationMs = 1500)

        val requests = listOf(
            EntityLoopSoundRequest(entityIndex = 2, sound = firstLoop, attenuation = Defines.ATTN_STATIC.toFloat()),
            EntityLoopSoundRequest(entityIndex = 3, sound = secondLoop, attenuation = Defines.ATTN_STATIC.toFloat())
        )

        system.syncEntityLoopingSounds(requests)
        system.syncEntityLoopingSounds(requests)

        assertEquals(1, firstLoop.loopCalls.size)
        assertTrue(firstLoop.stoppedIds.isEmpty())
        assertTrue(secondLoop.loopCalls.isEmpty())
    }

    @Test
    fun replacementPrefersShortestRemainingLife() {
        Cvar.getInstance().Set("s_maxvoices", "2")
        val system = createSystem()
        val longShot = registerFakeSound(durationMs = 2000)
        val shortShot = registerFakeSound(durationMs = 300)
        val newShot = registerFakeSound(durationMs = 150)

        system.play(
            SoundPlaybackRequest(
                sound = longShot,
                entityIndex = 2,
                channel = Defines.CHAN_WEAPON,
                attenuation = Defines.ATTN_NORM.toFloat()
            )
        )
        system.play(
            SoundPlaybackRequest(
                sound = shortShot,
                entityIndex = 3,
                channel = Defines.CHAN_WEAPON,
                attenuation = Defines.ATTN_NORM.toFloat()
            )
        )

        nowMs = 100
        system.play(
            SoundPlaybackRequest(
                sound = newShot,
                entityIndex = 4,
                channel = Defines.CHAN_WEAPON,
                attenuation = Defines.ATTN_NORM.toFloat()
            )
        )

        assertTrue(shortShot.stoppedIds.contains(1L))
        assertTrue(longShot.stoppedIds.isEmpty())
        assertEquals(1, newShot.playCalls.size)
    }

    @Test
    fun localPlayerVoiceIsProtectedFromNonPlayerReplacement() {
        Cvar.getInstance().Set("s_maxvoices", "1")
        val system = createSystem(localPlayerEntityIndex = 1)
        val localPlayerShot = registerFakeSound(durationMs = 1500)
        val monsterShot = registerFakeSound(durationMs = 150)

        system.play(
            SoundPlaybackRequest(
                sound = localPlayerShot,
                entityIndex = 1,
                channel = Defines.CHAN_WEAPON,
                attenuation = Defines.ATTN_NORM.toFloat()
            )
        )
        system.play(
            SoundPlaybackRequest(
                sound = monsterShot,
                entityIndex = 2,
                channel = Defines.CHAN_WEAPON,
                attenuation = Defines.ATTN_NORM.toFloat()
            )
        )

        assertTrue(localPlayerShot.stoppedIds.isEmpty())
        assertTrue(monsterShot.playCalls.isEmpty())
    }

    @Test
    fun explicitChannelOverridesSameEntityChannel() {
        Cvar.getInstance().Set("s_maxvoices", "1")
        val system = createSystem()
        val firstShot = registerFakeSound(durationMs = 1000)
        val secondShot = registerFakeSound(durationMs = 1000)

        system.play(
            SoundPlaybackRequest(
                sound = firstShot,
                entityIndex = 7,
                channel = Defines.CHAN_WEAPON,
                attenuation = Defines.ATTN_NORM.toFloat()
            )
        )
        system.play(
            SoundPlaybackRequest(
                sound = secondShot,
                entityIndex = 7,
                channel = Defines.CHAN_WEAPON,
                attenuation = Defines.ATTN_NORM.toFloat()
            )
        )

        assertEquals(listOf(1L), firstShot.stoppedIds)
        assertEquals(1, secondShot.playCalls.size)
        assertFalse(secondShot.stoppedIds.contains(1L))
    }

    private fun createSystem(localPlayerEntityIndex: Int? = null): FireAndForgetCakeAudioSystem {
        return FireAndForgetCakeAudioSystem(
            currentTimeMsProvider = { nowMs },
            entityOriginProvider = { Vector3(0f, 0f, 0f) },
            localPlayerEntityIndexProvider = { localPlayerEntityIndex }
        ).apply {
            beginFrame(
                ListenerState(
                    position = Vector3(0f, 0f, 0f),
                    forward = Vector3(0f, 1f, 0f),
                    up = Vector3(0f, 0f, 1f)
                )
            )
        }
    }

    private fun registerFakeSound(durationMs: Int): FakeSound {
        return FakeSound().also {
            registeredSounds += it
            SoundDurationRegistry.register(it, durationMs)
        }
    }

    private class FakeSound : Sound {
        data class PlaybackCall(val id: Long, val volume: Float, val pitch: Float, val pan: Float)
        data class PanCall(val id: Long, val pan: Float, val volume: Float)

        private var nextId = 1L
        val playCalls = mutableListOf<PlaybackCall>()
        val loopCalls = mutableListOf<PlaybackCall>()
        val stoppedIds = mutableListOf<Long>()
        val panCalls = mutableListOf<PanCall>()

        override fun play(): Long = play(1f)

        override fun play(volume: Float): Long = play(volume, 1f, 0f)

        override fun play(volume: Float, pitch: Float, pan: Float): Long {
            return nextId++.also { playCalls += PlaybackCall(it, volume, pitch, pan) }
        }

        override fun loop(): Long = loop(1f)

        override fun loop(volume: Float): Long = loop(volume, 1f, 0f)

        override fun loop(volume: Float, pitch: Float, pan: Float): Long {
            return nextId++.also { loopCalls += PlaybackCall(it, volume, pitch, pan) }
        }

        override fun stop() = Unit

        override fun pause() = Unit

        override fun resume() = Unit

        override fun dispose() = Unit

        override fun stop(soundId: Long) {
            stoppedIds += soundId
        }

        override fun pause(soundId: Long) = Unit

        override fun resume(soundId: Long) = Unit

        override fun setLooping(soundId: Long, looping: Boolean) = Unit

        override fun setPitch(soundId: Long, pitch: Float) = Unit

        override fun setVolume(soundId: Long, volume: Float) = Unit

        override fun setPan(soundId: Long, pan: Float, volume: Float) {
            panCalls += PanCall(soundId, pan, volume)
        }
    }
}
