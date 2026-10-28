package jake2.qcommon.network.protocol

import jake2.qcommon.network.messages.NetworkMessage
import jake2.qcommon.network.messages.client.*
import jake2.qcommon.network.messages.server.*

/**
 * Decode all payload messages before controllers dispatch any of them. No logging or teardown policy.
 * Structural failures reject the packet; movement checksum validity retains its existing controller policy.
 */
object MessageBodyCodec {
    @JvmStatic fun decodeServer(envelope: PacketEnvelope): MessageDecodeResult<ServerMessage> =
        decode(envelope, PacketDirection.FROM_SERVER, ServerMessageCodec::read) { it is EndOfServerPacketMessage }

    @JvmStatic fun decodeClient(envelope: PacketEnvelope, sequence: Int): MessageDecodeResult<ClientMessage> =
        decode(envelope, PacketDirection.FROM_CLIENT, { ClientMessageCodec.read(it, sequence) }) { it is EndOfClientPacketMessage }

    private fun <T : NetworkMessage> decode(
        envelope: PacketEnvelope, direction: PacketDirection,
        parse: (MessageReader) -> T?, isEnd: (T) -> Boolean,
    ): MessageDecodeResult<T> {
        if (envelope !is PacketEnvelope.Sequenced)
            return MessageDecodeResult.Rejected(MessageRejection.NOT_SEQUENCED, 0, "Connectionless packet has no message body")
        if ((envelope.qport != null) != (direction == PacketDirection.FROM_CLIENT))
            return MessageDecodeResult.Rejected(MessageRejection.WRONG_DIRECTION, 0, "Unexpected channel header")
        val reader = MessageReader(envelope.bytes, envelope.headerSize)
        val messages = ArrayList<T>()
        return try {
            while (reader.readcount < reader.cursize) {
                val message = parse(reader) ?: continue // A valid server no-op carries no application data.
                if (isEnd(message)) {
                    if (reader.readcount != reader.cursize)
                        throw MessageReadException(MessageRejection.INVALID_VALUE, reader.readcount, "Data after end marker")
                    break
                }
                messages.add(message)
            }
            MessageDecodeResult.Decoded(java.util.List.copyOf(messages))
        } catch (error: MessageReadException) {
            MessageDecodeResult.Rejected(error.reason, error.offset, error.message ?: "Invalid message")
        }
    }
}
