package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import jake2.qcommon.Globals
import org.demoth.cake.stages.ingame.RenderTuningCvars
import kotlin.math.max

/**
 * Lightweight world-space particle runtime used by temp effects.
 *
 * Implementation notes:
 * - data-oriented simulation storage (no per-particle object allocations),
 * - render backend is decoupled and handled by [ParticleRenderer],
 * - supports multiple render/blend modes for future sprite extension.
 */
class EffectParticleSystem : Disposable {
    private var activeCount = 0
    private var capacity = INITIAL_CAPACITY
    private var posX = FloatArray(capacity)
    private var posY = FloatArray(capacity)
    private var posZ = FloatArray(capacity)
    private var velX = FloatArray(capacity)
    private var velY = FloatArray(capacity)
    private var velZ = FloatArray(capacity)
    private var spawnTimeMs = IntArray(capacity)
    private var lifetimeMs = IntArray(capacity)
    private var startAlpha = FloatArray(capacity)
    private var endAlpha = FloatArray(capacity)
    private var size = FloatArray(capacity)
    private var gravity = FloatArray(capacity)
    private var colorR = FloatArray(capacity)
    private var colorG = FloatArray(capacity)
    private var colorB = FloatArray(capacity)
    private var renderMode = ByteArray(capacity)
    private var blendMode = ByteArray(capacity)
    private val renderer = ParticleRenderer()

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
        repeat(max(0, count)) {
            ensureCapacity(activeCount + 1)
            val particleIndex = activeCount++
            val directionVector = randomDirection(direction, spread)
            val speed = randomFloat(speedMin, speedMax)
            val lifeMs = randomInt(lifetimeMinMs, lifetimeMaxMs)

            posX[particleIndex] = origin.x
            posY[particleIndex] = origin.y
            posZ[particleIndex] = origin.z
            velX[particleIndex] = directionVector.x * speed
            velY[particleIndex] = directionVector.y * speed
            velZ[particleIndex] = directionVector.z * speed
            spawnTimeMs[particleIndex] = Globals.curtime
            lifetimeMs[particleIndex] = max(1, lifeMs)
            this.startAlpha[particleIndex] = startAlpha
            this.endAlpha[particleIndex] = endAlpha
            size[particleIndex] = randomFloat(sizeMin, sizeMax).coerceAtLeast(0.1f)
            this.gravity[particleIndex] = gravity
            colorR[particleIndex] = color.r
            colorG[particleIndex] = color.g
            colorB[particleIndex] = color.b
            this.renderMode[particleIndex] = renderMode.id
            this.blendMode[particleIndex] = blendMode.id
        }
    }

    fun update(deltaSeconds: Float, nowMs: Int) {
        if (activeCount == 0) {
            return
        }
        var index = 0
        while (index < activeCount) {
            val elapsedMs = nowMs - spawnTimeMs[index]
            if (elapsedMs >= lifetimeMs[index]) {
                removeAt(index)
                continue
            }
            velZ[index] += gravity[index] * deltaSeconds
            posX[index] += velX[index] * deltaSeconds
            posY[index] += velY[index] * deltaSeconds
            posZ[index] += velZ[index] * deltaSeconds
            index++
        }
    }

    fun render(camera: Camera) {
        if (!RenderTuningCvars.particlesEnabled()) {
            return
        }
        if (activeCount == 0) {
            return
        }
        val nowMs = Globals.curtime
        renderer.begin(camera)
        var index = 0
        while (index < activeCount) {
            val life = ((nowMs - spawnTimeMs[index]).toFloat() / lifetimeMs[index].toFloat()).coerceIn(0f, 1f)
            val alpha = startAlpha[index] + (endAlpha[index] - startAlpha[index]) * life
            if (alpha <= 0f) {
                index++
                continue
            }
            renderer.submit(
                mode = ParticleRenderMode.fromId(renderMode[index]),
                blend = ParticleBlendMode.fromId(blendMode[index]),
                x = posX[index],
                y = posY[index],
                z = posZ[index],
                red = colorR[index],
                green = colorG[index],
                blue = colorB[index],
                alpha = alpha,
                size = size[index],
            )
            index++
        }
        renderer.flush()
    }

    override fun dispose() {
        activeCount = 0
        renderer.dispose()
    }

    private fun ensureCapacity(requiredCapacity: Int) {
        if (requiredCapacity <= capacity) {
            return
        }
        var newCapacity = capacity
        while (newCapacity < requiredCapacity) {
            newCapacity = newCapacity shl 1
        }
        posX = posX.copyOf(newCapacity)
        posY = posY.copyOf(newCapacity)
        posZ = posZ.copyOf(newCapacity)
        velX = velX.copyOf(newCapacity)
        velY = velY.copyOf(newCapacity)
        velZ = velZ.copyOf(newCapacity)
        spawnTimeMs = spawnTimeMs.copyOf(newCapacity)
        lifetimeMs = lifetimeMs.copyOf(newCapacity)
        startAlpha = startAlpha.copyOf(newCapacity)
        endAlpha = endAlpha.copyOf(newCapacity)
        size = size.copyOf(newCapacity)
        gravity = gravity.copyOf(newCapacity)
        colorR = colorR.copyOf(newCapacity)
        colorG = colorG.copyOf(newCapacity)
        colorB = colorB.copyOf(newCapacity)
        renderMode = renderMode.copyOf(newCapacity)
        blendMode = blendMode.copyOf(newCapacity)
        capacity = newCapacity
    }

    private fun removeAt(index: Int) {
        val last = activeCount - 1
        if (index != last) {
            posX[index] = posX[last]
            posY[index] = posY[last]
            posZ[index] = posZ[last]
            velX[index] = velX[last]
            velY[index] = velY[last]
            velZ[index] = velZ[last]
            spawnTimeMs[index] = spawnTimeMs[last]
            lifetimeMs[index] = lifetimeMs[last]
            startAlpha[index] = startAlpha[last]
            endAlpha[index] = endAlpha[last]
            size[index] = size[last]
            gravity[index] = gravity[last]
            colorR[index] = colorR[last]
            colorG[index] = colorG[last]
            colorB[index] = colorB[last]
            renderMode[index] = renderMode[last]
            blendMode[index] = blendMode[last]
        }
        activeCount = last
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

    companion object {
        private const val INITIAL_CAPACITY = 1024
        private val tempDirection = Vector3()
    }
}

enum class ParticleRenderMode(val id: Byte) {
    POINT_SPRITE(0),
    BILLBOARD_SPRITE(1);

    companion object {
        fun fromId(id: Byte): ParticleRenderMode {
            return if (id == BILLBOARD_SPRITE.id) BILLBOARD_SPRITE else POINT_SPRITE
        }
    }
}

enum class ParticleBlendMode(val id: Byte) {
    ALPHA(0),
    ADDITIVE(1);

    companion object {
        fun fromId(id: Byte): ParticleBlendMode {
            return if (id == ADDITIVE.id) ADDITIVE else ALPHA
        }
    }
}
