package jake2.qcommon.filesystem

import jake2.qcommon.Defines
import jake2.qcommon.math.Vector3f
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.experimental.and

const val IDBSPHEADER = (('P'.code shl 24) + ('S'.code shl 16) + ('B'.code shl 8) + 'I'.code)

class Bsp(buffer: ByteBuffer) {
    val header = BspHeader(buffer)
    val entities = readEntities(buffer, header.lumps[0])
    // planes lump skipped
    val vertices = readVertices(buffer, header.lumps[2])
    val edges = readEdges(buffer, header.lumps[11])

    private fun readEntities(buffer: ByteBuffer, bspLump: BspLump): String {
        buffer.position(bspLump.offset)
        val bytes = ByteArray(bspLump.length)
        buffer.get(bytes)
        return String(bytes).substring(0, bytes.size - 2) // skip last 0 byte
    }

    private fun readVertices(buffer: ByteBuffer, bspLump: BspLump): Array<Vector3f> {
        // every vertex is 12 bytes (3 floats)
        check(bspLump.length % 12 == 0) {
            "Unexpected vertex lump size: ${bspLump.length}, should be divisible by 12"
        }
        buffer.position(bspLump.offset)
        val vertices = mutableListOf<Vector3f>()
        repeat(bspLump.length / 12) {
            vertices.add(Vector3f(buffer.float, buffer.float, buffer.float))
        }
        return vertices.toTypedArray()
    }

    private fun readEdges(buffer: ByteBuffer, bspLump: BspLump): Array<BspEdge> {
        check(bspLump.length % 4 == 0) {
            "Unexpected edge lump size: ${bspLump.length}, should be divisible by 4"
        }
        buffer.position(bspLump.offset)
        val edges = mutableListOf<BspEdge>()
        repeat(bspLump.length / 4) {
            edges.add(BspEdge(
                (buffer.getShort() and 0xFFFF.toShort()).toInt(),
                (buffer.getShort() and 0xFFFF.toShort()).toInt()
            ))
        }
        return edges.toTypedArray()
    }
}

class BspHeader(buffer: ByteBuffer) {
    @JvmField val ident: Int
    @JvmField val version: Int
    @JvmField val lumps: Array<BspLump>

    init {
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        this.ident = buffer.getInt()
        check(ident == IDBSPHEADER) { "Wrong bsp identifier: $ident" }
        this.version = buffer.getInt()
        check(version == 38) { "Unsupported bsp version: $version" }
        val lumpList = ArrayList<BspLump>(Defines.HEADER_LUMPS)
        repeat(Defines.HEADER_LUMPS) {
            lumpList.add(BspLump(offset = buffer.getInt(), length = buffer.getInt()))
        }
        lumps = lumpList.toTypedArray()
    }
}

/**
 * lump_t
 */
class BspLump(
    @JvmField var offset: Int,
    @JvmField var length: Int
)

/**
 * medge_t
 */
data class BspEdge(
    val v1: Int,
    val v2: Int,
) {
    fun toFloats(vertices: Array<Vector3f>): List<Float> {
        return listOf(v1, v2).flatMap {
            listOf(
            vertices[v1].x,
            vertices[v1].y,
            vertices[v1].z,
            vertices[v2].x,
            vertices[v2].y,
            vertices[v2].z,
            )
        }
    }
}