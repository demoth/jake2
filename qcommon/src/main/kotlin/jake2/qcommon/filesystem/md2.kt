package jake2.qcommon.filesystem

import jake2.qcommon.Globals
import jake2.qcommon.math.Vector3f
import java.lang.Float.intBitsToFloat
import java.nio.ByteBuffer

const val IDALIASHEADER: Int = (('2'.code shl 24) + ('P'.code shl 16) + ('D'.code shl 8) + 'I'.code)
const val ALIAS_VERSION: Int = 8

/**
 * MD2 (quake2) model (previously named dmdl_t)
 *
 * Also known as "alias model" in the code - named after the model editor originally used (PowerAnimator)
 *
 * The Header section contains size information (how many vertices, frames, skins, etc)
 * and byte offsets to the respective sections.
 * Body sections are the data arrays - frames, glCommands, skin names.
 *
 * The GL commands (glCmd) format:
 *
 *  * positive integer starts a triangle strip command, followed by that many vertex structures.
 *  * negative integer starts a triangle fan command, followed by -x vertexes
 *  * zero indicates the end of the command list.
 *
 * A vertex consists of the texture coordinates (float s, float t) and an integer vertex index.
 *
 * Actual vertex positions are contained in the frames (Md2Frame).
 */
class Md2Model(buffer: ByteBuffer) {
    // public info
    // Body
    val skinNames: List<String>
    val glCommands: List<Md2GlCmd>

    // all frames have vertex array of equal size (num_xyz)
    val frames: List<Md2Frame>
    val verticesCount: Int


    // private fields
    private val skinCount: Int

    private val trisCount: Int
    private val glcmdCount: Int
    private val framesCount: Int

    private val skinsOffset: Int
    private val firstFrameOffset: Int
    private val glCommandsOffset: Int
    private val endOfFileOffset: Int // end of file
    private val glCommandIntegers: List<Int>

    fun getFrameVertices(frame: Int): FloatArray {
        return glCommands.flatMap {
            it.toVertexAttributes(frames[frame].points)
        }.toFloatArray()
    }

    init {
        //
        // region: HEADER
        //
        val fileIdentifier = buffer.getInt()

        check(fileIdentifier == IDALIASHEADER) {
            "Wrong .md2 identifier: $fileIdentifier"
        }

        val version = buffer.getInt()
        check(version == ALIAS_VERSION) {
            "Wrong .md2 version: $version"
        }

        // some properties are ignored by q2 engine
        buffer.getInt() // skinwidth
        buffer.getInt() // skinheight
        buffer.getInt() // framesize: byte size of each frame

        skinCount = buffer.getInt()
        verticesCount = buffer.getInt()
        // actual vertex coordinates are parsed from glcmd, so these are unused in q2
        buffer.getInt() //num_st: greater than num_xyz for seams, parsed into dstvert_t
        trisCount = buffer.getInt()
        glcmdCount = buffer.getInt() // dwords in strip/fan command list
        framesCount = buffer.getInt()

        skinsOffset = buffer.getInt() // each skin is a MAX_SKINNAME string
        buffer.getInt() // offset from start until dstvert_t[]
        buffer.getInt() // offset from start until dtriangles[]
        firstFrameOffset = buffer.getInt() // offset for first frame
        glCommandsOffset = buffer.getInt()
        endOfFileOffset = buffer.getInt() // end of file

        //
        // Parsing the BODY
        //

        // FRAMES
        frames = ArrayList(framesCount)
        buffer.position(firstFrameOffset)
        repeat(framesCount) {
            // parse frame from the buffer
            frames.add(Md2Frame.fromBuffer(buffer, verticesCount))
        }

        // GL COMMANDS
        glCommandIntegers = ArrayList(glcmdCount)
        buffer.position(glCommandsOffset)
        repeat(glcmdCount) {
            glCommandIntegers.add(buffer.getInt())
        }

        // unpack gl integer commands into the structured list of vertices
        glCommands = ArrayList()
        val commandQueue = ArrayDeque(glCommandIntegers.toList())
        while (commandQueue.isNotEmpty()) {
            // the first value tells the type of the gl command (it's sign) and number of vertices
            val command = commandQueue.removeFirst()

            val cmdType = if (command > 0)
                Md2GlCmdType.TRIANGLE_STRIP
            else if (command < 0)
                Md2GlCmdType.TRIANGLE_FAN
            else {
                // == 0
                check(commandQueue.isEmpty()) { "unexpected glcmds found after reaching command 0" }
                break
            }


            val numVertices = if (command > 0) command else -command

            val vertices = ArrayList<Md2VertexInfo>(numVertices)

            repeat(numVertices) {
                val s = intBitsToFloat(commandQueue.removeFirst())
                val t = intBitsToFloat(commandQueue.removeFirst())
                val vertexIndex = commandQueue.removeFirst()
                vertices.add(Md2VertexInfo(vertexIndex, s, t))
            }
            glCommands.add(Md2GlCmd(cmdType, vertices))
        }

        // SKINS
        skinNames = ArrayList()
        buffer.position(skinsOffset)
        repeat(skinCount) {
            val nameBuf = ByteArray(qfiles.MAX_SKINNAME)
            buffer.get(nameBuf)
            val skinName = String(nameBuf)
            skinNames.add(skinName.substring(0, skinName.indexOf(".pcx")) + ".pcx")
        }
    }
}

/**
 * Builds runtime vertex data for MD2 models consumed by Cake.
 *
 * The unpacked triangles are normalized to Cake-compatible winding so materials can use conventional
 * backface culling (`GL_BACK`) without per-model cull overrides.
 *
 * Legacy counterpart:
 * MD2 stores packed normal indices (`lightnormalindex`) and legacy renderers resolve them
 * through `anorms.h` / `Globals.bytedirs` before alias shading.
 */
fun buildVertexData(
    glCmds: List<Md2GlCmd>,
    frames: List<Md2Frame>
): Md2VertexData {
    check(frames.isNotEmpty()) { "MD2 must contain at least one frame" }

    // First, we need to reindex the vertices.
    // In md2 format the vertices are indexed without the texture coordinates (which are part of gl commands).
    // GL commands are shared between frames, therefore the uv don't change between frames.
    // To make this index, we need to iterate over the gl commands by vertex index, and cache the vertex coordinates.
    // If however, the same vertex has a different text coord, we need to make a new vertex, append it to the index,

    // map from (oldIndex, s,t ) to new index
    val attributes = glCmds.flatMap { glCmd ->
        glCmd.unpack().flatMap { vertex ->
            listOf(vertex.index.toFloat(), vertex.s, vertex.t)
        }
    }
    check(attributes.size % 3 == 0) { "MD2 vertex attributes should contain triples: (vatIndex, u, v)" }
    val indexedVertexCount = attributes.size / 3
    check(indexedVertexCount <= 0xFFFF) {
        "MD2 indexed vertex count exceeds unsigned short index range: $indexedVertexCount"
    }

    // flatten vertex positions and resolved normals in all frames.
    //
    // Legacy counterpart:
    // `lightnormalindex -> r_avertexnormals[]` (anorms table) in alias render path.
    val vertexPositions = mutableListOf<Float>()
    val vertexNormals = mutableListOf<Float>()
    frames.forEach { frame ->
        frame.points.forEach { point ->
            vertexPositions.add(point.position.x)
            vertexPositions.add(point.position.y)
            vertexPositions.add(point.position.z)
            val normal = Globals.bytedirs.getOrNull(point.normalIndex) ?: Globals.bytedirs[0]
            vertexNormals.add(normal[0])
            vertexNormals.add(normal[1])
            vertexNormals.add(normal[2])
        }
    }

    return Md2VertexData(
        indices = ShortArray(indexedVertexCount) { it.toShort() },
        vertexAttributes = attributes.toFloatArray(),
        vertexPositions = vertexPositions.toFloatArray(),
        vertexNormals = vertexNormals.toFloatArray(),
        frames = frames.size,
        vertices = frames.first().points.size, // assuming all frames have the same number of vertices
    )

}

@Suppress("ArrayInDataClass")
data class Md2VertexData(
    // indices to draw GL_TRIANGLES
    val indices: ShortArray,
    // indexed attributes (at the moment - only text coords)
    val vertexAttributes: FloatArray,
    // vertex positions in a 2d array, should correspond to the indices, used to create VAT (Vertex Animation Texture)
    // size is numVertices(width) * numFrames(height) * 3(rgb)
    val vertexPositions: FloatArray,
    // same layout as [vertexPositions], but containing resolved per-frame normal vectors.
    val vertexNormals: FloatArray,
    val frames: Int,
    val vertices: Int,
)

enum class Md2GlCmdType {
    TRIANGLE_STRIP,
    TRIANGLE_FAN,
}

data class Md2VertexInfo(val index: Int, val s: Float, val t: Float) {
    /**
     * create a vertex buffer part for this particular vertex (x y z s t)
     */
    fun toFloats(points: List<Md2Point>, returnTexCoords: Boolean): List<Float> {
        val p = points[index].position // todo: check bounds
        return if(returnTexCoords) listOf(p.x, p.y, p.z, s, t) else listOf(p.x, p.y, p.z)
    }
}

data class Md2GlCmd(
    val type: Md2GlCmdType,
    val vertices: List<Md2VertexInfo>,
) {

    /**
     * Convert triangle strip/fan commands into independent triangles with Cake-compatible winding.
     *
     * The parser first reproduces OpenGL primitive assembly and then flips each produced triangle.
     * This keeps decode-time output aligned with Cake's default backface-culling path.
     */
    fun unpack(): List<Md2VertexInfo> {
        if (vertices.size < 3) {
            return emptyList()
        }
        val unpacked = when (type) {
            Md2GlCmdType.TRIANGLE_STRIP -> buildList((vertices.size - 2) * 3) {
                // OpenGL strip assembly:
                // i=0: (v0,v1,v2), i=1: (v2,v1,v3), i=2: (v2,v3,v4), ...
                for (i in 0 until vertices.size - 2) {
                    if ((i and 1) == 0) {
                        add(vertices[i])
                        add(vertices[i + 1])
                        add(vertices[i + 2])
                    } else {
                        add(vertices[i + 1])
                        add(vertices[i])
                        add(vertices[i + 2])
                    }
                }
            }
            Md2GlCmdType.TRIANGLE_FAN -> buildList((vertices.size - 2) * 3) {
                // OpenGL fan assembly:
                // (v0,v1,v2), (v0,v2,v3), (v0,v3,v4), ...
                val center = vertices.first()
                for (i in 1 until vertices.lastIndex) {
                    add(center)
                    add(vertices[i])
                    add(vertices[i + 1])
                }
            }
        }
        // Flip every triangle from (a,b,c) -> (a,c,b).
        return buildList(unpacked.size) {
            for (i in unpacked.indices step 3) {
                add(unpacked[i])
                add(unpacked[i + 2])
                add(unpacked[i + 1])
            }
        }
    }

    /**
     * Convert indexed vertices into actual vertex buffer data.
     *
     * Also convert triangle strips and fans into sets of independent triangles.
     * It may waste a bit of VRAM, but makes it much easier to draw,
     * using a single drawElements(GL_TRIANGLES, ...) call.
     *
     */
    fun toVertexAttributes(
        framePositions: List<Md2Point>,
        returnTexCoords: Boolean = true,
    ): List<Float> {
        return unpack().flatMap { it.toFloats(framePositions, returnTexCoords) }
    }
}

/**
 * daliasframe_t
 * A frame in the MD2 model, represents a frame in the model animation, contains coordinates and normals of all vertices
 *
 * Frame header:
 *   - 3 floats: scale(xyz)
 *   - 3 floats: translation(xyz)
 *   - 16 bytes: name
 *   - number of vertices:
 *      - 4 bytes: packed normal index + x, y, z position
 *      [name] - frame name from grabbing (size 16)
 */
class Md2Frame(val name: String,  val points: List<Md2Point>) {
    companion object {
        fun fromBuffer(buffer: ByteBuffer, vertexCount: Int): Md2Frame {
            val scale = Vector3f(
                buffer.getFloat(),
                buffer.getFloat(),
                buffer.getFloat()
            )
            val translate = Vector3f(
                buffer.getFloat(),
                buffer.getFloat(),
                buffer.getFloat()
            )
            val nameBuf = ByteArray(16)
            buffer.get(nameBuf)
            val name = String(nameBuf).trim { it < ' ' }
            val points: ArrayList<Md2Point> = ArrayList(vertexCount)
            repeat(vertexCount) {
                // vertices are all 8 bit, so no swapping needed
                // 4 bytes:
                // highest - normal index
                // x y z
                val vertexData = buffer.getInt()
                // unpack vertex data
                points.add(
                    Md2Point(
                        Vector3f(
                            scale.x * (vertexData ushr 0 and 0xFF),
                            scale.y * (vertexData ushr 8 and 0xFF),
                            scale.z * (vertexData ushr 16 and 0xFF)
                        ) + translate,
                        vertexData ushr 24 and 0xFF
                    )
                )
            }
            return Md2Frame(name, points)
        }
    }

    override fun toString(): String {
        return name
    }
}

data class Md2Point(val position: Vector3f, val normalIndex: Int)
