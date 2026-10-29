package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import jake2.qcommon.Defines
import jake2.qcommon.math.Vector3f
import jake2.qcommon.network.messages.server.*
import kotlin.math.ceil
import kotlin.random.Random

internal enum class BeamKind(val modelPath: String, val segmentLength: Float) {
    CABLE("models/monsters/parasite/segment/tris.md2", 30f),
    GRAPPLE("models/ctf/segment/tris.md2", 30f),
    LIGHTNING("models/proj/lightning/tris.md2", 35f),
}

internal data class BeamSoundRequest(val path: String, val entityIndex: Int, val channel: Int)

internal fun interface BeamSegmentSink {
    fun submit(kind: BeamKind, x: Float, y: Float, z: Float, pitch: Float, yaw: Float, roll: Float)
}

/**
 * Owned, bounded beam state (CL_ParseBeam / CL_ParseLightning / CL_AddBeams).
 * Cables refresh by source entity; lightning refreshes by source AND destination.
 * Geometry is rebuilt from the current view each frame, independently of new network messages.
 */
internal class BeamEffects(
    private val playSound: (BeamSoundRequest) -> Unit,
    private val maximumBeams: Int = 32,
    private val maximumSegmentsPerBeam: Int = 128,
    private val random: Random = Random.Default,
) {
    init { require(maximumBeams > 0 && maximumSegmentsPerBeam > 0) }
    private data class Key(val source: Int, val destination: Int?)
    private data class Beam(val kind: BeamKind, val origin: Vector3f, val end: Vector3f,
                            val offset: Vector3f, val expiresAt: Long, val roll: Float)
    private val beams = LinkedHashMap<Key, Beam>()
    private val start = Vector3()
    private val direction = Vector3()
    val activeCount: Int get() = beams.size

    /** Returns false for unrelated messages, including heatbeams which have their own presentation. */
    fun process(message: TEMessage, nowMs: Int): Boolean {
        val key: Key
        val kind: BeamKind
        val origin: Vector3f
        val end: Vector3f
        val offset: Vector3f
        when (message) {
            is BeamTEMessage -> {
                if (message.style != Defines.TE_PARASITE_ATTACK && message.style != Defines.TE_MEDIC_CABLE_ATTACK) return false
                key = Key(message.ownerIndex, null); kind = BeamKind.CABLE
                origin = message.origin; end = message.destination; offset = Vector3f.zero
            }
            is BeamOffsetTEMessage -> {
                key = Key(message.ownerIndex, null); kind = BeamKind.GRAPPLE
                origin = message.origin; end = message.destination; offset = message.offset
            }
            is LightningTEMessage -> {
                key = Key(message.sourceIndex, message.destinationIndex); kind = BeamKind.LIGHTNING
                origin = message.origin; end = message.destination; offset = Vector3f.zero
            }
            else -> return false
        }
        if (key.source !in 0 until Defines.MAX_EDICTS ||
            (key.destination != null && key.destination !in 0 until Defines.MAX_EDICTS)) return true
        if (kind == BeamKind.LIGHTNING)
            playSound(BeamSoundRequest("sound/weapons/tesla.wav", key.source, Defines.CHAN_WEAPON))
        expire(nowMs)
        if (key !in beams && beams.size >= maximumBeams) return true
        beams[key] = Beam(kind, origin, end, offset, nowMs.toLong() + 200, beams[key]?.roll ?: random.nextFloat() * 360f)
        return true
    }

    fun submit(nowMs: Int, localPlayerEntity: Int?, viewOrigin: Vector3, sink: BeamSegmentSink) {
        expire(nowMs)
        for ((key, beam) in beams) {
            if (key.source == localPlayerEntity) start.set(viewOrigin).add(0f, 0f, -22f)
            else start.set(beam.origin.x, beam.origin.y, beam.origin.z)
            start.add(beam.offset.x, beam.offset.y, beam.offset.z)
            direction.set(beam.end.x, beam.end.y, beam.end.z).sub(start)
            val length = direction.len()
            if (length < .001f) continue
            direction.scl(1f / length)
            val yaw = MathUtils.atan2(direction.y, direction.x) * MathUtils.radiansToDegrees
            val pitch = -MathUtils.atan2(direction.z, kotlin.math.sqrt(direction.x * direction.x + direction.y * direction.y)) * MathUtils.radiansToDegrees
            val lightning = beam.kind == BeamKind.LIGHTNING
            // Lightning models point backwards. Short bolts start at the target so they don't pierce the Tesla mine.
            val usableLength = if (lightning) (length - 20f).coerceAtLeast(0f) else length
            if (lightning && usableLength <= beam.kind.segmentLength) {
                sink.submit(beam.kind, beam.end.x, beam.end.y, beam.end.z, pitch, yaw, roll(beam, nowMs, 0))
                continue
            }
            val count = ceil(usableLength / beam.kind.segmentLength).toInt().coerceIn(1, maximumSegmentsPerBeam)
            val step = if (count > 1) (usableLength - beam.kind.segmentLength) / (count - 1) else 0f
            for (index in 0 until count) {
                val distance = index * step
                sink.submit(beam.kind, start.x + direction.x * distance, start.y + direction.y * distance,
                    start.z + direction.z * distance, if (lightning) -pitch else pitch,
                    if (lightning) yaw + 180f else yaw, roll(beam, nowMs, index))
            }
        }
    }

    fun clear() = beams.clear()

    private fun expire(nowMs: Int) = beams.values.removeAll { nowMs.toLong() >= it.expiresAt }

    private fun roll(beam: Beam, nowMs: Int, index: Int): Float =
        (beam.roll + index * 137.5f + if (beam.kind == BeamKind.LIGHTNING) (nowMs % 3000) * .12f else 0f) % 360f
}
