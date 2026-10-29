package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import jake2.qcommon.Defines
import jake2.qcommon.math.Vector3f
import jake2.qcommon.network.messages.server.PointTEMessage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

class PointParticlesTest {
    private class Fixture(budget: Int = 1000) : AutoCloseable {
        val runtime = GdxParticleRuntime({ budget })
        val colors = mutableListOf<Int>()
        val particles = PointParticles(runtime, { colors += it; Color.WHITE }, Random(12))
        fun positions(now: Int): List<Vector3> = buildList {
            runtime.submit(now, ParticleSink { _, _, x, y, z, _, _, _, _, _ -> add(Vector3(x, y, z)) })
        }
        override fun close() = runtime.dispose()
    }

    @Test fun `boss teleport creates a tall radial column in its palette instead of only sound`() {
        Fixture().use { fixture ->
            fixture.particles.process(PointTEMessage(Defines.TE_BOSSTPORT, Vector3f.zero), 0)
            val start = fixture.positions(0)
            assertTrue(start.isNotEmpty())
            assertTrue(start.all { it.z in 8f..98f && it.x * it.x + it.y * it.y <= 1024.01f })
            assertTrue(fixture.colors.all { it in setOf(16, 104, 168, 144) })
            val later = fixture.positions(100)
            assertTrue(start.zip(later).all { (before, after) -> after.z < before.z })
            assertTrue(start.zip(later).any { (before, after) -> after.x * after.x + after.y * after.y > before.x * before.x + before.y * before.y })
            assertTrue(fixture.positions(2601).isEmpty())
        }
    }

    @Test fun `tracker emits an owned black particle cloud with independent motion and fade`() {
        Fixture().use { fixture ->
            fixture.particles.process(PointTEMessage(Defines.TE_TRACKER_EXPLOSION, Vector3f(100f, 200f, 300f)), 0)
            val initial = fixture.positions(0)
            assertTrue(initial.isNotEmpty())
            assertTrue(initial.all { it.x in 84f..116f && it.y in 184f..216f && it.z in 284f..316f })
            assertTrue(fixture.colors.all { it == 0 })
            val later = fixture.positions(100)
            assertNotEquals(initial, later)
            repeat(10) { assertEquals(later, fixture.positions(100)) }
            assertTrue(fixture.positions(2001).isEmpty())
        }
    }

    @Test fun `burst work respects disabled and exhausted particle budgets and ignores unrelated point effects`() {
        for (style in listOf(Defines.TE_BOSSTPORT, Defines.TE_TRACKER_EXPLOSION)) {
            Fixture(3).use { fixture ->
                fixture.particles.process(PointTEMessage(style, Vector3f.zero), 0)
                assertEquals(3, fixture.runtime.liveCount)
                fixture.particles.process(PointTEMessage(style, Vector3f.zero), 0)
                assertEquals(3, fixture.runtime.liveCount)
            }
            Fixture(0).use { fixture ->
                fixture.particles.process(PointTEMessage(style, Vector3f.zero), 0)
                assertEquals(0, fixture.runtime.liveCount)
            }
        }
        Fixture().use { fixture ->
            assertFalse(fixture.particles.process(PointTEMessage(Defines.TE_EXPLOSION1, Vector3f.zero), 0))
            assertEquals(0, fixture.runtime.liveCount)
        }
    }
}
