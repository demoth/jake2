package jake2.qcommon.network.messages.server

/** Wire no-op used when encoding; decoding skips it without publishing an application message. */
data object NopMessage : ServerMessage(ServerMessageType.svc_nop)
