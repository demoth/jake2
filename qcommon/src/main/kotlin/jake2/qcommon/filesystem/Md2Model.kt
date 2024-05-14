package jake2.qcommon.filesystem

import java.nio.ByteBuffer

const val IDALIASHEADER: Int = (('2'.code shl 24) + ('P'.code shl 16) + ('D'.code shl 8) + 'I'.code)
const val ALIAS_VERSION: Int = 8

/**
 * MD2 (quake2) model (previously named dmdl_t)
 *
 *
 * Also known as "alias model" in the code - named after the model editor originally used (PowerAnimator)
 *
 *
 * Header section contains size information (how many vertices, frames, skins, etc)
 * and byte offsets to the respective sections.
 * Body section are the data arrays.
 *
 *
 * The GL commands (glcmd) format:
 *
 *  *  positive integer starts a triangle strip command, followed by that many vertex structures.
 *  *  negative integer starts a triangle fan command, followed by -x vertexes
 *  *  zero indicates the end of the command list.
 *
 *
 *
 * A vertex consists of a floating point s, a floating point and an integer vertex index.
 */
class Md2Model(b: ByteBuffer) {
    // Header
    val ident: Int
    val version: Int
    val num_skins: Int
    val num_vertices: Int

    val num_tris: Int
    val num_glcmds: Int // dwords in strip/fan command list
    val num_frames: Int

    // each skin is a MAX_SKINNAME string
    val skinsOffset: Int
    val firstFrameOffset: Int
    val glCommandsOffset: Int
    val endOfFileOffset: Int // end of file

    // Body
    val skinNames: Array<String?>
    val glCmds: IntArray
    val glCommands: List<Md2GlCmd>

    // all frames have vertex array of equal size (num_xyz)
    val frames: List<Md2Frame>

    init {
        ident = b.getInt()
        check (ident == IDALIASHEADER) {
            "Wrong .md2 identifier: $ident"
        }

        version = b.getInt()
        check(version == ALIAS_VERSION) {
            "Wrong .md2 version: $version"
        }

        b.getInt() // skinwidth
        b.getInt() // skinheight
        b.getInt() // framesize: byte size of each frame

        num_skins = b.getInt()
        num_vertices = b.getInt()
        b.getInt() //num_st: greater than num_xyz for seams, parsed into dstvert_t
        num_tris = b.getInt()
        num_glcmds = b.getInt() // dwords in strip/fan command list
        num_frames = b.getInt()

        skinsOffset = b.getInt() // each skin is a MAX_SKINNAME string
        b.getInt() // offset from start until dstvert_t[]
        b.getInt() // offset from start until dtriangles[]
        firstFrameOffset = b.getInt() // offset for first frame
        glCommandsOffset = b.getInt()
        endOfFileOffset = b.getInt() // end of file

        //
        //	   load the frames
        //
        frames = ArrayList(num_frames)
        b.position(firstFrameOffset)
        for (i in 0 until num_frames) {
            frames.add(Md2Frame(b, num_vertices))
        }

        //
        // load the glcmds
        // STRIP or FAN
        glCmds = IntArray(num_glcmds)
        b.position(glCommandsOffset)
        for (i in 0 until num_glcmds) {
            glCmds[i] = b.getInt()
        }

        glCommands = ArrayList()
        var i = 0
        while (true) {
            val command = glCmds[i]
            if (command == 0)
                break
            val cmdType = if (command > 0)
                Md2GlCmdType.TRIANGLE_STRIP
            else
                Md2GlCmdType.TRIANGLE_FAN

            val numVertices = if (command > 0) command else -command

            val vertices = ArrayList<Md2VertexInfo>(numVertices)

            for (j: Int in 0 until numVertices) {
                val vertexIndex = b.getInt()
                val s = b.getFloat()
                val t = b.getFloat()
                vertices.add(Md2VertexInfo(vertexIndex, s, t))
            }
            glCommands.add(Md2GlCmd(cmdType, vertices))

            i++

        }


        skinNames = arrayOfNulls(num_skins)
        val nameBuf = ByteArray(qfiles.MAX_SKINNAME)
        b.position(skinsOffset)
        for (i in 0 until num_skins) {
            b[nameBuf]
            skinNames[i] = String(nameBuf)
            val n = skinNames[i]!!.indexOf('\u0000')
            if (n > -1) {
                skinNames[i] = skinNames[i]!!.substring(0, n)
            }
        }

    }
}

enum class Md2GlCmdType {
    TRIANGLE_STRIP,
    TRIANGLE_FAN,
}

data class Md2VertexInfo(val index: Int, val s: Float, val t: Float)

data class Md2GlCmd(
    val type: Md2GlCmdType,
    val vertices: List<Md2VertexInfo>,
)
