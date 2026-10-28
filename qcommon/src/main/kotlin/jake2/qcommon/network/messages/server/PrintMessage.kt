package jake2.qcommon.network.messages.server

/** Print message to console or to the top of the screen. */
data class PrintMessage(val level: Int, val text: String) : ServerMessage(ServerMessageType.svc_print)
