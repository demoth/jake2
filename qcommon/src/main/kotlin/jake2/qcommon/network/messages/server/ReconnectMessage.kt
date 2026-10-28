package jake2.qcommon.network.messages.server

/** Different from the "reconnect" command sent in a [StuffTextMessage]. */
data object ReconnectMessage : ServerMessage(ServerMessageType.svc_reconnect)
