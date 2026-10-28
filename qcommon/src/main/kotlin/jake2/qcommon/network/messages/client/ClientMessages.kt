package jake2.qcommon.network.messages.client

data object EndOfClientPacketMessage : ClientMessage(ClientMessageType.CLC_BAD)

data object NoopMessage : ClientMessage(ClientMessageType.CLC_NOP)

data class UserInfoMessage(val userInfo: String) : ClientMessage(ClientMessageType.CLC_USERINFO)

/**
 * Represents a user command sent from client to server, either manual or automatic.
 * Some commands are built-in (the constants below).
 * Other commands are redirected to the game to handle (like, 'god' or 'say').
 */
data class StringCmdMessage(val command: String) : ClientMessage(ClientMessageType.CLC_STRINGCMD) {
    companion object {
        const val NEW = "new"
        const val CONFIG_STRINGS = "configstrings"
        const val BASELINES = "baselines"
        const val BEGIN = "begin"
        const val NEXT_SERVER = "nextserver"
        const val DISCONNECT = "disconnect"
        const val INFO = "info"
        const val DOWNLOAD = "download"
        const val NEXT_DOWNLOAD = "nextdl"
        const val PRECACHE = "precache"
    }
}
