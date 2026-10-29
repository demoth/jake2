package jake2.qcommon.network.messages.server

import jake2.qcommon.Defines.*

data class ConfigStringMessage(val index: Int, val config: String) : ServerMessage(ServerMessageType.svc_configstring) {
    fun getType(index: Int): String = when (index) {
        CS_NAME -> "CS_NAME"
        CS_CDTRACK -> "CS_CDTRACK"
        CS_SKY -> "CS_SKY"
        CS_SKYAXIS -> "CS_SKYAXIS"
        CS_SKYROTATE -> "CS_SKYROTATE"
        CS_STATUSBAR -> "CS_STATUSBAR"
        // Reserved configstring slots lie between CS_STATUSBAR (5) and CS_AIRACCEL (29).
        CS_AIRACCEL -> "CS_AIRACCEL"
        CS_MAXCLIENTS -> "CS_MAXCLIENTS"
        CS_MAPCHECKSUM -> "CS_MAPCHECKSUM"
        else -> when {
            index < CS_SOUNDS -> "CS_MODELS"
            index < CS_IMAGES -> "CS_SOUNDS"
            index < CS_LIGHTS -> "CS_IMAGES"
            index < CS_ITEMS -> "CS_LIGHTS"
            index < CS_PLAYERSKINS -> "CS_ITEMS"
            index < CS_GENERAL -> "CS_PLAYERSKINS"
            index < MAX_CONFIGSTRINGS -> "CS_GENERAL"
            else -> "UNKNOWN" // Incoming indices are validated by the codec.
        }
    }

    override fun toString(): String = "ConfigStringMessage{${getType(index)}($index)=$config}"
}
