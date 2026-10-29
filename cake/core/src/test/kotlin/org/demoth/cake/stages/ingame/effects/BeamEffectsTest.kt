package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.math.Vector3
import jake2.qcommon.Defines
import jake2.qcommon.math.Vector3f
import jake2.qcommon.network.messages.server.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

class BeamEffectsTest {
    private data class Segment(val kind: BeamKind, val position: Vector3, val pitch: Float, val yaw: Float, val roll: Float)
    private fun segments(beams: BeamEffects, now: Int, local: Int? = null, view: Vector3 = Vector3.Zero): List<Segment> = buildList {
        beams.submit(now, local, view, BeamSegmentSink { kind, x, y, z, pitch, yaw, roll ->
            add(Segment(kind, Vector3(x, y, z), pitch, yaw, roll))
        })
    }
    private fun cable(owner: Int = 1, end: Vector3f = Vector3f(90f, 0f, 0f)) =
        BeamTEMessage(Defines.TE_PARASITE_ATTACK, owner, Vector3f.zero, end)

    @Test fun `refresh replaces a source beam and extends its lifetime without accumulating segments`() {
        val beams = BeamEffects({})
        beams.process(cable(), 0)
        assertEquals(3, segments(beams, 0).size)
        beams.process(cable(end = Vector3f(60f, 0f, 0f)), 150)
        assertEquals(1, beams.activeCount)
        assertEquals(2, segments(beams, 200).size)
        assertTrue(segments(beams, 350).isEmpty())
        assertEquals(0, beams.activeCount)
    }

    @Test fun `lightning retains distinct destinations and issues entity weapon-channel sound requests`() {
        val sounds = mutableListOf<BeamSoundRequest>()
        val beams = BeamEffects(sounds::add)
        fun lightning(target: Int) = LightningTEMessage(7, target, Vector3f.zero, Vector3f(100f, 0f, 0f))
        beams.process(lightning(8), 0)
        beams.process(lightning(9), 0)
        beams.process(lightning(8), 100)
        assertEquals(2, beams.activeCount)
        assertEquals(3, sounds.size)
        assertTrue(sounds.all { it.path == "sound/weapons/tesla.wav" && it.entityIndex == 7 && it.channel == Defines.CHAN_WEAPON })
        segments(beams, 200)
        assertEquals(1, beams.activeCount)
        segments(beams, 300)
        assertEquals(0, beams.activeCount)
    }

    @Test fun `player-origin cables follow the current view and offset between network updates`() {
        val beams = BeamEffects({})
        beams.process(BeamOffsetTEMessage(Defines.TE_GRAPPLE_CABLE, 4, Vector3f(50f, 60f, 70f),
            Vector3f(300f, 0f, 0f), Vector3f(1f, 2f, 3f)), 0)
        assertEquals(Vector3(11f, 22f, 11f), segments(beams, 0, 4, Vector3(10f, 20f, 30f)).first().position)
        assertEquals(Vector3(21f, 32f, 21f), segments(beams, 50, 4, Vector3(20f, 30f, 40f)).first().position)
        assertEquals(Vector3(51f, 62f, 73f), segments(beams, 50).first().position)
    }

    @Test fun `short and vertical lightning and single-segment cables produce finite transforms`() {
        val beams = BeamEffects({})
        beams.process(LightningTEMessage(1, 2, Vector3f.zero, Vector3f(0f, 0f, 10f)), 0)
        val bolt = segments(beams, 0).single()
        assertEquals(Vector3(0f, 0f, 10f), bolt.position)
        assertEquals(-90f, bolt.pitch, .001f)
        assertTrue(bolt.yaw.isFinite() && bolt.roll.isFinite())
        beams.clear()
        beams.process(cable(end = Vector3f(10f, 0f, 0f)), 0)
        assertEquals(Vector3.Zero, segments(beams, 0).single().position)
        beams.process(cable(end = Vector3f.zero), 1)
        assertTrue(segments(beams, 1).isEmpty())
    }

    @Test fun `lightning geometry reverses long segments and applies its endpoint correction`() {
        val beams = BeamEffects({})
        beams.process(LightningTEMessage(1, 2, Vector3f.zero, Vector3f(100f, 0f, 0f)), 0)
        val result = segments(beams, 0)
        assertEquals(3, result.size)
        assertEquals(0f, result.first().position.x)
        assertEquals(45f, result.last().position.x)
        assertTrue(result.all { it.yaw == 180f })
    }

    @Test fun `state and geometry are bounded expired slots are reusable and clear releases all beams`() {
        val beams = BeamEffects({}, maximumBeams = 1, maximumSegmentsPerBeam = 4)
        beams.process(cable(end = Vector3f(4000f, 0f, 0f)), 0)
        beams.process(cable(owner = 2), 0)
        assertEquals(1, beams.activeCount)
        assertEquals(4, segments(beams, 0).size)
        beams.process(cable(owner = 2), 200)
        assertEquals(1, beams.activeCount)
        beams.clear()
        assertTrue(segments(beams, 200).isEmpty())
    }

    @Test fun `paused clocks retain geometry and unrelated heatbeams and invalid entities add no state`() {
        val beams = BeamEffects({}, random = Random(7))
        beams.process(LightningTEMessage(1, 2, Vector3f.zero, Vector3f(100f, 0f, 0f)), 0)
        assertEquals(segments(beams, 50), segments(beams, 50))
        beams.clear()
        assertFalse(beams.process(BeamTEMessage(Defines.TE_HEATBEAM, 1, Vector3f.zero, Vector3f.unitX), 0))
        beams.process(cable(owner = -1), 0)
        beams.process(LightningTEMessage(1, Defines.MAX_EDICTS, Vector3f.zero, Vector3f.unitX), 0)
        assertEquals(0, beams.activeCount)
    }
}
