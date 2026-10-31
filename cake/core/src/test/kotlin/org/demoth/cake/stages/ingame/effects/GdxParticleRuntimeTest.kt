package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Engine behavior with the real libGDX controller/emitter; no GL, Cvars, globals or assets. */
class GdxParticleRuntimeTest {
    private data class Sample(val mode: ParticleRenderMode, val blend: ParticleBlendMode,
                              val position: Vector3, val color: Color, val size: Float)

    private fun samples(runtime: GdxParticleRuntime, nowMs: Int): List<Sample> = buildList {
        runtime.submit(nowMs, ParticleSink { mode, blend, x, y, z, red, green, blue, alpha, size ->
            add(Sample(mode, blend, Vector3(x, y, z), Color(red, green, blue, alpha), size))
        })
    }

    private fun movingParticle() = GdxParticleRuntime({ 10 }).apply {
        emit(Vector3(1f, 2f, 3f), Vector3(4f, -2f, 8f), Vector3(2f, 3f, -4f), Color.WHITE,
            size = 1f, lifetimeMillis = 2000, nowMs = 1000)
    }

    @Test fun `explicit motion acts on every axis and fades over the emitter lifetime`() {
        val runtime = movingParticle()
        try {
            val result = samples(runtime, 1500).single()
            assertEquals(Vector3(3.25f, 1.375f, 6.5f), result.position)
            assertEquals(.75f, result.color.a, .00001f)
        } finally { runtime.dispose() }
    }

    @Test fun `update frequency does not materially change motion and a paused clock freezes it`() {
        val singleStep = movingParticle()
        val manySteps = movingParticle()
        try {
            singleStep.update(2000)
            for (now in 1010..2000 step 10) manySteps.update(now)
            val expected = samples(singleStep, 2000).single()
            val actual = samples(manySteps, 2000).single()
            assertTrue(expected.position.epsilonEquals(actual.position, .001f))
            assertEquals(expected.color.a, actual.color.a, .0001f)
            repeat(100) { manySteps.update(2000) }
            assertEquals(actual, samples(manySteps, 2000).single())
        } finally { singleStep.dispose(); manySteps.dispose() }
    }

    @Test fun `particles are not aged before birth and short pulses survive their first frame`() {
        val runtime = GdxParticleRuntime({ 10 })
        try {
            runtime.update(0)
            runtime.emit(Vector3.Zero, Vector3.Zero, Vector3.Zero, Color.RED,
                size = 1f, lifetimeMillis = 1, nowMs = 1000)
            runtime.update(1000)
            assertEquals(1, samples(runtime, 1000).size)
            assertTrue(samples(runtime, 1001).isEmpty())
            assertEquals(0, runtime.liveCount)
        } finally { runtime.dispose() }
    }

    @Test fun `existing particles reach the emission time before a new particle is added`() {
        val runtime = GdxParticleRuntime({ 10 })
        try {
            runtime.emit(Vector3.Zero, Vector3.X, Vector3.Zero, Color.RED, 1f, 1000, 0)
            runtime.emit(Vector3.Zero, Vector3.X, Vector3.Zero, Color.BLUE, 1f, 1000, 500)
            val particles = samples(runtime, 500).sortedBy { it.position.x }
            assertEquals(0f, particles[0].position.x)
            assertEquals(1f, particles[0].color.a)
            assertEquals(.5f, particles[1].position.x, .00001f)
            assertEquals(.5f, particles[1].color.a, .00001f)
            runtime.update(1000)
            assertEquals(1, runtime.liveCount)
            assertEquals(Color.BLUE.b, samples(runtime, 1000).single().color.b)
        } finally { runtime.dispose() }
    }

    @Test fun `lifetime expiry is independent of final opacity`() {
        val runtime = GdxParticleRuntime({ 10 })
        try {
            runtime.emit(Vector3.Zero, Vector3.Zero, Vector3.Zero, Color.WHITE,
                1f, 100, 0, startAlpha = .2f, endAlpha = .8f)
            assertEquals(.5f, samples(runtime, 50).single().color.a, .00001f)
            assertEquals(1, samples(runtime, 99).size)
            assertTrue(samples(runtime, 100).isEmpty())
        } finally { runtime.dispose() }
    }

    @Test fun `budgets reject excess emission without destroying particles when reduced`() {
        var budget = 2
        val runtime = GdxParticleRuntime({ budget })
        try {
            fun emit(nowMs: Int) = runtime.emit(Vector3.Zero, Vector3.Zero, Vector3.Zero, Color.WHITE, 1f, 100, nowMs)
            assertTrue(emit(0)); assertTrue(emit(0)); assertFalse(emit(0))
            budget = 1
            assertFalse(emit(0))
            assertEquals(2, runtime.liveCount)
            assertTrue(emit(100)) // libGDX expiry frees space before the budget check.
            budget = 0
            assertFalse(emit(100))
            assertEquals(1, runtime.liveCount)
            runtime.update(200)
            assertEquals(0, runtime.liveCount)
        } finally { runtime.dispose() }
    }

    @Test fun `channel growth and removal retain copied motion colors sizes and rendering modes`() {
        val runtime = GdxParticleRuntime({ 10 }, initialCapacity = 1)
        try {
            val position = Vector3()
            val velocity = Vector3()
            val acceleration = Vector3()
            val color = Color()
            repeat(6) { id ->
                runtime.emit(position.set(id.toFloat(), 0f, 0f), velocity.set(id.toFloat(), 0f, 0f),
                    acceleration.set(0f, id.toFloat(), 0f), color.set(id / 10f, .2f, .3f, 1f),
                    size = id + 1f, lifetimeMillis = if (id % 2 == 0) 10 else 1000, nowMs = 0,
                    startAlpha = .8f, endAlpha = .2f,
                    mode = ParticleRenderMode.BILLBOARD_SPRITE, blend = ParticleBlendMode.ADDITIVE)
            }
            position.setZero(); velocity.setZero(); acceleration.setZero(); color.set(Color.BLACK)
            val survivors = samples(runtime, 500).sortedBy { it.size }
            assertEquals(listOf(2f, 4f, 6f), survivors.map { it.size })
            for ((sample, id) in survivors.zip(listOf(1, 3, 5))) {
                assertEquals(ParticleRenderMode.BILLBOARD_SPRITE, sample.mode)
                assertEquals(ParticleBlendMode.ADDITIVE, sample.blend)
                assertEquals(id / 10f, sample.color.r)
                assertEquals(.2f, sample.color.g)
                assertEquals(.3f, sample.color.b)
                assertEquals(.5f, sample.color.a, .00001f)
                assertEquals(Vector3(id * 1.5f, id * .125f, 0f), sample.position)
            }
            runtime.update(1000)
            assertEquals(0, runtime.liveCount)
        } finally { runtime.dispose() }
    }

    @Test fun `clear removes pending state and permits a new playback clock`() {
        val runtime = movingParticle()
        try {
            runtime.update(1500)
            runtime.clear()
            assertEquals(0, runtime.liveCount)
            runtime.emit(Vector3.Zero, Vector3.X, Vector3.Zero, Color.WHITE, 1f, 1000, 0)
            assertEquals(.5f, samples(runtime, 500).single().position.x, .00001f)
        } finally { runtime.dispose() }
        assertEquals(0, runtime.liveCount)
    }
}
