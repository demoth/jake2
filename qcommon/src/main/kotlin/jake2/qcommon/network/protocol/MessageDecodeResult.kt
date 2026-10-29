package jake2.qcommon.network.protocol

/** Complete packet-body decode; rejected packets never publish a partial message collection. */
sealed interface MessageDecodeResult<out T> {
    data class Decoded<T>(val messages: List<T>) : MessageDecodeResult<T>
    data class Rejected(val reason: MessageRejection, val offset: Int, val detail: String) : MessageDecodeResult<Nothing>
}

enum class MessageRejection { TRUNCATED, UNKNOWN_OPCODE, INVALID_LENGTH, INVALID_VALUE, WRONG_DIRECTION, NOT_SEQUENCED }

/** Abort decoding; [MessageBodyCodec] converts this exception into a typed rejection. */
class MessageReadException(val reason: MessageRejection, val offset: Int, detail: String) : RuntimeException(detail, null, false, false)
