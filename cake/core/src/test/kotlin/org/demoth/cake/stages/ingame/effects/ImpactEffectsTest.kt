package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import jake2.qcommon.Defines.*
import jake2.qcommon.math.Vector3f
import jake2.qcommon.network.messages.server.*
import jake2.qcommon.network.protocol.DirectionCodec
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

class ImpactEffectsTest {
    private class FixedBits(private val value: Int) : Random() {
        override fun nextBits(bitCount: Int) = if (bitCount == 0) 0 else value and (-1 ushr (32 - bitCount))
    }
    private class Fixture(budget: Int = 1000, random: Random = Random(7)) : AutoCloseable {
        val runtime = GdxParticleRuntime({ budget })
        val colors = mutableListOf<Int>()
        val sounds = mutableListOf<ImpactSoundRequest>()
        val effects = ImpactEffects(runtime, { colors += it; Color.WHITE }, sounds::add, random)
        fun positions(now: Int): List<Vector3> = buildList {
            runtime.submit(now, ParticleSink { _, _, x, y, z, _, _, _, _, _ -> add(Vector3(x, y, z)) })
        }
        override fun close() = runtime.dispose()
    }
    private val direction = DirectionCodec.index(Vector3f.unitX)
    private fun impact(style: Int) = PointDirectionTEMessage(style, Vector3f.zero, direction)

    @Test fun `blaster variants emit normal-directed bursts in their own palette with the impact sound`() {
        for ((style, palette) in listOf(TE_BLASTER to 0xE0, TE_BLASTER2 to 0xD0, TE_FLECHETTE to 0x6F)) Fixture().use { fixture ->
            assertTrue(fixture.effects.process(impact(style), 0))
            val first = fixture.positions(0)
            assertTrue(first.isNotEmpty())
            assertTrue(first.all { it.x in -4f..19f && it.y in -4f..4f && it.z in -4f..4f })
            assertTrue(fixture.colors.all { it in palette..palette + 7 })
            for ((before, after) in first.zip(fixture.positions(100))) assertTrue(after.x - before.x in -1.001f..7.001f)
            assertEquals(listOf("sound/weapons/lashit.wav"), fixture.sounds.map { it.path })
            assertTrue(fixture.positions(801).isEmpty())
        }
        Fixture().use { fixture ->
            fixture.effects.process(TrailTEMessage(TE_BLUEHYPERBLASTER, Vector3f.zero, Vector3f.unitX), 0)
            assertTrue(fixture.positions(0).isNotEmpty())
            assertTrue(fixture.colors.all { it in 0xE0..0xE7 })
            assertTrue(fixture.sounds.isEmpty())
        }
    }

    @Test fun `ordinary impacts scatter positions along the normal and velocities independently`() {
        Fixture().use { fixture ->
            fixture.effects.process(impact(TE_GUNSHOT), 0)
            val first = fixture.positions(0)
            assertTrue(first.all { it.x in -4f..35f && it.y in -4f..4f && it.z in -4f..4f })
            val motion = first.zip(fixture.positions(100)).map { (before, after) -> after.x - before.x }
            assertTrue(motion.any { it > 0f } && motion.any { it < 0f })
            assertTrue(motion.all { it in -2.001f..2.001f })
        }
    }

    @Test fun `all splash families respect zero small and large transmitted counts without private caps`() {
        for (style in SplashTEMessage.SUBTYPES) for (count in listOf(0, 1, 64, 128, 255)) Fixture().use { fixture ->
            val parameter = if (style == TE_SPLASH) 2 else 200
            fixture.effects.process(SplashTEMessage(style, count, Vector3f.zero, direction, parameter), 0)
            assertEquals(count, fixture.runtime.liveCount, "Style $style count $count")
            val palette = if (style == TE_SPLASH) 0xB0 else 200
            assertTrue(fixture.colors.all { if (style == TE_TUNNEL_SPARKS) it == palette else it in palette..palette + 7 })
        }
    }

    @Test fun `tunnel sparks accelerate upwards while laser and welding sparks fall`() {
        for (style in listOf(TE_TUNNEL_SPARKS, TE_LASER_SPARKS, TE_WELDING_SPARKS)) Fixture().use { fixture ->
            fixture.effects.process(SplashTEMessage(style, 3, Vector3f.zero, direction, 20), 0)
            val first = fixture.positions(0)
            val second = fixture.positions(100)
            val third = fixture.positions(200)
            for (index in first.indices) {
                val curvature = third[index].z - 2f * second[index].z + first[index].z
                assertTrue(if (style == TE_TUNNEL_SPARKS) curvature > 0f else curvature < 0f)
            }
        }
    }

    @Test fun `gunshot ricochets are occasional and particle budgets do not mute audio`() {
        for (style in listOf(TE_GUNSHOT, TE_BULLET_SPARKS)) {
            val heard = mutableListOf<String>()
            for (roll in 0..15) Fixture(budget = 0, random = FixedBits(roll)).use { fixture ->
                fixture.effects.process(impact(style), 0)
                assertEquals(if (roll in 1..3) 1 else 0, fixture.sounds.size)
                assertTrue(fixture.sounds.all { it.origin == Vector3f.zero && it.attenuation == ATTN_NORM.toFloat() })
                heard += fixture.sounds.map { it.path }
            }
            assertEquals(listOf("sound/world/ric1.wav", "sound/world/ric2.wav", "sound/world/ric3.wav"), heard)
        }
        Fixture(budget = 0).use { fixture ->
            fixture.effects.process(impact(TE_SPARKS), 0)
            fixture.effects.process(impact(TE_SHOTGUN), 0)
            assertTrue(fixture.sounds.isEmpty())
            fixture.effects.process(impact(TE_BLASTER), 0)
            assertEquals("sound/weapons/lashit.wav", fixture.sounds.single().path)
        }
    }

    @Test fun `spark splashes use weighted sound variants with static attenuation even at zero count`() {
        val heard = mutableListOf<String>()
        for (roll in 0..3) Fixture(budget = 0, random = FixedBits(roll)).use { fixture ->
            fixture.effects.process(SplashTEMessage(TE_SPLASH, 0, Vector3f.zero, direction, SPLASH_SPARKS), 0)
            val sound = fixture.sounds.single()
            assertEquals(ATTN_STATIC.toFloat(), sound.attenuation)
            heard += sound.path
        }
        assertEquals(listOf("sound/world/spark5.wav", "sound/world/spark6.wav", "sound/world/spark7.wav", "sound/world/spark7.wav"), heard)
    }

    @Test fun `chainfist smoke rises without downward acceleration and particles remain budget limited`() {
        Fixture().use { fixture ->
            fixture.effects.process(PointTEMessage(TE_CHAINFIST_SMOKE, Vector3f.zero), 0)
            val first = fixture.positions(0)
            assertTrue(first.isNotEmpty())
            assertTrue(fixture.colors.all { it in 0..7 })
            for ((before, after) in first.zip(fixture.positions(100))) assertEquals(2f, after.z - before.z, .001f)
        }
        Fixture(budget = 3).use { fixture ->
            fixture.effects.process(SplashTEMessage(TE_SPLASH, 255, Vector3f.zero, direction, 0), 0)
            assertEquals(3, fixture.runtime.liveCount)
        }
    }

    @Test fun `directional family coverage excludes only heatbeam steam handled by its own controller`() {
        for (style in PointDirectionTEMessage.SUBTYPES - setOf(TE_HEATBEAM_SPARKS, TE_HEATBEAM_STEAM)) Fixture(budget = 0).use {
            assertTrue(it.effects.process(impact(style), 0), "Style $style")
        }
        Fixture().use {
            assertFalse(it.effects.process(impact(TE_HEATBEAM_STEAM), 0))
            assertFalse(it.effects.process(PointTEMessage(TE_EXPLOSION1, Vector3f.zero), 0))
            assertFalse(it.effects.process(TrailTEMessage(TE_RAILTRAIL, Vector3f.zero, Vector3f.unitX), 0))
        }
    }
}
