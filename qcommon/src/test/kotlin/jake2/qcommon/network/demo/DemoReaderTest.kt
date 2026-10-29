package jake2.qcommon.network.demo

import jake2.qcommon.Defines
import jake2.qcommon.network.protocol.MessageBodyCodec
import jake2.qcommon.network.protocol.MessageDecodeResult
import jake2.qcommon.network.messages.server.PrintMessage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DemoReaderTest {
    private fun int(value: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()

    @Test fun `independently framed protocol 34 print record needs no channel header`() {
        // Hand-authored from the .dm2 layout in Yamagi CL_WriteDemoMessage and svc_print (opcode 10).
        val body = byteArrayOf(10, 2, 'h'.code.toByte(), 'i'.code.toByte(), 0)
        DemoReader(ByteArrayInputStream(int(0) + int(body.size) + body + int(-1))).use { reader ->
            assertEquals(0, (reader.read() as DemoReader.Record.Body).bytes.size)
            val result = MessageBodyCodec.decodeServerBody((reader.read() as DemoReader.Record.Body).bytes)
                as MessageDecodeResult.Decoded
            assertEquals("hi", (result.messages.single() as PrintMessage).text)
            assertEquals(DemoReader.Record.End, reader.read())
            assertEquals(DemoReader.Record.End, reader.read())
        }
    }

    @Test fun `all truncated prefixes and invalid lengths fail before allocation`() {
        val valid = int(2) + byteArrayOf(6, 6) + int(-1)
        for (size in 0 until valid.size) {
            DemoReader(ByteArrayInputStream(valid.copyOf(size))).use { reader ->
                assertThrows(IOException::class.java) { while (reader.read() != DemoReader.Record.End) Unit }
            }
        }
        for (length in listOf(-2, Int.MIN_VALUE, Defines.MAX_MSGLEN + 1, Int.MAX_VALUE)) {
            DemoReader(ByteArrayInputStream(int(length))).use { reader ->
                assertThrows(IOException::class.java) { reader.read() }
            }
        }
        DemoReader(ByteArrayInputStream(int(Defines.MAX_MSGLEN) + ByteArray(Defines.MAX_MSGLEN))).use {
            assertEquals(Defines.MAX_MSGLEN, (it.read() as DemoReader.Record.Body).bytes.size)
        }
    }

    @Test fun `reader owns and closes input once`() {
        var closes = 0
        val reader = DemoReader(object : ByteArrayInputStream(int(-1)) {
            override fun close() { closes++ }
        })
        reader.close()
        reader.close()
        assertEquals(1, closes)
        assertThrows(IllegalStateException::class.java) { reader.read() }
    }
}
