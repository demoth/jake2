package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import jake2.qcommon.Defines
import jake2.qcommon.math.Vector3f
import jake2.qcommon.network.messages.server.PointDirectionTEMessage
import jake2.qcommon.network.messages.server.SteamTEMessage
import kotlin.math.abs
import kotlin.random.Random

/**
 * Steam bursts and their message-owned sustain state (CL_ParseSteam / CL_ParticleSteamEffect).
 * libGDX owns the resulting particles; clearing this owner stops future bursts without removing particles
 * which are already fading. The outer effects system disposes the shared particle runtime at teardown.
 */
internal class SteamEffects(
    private val particles: GdxParticleRuntime,
    private val paletteColor: (Int) -> Color,
    private val random: Random = Random.Default,
    maximumSustains: Int = 32,
) {
    private val sustains = SustainScheduler<SteamTEMessage>(maximumSustains)
    private val direction = Vector3()
    private val right = Vector3()
    private val up = Vector3()
    private val position = Vector3()
    private val velocity = Vector3()
    private val acceleration = Vector3(0f, 0f, -40f)
    val activeSustainCount: Int get() = sustains.activeCount

    fun process(message: SteamTEMessage, nowMs: Int) {
        if (message.id == -1) {
            emitBurst(message.position, message.direction, message.count, message.color, message.magnitude, nowMs)
        } else {
            sustains.start(message.id, message, nowMs, checkNotNull(message.durationMillis), intervalMillis = 100)
        }
    }

    /** Heatbeam impact steam and sparks use the steam recipe, and their existing impact sound is played by the caller. */
    fun processImpact(message: PointDirectionTEMessage, nowMs: Int): Boolean {
        val (count, color) = when (message.style) {
            Defines.TE_HEATBEAM_SPARKS -> 50 to 0x08
            Defines.TE_HEATBEAM_STEAM -> 20 to 0xE0
            else -> return false
        }
        emitBurst(message.position, message.direction, count, color, magnitude = 60, nowMs = nowMs)
        return true
    }

    fun update(nowMs: Int) {
        sustains.update(nowMs) { message ->
            emitBurst(message.position, message.direction, message.count, message.color, message.magnitude, nowMs)
        }
    }

    fun clear() = sustains.clear()

    private fun emitBurst(origin: Vector3f, normal: Vector3f, count: Int, color: Int, magnitude: Int, nowMs: Int) {
        if (count == 0) return
        direction.set(normal.x, normal.y, normal.z).nor()
        // Build a stable transverse basis with libGDX vectors, including directions parallel to the vertical axis.
        right.set(direction).crs(if (abs(direction.z) < .9f) Vector3.Z else Vector3.X).nor()
        up.set(right).crs(direction).nor()
        val speed = magnitude.toFloat()
        val spread = abs(speed) / 3f
        val jitter = abs(speed) * .1f
        repeat(count) {
            position.set(origin.x + signedRandom() * jitter, origin.y + signedRandom() * jitter, origin.z + signedRandom() * jitter)
            velocity.set(direction).scl(speed).mulAdd(right, signedRandom() * spread).mulAdd(up, signedRandom() * spread)
            val emitted = particles.emit(position, velocity, acceleration,
                paletteColor((color + random.nextInt(8)) and 255), size = .4f + random.nextFloat() * .6f,
                lifetimeMillis = random.nextInt(500, 801), nowMs = nowMs)
            if (!emitted) return // A full/disabled particle budget drops this burst's remainder.
        }
    }

    private fun signedRandom(): Float = random.nextFloat() * 2f - 1f
}
