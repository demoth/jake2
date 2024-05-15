package org.demoth.cake.modelviewer

import java.nio.ByteBuffer

/**
 * daliasframe_t
 * A frame in the MD2 model
 */

data class Point(
    val x: Int,
    val y: Int,
    val z: Int,
    val normalIndex: Int)

/**
 * Header:
 *  gl cmd: +3, s1 t1 i1, s2 t2 i2, s3 t3 i3,
 *          -5 i1 i2 i3 i4 i5
 *   * ---- *
 *   |\     |
 *   | \    |
 *   |  \   |
 *   |   \  |
 *   |    \ |
 *   * ---- *
 */

class Md2Frame(buffer: ByteBuffer, num_xyz: Int) {
    var scale = floatArrayOf(0f, 0f, 0f) // multiply byte verts by this
    var translate = floatArrayOf(0f, 0f, 0f) // then add this
    var name // frame name from grabbing (size 16)
            : String
    var verts: IntArray
    val points: Array<Point>

    init {
        scale[0] = buffer.float
        scale[1] = buffer.float
        scale[2] = buffer.float
        translate[0] = buffer.float
        translate[1] = buffer.float
        translate[2] = buffer.float
        val nameBuf = ByteArray(16)
        buffer[nameBuf]
        name = String(nameBuf).trim { it <= ' ' }

        // vertices are all 8 bit, so no swapping needed
        verts = IntArray(num_xyz)
        points = Array(num_xyz) { Point(0, 0, 0, 0) }


        for (k in 0 until num_xyz) {
            verts[k] = buffer.int
            points[k] = Point(
                verts[k] ushr 0 and 0xFF,
                verts[k] ushr 8 and 0xFF,
                verts[k] ushr 16 and 0xFF,
                verts[k] ushr 24 and 0xFF
            )
        }
    }
}

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
 *  *  positive integer starts a tristrip command, followed by that many vertex structures.
 *  *  negative integer starts a trifan command, followed by -x vertexes
 *  *  zero indicates the end of the command list.
 *
 *
 *
 * A vertex consists of a floating point s, a floating point and an integer vertex index.
 */
class Md2Model(b: ByteBuffer) {
    // Header
    var ident: Int
    var version: Int
    var num_skins: Int
    var num_vertices: Int
    var num_tris: Int
    var num_glcmds: Int // dwords in strip/fan command list

    var num_frames: Int

    // each skin is a MAX_SKINNAME string
    var skinsOffset: Int
    var firstFrameOffset: Int
    var glCommandsOffset: Int
    var endOfFileOffset: Int // end of file


    // Body
    var skinNames: Array<String?> = arrayOf()
    var glCmds: IntArray = intArrayOf()

    // all frames have vertex array of equal size (num_xyz)
    var frames: Array<Md2Frame?> = arrayOf()

    constructor(b: ByteBuffer, modelName: String) : this(b) {
        loadBody(b)
    }

    /**
     * Reads md2 header
     */
    init {
        ident = b.int
        version = b.int
        b.int // skinwidth
        b.int // skinheight
        b.int // framesize: byte size of each frame
        num_skins = b.int
        num_vertices = b.int
        b.int //num_st: greater than num_xyz for seams, parsed into dstvert_t
        num_tris = b.int
        num_glcmds = b.int // dwords in strip/fan command list
        num_frames = b.int
        skinsOffset = b.int // each skin is a MAX_SKINNAME string
        b.int // offset from start until dstvert_t[]
        b.int // offset from start until dtriangles[]
        firstFrameOffset = b.int // offset for first frame
        glCommandsOffset = b.int
        endOfFileOffset = b.int // end of file
    }

    /**
     * Loads the bulk of the data
     */
    private fun loadBody(buffer: ByteBuffer) {
        //
        //	   load the frames
        //
        frames = arrayOfNulls(num_frames)
        buffer.position(firstFrameOffset)
        for (i in 0 until num_frames) {
            frames[i] = Md2Frame(buffer, num_vertices)
        }

        //
        // load the glcmds
        // STRIP or FAN
        glCmds = IntArray(num_glcmds)
        buffer.position(glCommandsOffset)
        for (i in 0 until num_glcmds) {
            glCmds[i] = buffer.int
        }
        skinNames = arrayOfNulls(num_skins)
        val nameBuf = ByteArray(64)
        buffer.position(skinsOffset)
        for (i in 0 until num_skins) {
            buffer[nameBuf]
            skinNames[i] = String(nameBuf)
            val n = skinNames[i]!!.indexOf('\u0000')
            if (n > -1) {
                skinNames[i] = skinNames[i]!!.substring(0, n)
            }
        }
    }
}

val VERTEXNORMALS: Array<FloatArray> = arrayOf(
    floatArrayOf(-0.525731f, 0.000000f, 0.850651f),
    floatArrayOf(-0.442863f, 0.238856f, 0.864188f),
    floatArrayOf(-0.295242f, 0.000000f, 0.955423f),
    floatArrayOf(-0.309017f, 0.500000f, 0.809017f),
    floatArrayOf(-0.162460f, 0.262866f, 0.951056f),
    floatArrayOf(0.000000f, 0.000000f, 1.000000f),
    floatArrayOf(0.000000f, 0.850651f, 0.525731f),
    floatArrayOf(-0.147621f, 0.716567f, 0.681718f),
    floatArrayOf(0.147621f, 0.716567f, 0.681718f),
    floatArrayOf(0.000000f, 0.525731f, 0.850651f),
    floatArrayOf(0.309017f, 0.500000f, 0.809017f),
    floatArrayOf(0.525731f, 0.000000f, 0.850651f),
    floatArrayOf(0.295242f, 0.000000f, 0.955423f),
    floatArrayOf(0.442863f, 0.238856f, 0.864188f),
    floatArrayOf(0.162460f, 0.262866f, 0.951056f),
    floatArrayOf(-0.681718f, 0.147621f, 0.716567f),
    floatArrayOf(-0.809017f, 0.309017f, 0.500000f),
    floatArrayOf(-0.587785f, 0.425325f, 0.688191f),
    floatArrayOf(-0.850651f, 0.525731f, 0.000000f),
    floatArrayOf(-0.864188f, 0.442863f, 0.238856f),
    floatArrayOf(-0.716567f, 0.681718f, 0.147621f),
    floatArrayOf(-0.688191f, 0.587785f, 0.425325f),
    floatArrayOf(-0.500000f, 0.809017f, 0.309017f),
    floatArrayOf(-0.238856f, 0.864188f, 0.442863f),
    floatArrayOf(-0.425325f, 0.688191f, 0.587785f),
    floatArrayOf(-0.716567f, 0.681718f, -0.147621f),
    floatArrayOf(-0.500000f, 0.809017f, -0.309017f),
    floatArrayOf(-0.525731f, 0.850651f, 0.000000f),
    floatArrayOf(0.000000f, 0.850651f, -0.525731f),
    floatArrayOf(-0.238856f, 0.864188f, -0.442863f),
    floatArrayOf(0.000000f, 0.955423f, -0.295242f),
    floatArrayOf(-0.262866f, 0.951056f, -0.162460f),
    floatArrayOf(0.000000f, 1.000000f, 0.000000f),
    floatArrayOf(0.000000f, 0.955423f, 0.295242f),
    floatArrayOf(-0.262866f, 0.951056f, 0.162460f),
    floatArrayOf(0.238856f, 0.864188f, 0.442863f),
    floatArrayOf(0.262866f, 0.951056f, 0.162460f),
    floatArrayOf(0.500000f, 0.809017f, 0.309017f),
    floatArrayOf(0.238856f, 0.864188f, -0.442863f),
    floatArrayOf(0.262866f, 0.951056f, -0.162460f),
    floatArrayOf(0.500000f, 0.809017f, -0.309017f),
    floatArrayOf(0.850651f, 0.525731f, 0.000000f),
    floatArrayOf(0.716567f, 0.681718f, 0.147621f),
    floatArrayOf(0.716567f, 0.681718f, -0.147621f),
    floatArrayOf(0.525731f, 0.850651f, 0.000000f),
    floatArrayOf(0.425325f, 0.688191f, 0.587785f),
    floatArrayOf(0.864188f, 0.442863f, 0.238856f),
    floatArrayOf(0.688191f, 0.587785f, 0.425325f),
    floatArrayOf(0.809017f, 0.309017f, 0.500000f),
    floatArrayOf(0.681718f, 0.147621f, 0.716567f),
    floatArrayOf(0.587785f, 0.425325f, 0.688191f),
    floatArrayOf(0.955423f, 0.295242f, 0.000000f),
    floatArrayOf(1.000000f, 0.000000f, 0.000000f),
    floatArrayOf(0.951056f, 0.162460f, 0.262866f),
    floatArrayOf(0.850651f, -0.525731f, 0.000000f),
    floatArrayOf(0.955423f, -0.295242f, 0.000000f),
    floatArrayOf(0.864188f, -0.442863f, 0.238856f),
    floatArrayOf(0.951056f, -0.162460f, 0.262866f),
    floatArrayOf(0.809017f, -0.309017f, 0.500000f),
    floatArrayOf(0.681718f, -0.147621f, 0.716567f),
    floatArrayOf(0.850651f, 0.000000f, 0.525731f),
    floatArrayOf(0.864188f, 0.442863f, -0.238856f),
    floatArrayOf(0.809017f, 0.309017f, -0.500000f),
    floatArrayOf(0.951056f, 0.162460f, -0.262866f),
    floatArrayOf(0.525731f, 0.000000f, -0.850651f),
    floatArrayOf(0.681718f, 0.147621f, -0.716567f),
    floatArrayOf(0.681718f, -0.147621f, -0.716567f),
    floatArrayOf(0.850651f, 0.000000f, -0.525731f),
    floatArrayOf(0.809017f, -0.309017f, -0.500000f),
    floatArrayOf(0.864188f, -0.442863f, -0.238856f),
    floatArrayOf(0.951056f, -0.162460f, -0.262866f),
    floatArrayOf(0.147621f, 0.716567f, -0.681718f),
    floatArrayOf(0.309017f, 0.500000f, -0.809017f),
    floatArrayOf(0.425325f, 0.688191f, -0.587785f),
    floatArrayOf(0.442863f, 0.238856f, -0.864188f),
    floatArrayOf(0.587785f, 0.425325f, -0.688191f),
    floatArrayOf(0.688191f, 0.587785f, -0.425325f),
    floatArrayOf(-0.147621f, 0.716567f, -0.681718f),
    floatArrayOf(-0.309017f, 0.500000f, -0.809017f),
    floatArrayOf(0.000000f, 0.525731f, -0.850651f),
    floatArrayOf(-0.525731f, 0.000000f, -0.850651f),
    floatArrayOf(-0.442863f, 0.238856f, -0.864188f),
    floatArrayOf(-0.295242f, 0.000000f, -0.955423f),
    floatArrayOf(-0.162460f, 0.262866f, -0.951056f),
    floatArrayOf(0.000000f, 0.000000f, -1.000000f),
    floatArrayOf(0.295242f, 0.000000f, -0.955423f),
    floatArrayOf(0.162460f, 0.262866f, -0.951056f),
    floatArrayOf(-0.442863f, -0.238856f, -0.864188f),
    floatArrayOf(-0.309017f, -0.500000f, -0.809017f),
    floatArrayOf(-0.162460f, -0.262866f, -0.951056f),
    floatArrayOf(0.000000f, -0.850651f, -0.525731f),
    floatArrayOf(-0.147621f, -0.716567f, -0.681718f),
    floatArrayOf(0.147621f, -0.716567f, -0.681718f),
    floatArrayOf(0.000000f, -0.525731f, -0.850651f),
    floatArrayOf(0.309017f, -0.500000f, -0.809017f),
    floatArrayOf(0.442863f, -0.238856f, -0.864188f),
    floatArrayOf(0.162460f, -0.262866f, -0.951056f),
    floatArrayOf(0.238856f, -0.864188f, -0.442863f),
    floatArrayOf(0.500000f, -0.809017f, -0.309017f),
    floatArrayOf(0.425325f, -0.688191f, -0.587785f),
    floatArrayOf(0.716567f, -0.681718f, -0.147621f),
    floatArrayOf(0.688191f, -0.587785f, -0.425325f),
    floatArrayOf(0.587785f, -0.425325f, -0.688191f),
    floatArrayOf(0.000000f, -0.955423f, -0.295242f),
    floatArrayOf(0.000000f, -1.000000f, 0.000000f),
    floatArrayOf(0.262866f, -0.951056f, -0.162460f),
    floatArrayOf(0.000000f, -0.850651f, 0.525731f),
    floatArrayOf(0.000000f, -0.955423f, 0.295242f),
    floatArrayOf(0.238856f, -0.864188f, 0.442863f),
    floatArrayOf(0.262866f, -0.951056f, 0.162460f),
    floatArrayOf(0.500000f, -0.809017f, 0.309017f),
    floatArrayOf(0.716567f, -0.681718f, 0.147621f),
    floatArrayOf(0.525731f, -0.850651f, 0.000000f),
    floatArrayOf(-0.238856f, -0.864188f, -0.442863f),
    floatArrayOf(-0.500000f, -0.809017f, -0.309017f),
    floatArrayOf(-0.262866f, -0.951056f, -0.162460f),
    floatArrayOf(-0.850651f, -0.525731f, 0.000000f),
    floatArrayOf(-0.716567f, -0.681718f, -0.147621f),
    floatArrayOf(-0.716567f, -0.681718f, 0.147621f),
    floatArrayOf(-0.525731f, -0.850651f, 0.000000f),
    floatArrayOf(-0.500000f, -0.809017f, 0.309017f),
    floatArrayOf(-0.238856f, -0.864188f, 0.442863f),
    floatArrayOf(-0.262866f, -0.951056f, 0.162460f),
    floatArrayOf(-0.864188f, -0.442863f, 0.238856f),
    floatArrayOf(-0.809017f, -0.309017f, 0.500000f),
    floatArrayOf(-0.688191f, -0.587785f, 0.425325f),
    floatArrayOf(-0.681718f, -0.147621f, 0.716567f),
    floatArrayOf(-0.442863f, -0.238856f, 0.864188f),
    floatArrayOf(-0.587785f, -0.425325f, 0.688191f),
    floatArrayOf(-0.309017f, -0.500000f, 0.809017f),
    floatArrayOf(-0.147621f, -0.716567f, 0.681718f),
    floatArrayOf(-0.425325f, -0.688191f, 0.587785f),
    floatArrayOf(-0.162460f, -0.262866f, 0.951056f),
    floatArrayOf(0.442863f, -0.238856f, 0.864188f),
    floatArrayOf(0.162460f, -0.262866f, 0.951056f),
    floatArrayOf(0.309017f, -0.500000f, 0.809017f),
    floatArrayOf(0.147621f, -0.716567f, 0.681718f),
    floatArrayOf(0.000000f, -0.525731f, 0.850651f),
    floatArrayOf(0.425325f, -0.688191f, 0.587785f),
    floatArrayOf(0.587785f, -0.425325f, 0.688191f),
    floatArrayOf(0.688191f, -0.587785f, 0.425325f),
    floatArrayOf(-0.955423f, 0.295242f, 0.000000f),
    floatArrayOf(-0.951056f, 0.162460f, 0.262866f),
    floatArrayOf(-1.000000f, 0.000000f, 0.000000f),
    floatArrayOf(-0.850651f, 0.000000f, 0.525731f),
    floatArrayOf(-0.955423f, -0.295242f, 0.000000f),
    floatArrayOf(-0.951056f, -0.162460f, 0.262866f),
    floatArrayOf(-0.864188f, 0.442863f, -0.238856f),
    floatArrayOf(-0.951056f, 0.162460f, -0.262866f),
    floatArrayOf(-0.809017f, 0.309017f, -0.500000f),
    floatArrayOf(-0.864188f, -0.442863f, -0.238856f),
    floatArrayOf(-0.951056f, -0.162460f, -0.262866f),
    floatArrayOf(-0.809017f, -0.309017f, -0.500000f),
    floatArrayOf(-0.681718f, 0.147621f, -0.716567f),
    floatArrayOf(-0.681718f, -0.147621f, -0.716567f),
    floatArrayOf(-0.850651f, 0.000000f, -0.525731f),
    floatArrayOf(-0.688191f, 0.587785f, -0.425325f),
    floatArrayOf(-0.587785f, 0.425325f, -0.688191f),
    floatArrayOf(-0.425325f, 0.688191f, -0.587785f),
    floatArrayOf(-0.425325f, -0.688191f, -0.587785f),
    floatArrayOf(-0.587785f, -0.425325f, -0.688191f),
    floatArrayOf(-0.688191f, -0.587785f, -0.425325f)
)


