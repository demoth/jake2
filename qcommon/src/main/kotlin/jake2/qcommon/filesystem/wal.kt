package jake2.qcommon.filesystem

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Quake2 WAL texture image format (miptex_t)
 *
 * Quake 2 stores textures in a proprietary 2D image format called WAL.
*
 * WAL textures are stored in a 8-bit indexed color format with a specific palette being used by all textures
 * (this palette is stored in the PAK data file that comes with Quake 2).
 * Four mip-map levels are stored for each texture at sizes decreasing by a factor of two.
 * This is mostly for software rendering since most 3D APIs will automatically generate the mip-map levels
 * when you create a texture. Each frame of an animated texture is stored as an individual WAL file,
 * and the animation sequence is encoded by storing the name of the next texture in the sequence for each frame;
 * texture names are stored with paths and without any extension.
 *
 * The actual texture data is stored in an 8-bits-per-pixel raw format in a left-right, top-down order.
 *
 */
class WAL(buffer: ByteBuffer) {
    //
    // HEADER
    //
    /**
     * name of the texture, 32 bytes
     */
    val name: String
    /**
     * width (in pixels) of the largest mipmap level
     */
    val width: Int // fixme: unsigned
    /**
     * height (in pixels) of the largest mipmap level
     */
    val height: Int // fixme: unsigned
    /**
     * byte offsets of the start of each of the 4 mipmap levels, starting with largest
     */
    val offsets: IntArray

    /**
     * name of the next texture in the animation, 32 bytes
     */
    val nextName: String

    // TODO:
    val flags: Int // fixme: unsigned
    val contents: Int  // fixme: unsigned
    val value: Int  // fixme: unsigned

    //
    // BODY
    //
    val imageData: ByteArray

    constructor(byteArray: ByteArray): this(ByteBuffer.wrap(byteArray).also { it.order(ByteOrder.LITTLE_ENDIAN) })

    init {
        name = String(ByteArray(32).also { buffer.get(it) }).trim { it < ' '}
        width = buffer.getInt()
        height = buffer.getInt()
        offsets = IntArray(MIP_LEVELS) { buffer.getInt() }
        nextName = String(ByteArray(32).also { buffer.get(it) }).trim { it < ' '}
        flags = buffer.getInt()
        contents = buffer.getInt()
        value = buffer.getInt()

        // read the largest MIP
        buffer.position(offsets.first())
        imageData = ByteArray(width * height)
        buffer.get(imageData)
    }
}

const val MIP_LEVELS = 4
