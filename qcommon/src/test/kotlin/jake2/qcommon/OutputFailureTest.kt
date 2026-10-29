package jake2.qcommon

import jake2.qcommon.network.NET
import jake2.qcommon.network.NetAddrType
import jake2.qcommon.network.netadr_t
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.net.DatagramSocket

class OutputFailureTest {
    @Test
    fun `disallowed overflow preserves buffer until caller handles failure`() {
        val buffer = sizebuf_t(4)
        buffer.writeInt(123)
        val saved = buffer.data.copyOf()
        val failure = assertThrows(IllegalStateException::class.java) { buffer.writeByte(1) }
        assertTrue(failure.message!!.contains("overflow without allowoverflow"))
        assertEquals(4, buffer.cursize)
        assertArrayEquals(saved, buffer.data)
        assertFalse(buffer.overflowed)
        buffer.clear()
        buffer.writeInt(456)
        assertEquals(456, buffer.readInt())
    }

    @Test
    fun `permitted overflow restarts buffer but oversized single write still throws`() {
        val buffer = sizebuf_t(4)
        buffer.allowoverflow = true
        buffer.writeInt(123)
        buffer.writeShort(456)
        assertTrue(buffer.overflowed)
        assertEquals(2, buffer.cursize)
        assertEquals(456, buffer.readShort())
        val saved = buffer.data.copyOf()
        val failure = assertThrows(IllegalStateException::class.java) { buffer.writeBytes(ByteArray(5)) }
        assertTrue(failure.message!!.contains("full buffer size"))
        assertEquals(2, buffer.cursize)
        assertArrayEquals(saved, buffer.data)
    }

    @ParameterizedTest
    @EnumSource(NetAddrType::class, names = ["NA_IPX", "NA_BROADCAST_IPX"])
    fun `unsupported destination propagates without closing the socket`(type: NetAddrType) {
        val previous = NET.ip_sockets[Defines.NS_SERVER]
        DatagramSocket(0).use { socket ->
            NET.ip_sockets[Defines.NS_SERVER] = socket
            try {
                val failure = assertThrows(IllegalArgumentException::class.java) {
                    NET.SendPacket(Defines.NS_SERVER, 1, byteArrayOf(1), netadr_t().apply { this.type = type })
                }
                assertTrue(failure.message!!.contains(type.name))
                assertSame(socket, NET.ip_sockets[Defines.NS_SERVER])
                assertFalse(socket.isClosed)
            } finally {
                NET.ip_sockets[Defines.NS_SERVER] = previous
            }
        }
    }
}
