package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import jake2.qcommon.Defines
import jake2.qcommon.network.messages.server.PointTEMessage
import kotlin.random.Random

/** Particle components of CL_BigTeleportParticles and CL_ColorExplosionParticles, backed by libGDX. */
internal class PointParticles(
    private val particles: GdxParticleRuntime,
    private val paletteColor: (Int) -> Color,
    private val random: Random = Random.Default,
) {
    private val position = Vector3()
    private val velocity = Vector3()
    private val acceleration = Vector3()
    private val teleportPalette = intArrayOf(16, 104, 168, 144)

    fun process(message: PointTEMessage, nowMs: Int): Boolean {
        val teleport = when (message.style) {
            Defines.TE_BOSSTPORT -> true
            Defines.TE_TRACKER_EXPLOSION -> false
            else -> return false
        }
        repeat(if (teleport) 768 else 128) {
            val color: Int
            val lifetime: Int
            if (teleport) {
                val angle = random.nextFloat() * MathUtils.PI2
                val x = MathUtils.cos(angle); val y = MathUtils.sin(angle)
                val radius = random.nextFloat() * 32f
                position.set(message.position.x + x * radius, message.position.y + y * radius,
                    message.position.z + 8f + random.nextFloat() * 90f)
                velocity.set(x * (70f + random.nextFloat() * 63f), y * (70f + random.nextFloat() * 63f),
                    -100f + random.nextFloat() * 31f)
                acceleration.set(-x * 100f, -y * 100f, 160f)
                color = teleportPalette[random.nextInt(teleportPalette.size)]
                lifetime = random.nextInt(1600, 2601)
            } else {
                position.set(message.position.x + signedRandom() * 16f, message.position.y + signedRandom() * 16f,
                    message.position.z + signedRandom() * 16f)
                velocity.set(signedRandom() * 128f, signedRandom() * 128f, signedRandom() * 128f)
                acceleration.set(0f, 0f, -80f)
                color = 0
                lifetime = random.nextInt(1500, 2001)
            }
            if (!particles.emit(position, velocity, acceleration, paletteColor(color), size = .6f,
                    lifetimeMillis = lifetime, nowMs = nowMs)) return true
        }
        return true
    }

    private fun signedRandom() = random.nextFloat() * 2f - 1f
}
