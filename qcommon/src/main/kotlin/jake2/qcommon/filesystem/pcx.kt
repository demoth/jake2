package jake2.qcommon.filesystem

import java.nio.ByteBuffer
import java.nio.ByteOrder

// size of byte arrays
private const val PCX_COLORMAP_SIZE = 48
private const val PCX_FILLER_SIZE = 58
const val PCX_PALETTE_SIZE = 768
const val PCX_PALLETE_OFFSET = 769

/**
 * pcx_t
 */
class PCX(b: ByteBuffer) {
    val manufacturer: Byte
    val version: Byte
    val encoding: Byte
    val bits_per_pixel: Byte
    val xmin: Int
    val ymin: Int
    val xmax: Int
    val ymax: Int // unsigned short
    val hres: Int
    val vres: Int // unsigned short
    val colorMap = ByteArray(PCX_COLORMAP_SIZE) //unsigned byte; size 48
    val reserved: Byte
    val color_planes: Byte
    val bytes_per_line: Int // unsigned short
    val palette_type: Int // unsigned short
    val filler = ByteArray(PCX_FILLER_SIZE) // size 58
    val data: ByteBuffer //unbounded data

    constructor(dataBytes: ByteArray) : this(ByteBuffer.wrap(dataBytes))

    init {
        b.order(ByteOrder.LITTLE_ENDIAN)

        // fill header
        manufacturer = b.get()
        version = b.get()
        encoding = b.get()
        bits_per_pixel = b.get()
        xmin = b.getShort().toInt() and 0xffff
        ymin = b.getShort().toInt() and 0xffff
        xmax = b.getShort().toInt() and 0xffff
        ymax = b.getShort().toInt() and 0xffff
        hres = b.getShort().toInt() and 0xffff
        vres = b.getShort().toInt() and 0xffff
        b.get(colorMap)
        reserved = b.get()
        color_planes = b.get()
        bytes_per_line = b.getShort().toInt() and 0xffff
        palette_type = b.getShort().toInt() and 0xffff
        b.get(filler)

        // fill data
        data = b.slice()

        check(
            (manufacturer == 0x0a.toByte()
                    && version == 5.toByte()
                    && encoding == 1.toByte()
                    && bits_per_pixel == 8.toByte()
                    && xmax < 640
                    && ymax < 480)
        ) {
            "Bad pcx file"
        }

    }
}
