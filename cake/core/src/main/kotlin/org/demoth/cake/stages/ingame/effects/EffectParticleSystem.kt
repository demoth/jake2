package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import jake2.qcommon.Globals
import org.demoth.cake.stages.ingame.RenderTuningCvars
import kotlin.math.max
import kotlin.math.min

/**
 * Lightweight world-space particle simulation/runtime used by temp effects.
 *
 * Decision summary:
 * - This facade maps effect emissions to a libGDX particle controller, independently of `ModelBatch`.
 * - Rendering is delegated to [ParticleRenderer] in a dedicated pass to keep draw calls bounded.
 * - libGDX particle channels own storage and keep emission free of per-particle object allocations.
 *
 * Practical goals:
 * - preserve legacy effect readability and per-effect color correctness,
 * - keep the pipeline ready for sprite billboards without changing emitter call sites,
 * - favor visual parity and performance over temporary intermediate fixes.
 *
 * Notes:
 * - libGDX removes particles by swapping channel entries, so particle iteration order is not stable.
 * - live particles are capped by the validated `r_particles` budget; overflow spawns are dropped.
 */
class EffectParticleSystem(
    assetManager: AssetManager,
    private val currentTimeMsProvider: () -> Int = { Globals.curtime },
) : Disposable {
    internal val runtime = GdxParticleRuntime(RenderTuningCvars::particleBudget)
    private val renderer = ParticleRenderer(assetManager)
    private val tempDirection = Vector3()
    private val tempVelocity = Vector3()
    private val tempAcceleration = Vector3()

    /** Emits explicit world-space state through libGDX's particle lifetime and storage runtime. */
    fun emit(
        position: Vector3, velocity: Vector3, acceleration: Vector3, color: Color,
        size: Float, lifetimeMillis: Int, startAlpha: Float = 1f, endAlpha: Float = 0f,
        renderMode: ParticleRenderMode = ParticleRenderMode.POINT_SPRITE,
        blendMode: ParticleBlendMode = ParticleBlendMode.ALPHA,
    ): Boolean = runtime.emit(position, velocity, acceleration, color, size, lifetimeMillis,
        currentTimeMsProvider(), startAlpha, endAlpha, renderMode, blendMode)

    /**
     * Emits a burst of particles with shared spawn parameters.
     *
     * Spawn behavior is intentionally simple and stateless:
     * - each particle samples random direction/speed/lifetime/size in caller-provided ranges,
     * - color/render mode/blend mode are copied per particle and consumed by [render].
     *
     * The method is hot-path safe:
     * - no per-particle object allocations,
     * - dynamic growth only when capacity is exhausted.
     */
    fun emitBurst(
        origin: Vector3,
        direction: FloatArray?,
        count: Int,
        color: Color,
        speedMin: Float,
        speedMax: Float,
        spread: Float,
        gravity: Float,
        startAlpha: Float,
        endAlpha: Float,
        sizeMin: Float,
        sizeMax: Float,
        lifetimeMinMs: Int,
        lifetimeMaxMs: Int,
        renderMode: ParticleRenderMode = ParticleRenderMode.POINT_SPRITE,
        blendMode: ParticleBlendMode = ParticleBlendMode.ALPHA,
    ) {
        if (!RenderTuningCvars.particlesEnabled()) {
            return
        }
        val requestedCount = max(0, count)
        if (requestedCount == 0) {
            return
        }

        val nowMs = currentTimeMsProvider()
        runtime.update(nowMs)
        val remainingBudget = RenderTuningCvars.particleBudget() - runtime.liveCount
        if (remainingBudget <= 0) return
        val spawnCount = min(requestedCount, remainingBudget)
        tempAcceleration.set(0f, 0f, gravity)
        repeat(spawnCount) {
            val directionVector = randomDirection(direction, spread)
            val speed = randomFloat(speedMin, speedMax)
            val lifeMs = max(1, randomInt(lifetimeMinMs, lifetimeMaxMs))
            tempVelocity.set(directionVector).scl(speed)
            runtime.emit(
                position = origin, velocity = tempVelocity, acceleration = tempAcceleration, color = color,
                size = randomFloat(sizeMin, sizeMax).coerceAtLeast(.1f),
                lifetimeMillis = lifeMs, nowMs = nowMs, startAlpha = startAlpha, endAlpha = endAlpha,
                mode = renderMode, blend = blendMode,
            )
        }
    }

    /**
     * Advances simulation for all live particles.
     *
     * libGDX handles lifetime and removal; the presentation clock also freezes particle motion when paused.
     */
    fun update(nowMs: Int) {
        runtime.update(nowMs)
    }

    /**
     * Submits all visible particles to the dedicated particle renderer.
     *
     * Rendering policy:
     * - libGDX normalized lifetime drives the caller-provided alpha ramp,
     * - fully transparent particles are skipped,
     * - render/blend mode routing is delegated to [ParticleRenderer].
     */
    fun render(camera: Camera) {
        if (!RenderTuningCvars.particlesEnabled()) {
            return
        }
        if (runtime.liveCount == 0) return
        renderer.begin(camera)
        runtime.submit(currentTimeMsProvider(), renderer)
        renderer.flush()
    }

    fun liveParticleCount(): Int = runtime.liveCount

    fun lastRenderStats(): ParticleRenderer.Stats = renderer.lastStats

    override fun dispose() {
        runtime.dispose()
        renderer.dispose()
    }

    private fun randomDirection(direction: FloatArray?, spread: Float): Vector3 {
        val base = if (direction != null && direction.size >= 3) {
            tempDirection.set(direction[0], direction[1], direction[2]).nor()
        } else {
            tempDirection.set(0f, 0f, 1f)
        }
        base.x += randomFloat(-spread, spread)
        base.y += randomFloat(-spread, spread)
        base.z += randomFloat(-spread, spread)
        if (base.isZero) {
            base.set(0f, 0f, 1f)
        }
        return base.nor()
    }

    private fun randomFloat(min: Float, max: Float): Float {
        if (min >= max) {
            return min
        }
        return min + Globals.rnd.nextFloat() * (max - min)
    }

    private fun randomInt(min: Int, max: Int): Int {
        if (min >= max) {
            return min
        }
        return min + Globals.rnd.nextInt(max - min + 1)
    }

}

/**
 * Particle primitive family.
 *
 * `POINT_SPRITE` is the active production path.
 * `BILLBOARD_SPRITE` is kept as a first-class mode to support sprite-backed particles without emitter API churn.
 */
enum class ParticleRenderMode(val id: Byte) {
    POINT_SPRITE(0),
    BILLBOARD_SPRITE(1);

    companion object {
        fun fromId(id: Byte): ParticleRenderMode {
            return if (id == BILLBOARD_SPRITE.id) BILLBOARD_SPRITE else POINT_SPRITE
        }
    }
}

/**
 * Blending behavior for particle submission buckets.
 *
 * Buckets are submitted unsorted for simpler legacy-style behavior.
 */
enum class ParticleBlendMode(val id: Byte) {
    ALPHA(0),
    ADDITIVE(1);

    companion object {
        fun fromId(id: Byte): ParticleBlendMode {
            return if (id == ADDITIVE.id) ADDITIVE else ALPHA
        }
    }
}
