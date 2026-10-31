package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import jake2.qcommon.Defines
import jake2.qcommon.math.Vector3f
import jake2.qcommon.network.messages.server.PointTEMessage
import jake2.qcommon.network.messages.server.TEMessage
import jake2.qcommon.network.messages.server.WidowBeamOutTEMessage
import kotlin.random.Random

/** CL_Widowbeamout / CL_Nukeblast, presented as bounded expanding particle pulses through libGDX. */
internal class ShellEffects(
    private val particles: GdxParticleRuntime,
    private val paletteColor: (Int) -> Color,
    private val random: Random = Random.Default,
    maximumSustains: Int = 32,
) {
    private enum class Kind(val duration: Int, val radius: Float, val count: Int, val palette: List<Int>) {
        WIDOW(2100, 45f, 128, listOf(16, 104, 168, 144)),
        NUKE(1000, 200f, 256, listOf(110, 112, 114, 116)),
    }
    private data class Shell(val kind: Kind, val origin: Vector3f, val startedAt: Int)
    private val sustains = SustainScheduler<Shell>(maximumSustains)
    private var nextNukeId = Short.MAX_VALUE + 1
    private val direction = Vector3()
    private val position = Vector3()
    val activeSustainCount: Int get() = sustains.activeCount

    fun process(message: TEMessage, nowMs: Int): Boolean {
        val kind: Kind
        val origin: Vector3f
        val id: Int
        when (message) {
            is WidowBeamOutTEMessage -> { kind = Kind.WIDOW; origin = message.position; id = message.id }
            is PointTEMessage -> {
                if (message.style != Defines.TE_NUKEBLAST) return false
                kind = Kind.NUKE; origin = message.position
                // Nuke messages have no identifier. Keep independent explosions outside the wire's signed-short ID range.
                id = nextNukeId
                nextNukeId = if (nextNukeId == Int.MAX_VALUE) Short.MAX_VALUE + 1 else nextNukeId + 1
            }
            else -> return false
        }
        sustains.start(id, Shell(kind, origin, nowMs), nowMs, kind.duration, intervalMillis = 50)
        return true
    }

    fun update(nowMs: Int) {
        sustains.update(nowMs) pulse@ { shell ->
            val progress = ((nowMs.toLong() - shell.startedAt).toFloat() / shell.kind.duration).coerceIn(0f, 1f)
            val radius = shell.kind.radius * progress
            repeat(shell.kind.count) {
                direction.set(random.nextFloat() * 2f - 1f, random.nextFloat() * 2f - 1f, random.nextFloat() * 2f - 1f)
                if (direction.isZero) direction.set(Vector3.Z)
                position.set(shell.origin.x, shell.origin.y, shell.origin.z).mulAdd(direction.nor(), radius)
                val color = shell.kind.palette[random.nextInt(shell.kind.palette.size)]
                if (!particles.emit(position, Vector3.Zero, Vector3.Zero, paletteColor(color), size = .6f,
                        lifetimeMillis = 75, nowMs = nowMs)) return@pulse
            }
        }
    }

    /** Stop future pulses; existing particles remain owned by the shared particle runtime. */
    fun clear() {
        sustains.clear()
        nextNukeId = Short.MAX_VALUE + 1
    }
}
