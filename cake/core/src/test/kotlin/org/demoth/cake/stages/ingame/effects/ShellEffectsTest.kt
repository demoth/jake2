package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import jake2.qcommon.Defines
import jake2.qcommon.math.Vector3f
import jake2.qcommon.network.messages.server.PointTEMessage
import jake2.qcommon.network.messages.server.WidowBeamOutTEMessage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

class ShellEffectsTest {
    private class Fixture(budget: Int = 10000, maximumSustains: Int = 8) : AutoCloseable {
        val particles = GdxParticleRuntime({ budget })
        val colors = mutableListOf<Int>()
        val effects = ShellEffects(particles, { index -> colors += index; Color.WHITE }, Random(7), maximumSustains)
        fun advance(nowMs: Int) { particles.update(nowMs); effects.update(nowMs) }
        fun positions(nowMs: Int): List<Vector3> = buildList {
            particles.submit(nowMs, ParticleSink { _, _, x, y, z, _, _, _, _, _ -> add(Vector3(x, y, z)) })
        }
        override fun close() { effects.clear(); particles.dispose() }
    }

    @Test fun `widow beam-out expands over its lifetime using its palette and freezes on a paused clock`() {
        Fixture().use { fixture ->
            fixture.effects.process(WidowBeamOutTEMessage(7, Vector3f.zero), 0)
            fixture.advance(0)
            val count = fixture.particles.liveCount
            assertTrue(count > 0)
            assertTrue(fixture.positions(0).all { it.isZero })
            fixture.advance(1050)
            val halfway = fixture.positions(1050)
            assertEquals(count, halfway.size) // A stall produces one current shell, not a backlog of pulses.
            assertTrue(halfway.all { kotlin.math.abs(it.len() - 22.5f) < .001f })
            assertTrue(fixture.colors.all { it in setOf(16, 104, 168, 144) })
            repeat(100) { fixture.advance(1050) }
            assertEquals(halfway, fixture.positions(1050))
            fixture.advance(2100)
            assertEquals(0, fixture.effects.activeSustainCount)
            assertEquals(0, fixture.particles.liveCount)
        }
    }

    @Test fun `nuke blast expands independently and expires after one second`() {
        Fixture().use { fixture ->
            fixture.effects.process(PointTEMessage(Defines.TE_NUKEBLAST, Vector3f.zero), 0)
            fixture.advance(500)
            val halfway = fixture.positions(500)
            assertTrue(halfway.isNotEmpty())
            assertTrue(halfway.all { kotlin.math.abs(it.len() - 100f) < .001f })
            assertTrue(fixture.colors.all { it in setOf(110, 112, 114, 116) })
            fixture.advance(1000)
            assertEquals(0, fixture.effects.activeSustainCount)
            assertTrue(fixture.positions(1000).isEmpty())
        }
    }

    @Test fun `nukes coexist with each other and with wire-identified widow effects`() {
        Fixture().use { fixture ->
            fixture.effects.process(WidowBeamOutTEMessage(21000, Vector3f.zero), 0)
            fixture.effects.process(PointTEMessage(Defines.TE_NUKEBLAST, Vector3f(1000f, 0f, 0f)), 0)
            fixture.effects.process(PointTEMessage(Defines.TE_NUKEBLAST, Vector3f(2000f, 0f, 0f)), 0)
            fixture.advance(0)
            assertEquals(3, fixture.effects.activeSustainCount)
            val groups = fixture.positions(0).groupBy { it.x }
            assertEquals(setOf(0f, 1000f, 2000f), groups.keys)
            assertTrue(groups.values.all { it.isNotEmpty() })
        }
    }

    @Test fun `repeated widow identifiers replace their origin and refresh lifetime`() {
        Fixture().use { fixture ->
            fixture.effects.process(WidowBeamOutTEMessage(7, Vector3f.zero), 0)
            fixture.advance(1000)
            fixture.effects.process(WidowBeamOutTEMessage(7, Vector3f(100f, 0f, 0f)), 1100)
            fixture.advance(1100)
            assertEquals(1, fixture.effects.activeSustainCount)
            assertTrue(fixture.positions(1100).all { it == Vector3(100f, 0f, 0f) })
            fixture.advance(2200)
            assertEquals(1, fixture.effects.activeSustainCount)
            fixture.advance(3200)
            assertEquals(0, fixture.effects.activeSustainCount)
        }
    }

    @Test fun `sustain capacity particle budgets and teardown bound all future work`() {
        Fixture(budget = 3, maximumSustains = 1).use { fixture ->
            fixture.effects.process(WidowBeamOutTEMessage(1, Vector3f.zero), 0)
            fixture.effects.process(PointTEMessage(Defines.TE_NUKEBLAST, Vector3f.zero), 0)
            fixture.advance(0)
            assertEquals(1, fixture.effects.activeSustainCount)
            assertEquals(3, fixture.particles.liveCount)
            fixture.effects.clear()
            fixture.advance(100)
            assertEquals(0, fixture.effects.activeSustainCount)
            assertEquals(0, fixture.particles.liveCount)
        }
        Fixture(budget = 0).use { fixture ->
            assertFalse(fixture.effects.process(PointTEMessage(Defines.TE_EXPLOSION1, Vector3f.zero), 0))
            fixture.effects.process(PointTEMessage(Defines.TE_NUKEBLAST, Vector3f.zero), 0)
            fixture.advance(0)
            assertEquals(0, fixture.particles.liveCount)
            fixture.advance(2000)
            assertEquals(0, fixture.effects.activeSustainCount)
        }
    }
}
