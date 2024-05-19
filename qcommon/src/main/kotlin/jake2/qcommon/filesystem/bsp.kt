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

    val firstEdgeIndex: Int, // unsigned
    val numEdges: Int, // unsigned short
    val textureInfoIndex: Int, // unsigned short
    val lightMapStyles: ByteArray = ByteArray(4),
    val lightMapOffset: Int // unsigned
)

////////////////////////////
/*
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class Vec3(val x: Float, val y: Float, val z: Float)
data class Edge(val vertex0: UShort, val vertex1: UShort)
data class Face(
    val planeId: UShort,
    val side: UShort,
    val ledgeId: Int,
    val ledgeNum: UShort,
    val texinfoId: UShort
)
data class TexInfo(
    val vectorS: Vec3,
    val distS: Float,
    val vectorT: Vec3,
    val distT: Float,
    val textureId: Int
)
data class MipTex(
    val name: String,
    val width: Int,
    val height: Int,
    val offsets: IntArray
)

fun main(args: Array<String>) {
    if (args.size != 1) {
        println("usage: <program> <map.bsp>")
        return
    }

    val filePath = Paths.get(args[0])
    val buf = Files.readAllBytes(filePath)
    val byteBuffer = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)

    // Parse header and entries (skipped for brevity)
    val header = Bsp(byteBuffer)
    // Load vertices
    val vertices = Array(header.vertices.size.toInt() / 12) {
        Vec3(byteBuffer.getFloat(), byteBuffer.getFloat(), byteBuffer.getFloat())
    }

    // Load edges
    val edges = Array(header.edges.size.toInt() / 4) { Edge(byteBuffer.getUShort(), byteBuffer.getUShort()) }

    // Load faces
    val faces = Array(header.faces.size.toInt() / 20) {
        Face(
            byteBuffer.getUShort(),
            byteBuffer.getUShort(),
            byteBuffer.getInt(),
            byteBuffer.getUShort(),
            byteBuffer.getUShort()
        )
    }

    // Load texinfo
    val texInfos = Array(header.texinfo.size.toInt() / 40) {
        TexInfo(
            Vec3(byteBuffer.getFloat(), byteBuffer.getFloat(), byteBuffer.getFloat()),
            byteBuffer.getFloat(),
            Vec3(byteBuffer.getFloat(), byteBuffer.getFloat(), byteBuffer.getFloat()),
            byteBuffer.getFloat(),
            byteBuffer.getInt()
        )
    }

    // Load miptex (texture) data
    byteBuffer.position(header.miptex.offset.toInt())
    val numMiptex = byteBuffer.getInt()
    val miptexOffsets = IntArray(numMiptex) { byteBuffer.getInt() }

    val miptexes = miptexOffsets.map { offset ->
        byteBuffer.position(header.miptex.offset.toInt() + offset)
        val name = ByteArray(16).let { byteBuffer.get(it); String(it).trim('\u0000') }
        val width = byteBuffer.getInt()
        val height = byteBuffer.getInt()
        val offsets = IntArray(4) { byteBuffer.getInt() }
        MipTex(name, width, height, offsets)
    }

    // Prepare data for rendering
    val vertexBuffer = mutableListOf<Float>()
    val texCoordBuffer = mutableListOf<Float>()
    val indexBuffer = mutableListOf<Int>()

    for (face in faces) {
        for (i in 0 until face.ledgeNum - 2) {
            val edge1 = edges[edgesList[face.ledgeId + i].let { if (it < 0) -it else it }]
            val edge2 = edges[edgesList[face.ledgeId + i + 1].let { if (it < 0) -it else it }]
            val edge3 = edges[edgesList[face.ledgeId + i + 2].let { if (it < 0) -it else it }]

            val vertices = listOf(
                vertices[edge1.vertex0.toInt()],
                vertices[edge2.vertex0.toInt()],
                vertices[edge3.vertex0.toInt()]
            )

            val texInfo = texInfos[face.texinfoId.toInt()]
            val miptex = miptexes[texInfo.textureId]

            vertices.forEach { vertex ->
                val u = vertex.x * texInfo.vectorS.x + vertex.y * texInfo.vectorS.y + vertex.z * texInfo.vectorS.z + texInfo.distS
                val v = vertex.x * texInfo.vectorT.x + vertex.y * texInfo.vectorT.y + vertex.z * texInfo.vectorT.z + texInfo.distT

                vertexBuffer.add(vertex.x)
                vertexBuffer.add(vertex.y)
                vertexBuffer.add(vertex.z)

                texCoordBuffer.add(u / miptex.width)
                texCoordBuffer.add(v / miptex.height)
            }

            indexBuffer.add(vertexBuffer.size / 3 - 3)
            indexBuffer.add(vertexBuffer.size / 3 - 2)
            indexBuffer.add(vertexBuffer.size / 3 - 1)
        }
    }

    println("Vertex Buffer:")
    vertexBuffer.chunked(3).forEach { println("v ${it[0]} ${it[1]} ${it[2]}") }

    println("Texture Coordinate Buffer:")
    texCoordBuffer.chunked(2).forEach { println("vt ${it[0]} ${it[1]}") }

    println("Index Buffer:")
    indexBuffer.chunked(3).forEach { println("f ${it[0] + 1} ${it[1] + 1} ${it[2] + 1}") }
}
*/