package jake2.qcommon.network.protocol

import jake2.qcommon.Defines
import jake2.qcommon.Globals
import jake2.qcommon.math.Vector3f
import jake2.qcommon.sizebuf_t
import jake2.qcommon.network.transport.PacketBytes

/** Owned packet bytes with bounded reads; legacy file/demo buffer readers retain their sentinel API. */
class MessageReader(bytes: PacketBytes, offset: Int) : sizebuf_t(bytes.size) {
    init {
        require(offset in 0..bytes.size)
        data = bytes.toByteArray()
        cursize = bytes.size
        readcount = offset
    }

    companion object {
        /** Bridge direct legacy factory calls into bounded reads and let the factory copy back its cursor. */
        @JvmStatic fun fromBuffer(buffer: sizebuf_t): MessageReader {
            requireRemaining(buffer, 0)
            return MessageReader(PacketBytes.copyOf(buffer.data, buffer.cursize), buffer.readcount)
        }

        /** Validate a wire length before allocation or copying, including when a legacy parser is called directly. */
        @JvmStatic fun requireRemaining(buffer: sizebuf_t, length: Int) {
            if (length < 0) throw MessageReadException(MessageRejection.INVALID_LENGTH, buffer.readcount, "Negative length $length")
            if (buffer.readcount < 0 || buffer.cursize > (buffer.data?.size ?: 0) || length > buffer.cursize - buffer.readcount)
                throw MessageReadException(MessageRejection.TRUNCATED, buffer.readcount, "Need $length bytes")
        }
    }

    override fun readByte(): Int {
        requireRemaining(this, 1)
        return super.readByte()
    }
    /** Unsigned wire byte, widened once at the boundary to 0..255. */
    fun readUnsignedByte(): Int = readByte()

    /** Unsigned wire short, widened once at the boundary to 0..65535. */
    fun readUnsignedShort(): Int = readShort().toInt() and 0xffff

    /** Signed wire short, widened once at the boundary to -32768..32767, including negative protocol markers. */
    fun readSignedShort(): Int = readShort().toInt()

    /** Snapshot a bounded payload range before its packet buffer can be reused. */
    fun readBytes(length: Int): PacketBytes {
        requireRemaining(this, length)
        return PacketBytes.copyOfRange(data, readcount, length).also { readcount += length }
    }

    /** Three signed fixed-point coordinates, each stored at one eighth of a world unit. */
    fun readPosition(): Vector3f = Vector3f(readCoord(), readCoord(), readCoord())

    override fun readSignedByte(): Byte {
        requireRemaining(this, 1)
        return super.readSignedByte()
    }
    override fun readShort(): Short {
        requireRemaining(this, 2)
        return super.readShort()
    }
    override fun readInt(): Int {
        requireRemaining(this, 4)
        return super.readInt()
    }

    override fun readData(destination: ByteArray, length: Int) {
        if (length < 0 || length > destination.size)
            throw MessageReadException(MessageRejection.INVALID_LENGTH, readcount, "Invalid copy length $length")
        requireRemaining(this, length)
        System.arraycopy(data, readcount, destination, 0, length)
        readcount += length
    }

    override fun readDir(): FloatArray {
        val index = readByte()
        if (index !in 0 until Defines.NUMVERTEXNORMALS)
            throw MessageReadException(MessageRejection.INVALID_VALUE, readcount - 1, "Invalid direction index $index")
        return Globals.bytedirs[index].clone()
    }
}
