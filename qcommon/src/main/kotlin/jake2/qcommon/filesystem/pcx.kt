package jake2.qcommon.filesystem

import java.nio.ByteBuffer
import java.nio.ByteOrder

// size of byte arrays
private const val PCX_EGA_PALETTE_SIZE = 48
private const val PCX_FILLER_SIZE = 54
const val PCX_PALETTE_SIZE = 768 // bytes

// Run-Length Encoding
private const val RLE_UPPER = 0xC0 // top 2 bits indicate a RLE compression
private const val RLE_LOWER = 0x3F // last 6 bits is the number of the same pixels


/**
 * PCX, standing for PiCture eXchange, is an image file format developed by the ZSoft Corporation.
 * pcx_t
 */
class PCX(buffer: ByteBuffer) {
    //
    // HEADER
    //

    /**
     * The fixed header field valued at a hexadecimal 0x0A (= 10 in decimal).
     */
    val manufacturer: Byte

    /**
     * The version number referring to the Paintbrush software release, which might be:
     * * 0 - PC Paintbrush version 2.5 using a fixed EGA palette
     * * 2 - PC Paintbrush version 2.8 using a modifiable EGA palette
     * * 3 - PC Paintbrush version 2.8 using no palette
     * * 4 - PC Paintbrush for Windows
     * * 5 - PC Paintbrush version 3.0, including 24-bit images
     */
    val version: Byte

    /**
     * The method used for encoding the image data. Can be:
     * 0 - No encoding (rarely used)
     * 1 - Run-length encoding (RLE)
     */
    val encoding: Byte

    /**
     * The number of bits constituting one plane. Most often 1, 2, 4 or 8.
     */
    val bits_per_pixel: Byte
    val xmin: Int // unsigned short
    val ymin: Int // unsigned short
    val xmax: Int // unsigned short
    val ymax: Int // unsigned short
    val hres: Int // unsigned short
    val vres: Int // unsigned short
    val width: Int
    val height: Int

    /**
     * The EGA palette for 16-color images (unused in q2)
     */
    private val egaPalette = ByteArray(PCX_EGA_PALETTE_SIZE) //unsigned byte; size 48

    /**
     * The first reserved field, usually set to zero.
     */
    private val reserved: Byte

    /**
     * The number of color planes constituting the pixel data. Mostly chosen to be 1, 3, or 4.
     */
    val color_planes: Byte

    /**
     * The number of bytes of one color plane representing a single scan line.
     */
    val bytes_per_line: Int // unsigned short

    /**
     * The mode in which to construe the palette:
     * * 1 - The palette contains monochrome or color information
     * * 2 - The palette contains grayscale information
     */
    val palette_type: Int // unsigned short
    val hScreenSize: Int // unsigned short
    val vScreenSize: Int // unsigned short

    // Data arrays

    /**
     * A PCX file has space in its header for a 16 color palette.
     * When 256-color VGA hardware became available there was not enough space for the palette in a PCX file;
     * even the 54 unused bytes after the header would not be enough.
     * The solution chosen was to put the palette at the end of the file, along with a marker byte to confirm its existence.
     * If a PCX file has a 256-color palette, it is found 768 bytes from the end of the file.
     * In this case the value in the byte preceding the palette should be 12 (0x0C).
     * The palette is stored as a sequence of RGB triples;
     * its usable length is defined by the number of colors in the image.
     * Colors values in a PCX palette always use 8 bits, regardless of the bit depth of the image.
     */
    private val palette = ByteArray(PCX_PALETTE_SIZE) // used to reconstruct the colors array
    val colors = IntArray(256) // RGBA8888

    /**
     * actual pixel data, each byte pointing to the colors map
     */
    val imageData: ByteArray
    private val filler = ByteArray(PCX_FILLER_SIZE) // size 58
    // data part that comes after the header
    private val data: ByteBuffer //unbounded data

    constructor(dataBytes: ByteArray) : this(ByteBuffer.wrap(dataBytes))

    init {
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        // fill header
        manufacturer = buffer.get()
        version = buffer.get()
        encoding = buffer.get()
        bits_per_pixel = buffer.get()
        xmin = buffer.getShort().toInt() and 0xFFFF
        ymin = buffer.getShort().toInt() and 0xFFFF
        xmax = buffer.getShort().toInt() and 0xFFFF
        ymax = buffer.getShort().toInt() and 0xFFFF
        hres = buffer.getShort().toInt() and 0xFFFF
        vres = buffer.getShort().toInt() and 0xFFFF
        buffer.get(egaPalette)
        reserved = buffer.get()
        color_planes = buffer.get()
        bytes_per_line = buffer.getShort().toInt() and 0xFFFF
        palette_type = buffer.getShort().toInt() and 0xFFFF
        hScreenSize = buffer.getShort().toInt() and 0xFFFF
        vScreenSize = buffer.getShort().toInt() and 0xFFFF
        buffer.get(filler)

        // fill data
        data = buffer.slice()

        // Q2 supports only a subset of all possible formats fixme: why not support all?
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

        // parse image data
        width = xmax - xmin + 1
        height = ymax - ymin + 1
        imageData = ByteArray(bytes_per_line * height) // same as width * height?
        var index = 0
        while (index < imageData.size) {
            val dataByte = data.get().toInt() and 0xFF // unsigned
            if ((dataByte and RLE_UPPER) == RLE_UPPER) {
                // Run length encoding inflation
                val runLength = dataByte and RLE_LOWER
                val nextByte = data.get().toInt() and 0xFF
                // write runLength pixels
                repeat(runLength) {
                    imageData[index++] = nextByte.toByte()
                }
            } else {
                // write one pixel
                imageData[index++] = dataByte.toByte()
            }
        }

        // read the palette, located at the end of the file
        // skip to byte -769
        data.position(data.limit() - PCX_PALETTE_SIZE - 1)
        data.get(palette)

        // read colors
        for (i in 0..255) {
            val r: Int = 0xFF and palette.get(i * 3).toInt()
            val g: Int = 0xFF and palette.get(i * 3 + 1).toInt()
            val b: Int = 0xFF and palette.get(i * 3 + 2).toInt()
            colors[i] = (r shl 24) or (g shl 16) or (b shl 8) or 0xFF // alpha
        }
    }
}
