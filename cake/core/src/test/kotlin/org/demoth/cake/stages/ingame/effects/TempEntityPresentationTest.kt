package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.FileHandleResolver
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.utils.GdxNativesLoader
import jake2.qcommon.Defines.*
import jake2.qcommon.math.Vector3f
import jake2.qcommon.network.messages.server.*
import org.demoth.cake.CakeCvars
import org.demoth.cake.audio.*
import org.demoth.cake.stages.ingame.ClientEntityManager
import org.demoth.cake.stages.ingame.DynamicLightSystem
import org.demoth.cake.stages.ingame.RenderTuningCvars
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/** Exercises the real dispatcher and frame lifecycle, without render calls, GL or installed assets. */
class TempEntityPresentationTest {
    private class Fixture : AutoCloseable {
        var now = 0
        val assets = AssetManager(FileHandleResolver { FileHandle(it) }, false)
        val entities = ClientEntityManager()
        val lights = DynamicLightSystem()
        val effects = ClientEffectsSystem(assets, entities, object : CakeAudioSystem {
            override fun beginFrame(listener: ListenerState) = Unit
            override fun play(request: SoundPlaybackRequest) = Unit
            override fun syncEntityLoopingSounds(requests: Collection<EntityLoopSoundRequest>) = Unit
            override fun endFrame() = Unit
            override fun stopAll() = Unit
            override fun dispose() = Unit
        }, { PerspectiveCamera() }, lights, { now })

        fun frame(time: Int) {
            now = time
            effects.update(0f, 0)
            lights.beginFrame(now, 0f)
            effects.collectDynamicLights()
        }

        override fun close() { effects.dispose(); entities.dispose(); assets.dispose() }
    }

    private fun messages(): List<TEMessage> = buildList {
        val start = Vector3f(10f, 20f, 30f)
        val end = Vector3f(100f, 40f, 50f)
        PointTEMessage.SUBTYPES.forEach { add(PointTEMessage(it, start)) }
        PointDirectionTEMessage.SUBTYPES.forEach { add(PointDirectionTEMessage(it, start, 0)) }
        TrailTEMessage.SUBTYPES.forEach { add(TrailTEMessage(it, start, end)) }
        SplashTEMessage.SUBTYPES.forEach { add(SplashTEMessage(it, 20, start, 0, 1)) }
        BeamTEMessage.SUBTYPES.forEach { add(BeamTEMessage(it, 1, start, end)) }
        BeamOffsetTEMessage.SUBTYPES.forEach { add(BeamOffsetTEMessage(it, 1, start, end, Vector3f.zero)) }
        add(LightningTEMessage(1, 2, start, end))
        add(FlashlightTEMessage(start, 1))
        add(ForceWallTEMessage(start, end, 100))
        add(SteamTEMessage(1, 20, start, 0, 100, 60, 500))
        add(WidowBeamOutTEMessage(1, start))
    }

    @Test fun `fixtures account for every classic supported wire subtype exactly once`() {
        assertEquals((0..55).toSet() - setOf(TE_RAILTRAIL2, TE_FLAME), messages().map { it.style }.toSet())
        assertEquals(54, messages().size)
    }

    @TestFactory fun `every subtype produces presentation state and expires without further messages or drawing`() =
        messages().map { message -> DynamicTest.dynamicTest("temporary entity ${message.style}") {
            Fixture().use { fixture ->
                fixture.effects.processTempEntityMessage(message)
                fixture.frame(0)
                val particles = fixture.effects.debugLiveParticleCount()
                val effects = fixture.effects.debugActiveEffectCount()
                val lights = fixture.lights.visibleLightsForShader().size
                assertTrue(particles + effects + lights > 0, "Subtype ${message.style} produced no presentation")
                repeat(3) {
                    fixture.frame(0)
                    assertEquals(particles, fixture.effects.debugLiveParticleCount(), "Paused emission duplicated particles")
                    assertEquals(effects, fixture.effects.debugActiveEffectCount())
                    assertEquals(lights, fixture.lights.visibleLightsForShader().size)
                }
                fixture.frame(11_000)
                assertEquals(0, fixture.effects.debugActiveEffectCount(), "Effect state outlived its lifetime")
                assertEquals(0, fixture.effects.debugLiveParticleCount())
                assertTrue(fixture.lights.visibleLightsForShader().isEmpty())
            }
        } }

    @Test fun `teardown clears sustained and beam state and a new session starts empty`() {
        Fixture().use { fixture ->
            messages().forEach(fixture.effects::processTempEntityMessage)
            fixture.frame(0)
            assertTrue(fixture.effects.debugActiveEffectCount() > 0)
        }
        Fixture().use { fixture ->
            fixture.frame(0)
            assertEquals(0, fixture.effects.debugActiveEffectCount())
            assertEquals(0, fixture.effects.debugLiveParticleCount())
            assertTrue(fixture.lights.visibleLightsForShader().isEmpty())
        }
    }

    companion object {
        @BeforeAll @JvmStatic fun setup() {
            GdxNativesLoader.load()
            CakeCvars.registerEntities()
            RenderTuningCvars.register()
        }
    }
}
