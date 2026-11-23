package jake2.qcommon.filesystem

import jake2.qcommon.Defines
import jake2.qcommon.math.Vector3f
import jake2.qcommon.parseEntities
import jake2.qcommon.util.Lib
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.experimental.and
import kotlin.math.abs
import kotlin.math.max

const val IDBSPHEADER = (('P'.code shl 24) + ('S'.code shl 16) + ('B'.code shl 8) + 'I'.code)

class Bsp(buffer: ByteBuffer) {
    val header = BspHeader(buffer).also { it.validateLumpBounds(buffer.limit()) }
    val rootNodeBounds = readRootNodeBounds(buffer, header.lumps[Defines.LUMP_NODES])
    val entityString = readEntities(buffer, header.lumps[0])
    // planes lump skipped
    val lighting = readLighting(buffer, header.lumps[7])
    val vertices = readVertices(buffer, header.lumps[2])
    val edges = readEdges(buffer, header.lumps[11])
    val faceEdges = readFaceEdges(buffer, header.lumps[12])
    val faces = readFaces(buffer, header.lumps[6])
    val textures = readTextures(buffer, header.lumps[5])
    val leaves = readLeaves(buffer, header.lumps[8])
    val leafFaces = readLeafFaces(buffer, header.lumps[9])
    val models = readModels(buffer, header.lumps[13])
    val entities = parseEntities(entityString)

    private fun readRootNodeBounds(buffer: ByteBuffer, lump: BspLump): BspNodeBounds? {
        checkResourceFormat(lump.length % qfiles.dnode_t.SIZE == 0) {
            "Unexpected node lump size: ${lump.length}, should be divisible by ${qfiles.dnode_t.SIZE}"
        }
        if (lump.length == 0) return null
        buffer.position(lump.offset)
        val root = qfiles.dnode_t(buffer)
        return BspNodeBounds(
            mins = Vector3f(root.mins[0].toFloat(), root.mins[1].toFloat(), root.mins[2].toFloat()),
            maxs = Vector3f(root.maxs[0].toFloat(), root.maxs[1].toFloat(), root.maxs[2].toFloat()),
        )
    }

    private fun readEntities(buffer: ByteBuffer, bspLump: BspLump): String {
        buffer.position(bspLump.offset)
        val bytes = ByteArray(bspLump.length)
        buffer.get(bytes)
        return Lib.CtoJava(bytes)
    }

    private fun readVertices(buffer: ByteBuffer, bspLump: BspLump): Array<Vector3f> {
        // every vertex is 12 bytes (3 floats)
        checkResourceFormat(bspLump.length % 12 == 0) {
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
        checkResourceFormat(bspLump.length % 4 == 0) {
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
        checkResourceFormat(bspLump.length % 4 == 0) {
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
        checkResourceFormat(bspLump.length % 20 == 0) {
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

    private fun readLighting(buffer: ByteBuffer, bspLump: BspLump): ByteArray {
        if (bspLump.length <= 0) {
            return byteArrayOf()
        }
        buffer.position(bspLump.offset)
        return ByteArray(bspLump.length).also { buffer.get(it) }
    }

    private fun readTextures(buffer: ByteBuffer, bspLump: BspLump): Array<BspTextureInfo> {
        checkResourceFormat(bspLump.length % 76 == 0) {
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
                name = Lib.CtoJava(nameBytes.also { buffer.get(it) }),
                next = buffer.getInt()
            ))
        }
        return textures.toTypedArray()
    }

    private fun readLeaves(buffer: ByteBuffer, bspLump: BspLump): Array<BspLeaf> {
        checkResourceFormat(bspLump.length % 28 == 0) {
            "Unexpected leaf lump size: ${bspLump.length}, should be divisible by 28"
        }
        buffer.position(bspLump.offset)
        val leaves = mutableListOf<BspLeaf>()
        repeat(bspLump.length / 28) {
            val contents = buffer.getInt()
            val cluster = buffer.getShort().toInt()
            val area = buffer.getShort().toInt()
            // mins[3] and maxs[3] are currently not needed by cake runtime representation
            repeat(6) { buffer.getShort() }
            leaves.add(
                BspLeaf(
                    contents = contents,
                    cluster = cluster,
                    area = area,
                    firstLeafFace = (buffer.getShort() and 0xFFFF.toShort()).toInt(),
                    numLeafFaces = (buffer.getShort() and 0xFFFF.toShort()).toInt(),
                    firstLeafBrush = (buffer.getShort() and 0xFFFF.toShort()).toInt(),
                    numLeafBrushes = (buffer.getShort() and 0xFFFF.toShort()).toInt(),
                )
            )
        }
        return leaves.toTypedArray()
    }

    private fun readLeafFaces(buffer: ByteBuffer, bspLump: BspLump): Array<Int> {
        checkResourceFormat(bspLump.length % 2 == 0) {
            "Unexpected leaf-face lump size: ${bspLump.length}, should be divisible by 2"
        }
        buffer.position(bspLump.offset)
        val leafFaces = mutableListOf<Int>()
        repeat(bspLump.length / 2) {
            leafFaces.add((buffer.getShort() and 0xFFFF.toShort()).toInt())
        }
        return leafFaces.toTypedArray()
    }

    private fun readModels(buffer: ByteBuffer, bspLump: BspLump): Array<BspModel> {
        checkResourceFormat(bspLump.length % 48 == 0) {
            "Unexpected model lump size: ${bspLump.length}, should be divisible by 48"
        }
        buffer.position(bspLump.offset)
        val models = mutableListOf<BspModel>()
        repeat(bspLump.length / 48) {
            models.add(
                BspModel(
                    mins = Vector3f(buffer.getFloat(), buffer.getFloat(), buffer.getFloat()),
                    maxs = Vector3f(buffer.getFloat(), buffer.getFloat(), buffer.getFloat()),
                    origin = Vector3f(buffer.getFloat(), buffer.getFloat(), buffer.getFloat()),
                    headNode = buffer.getInt(),
                    firstFace = buffer.getInt(),
                    faceCount = buffer.getInt()
                )
            )
        }
        return models.toTypedArray()
    }
}

class BspHeader(buffer: ByteBuffer) {
    @JvmField val ident: Int
    @JvmField val version: Int
    @JvmField val lumps: Array<BspLump>

    init {
        val headerSize = (2 + 2 * Defines.HEADER_LUMPS) * Int.SIZE_BYTES
        checkResourceFormat(buffer.remaining() >= headerSize) { "Truncated BSP header" }
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        this.ident = buffer.getInt()
        checkResourceFormat(ident == IDBSPHEADER) { "Wrong bsp identifier: $ident" }
        this.version = buffer.getInt()
        checkResourceFormat(version == 38) { "Unsupported bsp version: $version" }
        val lumpList = ArrayList<BspLump>(Defines.HEADER_LUMPS)
        repeat(Defines.HEADER_LUMPS) {
            lumpList.add(BspLump(offset = buffer.getInt(), length = buffer.getInt()))
        }
        lumps = lumpList.toTypedArray()
    }

    /** Validate lump ranges when reading the full file. Header-only readers can omit this check. */
    fun validateLumpBounds(fileSize: Int) {
        lumps.forEachIndexed { index, lump ->
            checkResourceFormat(lump.offset in 0..fileSize && lump.length in 0..(fileSize - lump.offset)) {
                "BSP lump $index is outside the file"
            }
        }
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

        val normalizedU = uRaw / textureWidth
        val normalizedV = vRaw / textureHeight

        return listOf(normalizedU, normalizedV)
    }
}

/** Root BSP node bounds, distinct from the world model's padded bounds. */
data class BspNodeBounds(val mins: Vector3f, val maxs: Vector3f)

data class BspModel(
    val mins: Vector3f,
    val maxs: Vector3f,
    val origin: Vector3f,
    val headNode: Int,
    val firstFace: Int, // unsigned
    val faceCount: Int, // unsigned
) {
    val radius = radiusFromBounds(mins, maxs)

    /**
     * Calculates the radius of a bounding box using the minimum and maximum coordinates.
     * The bounding radius is the distance from the center of the bounding box to the furthest corner of the bounding box.
     * This is useful for various spatial queries, such as frustum culling, collision detection, etc.
     */
    private fun radiusFromBounds(mins: Vector3f, maxs: Vector3f): Float {
        val corner = Vector3f(
            x = max(abs(mins.x), abs(maxs.x)),
            y = max(abs(mins.y), abs(maxs.y)),
            z = max(abs(mins.z), abs(maxs.z))
        )

        return corner.length()
    }
}

data class BspLeaf(
    val contents: Int,
    val cluster: Int, // signed short; -1 means invalid cluster
    val area: Int, // signed short
    val firstLeafFace: Int, // unsigned short
    val numLeafFaces: Int, // unsigned short
    val firstLeafBrush: Int, // unsigned short
    val numLeafBrushes: Int, // unsigned short
)
