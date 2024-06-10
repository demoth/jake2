package org.demoth.cake

import jake2.qcommon.network.messages.server.ConfigStringMessage
import jake2.qcommon.network.messages.server.FrameHeaderMessage
import jake2.qcommon.network.messages.server.PacketEntitiesMessage
import jake2.qcommon.network.messages.server.PlayerInfoMessage
import jake2.qcommon.network.messages.server.ServerDataMessage
import jake2.qcommon.network.messages.server.SoundMessage
import jake2.qcommon.network.messages.server.SpawnBaselineMessage

/**
 * Handle updates from server
 */
interface ServerMessageProcessor {

    // These messages are sent when connecting to the server
    fun processServerDataMessage(msg: ServerDataMessage)
    fun processConfigStringMessage(msg: ConfigStringMessage)
    fun processBaselineMessage(msg: SpawnBaselineMessage)

    // These messages are sent every frame
    fun processServerFrameHeader(msg: FrameHeaderMessage)
    fun processPlayerInfoMessage(msg: PlayerInfoMessage)
    fun processPacketEntitiesMessage(msg: PacketEntitiesMessage): Boolean

    // These messages are sent occasionally
    fun processSoundMessage(msg: SoundMessage)
}