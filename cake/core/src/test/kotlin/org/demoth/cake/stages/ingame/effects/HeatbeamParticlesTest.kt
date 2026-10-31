package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.sqrt
import kotlin.random.Random

class HeatbeamParticlesTest {
    private data class Sample(val position: Vector3, val alpha: Float)
    private class Fixture(private val budget: Int = 1000) : AutoCloseable {
        val runtime = GdxParticleRuntime({ budget })
        val paletteIndices = mutableListOf<Int>()
        val effect = HeatbeamParticles(runtime, { index -> paletteIndices += index; Color.WHITE }, Random(7))
        fun emit(local: Boolean, length: Float = 500f, now: Int = 0) {
            effect.emit(local, Vector3.Zero, Vector3.X, Vector3(0f, -1f, 0f), Vector3.Z, length, now)
        }
        fun samples(now: Int): List<Sample> = buildList {
            runtime.submit(now, ParticleSink { _, _, x, y, z, _, _, _, alpha, _ -> add(Sample(Vector3(x, y, z), alpha)) })
        }
        override fun close() = runtime.dispose()
    }

    @Test fun `player rings stay near the beam origin use a narrow radius and fade as short pulses`() {
        Fixture().use { fixture ->
            fixture.emit(local = true)
            val particles = fixture.samples(0)
            assertEquals(100, particles.size)
            assertTrue(fixture.paletteIndices.all { it in 216..223 })
            assertTrue(particles.all { it.position.x in 8f..160f && it.alpha == .5f })
            assertEquals(5, particles.map { it.position.x }.toSet().size)
            assertTrue(particles.all { sqrt(it.position.y * it.position.y + it.position.z * it.position.z) <= 1.51f })
            assertTrue(fixture.samples(50).all { it.alpha in 0f..0.5f })
            assertTrue(fixture.samples(75).isEmpty())
        }
    }

    @Test fun `rings respect beam range and advance along the beam with presentation time`() {
        Fixture().use { fixture ->
            fixture.emit(local = true, length = 5f)
            assertEquals(0, fixture.runtime.liveCount)
            fixture.emit(local = true, length = 25f)
            assertEquals(20, fixture.runtime.liveCount)
            assertEquals(8f, fixture.samples(0).first().position.x)
            fixture.runtime.clear()
            fixture.emit(local = true, length = 25f, now = 50)
            assertEquals(12.8f, fixture.samples(50).first().position.x, .001f)
            assertTrue(fixture.samples(50).all { it.position.x <= 25f })
        }
    }

    @Test fun `monster plasma forms a bounded shell instead of a forward trail`() {
        Fixture().use { fixture ->
            fixture.emit(local = false)
            val particles = fixture.samples(0)
            assertEquals(40, particles.size)
            assertTrue(particles.all { kotlin.math.abs(it.position.len() - 10f) < .001f && it.alpha == 1f })
            assertTrue(fixture.paletteIndices.all { it == 0xE0 })
            assertEquals(particles.map { it.position }, fixture.samples(50).map { it.position })
            assertTrue(fixture.samples(75).isEmpty())
        }
    }

    @Test fun `particle budgets cap rings and shells and disable emission at zero`() {
        for (local in listOf(true, false)) {
            Fixture(budget = 3).use { fixture ->
                fixture.emit(local)
                assertEquals(3, fixture.runtime.liveCount)
                fixture.emit(local)
                assertEquals(3, fixture.runtime.liveCount)
            }
            Fixture(budget = 0).use { fixture ->
                fixture.emit(local)
                assertEquals(0, fixture.runtime.liveCount)
            }
        }
    }
}
