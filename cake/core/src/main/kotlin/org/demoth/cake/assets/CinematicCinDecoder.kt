package org.demoth.cake.assets

import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream

/**
 * IdTech2 `.cin` stream decoder shared by gameplay and resource previews.
 *
 * Legacy cross-reference:
 * - Jake2 `client/SCR.java`: `Huff1TableInit`, `Huff1Decompress`, `ReadNextFrame`.
 * - q2pro `src/client/cin.c`: runtime stepping/EOF flow (`SCR_RunCinematic`/`SCR_FinishCinematic`).
 *
 * Notes:
 * - Audio bytes are consumed and returned per frame to preserve stream alignment.
 * - Hosts own playback timing, texture uploads, and audio output.
 */
internal class CinematicCinDecoder(
    input: InputStream,
    defaultPalette: IntArray,
) : AutoCloseable {
    val width: Int
    val height: Int
    val sampleRate: Int
    val sampleWidth: Int
    val sampleChannels: Int
    private val dis = DataInputStream(input)
    private var closed = false
    private val hnodes = IntArray(HUFF_PREV_CONTEXTS * HUFF_PREV_CONTEXTS * 2)
    private val numhnodes = IntArray(HUFF_PREV_CONTEXTS)
    private val hCount = IntArray(HUFF_MAX_NODES + 1)
    private val hUsed = IntArray(HUFF_MAX_NODES + 1)
    private val paletteRgba8888 = IntArray(PALETTE_COLOR_COUNT)

    var decodedFrameCount: Int = 0
        private set

    init {
        try {
            width = readInt()
            height = readInt()
            sampleRate = readInt()
            sampleWidth = readInt()
            sampleChannels = readInt()
            require(width > 0 && height > 0 && width.toLong() * height <= Int.MAX_VALUE) { "Invalid .cin dimensions" }
            require(sampleRate >= 0) { "Invalid .cin sample rate: $sampleRate" }
            require(sampleWidth >= 0 && sampleChannels >= 0) { "Invalid .cin audio format" }
            for (i in 0 until PALETTE_COLOR_COUNT) paletteRgba8888[i] = defaultPalette.getOrElse(i) { 0xFF }
            initHuffmanTables()
        } catch (error: Exception) {
            close()
            throw error
        }
    }

    private fun readInt(): Int = Integer.reverseBytes(dis.readInt())

    private fun readBytes(count: Int): ByteArray = dis.readNBytes(count).also {
        if (it.size != count) throw EOFException("Truncated .cin stream")
    }

    fun currentPalette(): IntArray = paletteRgba8888

    /** Reads only the next frame and its PCM block; owns the input until EOF or close(). */
    fun readNextFrame(): DecodedCinFrame? {
        if (closed) return null
        try {
            val command = readInt()
            if (command == CIN_COMMAND_END) {
                close()
                return null
            }
            require(command in 0..1) { "Invalid .cin frame command: $command" }
            if (command == CIN_COMMAND_PALETTE) updatePalette(readBytes(PALETTE_RGB_BYTES))
            val compressedSize = readInt()
            require(compressedSize > 0 && compressedSize <= width.toLong() * height * 32 + 4) { "Invalid .cin frame size" }
            val compressed = readBytes(compressedSize)
            val start = decodedFrameCount.toLong() * sampleRate / CINEMATIC_FPS
            val end = (decodedFrameCount.toLong() + 1) * sampleRate / CINEMATIC_FPS
            val soundBytes = (end - start) * sampleWidth * sampleChannels
            require(soundBytes in 0..Int.MAX_VALUE.toLong()) { "Invalid .cin audio block size" }
            val audio = readBytes(soundBytes.toInt())
            val indexedFrame = huff1Decompress(compressed)
            decodedFrameCount++
            return DecodedCinFrame(indexedFrame, audio)
        } catch (_: EOFException) {
            close()
            return null
        } catch (error: Exception) {
            close()
            throw error
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        dis.close()
    }

    private fun initHuffmanTables() {
        for (prev in 0 until HUFF_PREV_CONTEXTS) {
            hCount.fill(0)
            hUsed.fill(0)
            val counts = readBytes(HUFF_PREV_CONTEXTS)
            for (i in 0 until HUFF_PREV_CONTEXTS) {
                hCount[i] = counts[i].toInt() and 0xFF
            }

            var nodes = HUFF_PREV_CONTEXTS
            val nodeBase = prev * HUFF_PREV_CONTEXTS * 2
            while (nodes != HUFF_MAX_NODES) {
                val index = nodeBase + (nodes - HUFF_PREV_CONTEXTS) * 2
                hnodes[index] = smallestNode(nodes)
                if (hnodes[index] == -1) {
                    break
                }
                hnodes[index + 1] = smallestNode(nodes)
                if (hnodes[index + 1] == -1) {
                    break
                }
                hCount[nodes] = hCount[hnodes[index]] + hCount[hnodes[index + 1]]
                nodes++
            }
            numhnodes[prev] = nodes - 1
        }
    }

    private fun smallestNode(numNodes: Int): Int {
        var bestCount = Int.MAX_VALUE
        var bestNode = -1
        for (node in 0 until numNodes) {
            if (hUsed[node] != 0 || hCount[node] == 0) {
                continue
            }
            if (hCount[node] < bestCount) {
                bestCount = hCount[node]
                bestNode = node
            }
        }
        if (bestNode != -1) {
            hUsed[bestNode] = 1
        }
        return bestNode
    }

    private fun updatePalette(rgbBytes: ByteArray) {
        for (i in 0 until PALETTE_COLOR_COUNT) {
            val r = rgbBytes[i * 3].toInt() and 0xFF
            val g = rgbBytes[i * 3 + 1].toInt() and 0xFF
            val b = rgbBytes[i * 3 + 2].toInt() and 0xFF
            paletteRgba8888[i] = (r shl 24) or (g shl 16) or (b shl 8) or 0xFF
        }
    }

    private fun huff1Decompress(compressed: ByteArray): ByteArray {
        if (compressed.size < Int.SIZE_BYTES) {
            return ByteArray(0)
        }
        val outputCount = (compressed[0].toInt() and 0xFF) or
            ((compressed[1].toInt() and 0xFF) shl 8) or
            ((compressed[2].toInt() and 0xFF) shl 16) or
            ((compressed[3].toInt() and 0xFF) shl 24)
        require(outputCount == width * height) { "Invalid .cin decoded frame size" }

        val output = ByteArray(outputCount)
        var outputPos = 0
        var inputPos = Int.SIZE_BYTES
        var nodeBase = -HUFF_PREV_CONTEXTS * 2
        var node = numhnodes[0]

        while (outputPos < outputCount && inputPos < compressed.size) {
            var inByte = compressed[inputPos++].toInt() and 0xFF
            repeat(8) {
                if (node < HUFF_PREV_CONTEXTS) {
                    nodeBase = -HUFF_PREV_CONTEXTS * 2 + (node shl 9)
                    output[outputPos++] = node.toByte()
                    if (outputPos >= outputCount) {
                        return output
                    }
                    node = numhnodes[node]
                }
                node = hnodes[nodeBase + node * 2 + (inByte and 1)]
                inByte = inByte ushr 1
            }
        }
        return output
    }

    private companion object {
        private const val CIN_COMMAND_PALETTE = 1
        private const val CIN_COMMAND_END = 2
        private const val HUFF_PREV_CONTEXTS = 256
        private const val HUFF_MAX_NODES = 511
        private const val PALETTE_COLOR_COUNT = 256
        private const val PALETTE_RGB_BYTES = PALETTE_COLOR_COUNT * 3
        private const val CINEMATIC_FPS = 14
    }
}

internal data class DecodedCinFrame(
    val indexedFrame: ByteArray,
    val audioPcmBytes: ByteArray,
)
