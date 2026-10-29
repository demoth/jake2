package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import jake2.qcommon.Defines
import jake2.qcommon.math.Vector3f
import jake2.qcommon.network.messages.server.*
import org.demoth.cake.stages.ingame.DynamicLightSystem
import org.demoth.cake.stages.ingame.RenderTuningCvars
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

class ExpansionEffectsTest {
    private class Fixture(budget: Int = 1000, maximumTrailPoints: Int = 1024) : AutoCloseable {
        val particles = GdxParticleRuntime({ budget })
        val colors = mutableListOf<Int>()
        val sounds = mutableListOf<Vector3f>()
        val lights = DynamicLightSystem()
        val effects = ExpansionEffects(particles, { index -> colors += index; Color.WHITE }, lights,
            sounds::add, Random(7), maximumTrailPoints)
        fun positions(nowMs: Int): List<Vector3> = buildList {
            particles.submit(nowMs, ParticleSink { _, _, x, y, z, _, _, _, _, _ -> add(Vector3(x, y, z)) })
        }
        override fun close() = particles.dispose()
    }

    @Test fun `flashlight refreshes the source keyed light and expires through the shared lighting runtime`() {
        RenderTuningCvars.register()
        Fixture().use { fixture ->
            fixture.effects.process(FlashlightTEMessage(Vector3f(1f, 2f, 3f), 7), 0)
            fixture.lights.beginFrame(0, 0f)
            val first = fixture.lights.visibleLightsForShader().single()
            assertEquals(Vector3(1f, 2f, 3f), first.origin)
            assertEquals(400f, first.radius)
            assertEquals(1f, first.red); assertEquals(1f, first.green); assertEquals(1f, first.blue)
            fixture.effects.process(FlashlightTEMessage(Vector3f(4f, 5f, 6f), 7), 50)
            fixture.lights.beginFrame(100, .05f)
            assertEquals(Vector3(4f, 5f, 6f), fixture.lights.visibleLightsForShader().single().origin)
            fixture.lights.beginFrame(200, .1f)
            assertTrue(fixture.lights.visibleLightsForShader().isEmpty())
            fixture.effects.process(FlashlightTEMessage(Vector3f.zero, -1), 200)
            fixture.lights.beginFrame(200, 0f)
            assertTrue(fixture.lights.visibleLightsForShader().isEmpty())
        }
    }

    @Test fun `force wall spreads its palette particles across the segment and lets them fall`() {
        Fixture().use { fixture ->
            fixture.effects.process(ForceWallTEMessage(Vector3f.zero, Vector3f(80f, 0f, 0f), 37), 0)
            val first = fixture.positions(0)
            assertTrue(first.size in 1..20)
            assertTrue(first.all { it.x in -3f..83f && it.y in -3f..3f && it.z in -3f..3f })
            assertTrue(fixture.colors.all { it == 37 })
            val later = fixture.positions(250)
            for ((before, after) in first.zip(later)) {
                assertEquals(before.x, after.x)
                assertEquals(before.y, after.y)
                assertTrue(after.z - before.z in -12.51f..-7.49f)
            }
            assertTrue(fixture.positions(3501).isEmpty())
        }
    }

    @Test fun `bubble trail two is dense rising palette colored and requests its sound at the start`() {
        Fixture().use { fixture ->
            val message = TrailTEMessage(Defines.TE_BUBBLETRAIL2, Vector3f.zero, Vector3f(32f, 0f, 0f))
            assertTrue(fixture.effects.process(message, 0))
            val first = fixture.positions(0)
            assertEquals(4, first.size)
            assertTrue(first.all { it.y in -2f..2f && it.z in -6f..-2f })
            assertTrue(fixture.colors.all { it in 4..11 })
            assertEquals(listOf(Vector3f.zero), fixture.sounds)
            for ((before, after) in first.zip(fixture.positions(100))) assertTrue(after.z > before.z)
            assertTrue(fixture.positions(1101).isEmpty())
            assertFalse(fixture.effects.process(message.copy(style = Defines.TE_RAILTRAIL), 1200))
        }
    }

    @Test fun `degenerate segments emit no particles and long trails and budgets have bounded work`() {
        Fixture(budget = 3, maximumTrailPoints = 10).use { fixture ->
            fixture.effects.process(ForceWallTEMessage(Vector3f.zero, Vector3f.zero, 10), 0)
            assertEquals(0, fixture.particles.liveCount)
            fixture.effects.process(TrailTEMessage(Defines.TE_BUBBLETRAIL2, Vector3f.zero, Vector3f(10000f, 0f, 0f)), 0)
            assertEquals(3, fixture.particles.liveCount)
        }
        Fixture(maximumTrailPoints = 10).use { fixture ->
            fixture.effects.process(TrailTEMessage(Defines.TE_BUBBLETRAIL2, Vector3f.zero, Vector3f(10000f, 0f, 0f)), 0)
            assertEquals(10, fixture.particles.liveCount)
        }
        Fixture(budget = 0).use { fixture ->
            fixture.effects.process(ForceWallTEMessage(Vector3f.zero, Vector3f(100f, 0f, 0f), 10), 0)
            assertEquals(0, fixture.particles.liveCount)
        }
    }
}
