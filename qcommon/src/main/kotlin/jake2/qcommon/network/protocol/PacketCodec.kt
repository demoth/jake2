package jake2.qcommon.network.protocol

import jake2.qcommon.Defines
import jake2.qcommon.network.transport.PacketBytes
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Stateless envelope decoding. No sockets, Cvars, logging, or mutable channel state. */
object PacketCodec {
    @JvmStatic
    fun decode(bytes: PacketBytes, direction: PacketDirection): PacketDecodeResult {
        if (bytes.size > Defines.MAX_MSGLEN) return PacketDecodeResult.Rejected(PacketRejection.OVERSIZED)
        if (bytes.size < 4) return PacketDecodeResult.Rejected(PacketRejection.TRUNCATED_HEADER)

        val reader = bytes.readOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)
        val sequence = reader.int
        if (sequence == -1) {
            val commandLine = readText(reader, stopAtNewline = true)
            if (commandLine.isEmpty()) return PacketDecodeResult.Rejected(PacketRejection.EMPTY_CONNECTIONLESS_COMMAND)
            val parameters = if (direction == PacketDirection.FROM_SERVER &&
                (commandLine == "info" || commandLine == "print")) {
                readText(reader, stopAtNewline = false)
            } else null
            return PacketDecodeResult.Decoded(PacketEnvelope.Connectionless(bytes, commandLine, parameters))
        }

        if (bytes.size < direction.sequencedHeaderSize) {
            return PacketDecodeResult.Rejected(PacketRejection.TRUNCATED_HEADER)
        }
        val sequenceAck = reader.int
        val qport = if (direction == PacketDirection.FROM_CLIENT) reader.short.toInt() and 0xffff else null
        return PacketDecodeResult.Decoded(PacketEnvelope.Sequenced(bytes, sequence, sequenceAck, qport))
    }

    private fun readText(reader: ByteBuffer, stopAtNewline: Boolean): String {
        val text = ByteArray(reader.remaining())
        var length = 0
        while (reader.hasRemaining()) {
            val value = reader.get().toInt() and 0xff
            // Retain the legacy NUL/0xff terminators and accept the datagram boundary.
            if (value == 0 || value == 0xff || (stopAtNewline && value == '\n'.code)) break
            text[length++] = value.toByte()
        }
        // Matches Lib.stringToBytes used by outgoing connectionless messages.
        return String(text, 0, length, Charsets.ISO_8859_1)
    }
}
