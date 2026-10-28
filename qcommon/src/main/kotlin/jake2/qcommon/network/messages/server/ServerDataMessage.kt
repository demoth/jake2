package jake2.qcommon.network.messages.server

import jake2.qcommon.Defines.PROTOCOL_VERSION
import jake2.qcommon.network.messages.client.StringCmdMessage

/**
 * Generic server information, sent as a response to the [StringCmdMessage.NEW] client command.
 * @property gameName name of the mod folder; empty means "baseq2".
 * @property playerNumber local player slot, or -1 for cinematic/picture presentation.
 */
data class ServerDataMessage(
    val protocol: Int,
    val spawnCount: Int,
    val demo: Boolean,
    val gameName: String,
    val playerNumber: Int,
    val levelString: String,
) : ServerMessage(ServerMessageType.svc_serverdata) {
    companion object {
        /** Outbound server data uses the supported protocol; decoded values retain their received version. */
        @JvmStatic
        fun forClient(spawnCount: Int, demo: Boolean, gameName: String, playerNumber: Int, levelString: String): ServerDataMessage =
            ServerDataMessage(PROTOCOL_VERSION, spawnCount, demo, gameName, playerNumber, levelString)
    }
}
