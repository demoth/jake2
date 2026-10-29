package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import jake2.qcommon.Defines
import jake2.qcommon.network.messages.server.BeamTEMessage
import kotlin.math.ceil
import kotlin.math.sqrt

/** Consumes borrowed per-frame vectors synchronously; implementations must copy anything they retain. */
internal fun interface HeatbeamParticleSink {
    fun emit(local: Boolean, origin: Vector3, direction: Vector3, right: Vector3, up: Vector3, length: Float, nowMs: Int)
}

/**
 * Owned heatbeam state (CL_ParseHeatBeam / CL_AddHeatBeams). Local beams follow the presented
 * camera and gun offset; all other beams retain their recorded endpoints. Both live and demo
 * views supply the same context. The existing world/view-weapon camera also projects these models.
 */
internal class HeatbeamEffects(
    private val particles: HeatbeamParticleSink,
    private val maximumBeams: Int = 32,
    private val maximumSegmentsPerBeam: Int = 128,
) {
    init { require(maximumBeams > 0 && maximumSegmentsPerBeam > 0) }
    private data class Beam(val message: BeamTEMessage, val expiresAt: Long, var lastParticlesAt: Int?)
    private val beams = LinkedHashMap<Int, Beam>()
    private val origin = Vector3()
    private val direction = Vector3()
    private val right = Vector3()
    private val up = Vector3()
    val activeCount: Int get() = beams.size

    fun process(message: BeamTEMessage, nowMs: Int): Boolean {
        if (message.style != Defines.TE_HEATBEAM && message.style != Defines.TE_MONSTER_HEATBEAM) return false
        if (message.ownerIndex !in 0 until Defines.MAX_EDICTS) return true
        expire(nowMs)
        if (message.ownerIndex !in beams && beams.size >= maximumBeams) return true
        beams[message.ownerIndex] = Beam(message, nowMs.toLong() + 200, beams[message.ownerIndex]?.lastParticlesAt)
        return true
    }

    fun submit(nowMs: Int, localPlayerEntity: Int?, camera: Camera, gunOffset: Vector3, hand: Int, sink: BeamSegmentSink) {
        expire(nowMs)
        for ((owner, beam) in beams) {
            val message = beam.message
            val local = owner == localPlayerEntity && message.style == Defines.TE_HEATBEAM
            if (local) {
                direction.set(camera.direction).nor()
                right.set(direction).crs(camera.up).nor()
                up.set(right).crs(direction).nor()
                val side = when (hand) { 1 -> -1f; 2 -> 0f; else -> 1f }
                // CL_ParseHeatBeam's player offset: right 2, forward 7, up -3; centered weapons sit one unit lower.
                origin.set(camera.position).add(gunOffset).mulAdd(right, 2f * side).mulAdd(direction, 7f)
                    .mulAdd(up, if (hand == 2) -4f else -3f)
            } else {
                origin.set(message.origin.x, message.origin.y, message.origin.z)
            }
            val end = message.destination
            val dx = end.x - origin.x; val dy = end.y - origin.y; val dz = end.z - origin.z
            val length = sqrt(dx * dx + dy * dy + dz * dz)
            if (length < .001f) continue
            if (!local) {
                direction.set(dx, dy, dz).scl(1f / length)
                right.set(direction).crs(if (kotlin.math.abs(direction.z) < .9f) Vector3.Z else Vector3.X).nor()
                up.set(right).crs(direction).nor()
            }
            val kind = if (local) BeamKind.HEATBEAM_PLAYER else BeamKind.HEATBEAM_MONSTER
            val pitch = MathUtils.atan2(direction.z, sqrt(direction.x * direction.x + direction.y * direction.y)) * MathUtils.radiansToDegrees
            val yaw = MathUtils.atan2(direction.y, direction.x) * MathUtils.radiansToDegrees + 180f
            val count = ceil(length / kind.segmentLength).toInt().coerceIn(1, maximumSegmentsPerBeam)
            val step = if (count > 1) (length - kind.segmentLength) / (count - 1) else 0f
            for (index in 0 until count) {
                val distance = index * step
                sink.submit(kind, origin.x + direction.x * distance, origin.y + direction.y * distance,
                    origin.z + direction.z * distance, pitch, yaw, (nowMs % 360).toFloat())
            }
            // Short overlapping pulses give consistent density without emitting on every render or while paused.
            if (beam.lastParticlesAt == null || nowMs.toLong() - beam.lastParticlesAt!! >= 50) {
                beam.lastParticlesAt = nowMs
                particles.emit(local, origin, direction, right, up, length, nowMs)
            }
        }
    }

    fun clear() = beams.clear()
    private fun expire(nowMs: Int) = beams.values.removeAll { nowMs.toLong() >= it.expiresAt }
}
