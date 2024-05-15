package jake2.qcommon.filesystem

import java.lang.Float.intBitsToFloat
import java.nio.ByteBuffer

const val IDALIASHEADER: Int = (('2'.code shl 24) + ('P'.code shl 16) + ('D'.code shl 8) + 'I'.code)
const val ALIAS_VERSION: Int = 8

/**
 * MD2 (quake2) model (previously named dmdl_t)
 *
 * Also known as "alias model" in the code - named after the model editor originally used (PowerAnimator)
 *
 * Header section contains size information (how many vertices, frames, skins, etc)
 * and byte offsets to the respective sections.
 * Body section are the data arrays.
 *
 * The GL commands (glcmd) format:
 *
 *  *  positive integer starts a triangle strip command, followed by that many vertex structures.
 *  *  negative integer starts a triangle fan command, followed by -x vertexes
 *  *  zero indicates the end of the command list.
 *
 * A vertex consists of a floating point s, a floating point and an integer vertex index.
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
        val nameBuf = ByteArray(qfiles.MAX_SKINNAME)
        buffer.position(skinsOffset)
        repeat(skinCount) {
            buffer.get(nameBuf)
            skinNames.add(String(nameBuf).trim { it < ' ' })
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
    fun toFloats(points: List<Point>): List<Float> {
        val p = points[index].position // todo: check bounds
        return listOf(p.x, p.y, p.z, s, t)
    }
}

data class Md2GlCmd(
    val type: Md2GlCmdType,
    val vertices: List<Md2VertexInfo>,
) {
    /**
     * Convert triangle strips and fans into sets of independent triangles.
     * It may waste a bit of VRAM, but makes it much easier to draw,
     * using a single drawElements(GL_TRIANGLES, ...) call.
     */
    fun toFloatArray(points: List<Point>): FloatArray {
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
                    vertices.first().toFloats(points) + strip[0].toFloats(points) + strip[1].toFloats(points)
                }
            }
        }
        return result.toFloatArray()
    }
}
