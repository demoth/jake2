package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.GdxNativesLoader
import jake2.qcommon.Defines
import jake2.qcommon.math.Vector3f
import jake2.qcommon.network.messages.server.BeamTEMessage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class HeatbeamEffectsTest {
    private data class Segment(val kind: BeamKind, val origin: Vector3, val pitch: Float, val yaw: Float, val roll: Float)
    private fun camera(fov: Float = 90f) = PerspectiveCamera(fov, 800f, 600f).apply {
        direction.set(1f, 0f, 0f); up.set(0f, 0f, 1f); update()
    }
    private fun message(owner: Int = 1, monster: Boolean = false, end: Vector3f = Vector3f(100f, 0f, 0f)) =
        BeamTEMessage(if (monster) Defines.TE_MONSTER_HEATBEAM else Defines.TE_HEATBEAM,
            owner, Vector3f.zero, end)
    private fun segments(effect: HeatbeamEffects, now: Int, local: Int? = null, view: PerspectiveCamera = camera(),
                         offset: Vector3 = Vector3.Zero, hand: Int = 0): List<Segment> = buildList {
        effect.submit(now, local, view, offset, hand, BeamSegmentSink { kind, x, y, z, pitch, yaw, roll ->
            add(Segment(kind, Vector3(x, y, z), pitch, yaw, roll))
        })
    }
    private fun effect() = HeatbeamEffects(HeatbeamParticleSink { _, _, _, _, _, _, _ -> })

    @Test fun `local muzzle follows camera gun offsets and handedness without needing another message`() {
        val effect = effect()
        effect.process(message(), 0)
        val view = camera().apply { position.set(10f, 20f, 30f) }
        val gun = Vector3(1f, 2f, 3f)
        assertEquals(Vector3(18f, 20f, 30f), segments(effect, 0, 1, view, gun).first().origin)
        assertEquals(Vector3(18f, 24f, 30f), segments(effect, 0, 1, view, gun, 1).first().origin)
        assertEquals(Vector3(18f, 22f, 29f), segments(effect, 0, 1, view, gun, 2).first().origin)
        view.position.add(10f, 0f, 0f)
        assertEquals(Vector3(28f, 20f, 30f), segments(effect, 50, 1, view, gun).first().origin)
        view.direction.set(0f, 1f, 0f)
        assertEquals(Vector3(23f, 29f, 30f), segments(effect, 100, 1, view, gun).first().origin)
    }

    @Test fun `monster and remote-player beams use recorded origins and the remote model frame`() {
        val effect = effect()
        effect.process(message(monster = true), 0)
        val monster = segments(effect, 0, 1).first()
        assertEquals(Vector3.Zero, monster.origin)
        assertEquals(BeamKind.HEATBEAM_MONSTER, monster.kind)
        effect.process(message(owner = 2), 0)
        assertTrue(segments(effect, 0, 1).all { it.kind == BeamKind.HEATBEAM_MONSTER })
    }

    @Test fun `muzzle uses the active camera projection across field of view changes`() {
        val effect = effect()
        effect.process(message(), 0)
        val projections = listOf(60f, 90f, 120f).map { fov ->
            val view = camera(fov)
            val first = segments(effect, 0, 1, view).first()
            assertEquals(BeamKind.HEATBEAM_PLAYER, first.kind)
            assertEquals(Vector3(7f, -2f, -3f), first.origin)
            view.project(first.origin.cpy(), 0f, 0f, 800f, 600f).x
        }
        assertTrue(projections.all { it > 400f })
        assertTrue(projections[0] > projections[1] && projections[1] > projections[2])
    }

    @Test fun `refresh and pause keep one beam and rate-limit particle pulses`() {
        var pulses = 0
        val effect = HeatbeamEffects(HeatbeamParticleSink { _, _, _, _, _, _, _ -> pulses++ })
        effect.process(message(), 0)
        val initial = segments(effect, 0)
        repeat(30) { assertEquals(initial, segments(effect, 0)) }
        assertEquals(1, pulses)
        effect.process(message(end = Vector3f(200f, 0f, 0f)), 0)
        segments(effect, 0)
        assertEquals(1, pulses)
        segments(effect, 50)
        assertEquals(2, pulses)
        effect.process(message(), 150)
        assertEquals(1, effect.activeCount)
        assertTrue(segments(effect, 200).isNotEmpty())
        assertTrue(segments(effect, 350).isEmpty())
    }

    @Test fun `short vertical and zero length beams stay finite and state and geometry stay bounded`() {
        val effect = HeatbeamEffects(HeatbeamParticleSink { _, _, _, _, _, _, _ -> }, maximumBeams = 1, maximumSegmentsPerBeam = 4)
        effect.process(message(end = Vector3f(0f, 0f, 10f)), 0)
        val short = segments(effect, 0).single()
        assertEquals(90f, short.pitch, .001f)
        assertTrue(short.yaw.isFinite() && short.roll.isFinite())
        effect.process(message(end = Vector3f.zero), 0)
        assertTrue(segments(effect, 0).isEmpty())
        effect.process(message(end = Vector3f(4000f, 0f, 0f)), 0)
        effect.process(message(owner = 2), 0)
        assertEquals(1, effect.activeCount)
        assertEquals(4, segments(effect, 0).size)
        effect.clear()
        assertEquals(0, effect.activeCount)
        assertTrue(segments(effect, 0).isEmpty())
    }

    @Test fun `the real particle runtime stays bounded during refresh pause and teardown`() {
        val runtime = GdxParticleRuntime({ 1000 })
        try {
            val effect = HeatbeamEffects(HeatbeamParticles(runtime, { com.badlogic.gdx.graphics.Color.WHITE }))
            effect.process(message(), 0)
            val view = camera()
            segments(effect, 0, 1, view)
            val firstCount = runtime.liveCount
            assertTrue(firstCount in 1..100)
            repeat(100) { segments(effect, 0, 1, view) }
            assertEquals(firstCount, runtime.liveCount)
            segments(effect, 50, 1, view)
            assertTrue(runtime.liveCount <= 200)
            effect.process(message(), 50)
            repeat(100) { segments(effect, 50, 1, view) }
            assertTrue(runtime.liveCount <= 200)
            effect.clear()
            segments(effect, 150, 1, view)
            runtime.update(150)
            assertEquals(0, effect.activeCount)
            assertEquals(0, runtime.liveCount)
        } finally { runtime.dispose() }
    }

    companion object { @BeforeAll @JvmStatic fun loadMathNatives() { GdxNativesLoader.load() } }
}
