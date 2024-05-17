package jake2.qcommon.filesystem

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
            frames.add(Md2Frame(buffer, verticesCount))
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

enum class Md2GlCmdType {
    TRIANGLE_STRIP,
    TRIANGLE_FAN,
}

data class Md2VertexInfo(val index: Int, val s: Float, val t: Float) {
    /**
     * create a vertex buffer part for this particular vertex (x y z s t)
     */
    fun toFloats(points: List<Md2Point>): List<Float> {
        val p = points[index].position // todo: check bounds
        return listOf(p.x, p.y, p.z, s, t)
    }
}

data class Md2GlCmd(
    val type: Md2GlCmdType,
    val vertices: List<Md2VertexInfo>,
) {
    /**
     * Convert indexed vertices into actual vertex buffer data.
     *
     * Also convert triangle strips and fans into sets of independent triangles.
     * It may waste a bit of VRAM, but makes it much easier to draw,
     * using a single drawElements(GL_TRIANGLES, ...) call.
     */
    fun toFloats(points: List<Md2Point>): List<Float> {
        val result = when (type) {
            Md2GlCmdType.TRIANGLE_STRIP -> {
                // (0, 1, 2, 3, 4) -> (0, 1, 2), (1, 2, 3), (2, 3, 4)
                // when converting a triangle strip into a set of separate triangles,
                // need to alternate the winding direction
                var clockwise = true
                vertices.windowed(3).flatMap { strip ->
                    clockwise = !clockwise
                    if (clockwise) {
                        strip[0].toFloats(points) + strip[1].toFloats(points) + strip[2].toFloats(points)
                    } else {
                        strip[2].toFloats(points) + strip[1].toFloats(points) + strip[0].toFloats(points)
                    }
                }
            }

            Md2GlCmdType.TRIANGLE_FAN -> {
                // (0, 1, 2, 3, 4) -> (0, 1, 2), (0, 2, 3), (0, 3, 4)
                vertices.drop(1).windowed(2).flatMap { strip ->
                    strip[1].toFloats(points) + strip[0].toFloats(points) + vertices.first().toFloats(points)
                }
            }
        }
        return result
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
 */
class Md2Frame(buffer: ByteBuffer, vertexCount: Int) {
    val points: List<Md2Point>
    val name: String // frame name from grabbing (size 16)

    init {
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
        name = String(nameBuf).trim { it < ' '}

        points = ArrayList(vertexCount)
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
    }

    override fun toString(): String {
        return name
    }
}

data class Md2Point(val position: Vector3f, val normalIndex: Int)
