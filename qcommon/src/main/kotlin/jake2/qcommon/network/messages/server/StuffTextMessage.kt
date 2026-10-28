package jake2.qcommon.network.messages.server

/**
 * Sent to client stuffed into client's console buffer, normally newline terminated.
 * [text] contains the exact wire text. Use [command] to append the command's terminating newline.
 */
data class StuffTextMessage(val text: String) : ServerMessage(ServerMessageType.svc_stufftext) {
    companion object {
        /** Preserve the outbound command convention of appending one newline, including for empty commands. */
        @JvmStatic fun command(text: String): StuffTextMessage = StuffTextMessage("$text\n")
    }
}
