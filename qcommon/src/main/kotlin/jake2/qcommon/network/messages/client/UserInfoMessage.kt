package jake2.qcommon.network.messages.client

data class UserInfoMessage(val userInfo: String) : ClientMessage(ClientMessageType.CLC_USERINFO)
