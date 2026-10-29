package jake2.qcommon

import jake2.qcommon.network.runtime.*
import jake2.qcommon.network.transport.LocalSide
import jake2.qcommon.network.messages.ConnectionlessCommand
import jake2.qcommon.network.transport.NetworkAddress
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import jake2.qcommon.exec.Cvar
import jake2.qcommon.network.Netchan

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

    @Test
    fun `oversized connectionless output propagates without closing the socket`() {
        val cvars = Cvar.getInstance()
        cvars.clear()
        Netchan.Netchan_Init()
        cvars.find("port", Int::class.javaObjectType).forceSet(0)
        val transport = NetworkTransport.standalone(LocalSide.SECOND)
        try {
            PacketNetwork(transport, NetworkRole.SERVER).use { packets ->
                assertTrue(packets.configure(true))
                val endpoint = transport.udpEndpoint!!
                val failure = assertThrows(IllegalArgumentException::class.java) {
                    packets.sendConnectionless(packets.localPeer, ConnectionlessCommand.print, "x".repeat(Defines.MAX_MSGLEN))
                }
                assertSame(endpoint, transport.udpEndpoint)
                assertTrue(endpoint.isOpen)
            }
        } finally {
            cvars.clear()
        }
    }
}
