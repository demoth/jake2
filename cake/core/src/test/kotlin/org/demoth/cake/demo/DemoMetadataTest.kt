package org.demoth.cake.demo

import jake2.qcommon.Defines
import jake2.qcommon.network.messages.server.*
import jake2.qcommon.sizebuf_t
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DemoMetadataTest {
    private fun int(value: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    private fun record(vararg messages: ServerMessage): ByteArray {
        val buffer = sizebuf_t(Defines.MAX_MSGLEN)
        messages.forEach { it.writeTo(buffer) }
        return int(buffer.cursize) + buffer.data.copyOf(buffer.cursize)
    }
    private fun server(protocol: Int) = ServerDataMessage(protocol, 1, true, "", 0, "Installation")

    @Test fun `metadata reads classic startup versions and map across records then leaves gameplay unread`() {
        for (protocol in listOf(26, 32, 34)) {
            val startup = record(server(protocol)) + record(ConfigStringMessage(Defines.CS_MODELS + 1, "maps/base1.bsp"))
            var closes = 0
            val input = object : ByteArrayInputStream(startup + byteArrayOf(1, 2, 3)) {
                override fun close() { closes++ }
            }
            assertEquals(DemoMetadata(protocol, "baseq2", "Installation", "maps/base1.bsp"), DemoMetadata.read(input))
            assertEquals(3, input.available())
            assertEquals(1, closes)
        }
    }

    @Test fun `header without map can still describe a demo when precache or terminator is reached`() {
        for (ending in listOf(record(StuffTextMessage("precache\n")), int(-1))) {
            assertEquals(DemoMetadata(34, "baseq2", "Installation", null),
                DemoMetadata.read(ByteArrayInputStream(record(server(34)) + ending)))
        }
    }

    @Test fun `empty records cannot cause an unbounded metadata scan`() {
        val input = ByteArrayInputStream(ByteArray(4 * (DemoMetadata.MAX_STARTUP_RECORDS + 10)))
        assertThrows(IOException::class.java) { DemoMetadata.read(input) }
        assertEquals(40, input.available())
    }

    @Test fun `malformed or unsupported startup fails and closes the stream`() {
        for (bytes in listOf(byteArrayOf(1, 2), int(-1), record(server(35)), record(server(34)) + int(-2))) {
            var closed = false
            val input = object : ByteArrayInputStream(bytes) { override fun close() { closed = true } }
            assertThrows(IOException::class.java) { DemoMetadata.read(input) }
            assertTrue(closed)
        }
    }
}
