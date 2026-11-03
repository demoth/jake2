package org.demoth.cake.stages.ingame

import com.badlogic.gdx.assets.AssetDescriptor
import com.badlogic.gdx.assets.AssetLoaderParameters
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.FileHandleResolver
import com.badlogic.gdx.assets.loaders.SynchronousAssetLoader
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.backends.headless.mock.audio.MockSound
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.utils.GdxNativesLoader
import jake2.qcommon.Defines.*
import jake2.qcommon.entity_state_t
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.find
import jake2.qcommon.network.messages.server.*
import org.demoth.cake.CakeCvars
import org.demoth.cake.audio.*
import org.demoth.cake.config.GameConfiguration
import org.demoth.cake.stages.ingame.effects.ClientEffectsSystem
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import com.badlogic.gdx.utils.Array as GdxArray

class IngameSoundMessageHandlerTest {
    @Test
    fun `live footstep toggle preserves fall respawn teleport and pickup sounds`() {
        GdxNativesLoader.load()
        val cvars = Cvar.getInstance()
        cvars.clear()
        CakeCvars.registerEntities()
        CakeCvars.registerAudio()
        RenderTuningCvars.register()
        val footsteps = cvars.find<Boolean>("cl_footsteps")
        assertTrue(footsteps.typedValue)

        val stepPaths = (1..4).map { "sound/player/step$it.wav" }.toSet()
        val otherPaths = listOf("sound/player/land1.wav", "sound/player/fall2.wav",
            "sound/player/fall1.wav", "sound/items/respawn1.wav", "sound/misc/tele1.wav",
            "sound/items/pkup.wav")
        val availablePaths = stepPaths + otherPaths
        val resolver = FileHandleResolver { if (it in availablePaths) FileHandle(it) else null }
        val assets = AssetManager(resolver, false)
        // Same synchronous loader pattern as EffectAssetCatalogTest, using libGDX's headless Sound.
        assets.setLoader(Sound::class.java, object : SynchronousAssetLoader<Sound, AssetLoaderParameters<Sound>>(resolver) {
            override fun load(manager: AssetManager, fileName: String, file: FileHandle,
                              parameter: AssetLoaderParameters<Sound>?): Sound = MockSound()
            override fun getDependencies(fileName: String, file: FileHandle,
                                         parameter: AssetLoaderParameters<Sound>?): GdxArray<AssetDescriptor<*>>? = null
        })
        val config = GameConfiguration(assets)
        config.applyConfigString(CS_SOUNDS + 1, "items/pkup.wav", loadResource = true)
        val entities = ClientEntityManager()
        val audio = RecordingAudio()
        val lights = DynamicLightSystem()
        val effects = ClientEffectsSystem(assets, entities, audio, { PerspectiveCamera() }, lights)
        val handler = IngameSoundMessageHandler(config, entities, effects, audio, lights)
        val events = listOf(EV_FOOTSTEP, EV_FALLSHORT, EV_FALL, EV_FALLFAR, EV_ITEM_RESPAWN, EV_PLAYER_TELEPORT)
        var frame = 0
        fun dispatch(): List<Pair<Int, String?>> {
            entities.processServerFrameHeader(FrameHeaderMessage(++frame, -1, 0, 0, byteArrayOf()))
            assertTrue(entities.processPacketEntitiesMessage(PacketEntitiesMessage(events.mapIndexed { index, event ->
                val state = entity_state_t(index + 1).apply { this.event = event }
                requireNotNull(EntityUpdate.plan(entity_state_t(state.index), state, true, true))
            })))
            audio.requests.clear()
            handler.playEntityEventSounds()
            handler.processSoundMessage(SoundMessage(1, source = SoundSource(7, CHAN_AUTO)))
            return audio.requests.map { it.entityIndex to assets.getAssetFileName(it.sound) }
        }
        fun assertEnabled() {
            val played = dispatch()
            assertTrue(played.first().second in stepPaths)
            assertEquals(1, played.first().first)
            assertEquals(otherPaths.mapIndexed { index, path -> index + 2 to path }, played.drop(1))
        }
        try {
            assertEnabled()
            footsteps.set(false)
            assertEquals(otherPaths.mapIndexed { index, path -> index + 2 to path }, dispatch())
            footsteps.set(true)
            assertEnabled()
        } finally {
            footsteps.set(true)
            effects.dispose()
            entities.dispose()
            config.unloadAssets()
            assets.dispose()
            cvars.clear()
        }
    }

    private class RecordingAudio : CakeAudioSystem {
        val requests = mutableListOf<SoundPlaybackRequest>()
        override fun play(request: SoundPlaybackRequest) { requests += request }
        override fun beginFrame(listener: ListenerState) = Unit
        override fun syncEntityLoopingSounds(requests: Collection<EntityLoopSoundRequest>) = Unit
        override fun endFrame() = Unit
        override fun stopAll() = Unit
        override fun dispose() = Unit
    }
}
