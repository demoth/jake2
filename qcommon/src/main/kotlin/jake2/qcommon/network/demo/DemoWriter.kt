package jake2.qcommon.network.demo

import jake2.qcommon.Defines
import jake2.qcommon.network.transport.PacketBytes
import java.io.Closeable
import java.io.IOException
import java.io.OutputStream

/**
 * Owns a protocol-34 .dm2 output stream (Yamagi CL_WriteDemoMessage / CL_Stop_f).
 * Records contain server message bodies, without channel sequence/acknowledgement headers.
 * The caller supplies validated bodies; this layer only handles framing and stream lifetime.
 */
class DemoWriter(private val output: OutputStream) : Closeable {
    private var closed = false
    private val header = ByteArray(4)

    /** Writes one bounded record, including empty records. An I/O failure closes the incomplete stream. */
    fun write(body: PacketBytes) {
        check(!closed) { "Demo writer is closed" }
        require(body.size <= Defines.MAX_MSGLEN) { "Demo record exceeds ${Defines.MAX_MSGLEN} bytes" }
        val bytes = body.toByteArray()
        try {
            writeLength(bytes.size)
            output.write(bytes)
        } catch (failure: IOException) {
            // A partial record cannot be retried or safely followed by a normal end marker.
            closed = true
            try {
                output.close()
            } catch (closeFailure: IOException) {
                if (closeFailure !== failure) failure.addSuppressed(closeFailure)
            }
            throw failure
        }
    }

    private fun writeLength(length: Int) {
        for (index in header.indices) header[index] = (length ushr (index * 8)).toByte()
        output.write(header)
    }

    /** Finishes a healthy recording with -1, flushes and closes once, even if finalization fails. */
    override fun close() {
        if (closed) return
        closed = true
        output.use {
            writeLength(-1)
            it.flush()
        }
    }
}
