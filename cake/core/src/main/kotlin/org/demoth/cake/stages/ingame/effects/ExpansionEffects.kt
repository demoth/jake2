package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import jake2.qcommon.Defines
import jake2.qcommon.math.Vector3f
import jake2.qcommon.network.messages.server.*
import org.demoth.cake.stages.ingame.DynamicLightSystem
import kotlin.math.ceil
import kotlin.random.Random

/** One-shot expansion effects: CL_Flashlight, CL_ForceWall and CL_BubbleTrail2. */
internal class ExpansionEffects(
    private val particles: GdxParticleRuntime,
    private val paletteColor: (Int) -> Color,
    private val lights: DynamicLightSystem?,
    private val playBubbleSound: (Vector3f) -> Unit,
    private val random: Random = Random.Default,
    private val maximumTrailPoints: Int = 1024,
) {
    init { require(maximumTrailPoints > 0) }
    private val origin = Vector3()
    private val direction = Vector3()
    private val position = Vector3()
    private val velocity = Vector3()

    fun process(message: TEMessage, nowMs: Int): Boolean {
        when (message) {
            is FlashlightTEMessage -> {
                if (message.entityIndex in 0 until Defines.MAX_EDICTS) {
                    lights?.spawnTransientLight(message.entityIndex, vector(message.position), 400f, 1f, 1f, 1f,
                        lifetimeMs = 100, currentTimeMs = nowMs)
                }
            }
            is ForceWallTEMessage -> emitTrail(message.origin, message.destination, wallColor = message.color, nowMs = nowMs)
            is TrailTEMessage -> {
                if (message.style != Defines.TE_BUBBLETRAIL2) return false
                emitTrail(message.position, message.destination, wallColor = null, nowMs = nowMs)
                playBubbleSound(message.position)
            }
            else -> return false
        }
        return true
    }

    private fun emitTrail(start: Vector3f, end: Vector3f, wallColor: Int?, nowMs: Int) {
        origin.set(start.x, start.y, start.z)
        direction.set(end.x, end.y, end.z).sub(origin)
        val length = direction.len()
        if (length < .001f) return
        direction.scl(1f / length)
        val spacing = if (wallColor != null) 4f else 8f
        val count = ceil(length / spacing).toInt().coerceIn(1, maximumTrailPoints)
        val step = length / count
        repeat(count) { index ->
            if (wallColor != null && random.nextFloat() < .3f) return@repeat
            val jitter = if (wallColor != null) 3f else 2f
            position.set(origin).mulAdd(direction, index * step)
                .add(signedRandom() * jitter, signedRandom() * jitter, signedRandom() * jitter)
            val color: Int
            val lifetime: Int
            if (wallColor != null) {
                velocity.set(0f, 0f, -40f + signedRandom() * 10f)
                color = wallColor
                lifetime = random.nextInt(3000, 3501)
            } else {
                position.z -= 4f
                velocity.set(signedRandom() * 10f, signedRandom() * 10f, 20f + signedRandom() * 10f)
                color = 4 + random.nextInt(8)
                lifetime = random.nextInt(1000, 1101)
            }
            if (!particles.emit(position, velocity, Vector3.Zero, paletteColor(color),
                    size = if (wallColor != null) .6f else .4f, lifetimeMillis = lifetime, nowMs = nowMs)) return
        }
    }

    private fun signedRandom(): Float = random.nextFloat() * 2f - 1f
    private fun vector(value: Vector3f) = position.set(value.x, value.y, value.z)
}
