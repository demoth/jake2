package jake2.qcommon.network.messages.server

/** Disconnect notification; no other fields. */
data object DisconnectMessage : ServerMessage(ServerMessageType.svc_disconnect)

/** Different from the "reconnect" command sent in a [StuffTextMessage]. */
data object ReconnectMessage : ServerMessage(ServerMessageType.svc_reconnect)

/** Wire no-op used when encoding; decoding skips it without publishing an application message. */
data object NopMessage : ServerMessage(ServerMessageType.svc_nop)

data object EndOfServerPacketMessage : ServerMessage(ServerMessageType.svc_end)
