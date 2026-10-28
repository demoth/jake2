package jake2.qcommon.network.messages.server

/** Disconnect notification; no other fields. */
data object DisconnectMessage : ServerMessage(ServerMessageType.svc_disconnect)
