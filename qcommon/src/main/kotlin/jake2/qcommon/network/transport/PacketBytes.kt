package jake2.qcommon.network.transport

import java.nio.ByteBuffer

/** An owned payload, safe to retain in a queue after the sender reuses its buffer. */
class PacketBytes private constructor(private val bytes: ByteArray) {
    val size: Int get() = bytes.size

    fun toByteArray(): ByteArray = bytes.copyOf()
    /** Copy into engine-owned storage without exposing this payload's backing array. */
    @JvmOverloads
    fun copyTo(destination: ByteArray, offset: Int = 0) { bytes.copyInto(destination, offset) }
    internal fun readOnlyBuffer(): ByteBuffer = ByteBuffer.wrap(bytes).asReadOnlyBuffer()

    override fun equals(other: Any?): Boolean = other is PacketBytes && bytes.contentEquals(other.bytes)
    override fun hashCode(): Int = bytes.contentHashCode()

    companion object {
        @JvmStatic
        @JvmOverloads
        fun copyOf(bytes: ByteArray, length: Int = bytes.size): PacketBytes {
            require(length in 0..bytes.size) { "Payload length exceeds its backing array" }
            return PacketBytes(bytes.copyOf(length))
        }

        @JvmStatic
        fun copyOfRange(bytes: ByteArray, offset: Int, length: Int): PacketBytes {
            require(offset >= 0 && length >= 0 && offset <= bytes.size - length) { "Payload range exceeds its backing array" }
            return PacketBytes(bytes.copyOfRange(offset, offset + length))
        }
    }
}
