package jake2.qcommon.network.demo

import jake2.qcommon.Defines
import jake2.qcommon.network.transport.PacketBytes
import java.io.Closeable
import java.io.IOException
import java.io.InputStream

/** Owns a classic .dm2 stream: little-endian lengths followed by server message bodies. */
class DemoReader(private val input: InputStream) : Closeable {
    sealed interface Record {
        data class Body(val bytes: PacketBytes) : Record
        data object End : Record
    }

    private var ended = false
    private var closed = false
    var offset: Long = 0
        private set

    /** Reads exactly one record, including empty records. EOF without the -1 marker is incomplete. */
    fun read(): Record {
        check(!closed) { "Demo reader is closed" }
        if (ended) return Record.End
        val header = readExactly(4)
        val length = (header[0].toInt() and 255) or ((header[1].toInt() and 255) shl 8) or
            ((header[2].toInt() and 255) shl 16) or (header[3].toInt() shl 24)
        if (length == -1) {
            ended = true
            return Record.End
        }
        if (length !in 0..Defines.MAX_MSGLEN) throw IOException("Invalid demo record length $length at byte ${offset - 4}")
        return Record.Body(PacketBytes.copyOf(readExactly(length)))
    }

    private fun readExactly(length: Int): ByteArray {
        val bytes = ByteArray(length)
        var read = 0
        while (read < length) {
            // Single-byte reads also work with streams that return zero from bulk reads.
            val count = input.read(bytes, read, length - read)
            if (count < 0) throw IOException("Incomplete demo at byte $offset (missing record data or terminator)")
            if (count == 0) {
                val value = input.read()
                if (value < 0) throw IOException("Incomplete demo at byte $offset")
                bytes[read++] = value.toByte()
                offset++
            } else {
                read += count
                offset += count
            }
        }
        return bytes
    }

    override fun close() {
        if (!closed) {
            closed = true
            input.close()
        }
    }
}
