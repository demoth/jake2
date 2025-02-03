package org.demoth.cake

import jake2.qcommon.network.messages.server.*

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
    fun processWeaponSoundMessage(msg: WeaponSoundMessage)
}