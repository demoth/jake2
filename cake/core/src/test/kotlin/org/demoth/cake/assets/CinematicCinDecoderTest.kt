package org.demoth.cake.assets

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CinematicCinDecoderTest {
    private class TrackedInput(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closed = false
        val consumed get() = pos
        // Exercise short reads instead of assuming one read fills every requested block.
        override fun read(b: ByteArray, off: Int, len: Int) = super.read(b, off, minOf(len, 3))
        override fun close() { closed = true }
    }

    private fun movie(): ByteArray = ByteBuffer.allocate(20 + 65536 + 2 * 14 + 4)
        .order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(2).putInt(2).putInt(14).putInt(1).putInt(1)
            repeat(256) { put(1).put(1); repeat(254) { put(0) } }
            repeat(2) { putInt(0).putInt(5).putInt(4).put(0).put(128.toByte()) }
            putInt(2)
        }.array()

    @Test fun `first frame leaves subsequent frames unread and closes explicitly`() {
        val input = TrackedInput(movie())
        CinematicCinDecoder(input, intArrayOf(0xff0000ff.toInt())).use { decoder ->
            assertEquals(20 + 65536, input.consumed)
            val frame = decoder.readNextFrame()!!
            assertEquals(20 + 65536 + 14, input.consumed)
            assertArrayEquals(ByteArray(4), frame.indexedFrame)
            assertArrayEquals(byteArrayOf(128.toByte()), frame.audioPcmBytes)
            assertFalse(input.closed)
        }
        assertTrue(input.closed)
    }

    @Test fun `completion and truncated frame close their streams`() {
        val input = TrackedInput(movie())
        val decoder = CinematicCinDecoder(input, IntArray(256))
        assertNotNull(decoder.readNextFrame())
        assertNotNull(decoder.readNextFrame())
        assertNull(decoder.readNextFrame())
        assertTrue(input.closed)
        assertNull(decoder.readNextFrame())
        val truncated = TrackedInput(movie().copyOf(20 + 65536 + 12))
        assertNull(CinematicCinDecoder(truncated, IntArray(256)).readNextFrame())
        assertTrue(truncated.closed)
    }

    @Test fun `failed initialization closes input`() {
        val input = TrackedInput(byteArrayOf(0))
        assertThrows(Exception::class.java) { CinematicCinDecoder(input, IntArray(256)) }
        assertTrue(input.closed)
    }
}
