package jake2.qcommon.network.protocol

import jake2.qcommon.Defines.*
import jake2.qcommon.network.messages.client.*
import jake2.qcommon.network.transport.PacketBytes
import jake2.qcommon.sizebuf_t
import jake2.qcommon.usercmd_t
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MovementCodecTest {
    private fun write(message: MoveMessage, sequence: Int): ByteArray {
        val buffer = sizebuf_t(message.size)
        message.writeTo(buffer, sequence)
        assertEquals(buffer.maxsize, buffer.cursize)
        return buffer.data
    }

    private fun read(bytes: ByteArray, sequence: Int): MoveMessage {
        val reader = MessageReader(PacketBytes.copyOf(bytes), 0)
        val message = ClientMessageCodec.read(reader, sequence) as MoveMessage
        assertEquals(bytes.size, reader.readcount)
        reader.data.fill(0)
        return message
    }

    @Test
    fun `all delta flags preserve signed fields inherited fields and explicit resets`() {
        for (flags in 0..255) {
            val command = UserCommand(255, if (flags and CM_BUTTONS != 0) 128 else 0,
                CommandAngles(if (flags and CM_ANGLE1 != 0) -32768 else 0,
                    if (flags and CM_ANGLE2 != 0) -1 else 0, if (flags and CM_ANGLE3 != 0) 32767 else 0),
                if (flags and CM_FORWARD != 0) -32768 else 0, if (flags and CM_SIDE != 0) 32767 else 0,
                if (flags and CM_UP != 0) -1 else 0, if (flags and CM_IMPULSE != 0) 255 else 0, 254)
            val movement = MoveMessage(-1, command, command.copy(msec = 1, lightlevel = 2), UserCommand())
            val bytes = write(movement, 17)
            assertEquals(flags, bytes[6].toInt() and 255)
            assertEquals(movement, read(bytes, 17))
        }
        for (value in 0..255) {
            val command = UserCommand(msec = value, buttons = value, impulse = value, lightlevel = value)
            val movement = MoveMessage(1, command, command, command)
            assertEquals(movement, read(write(movement, 1), 1))
        }
    }

    @Test
    fun `movement owns engine history and exports independent mutable engine commands`() {
        val source = usercmd_t().apply {
            msec = -1; buttons = -128; impulse = -1; lightlevel = -128
            angles = shortArrayOf(-32768, -1, 32767)
            forwardmove = -32768; sidemove = 32767; upmove = -1
        }
        val movement = MoveMessage.fromInput(false, 77, source, source, source)
        val bytes = write(movement, 17)
        val hash = movement.hashCode()
        source.clear()
        movement.newCmd.toEngineCommand().clear()
        assertEquals(hash, movement.hashCode())
        assertEquals(255, movement.newCmd.msec)
        assertEquals(-1, movement.newCmd.angles[1])
        assertEquals(movement, read(bytes, 17))
        assertArrayEquals(bytes, write(movement, 17))
        val destination = usercmd_t()
        movement.newCmd.copyTo(destination)
        assertEquals(-128, destination.buttons.toInt())
        destination.clear()
        assertArrayEquals(bytes, write(movement, 17))
        val fullFrame = MoveMessage.fromInput(true, 77, source, source, source)
        assertEquals(0, fullFrame.lastReceivedFrame)
        assertEquals(fullFrame, read(write(fullFrame, 17), 17))
    }

    @Test
    fun `sequence is required explicitly and affects only checksum context`() {
        val movement = MoveMessage(77, UserCommand(), UserCommand(), UserCommand(msec = 16, forwardmove = 120))
        val contexts = listOf(1, 2, 17, 1023)
        val encoded = contexts.map { write(movement, it) }
        assertTrue(encoded.map { it[1] }.distinct().size > 1)
        for ((sequence, bytes) in contexts.zip(encoded)) assertEquals(movement, read(bytes, sequence))
        val otherIndex = encoded.indices.first { encoded[it][1] != encoded[0][1] }
        assertFalse(read(encoded[0], contexts[otherIndex]).checksumValid)
        assertArrayEquals(encoded[0], write(movement.copy(checksumValid = false), contexts[0]))
        val buffer = sizebuf_t(movement.size)
        assertThrows(IllegalArgumentException::class.java) { movement.writeTo(buffer) }
        assertEquals(0, buffer.cursize)
        assertThrows(IllegalArgumentException::class.java) { movement.writeTo(buffer, -1) }
        assertEquals(0, buffer.cursize)
    }
}
