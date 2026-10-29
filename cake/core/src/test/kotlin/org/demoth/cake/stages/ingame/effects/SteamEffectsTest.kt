package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import jake2.qcommon.Defines
import jake2.qcommon.math.Vector3f
import jake2.qcommon.network.messages.server.PointDirectionTEMessage
import jake2.qcommon.network.messages.server.SteamTEMessage
import jake2.qcommon.network.protocol.DirectionCodec
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** Real particle-controller behavior with synthetic messages, palette values and presentation clocks. */
class SteamEffectsTest {
    private data class Sample(val position: Vector3, val alpha: Float)

    private class Fixture(initialBudget: Int = 1000) : AutoCloseable {
        var budget = initialBudget
        val particles = GdxParticleRuntime({ budget })
        val paletteIndices = mutableListOf<Int>()
        val steam = SteamEffects(particles, { index ->
            paletteIndices += index
            Color(index / 255f, .5f, .5f, 1f)
        }, random = Random(12), maximumSustains = 2)

        fun advance(now: Int) {
            particles.update(now)
            steam.update(now)
        }

        fun samples(now: Int): List<Sample> = buildList {
            particles.submit(now, ParticleSink { _, _, x, y, z, _, _, _, alpha, _ -> add(Sample(Vector3(x, y, z), alpha)) })
        }

        override fun close() { steam.clear(); particles.dispose() }
    }

    private val origin = Vector3f(10f, 20f, 30f)
    private val direction = DirectionCodec.index(Vector3f.unitX)
    private fun message(id: Int = -1, count: Int = 4, magnitude: Int = 100, duration: Int? = null) =
        SteamTEMessage(id, count, origin, direction, color = 100, magnitude = magnitude, durationMillis = duration)

    @Test fun `instant steam honors count palette direction magnitude and particle lifetime`() {
        Fixture().use { fixture ->
            fixture.steam.process(message(), 0)
            assertEquals(4, fixture.particles.liveCount)
            assertEquals(0, fixture.steam.activeSustainCount)
            assertEquals(4, fixture.paletteIndices.size)
            assertTrue(fixture.paletteIndices.all { it in 100..107 })
            val initial = fixture.samples(0)
            assertTrue(initial.all { it.position.x in 0f..20f && it.position.y in 10f..30f && it.position.z in 20f..40f })
            val advanced = fixture.samples(100)
            for ((before, after) in initial.zip(advanced)) {
                assertEquals(10f, after.position.x - before.position.x, .001f)
                assertTrue(after.alpha in 0f..1f && after.alpha < before.alpha)
            }
            fixture.advance(801)
            assertEquals(0, fixture.particles.liveCount)
        }
    }

    @Test fun `zero and signed magnitudes preserve directed motion and acceleration`() {
        for (magnitude in listOf(-120, 0, 120)) Fixture().use { fixture ->
            fixture.steam.process(message(count = 1, magnitude = magnitude), 0)
            val first = fixture.samples(0).single().position
            val second = fixture.samples(100).single().position
            val third = fixture.samples(200).single().position
            assertEquals(magnitude * .1f, second.x - first.x, .001f)
            // Steam falls under physical acceleration, rather than using the old generic impact burst.
            assertEquals(-.4f, third.z - 2f * second.z + first.z, .001f)
        }
    }

    @Test fun `vertical directions produce finite particles with motion along the supplied normal`() {
        Fixture().use { fixture ->
            fixture.steam.process(message(count = 1).copy(directionIndex = DirectionCodec.index(Vector3f.unitZ)), 0)
            val first = fixture.samples(0).single().position
            val second = fixture.samples(100).single().position
            assertTrue(second.x.isFinite() && second.y.isFinite() && second.z.isFinite())
            assertEquals(9.8f, second.z - first.z, .001f)
        }
    }

    @Test fun `sustained steam emits every hundred milliseconds without duplicating paused updates`() {
        Fixture().use { fixture ->
            fixture.steam.process(message(id = 7, count = 2, duration = 250), 0)
            assertEquals(0, fixture.particles.liveCount)
            fixture.advance(0)
            repeat(30) { fixture.advance(0) }
            fixture.advance(99)
            assertEquals(2, fixture.particles.liveCount)
            fixture.advance(100)
            assertEquals(4, fixture.particles.liveCount)
            fixture.advance(200)
            assertEquals(6, fixture.particles.liveCount)
            fixture.advance(250)
            assertEquals(0, fixture.steam.activeSustainCount)
            fixture.advance(1100)
            assertEquals(0, fixture.particles.liveCount)
        }
    }

    @Test fun `refresh replaces an identifier and clear stops future bursts while particles finish fading`() {
        Fixture().use { fixture ->
            fixture.steam.process(message(id = 1, count = 2, duration = 1000), 0)
            fixture.advance(0)
            fixture.steam.process(message(id = 1, count = 3, duration = 1000).copy(position = Vector3f(200f, 0f, 0f)), 50)
            fixture.advance(50)
            assertEquals(1, fixture.steam.activeSustainCount)
            assertEquals(5, fixture.particles.liveCount)
            assertEquals(3, fixture.samples(50).count { it.position.x in 190f..210f })
            fixture.steam.clear()
            fixture.advance(150)
            assertEquals(0, fixture.steam.activeSustainCount)
            assertEquals(5, fixture.particles.liveCount)
            fixture.advance(1000)
            assertEquals(0, fixture.particles.liveCount)
        }
    }

    @Test fun `stalls skip catchup bursts expired sustains stop and active effects stay bounded`() {
        Fixture().use { fixture ->
            for (id in 1..3) fixture.steam.process(message(id = id, count = 1, duration = 2000), 0)
            assertEquals(2, fixture.steam.activeSustainCount)
            fixture.advance(0)
            fixture.advance(1025)
            assertEquals(2, fixture.particles.liveCount) // Only one fresh burst per admitted sustain.
            repeat(20) { fixture.advance(1025) }
            assertEquals(2, fixture.particles.liveCount)
            fixture.advance(3000)
            assertEquals(0, fixture.particles.liveCount)
            assertEquals(0, fixture.steam.activeSustainCount)
        }
    }

    @Test fun `particle budget limits bursts and zero count emits nothing`() {
        Fixture(initialBudget = 3).use { fixture ->
            fixture.steam.process(message(count = 0), 0)
            assertEquals(0, fixture.particles.liveCount)
            fixture.steam.process(message(count = 255), 0)
            assertEquals(3, fixture.particles.liveCount)
            fixture.budget = 0
            fixture.advance(1000)
            fixture.steam.process(message(), 1000)
            assertEquals(0, fixture.particles.liveCount)
        }
    }

    @Test fun `heatbeam sparks and steam use directional steam particles with their own counts and palette`() {
        for ((style, count, color) in listOf(Triple(Defines.TE_HEATBEAM_SPARKS, 50, 0x08), Triple(Defines.TE_HEATBEAM_STEAM, 20, 0xE0))) {
            Fixture().use { fixture ->
                assertTrue(fixture.steam.processImpact(PointDirectionTEMessage(style, origin, direction), 0))
                assertEquals(count, fixture.particles.liveCount)
                assertTrue(fixture.paletteIndices.all { it in color..color + 7 })
                val initial = fixture.samples(0)
                val advanced = fixture.samples(100)
                for ((first, second) in initial.zip(advanced)) assertEquals(6f, second.position.x - first.position.x, .001f)
                assertFalse(fixture.steam.processImpact(PointDirectionTEMessage(Defines.TE_GUNSHOT, origin, direction), 100))
                assertEquals(count, fixture.particles.liveCount)
            }
        }
    }
}
