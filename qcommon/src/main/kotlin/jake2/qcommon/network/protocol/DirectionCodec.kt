package jake2.qcommon.network.protocol

import jake2.qcommon.Globals
import jake2.qcommon.math.Vector3f

/** Immutable normal table; select the greatest positive dot product, defaulting to index zero. */
object DirectionCodec {
    private val normals = Globals.bytedirs.map(::Vector3f)

    fun vector(index: Int): Vector3f = normals[index]

    fun index(direction: Vector3f?): Int {
        if (direction == null) return 0
        var bestDot = 0f
        var bestIndex = 0
        for ((index, normal) in normals.withIndex()) {
            val dot = direction.x * normal.x + direction.y * normal.y + direction.z * normal.z
            if (dot > bestDot) { bestDot = dot; bestIndex = index }
        }
        return bestIndex
    }

    fun readIndex(buffer: MessageReader): Int {
        val index = buffer.readUnsignedByte()
        if (index !in normals.indices)
            throw MessageReadException(MessageRejection.INVALID_VALUE, buffer.readcount - 1, "Invalid direction index $index")
        return index
    }
}
