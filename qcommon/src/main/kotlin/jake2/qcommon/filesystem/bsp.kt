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
    val faceEdges = readFaceEdges(buffer, header.lumps[12])
    val faces = readFaces(buffer, header.lumps[6])
    val textures = readTextures(buffer, header.lumps[5])

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

    // Array of Indices may be signed, which denotes the reversed direction
    private fun readFaceEdges(buffer: ByteBuffer, bspLump: BspLump): Array<Int> {
        check(bspLump.length % 4 == 0) {
            "Unexpected face edge lump size: ${bspLump.length}, should be divisible by 4"
        }
        buffer.position(bspLump.offset)
        val faceEdges = mutableListOf<Int>()
        repeat(bspLump.length / 4) {
            faceEdges.add(buffer.getInt())
        }
        return faceEdges.toTypedArray()
    }

    private fun readFaces(buffer: ByteBuffer, bspLump: BspLump): Array<BspFace> {
        check(bspLump.length % 20 == 0) {
            "Unexpected face lump size: ${bspLump.length}, should be divisible by 20"
        }
        buffer.position(bspLump.offset)
        val faces = mutableListOf<BspFace>()
        repeat(bspLump.length / 20) {
            val styles = ByteArray(4)
            faces.add(
                BspFace(
                    plane = (buffer.getShort() and 0xFFFF.toShort()).toInt(),
                    planeSide = (buffer.getShort() and 0xFFFF.toShort()).toInt(),
                    firstEdgeIndex = buffer.getInt(),
                    numEdges = (buffer.getShort() and 0xFFFF.toShort()).toInt(),
                    textureInfoIndex = (buffer.getShort() and 0xFFFF.toShort()).toInt(),
                    lightMapStyles = styles.also { buffer.get(it) },
                    lightMapOffset = buffer.getInt(),
                ))

        }
        return faces.toTypedArray()
    }

    private fun readTextures(buffer: ByteBuffer, bspLump: BspLump): Array<BspTextureInfo> {
        check(bspLump.length % 76 == 0) {
            "Unexpected texture lump size: ${bspLump.length}, should be divisible by 76"
        }
        buffer.position(bspLump.offset)
        val textures = mutableListOf<BspTextureInfo>()
        repeat(bspLump.length / 76) {
            val nameBytes = ByteArray(32)
            textures.add(BspTextureInfo(
                uAxis = Vector3f(buffer.getFloat(), buffer.getFloat(), buffer.getFloat()),
                uOffset = buffer.getFloat(),
                vAxis = Vector3f(buffer.getFloat(), buffer.getFloat(), buffer.getFloat()),
                vOffset = buffer.getFloat(),
                flags = buffer.getInt(),
                value = buffer.getInt(),
                name = String(nameBytes.also { buffer.get(it) }).trim { it < ' ' },
                next = buffer.getInt()
            ))
        }
        return textures.toTypedArray()
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
)

data class BspFace(
    val plane: Int, // unsigned short
    val planeSide: Int, // unsigned short

    val firstEdgeIndex: Int, // fixme: unsigned
    val numEdges: Int, // unsigned short
    val textureInfoIndex: Int, // unsigned short
    val lightMapStyles: ByteArray, //size 4
    val lightMapOffset: Int // fixme: unsigned
)

data class BspTextureInfo(
    val uAxis: Vector3f,
    val uOffset: Float,
    val vAxis: Vector3f,
    val vOffset: Float,

    val flags: Int,
    val value: Int, // fixme: unsigned

    val name: String, // max size 32 bytes
    val next: Int // fixme: unsigned
) {
    // need to know the texture size to calculate the uv
    fun calculateUV(p: Vector3f, textureWidth: Int, textureHeight: Int): List<Float> {
        val uRaw = (p.x * uAxis.x + p.y * uAxis.y + p.z * uAxis.z + uOffset)
        val vRaw = (p.x * vAxis.x + p.y * vAxis.y + p.z * vAxis.z + vOffset)

        // Normalize to the range [0, 1]
        val normalizedU = uRaw / textureWidth
        val normalizedV = vRaw / textureHeight

        // Handle negative values to ensure they are within [0, 1] range
        val finalU = if (normalizedU < 0) normalizedU + 1.0f else normalizedU
        val finalV = if (normalizedV < 0) normalizedV + 1.0f else normalizedV

        return listOf(finalU, finalV)
    }
}