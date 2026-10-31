package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import kotlin.random.Random

/** Brief libGDX particle pulses for CL_Heatbeam rings and CL_MonsterPlasma_Shell. */
internal class HeatbeamParticles(
    private val particles: GdxParticleRuntime,
    private val paletteColor: (Int) -> Color,
    private val random: Random = Random.Default,
) : HeatbeamParticleSink {
    private val position = Vector3()
    private val radial = Vector3()

    override fun emit(local: Boolean, origin: Vector3, direction: Vector3, right: Vector3, up: Vector3, length: Float, nowMs: Int) {
        if (local) {
            val phase = (nowMs % 1000) * .096f % 32f
            repeat(5) { ring ->
                val distance = 8f + phase + ring * 32f
                if (distance > length || distance > 160f) return
                val radius = 1.5f * (distance / 10f).coerceAtMost(1f)
                repeat(20) { point ->
                    val angle = point * 18f
                    position.set(origin).mulAdd(direction, distance).mulAdd(right, MathUtils.cosDeg(angle) * radius)
                        .mulAdd(up, MathUtils.sinDeg(angle) * radius)
                    if (!emitParticle(position, 223 - random.nextInt(8), .5f, nowMs)) return
                }
            }
        } else {
            repeat(40) {
                radial.set(random.nextFloat() * 2f - 1f, random.nextFloat() * 2f - 1f, random.nextFloat() * 2f - 1f)
                if (radial.isZero) radial.set(Vector3.Z)
                position.set(origin).mulAdd(radial.nor(), 10f)
                if (!emitParticle(position, 0xE0, 1f, nowMs)) return
            }
        }
    }

    private fun emitParticle(position: Vector3, color: Int, alpha: Float, nowMs: Int): Boolean =
        particles.emit(position, Vector3.Zero, Vector3.Zero, paletteColor(color), size = .5f,
            lifetimeMillis = 75, nowMs = nowMs, startAlpha = alpha)
}
