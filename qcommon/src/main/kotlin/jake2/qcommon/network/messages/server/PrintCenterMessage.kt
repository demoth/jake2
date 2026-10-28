package jake2.qcommon.network.messages.server

/**
 * Print a message in the center of the screen, like when a door is closed.
 * Sent to a particular client only.
 */
data class PrintCenterMessage(val text: String) : ServerMessage(ServerMessageType.svc_centerprint)
