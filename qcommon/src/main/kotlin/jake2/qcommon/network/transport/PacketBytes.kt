package jake2.qcommon.network.transport

import java.nio.ByteBuffer

/** An owned payload, safe to retain in a queue after the sender reuses its buffer. */
class PacketBytes private constructor(private val bytes: ByteArray) {
    val size: Int get() = bytes.size

    fun toByteArray(): ByteArray = bytes.copyOf()
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
    }
}
