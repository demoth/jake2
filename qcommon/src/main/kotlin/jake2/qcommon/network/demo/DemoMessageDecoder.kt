package jake2.qcommon.network.demo

import jake2.qcommon.Defines
import jake2.qcommon.network.messages.server.ServerDataMessage
import jake2.qcommon.network.messages.server.ServerMessage
import jake2.qcommon.network.messages.server.ServerMessageCodec
import jake2.qcommon.network.protocol.MessageBodyCodec
import jake2.qcommon.network.protocol.MessageDecodeResult
import jake2.qcommon.network.protocol.MessageReadException
import jake2.qcommon.network.protocol.MessageRejection
import jake2.qcommon.network.transport.PacketBytes

/**
 * Per-playback classic protocol context. Server data selects the layout immediately, including
 * for later messages in the same record. Rejected records commit neither messages nor version.
 * Live networking and recording continue to use the protocol-34 body codec.
 */
class DemoMessageDecoder {
    var protocol: Int = Defines.PROTOCOL_VERSION
        private set

    fun decode(bytes: PacketBytes): MessageDecodeResult<ServerMessage> {
        var nextProtocol = protocol
        val result = MessageBodyCodec.decodeServerBody(bytes) { reader ->
            val start = reader.readcount
            val message = ServerMessageCodec.readDemoMessage(reader, nextProtocol)
            if (message is ServerDataMessage) {
                if (message.protocol != 26 && message.protocol != 32 && message.protocol != Defines.PROTOCOL_VERSION)
                    throw MessageReadException(MessageRejection.INVALID_VALUE, start,
                        "Unsupported demo protocol ${message.protocol}; expected 26, 32 or ${Defines.PROTOCOL_VERSION}")
                nextProtocol = message.protocol
            }
            message
        }
        if (result is MessageDecodeResult.Decoded) protocol = nextProtocol
        return result
    }
}
