package jake2.qcommon.network.messages.server

/** Print message to console or to the top of the screen. */
data class PrintMessage(val level: Int, val text: String) : ServerMessage(ServerMessageType.svc_print)

/**
 * Print a message in the center of the screen, like when a door is closed.
 * Sent to a particular client only.
 */
data class PrintCenterMessage(val text: String) : ServerMessage(ServerMessageType.svc_centerprint)

data class LayoutMessage(val layout: String) : ServerMessage(ServerMessageType.svc_layout)

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
