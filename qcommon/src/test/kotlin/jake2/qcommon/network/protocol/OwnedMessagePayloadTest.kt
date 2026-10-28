package jake2.qcommon.network.protocol

import jake2.qcommon.Defines.MAX_ITEMS
import jake2.qcommon.Defines.MAX_MAP_AREAS
import jake2.qcommon.network.messages.server.*
import jake2.qcommon.network.transport.PacketBytes
import jake2.qcommon.sizebuf_t
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OwnedMessagePayloadTest {
    private fun write(message: ServerMessage): ByteArray {
        val buffer = sizebuf_t(message.size)
        message.writeTo(buffer)
        assertEquals(buffer.maxsize, buffer.cursize)
        return buffer.data
    }

    private fun read(bytes: ByteArray): ServerMessage {
        val reader = MessageReader(PacketBytes.copyOf(bytes), 0)
        val message = ServerMessageCodec.read(reader)!!
        assertEquals(bytes.size, reader.readcount)
        // Decoded payloads must no longer depend on even the decoder's owned mutable buffer.
        reader.data.fill(0)
        return message
    }

    @Test
    fun `inventory snapshots signed counts and ignores storage beyond the wire prefix`() {
        val source = IntArray(MAX_ITEMS + 3) { it * 257 }
        val expected = IntArray(MAX_ITEMS) { source[it].toShort().toInt() }
        val message = InventoryMessage(source)
        val bytes = write(message)
        val hash = message.hashCode()
        source.fill(99)
        assertArrayEquals(bytes, write(message))
        assertEquals(hash, message.hashCode())
        assertEquals(InventoryMessage(expected), message)
        assertEquals(message, read(bytes))
        val destination = IntArray(MAX_ITEMS)
        message.copyTo(destination)
        assertArrayEquals(expected, destination)
        destination.fill(0)
        assertArrayEquals(bytes, write(message))
        assertThrows(IllegalArgumentException::class.java) { InventoryMessage(IntArray(MAX_ITEMS - 1)) }
    }

    @Test
    fun `download chunks own input and exported bytes and use content equality`() {
        val source = byteArrayOf(1, -1, 3)
        val message = DownloadMessage.chunk(source, 128)
        val bytes = write(message)
        val hash = message.hashCode()
        source.fill(0)
        message.data.toByteArray().fill(0)
        val destination = ByteArray(5)
        message.data.copyTo(destination, 1)
        assertArrayEquals(byteArrayOf(0, 1, -1, 3, 0), destination)
        destination.fill(0)
        assertEquals(hash, message.hashCode())
        assertEquals(DownloadMessage.chunk(byteArrayOf(1, -1, 3), 128), message)
        assertArrayEquals(bytes, write(message))
        assertEquals(message, read(bytes))
    }

    @Test
    fun `download failure markers and unsigned percentages survive re-encoding`() {
        for (percent in 0..255) {
            for (marker in listOf(-1, DownloadMessage.LEGACY_FAILURE_MARKER)) {
                val bytes = byteArrayOf(16, marker.toByte(), (marker shr 8).toByte(), percent.toByte())
                val decoded = read(bytes)
                assertEquals(DownloadMessage.Failure(marker, percent), decoded)
                assertArrayEquals(bytes, write(decoded))
            }
            val chunk = DownloadMessage.chunk(byteArrayOf(), percent)
            assertEquals(chunk, read(write(chunk)))
        }
    }

    @Test
    fun `frame visibility owns only the transmitted prefix and has content equality`() {
        for (length in listOf(0, 1, MAX_MAP_AREAS / 8)) {
            val source = ByteArray(MAX_MAP_AREAS / 8) { -1 }
            val message = FrameHeaderMessage(7, -1, 255, length, source)
            val bytes = write(message)
            val hash = message.hashCode()
            source.fill(0)
            message.areaBits.toByteArray().fill(0)
            assertEquals(length, message.areaBits.size)
            assertEquals(hash, message.hashCode())
            assertArrayEquals(bytes, write(message.copy()))
            assertEquals(message, read(bytes))
            assertEquals(message.hashCode(), read(bytes).hashCode())
        }
    }

    @Test
    fun `payload readers reject invalid ranges without consuming bytes and constructors enforce wire lengths`() {
        for (length in listOf(-1, 4)) {
            val reader = MessageReader(PacketBytes.copyOf(byteArrayOf(1, 2, 3)), 1)
            val error = assertThrows(MessageReadException::class.java) { reader.readBytes(length) }
            assertEquals(if (length < 0) MessageRejection.INVALID_LENGTH else MessageRejection.TRUNCATED, error.reason)
            assertEquals(1, reader.readcount)
        }
        val reader = MessageReader(PacketBytes.copyOf(byteArrayOf(9, 1, 2, 8)), 1)
        val bytes = reader.readBytes(2)
        reader.data.fill(0)
        assertArrayEquals(byteArrayOf(1, 2), bytes.toByteArray())
        assertEquals(3, reader.readcount)
        assertThrows(IllegalArgumentException::class.java) { DownloadMessage.chunk(ByteArray(32768), 0) }
        assertThrows(IllegalArgumentException::class.java) { DownloadMessage.chunk(byteArrayOf(), 256) }
        assertThrows(IllegalArgumentException::class.java) { DownloadMessage.Failure(-2) }
        assertThrows(IllegalArgumentException::class.java) {
            FrameHeaderMessage(1, -1, 0, PacketBytes.copyOf(ByteArray(MAX_MAP_AREAS / 8 + 1)))
        }
    }
}
