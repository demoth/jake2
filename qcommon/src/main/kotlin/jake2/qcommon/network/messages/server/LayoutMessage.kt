package jake2.qcommon.network.messages.server

data class LayoutMessage(val layout: String) : ServerMessage(ServerMessageType.svc_layout)
