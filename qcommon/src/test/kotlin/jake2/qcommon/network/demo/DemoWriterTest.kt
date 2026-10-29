package jake2.qcommon.network.demo

import jake2.qcommon.Defines
import jake2.qcommon.network.messages.server.PrintMessage
import jake2.qcommon.network.protocol.MessageBodyCodec
import jake2.qcommon.network.protocol.MessageDecodeResult
import jake2.qcommon.network.transport.PacketBytes
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

class DemoWriterTest {
    @Test fun `record bytes match an independent protocol 34 print fixture without channel headers`() {
        // Hand-authored .dm2 framing from Yamagi CL_WriteDemoMessage and CL_Stop_f;
        // svc_print is opcode 10 followed by level and a zero-terminated string.
        val body = byteArrayOf(10, 2, 'h'.code.toByte(), 'i'.code.toByte(), 0)
        val output = ByteArrayOutputStream()
        val owned = PacketBytes.copyOf(body)
        body.fill(0)
        DemoWriter(output).use { it.write(owned) }
        assertArrayEquals(byteArrayOf(5, 0, 0, 0, 10, 2, 'h'.code.toByte(), 'i'.code.toByte(), 0, -1, -1, -1, -1),
            output.toByteArray())
        DemoReader(ByteArrayInputStream(output.toByteArray())).use { reader ->
            val record = reader.read() as DemoReader.Record.Body
            val decoded = MessageBodyCodec.decodeServerBody(record.bytes) as MessageDecodeResult.Decoded
            assertEquals("hi", (decoded.messages.single() as PrintMessage).text)
            assertEquals(DemoReader.Record.End, reader.read())
        }
    }

    @Test fun `empty and full capacity records round trip in order including lengths larger than one byte`() {
        val bodies = listOf(0, 1, 255, 256, Defines.MAX_MSGLEN).map { size ->
            PacketBytes.copyOf(ByteArray(size) { (it * 31).toByte() })
        }
        val output = ByteArrayOutputStream()
        DemoWriter(output).use { writer -> bodies.forEach(writer::write) }
        DemoReader(ByteArrayInputStream(output.toByteArray())).use { reader ->
            bodies.forEach { assertEquals(it, (reader.read() as DemoReader.Record.Body).bytes) }
            assertEquals(DemoReader.Record.End, reader.read())
        }
    }

    @Test fun `oversized record is rejected before any output and a valid recording can still follow`() {
        val output = ByteArrayOutputStream()
        DemoWriter(output).use { writer ->
            assertThrows(IllegalArgumentException::class.java) {
                writer.write(PacketBytes.copyOf(ByteArray(Defines.MAX_MSGLEN + 1)))
            }
            assertEquals(0, output.size())
            writer.write(PacketBytes.copyOf(byteArrayOf(6)))
        }
        assertArrayEquals(byteArrayOf(1, 0, 0, 0, 6, -1, -1, -1, -1), output.toByteArray())
    }

    @Test fun `closing an empty recording writes a single terminator flushes and closes once`() {
        val output = ProbeOutput()
        val writer = DemoWriter(output)
        writer.close()
        writer.close()
        assertArrayEquals(byteArrayOf(-1, -1, -1, -1), output.bytes.toByteArray())
        assertEquals(1, output.flushes)
        assertEquals(1, output.closes)
        assertThrows(IllegalStateException::class.java) { writer.write(PacketBytes.copyOf(byteArrayOf(6))) }
    }

    @Test fun `failure at every record byte closes the output without appending a terminator or retrying`() {
        for (limit in 0 until 7) {
            val output = ProbeOutput(limit = limit)
            val writer = DemoWriter(output)
            val failure = assertThrows(IOException::class.java) { writer.write(PacketBytes.copyOf(byteArrayOf(6, 6, 6))) }
            assertSame(output.writeFailure, failure)
            assertEquals(limit, output.bytes.size())
            assertEquals(1, output.closes)
            writer.close()
            assertEquals(limit, output.bytes.size())
            assertEquals(1, output.closes)
            assertThrows(IllegalStateException::class.java) { writer.write(PacketBytes.copyOf(byteArrayOf(6))) }
            DemoReader(ByteArrayInputStream(output.bytes.toByteArray())).use { reader ->
                assertThrows(IOException::class.java) { reader.read() }
            }
        }
    }

    @Test fun `partial terminator or failed flush still closes once and preserves the primary error`() {
        for (limit in 0..3) {
            val output = ProbeOutput(limit = limit, failClose = true)
            val writer = DemoWriter(output)
            val failure = assertThrows(IOException::class.java) { writer.close() }
            assertSame(output.writeFailure, failure)
            assertEquals(listOf(output.closeFailure), failure.suppressed.toList())
            assertEquals(1, output.closes)
            writer.close()
            assertEquals(1, output.closes)
        }
        val output = ProbeOutput(failFlush = true, failClose = true)
        val writer = DemoWriter(output)
        val failure = assertThrows(IOException::class.java) { writer.close() }
        assertSame(output.flushFailure, failure)
        assertEquals(listOf(output.closeFailure), failure.suppressed.toList())
        assertEquals(1, output.closes)
        writer.close()
        assertEquals(1, output.closes)
    }

    @Test fun `record failure preserves a secondary close error and final close failure is not retried`() {
        val output = ProbeOutput(limit = 5, failClose = true)
        val writer = DemoWriter(output)
        val failure = assertThrows(IOException::class.java) { writer.write(PacketBytes.copyOf(byteArrayOf(6, 6))) }
        assertSame(output.writeFailure, failure)
        assertEquals(listOf(output.closeFailure), failure.suppressed.toList())
        writer.close()
        assertEquals(1, output.closes)

        val closeOnly = ProbeOutput(failClose = true)
        val closingWriter = DemoWriter(closeOnly)
        assertSame(closeOnly.closeFailure, assertThrows(IOException::class.java) { closingWriter.close() })
        closingWriter.close()
        assertEquals(1, closeOnly.closes)
    }

    private class ProbeOutput(
        val limit: Int = Int.MAX_VALUE,
        val failFlush: Boolean = false,
        val failClose: Boolean = false,
    ) : OutputStream() {
        val bytes = ByteArrayOutputStream()
        var closes = 0
        var flushes = 0
        val writeFailure = IOException("synthetic write failure")
        val flushFailure = IOException("synthetic flush failure")
        val closeFailure = IOException("synthetic close failure")
        override fun write(value: Int) {
            if (bytes.size() >= limit) throw writeFailure
            bytes.write(value)
        }
        override fun flush() { flushes++; if (failFlush) throw flushFailure }
        override fun close() { closes++; if (failClose) throw closeFailure }
    }
}
