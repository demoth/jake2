package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor
import com.badlogic.gdx.graphics.g3d.particles.ParallelArray.FloatChannel
import com.badlogic.gdx.graphics.g3d.particles.ParticleChannels
import com.badlogic.gdx.graphics.g3d.particles.ParticleController
import com.badlogic.gdx.graphics.g3d.particles.emitters.RegularEmitter
import com.badlogic.gdx.graphics.g3d.particles.influencers.Influencer
import com.badlogic.gdx.graphics.g3d.particles.renderers.PointSpriteRenderer
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable

/** Bridges evaluated libGDX particle channels to the world's particle draw pass. */
fun interface ParticleSink {
    fun submit(mode: ParticleRenderMode, blend: ParticleBlendMode,
               x: Float, y: Float, z: Float, red: Float, green: Float, blue: Float, alpha: Float, size: Float)
}

/**
 * Message-driven particles using libGDX's controller, emitter, lifetime and channel storage.
 * The caller supplies presentation time so pause and demo playback need no global clock or graphics context.
 * PointSpriteRenderer allocates standard channels here; GPU submission remains in Cake's world draw pass.
 */
internal class GdxParticleRuntime(
    private val particleBudget: () -> Int,
    initialCapacity: Int = 1024,
) : Disposable {
    private val emitter = RegularEmitter().apply {
        require(initialCapacity > 0)
        maxParticleCount = initialCapacity
        emissionMode = RegularEmitter.EmissionMode.Disabled
        duration.setLow(1f)
        life.setHigh(1000f)
    }
    private val controller = ParticleController("Cake particles", emitter, PointSpriteRenderer(), BurstInfluencer()).apply {
        init()
        start()
    }
    private val position = channel(ParticleChannels.Position)
    private val color = channel(ParticleChannels.Color)
    private val scale = channel(ParticleChannels.Scale)
    private val life = channel(ParticleChannels.Life)
    private val motion = channel(MOTION)
    private val appearance = channel(APPEARANCE)
    private var previousTimeMs: Int? = null

    val liveCount: Int get() = controller.particles.size

    /** Copies inputs; acceleration is physical units/second² and alpha interpolates over the supplied lifetime. */
    fun emit(
        position: Vector3, velocity: Vector3, acceleration: Vector3, color: Color,
        size: Float, lifetimeMillis: Int, nowMs: Int, startAlpha: Float = 1f, endAlpha: Float = 0f,
        mode: ParticleRenderMode = ParticleRenderMode.POINT_SPRITE,
        blend: ParticleBlendMode = ParticleBlendMode.ALPHA,
    ): Boolean {
        require(lifetimeMillis > 0)
        require(size.isFinite() && size > 0f && startAlpha.isFinite() && endAlpha.isFinite())
        update(nowMs)
        if (liveCount >= particleBudget().coerceAtLeast(0)) return false
        if (liveCount == controller.particles.capacity) {
            val capacity = maxOf(liveCount + 1, controller.particles.capacity * 2)
            controller.particles.setCapacity(capacity)
            emitter.maxParticleCount = capacity
        }
        val index = liveCount
        controller.activateParticles(index, 1)
        controller.particles.size++
        val p = index * this.position.strideSize
        this.position.data[p] = position.x; this.position.data[p + 1] = position.y; this.position.data[p + 2] = position.z
        val m = index * motion.strideSize
        motion.data[m] = velocity.x; motion.data[m + 1] = velocity.y; motion.data[m + 2] = velocity.z
        motion.data[m + 3] = acceleration.x; motion.data[m + 4] = acceleration.y; motion.data[m + 5] = acceleration.z
        val c = index * this.color.strideSize
        this.color.data[c] = color.r; this.color.data[c + 1] = color.g; this.color.data[c + 2] = color.b
        this.color.data[c + 3] = startAlpha.coerceIn(0f, 1f)
        scale.data[index] = size
        val l = index * life.strideSize
        life.data[l + ParticleChannels.CurrentLifeOffset] = lifetimeMillis.toFloat()
        life.data[l + ParticleChannels.TotalLifeOffset] = lifetimeMillis.toFloat()
        life.data[l + ParticleChannels.LifePercentOffset] = 0f
        val a = index * appearance.strideSize
        appearance.data[a] = startAlpha; appearance.data[a + 1] = endAlpha
        appearance.data[a + 2] = mode.id.toFloat(); appearance.data[a + 3] = blend.id.toFloat()
        return true
    }

    /** New particles are born after existing particles reach this time, avoiding a premature first-frame update. */
    fun update(nowMs: Int) {
        val previous = previousTimeMs
        previousTimeMs = nowMs
        if (previous == null || nowMs <= previous) return
        controller.update((nowMs.toLong() - previous).toFloat() / 1000f)
    }

    fun submit(nowMs: Int, sink: ParticleSink) {
        update(nowMs)
        for (index in 0 until liveCount) {
            val p = index * position.strideSize
            val c = index * color.strideSize
            val a = index * appearance.strideSize
            val alpha = color.data[c + 3]
            if (alpha <= 0f) continue
            sink.submit(ParticleRenderMode.fromId(appearance.data[a + 2].toInt().toByte()),
                ParticleBlendMode.fromId(appearance.data[a + 3].toInt().toByte()),
                position.data[p], position.data[p + 1], position.data[p + 2],
                color.data[c], color.data[c + 1], color.data[c + 2], alpha, scale.data[index])
        }
    }

    fun clear() {
        controller.end()
        controller.start()
        previousTimeMs = null
    }

    override fun dispose() {
        controller.end()
        previousTimeMs = null
        controller.dispose()
    }

    private fun channel(descriptor: ChannelDescriptor): FloatChannel = controller.particles.getChannel(descriptor)

    /**
     * Burst messages supply individual velocities, accelerations and palette colors.
     * This adapter advances those values and fades alpha from the emitter's normalized life;
     * libGDX owns particle expiry and moves every channel together when particles are removed.
     */
    private class BurstInfluencer : Influencer() {
        private lateinit var position: FloatChannel
        private lateinit var motion: FloatChannel
        private lateinit var color: FloatChannel
        private lateinit var appearance: FloatChannel
        private lateinit var life: FloatChannel
        private val point = Vector3()
        private val velocity = Vector3()
        private val acceleration = Vector3()

        override fun allocateChannels() {
            position = controller.particles.addChannel(ParticleChannels.Position)
            color = controller.particles.addChannel(ParticleChannels.Color)
            life = controller.particles.addChannel(ParticleChannels.Life)
            motion = controller.particles.addChannel(MOTION)
            appearance = controller.particles.addChannel(APPEARANCE)
        }

        override fun update() {
            val delta = controller.deltaTime
            for (index in 0 until controller.particles.size) {
                val p = index * position.strideSize
                val m = index * motion.strideSize
                point.set(position.data[p], position.data[p + 1], position.data[p + 2])
                velocity.set(motion.data[m], motion.data[m + 1], motion.data[m + 2])
                acceleration.set(motion.data[m + 3], motion.data[m + 4], motion.data[m + 5])
                point.mulAdd(velocity, delta).mulAdd(acceleration, .5f * delta * delta)
                velocity.mulAdd(acceleration, delta)
                position.data[p] = point.x; position.data[p + 1] = point.y; position.data[p + 2] = point.z
                motion.data[m] = velocity.x; motion.data[m + 1] = velocity.y; motion.data[m + 2] = velocity.z
                val a = index * appearance.strideSize
                val progress = life.data[index * life.strideSize + ParticleChannels.LifePercentOffset]
                color.data[index * color.strideSize + ParticleChannels.AlphaOffset] =
                    (appearance.data[a] + (appearance.data[a + 1] - appearance.data[a]) * progress).coerceIn(0f, 1f)
            }
        }

        override fun copy(): Influencer = BurstInfluencer()
    }

    companion object {
        private val MOTION = ChannelDescriptor(ParticleChannels.newGlobalId(), Float::class.javaPrimitiveType, 6)
        private val APPEARANCE = ChannelDescriptor(ParticleChannels.newGlobalId(), Float::class.javaPrimitiveType, 4)
    }
}
