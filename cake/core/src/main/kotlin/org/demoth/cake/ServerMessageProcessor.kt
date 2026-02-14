package org.demoth.cake

import jake2.qcommon.network.messages.server.*

/**
 * Contract for processing server messages in client runtime order.
 *
 * Ownership/lifecycle:
 * implemented by `Game3dScreen`, invoked by `Cake.parseServerMessage`.
 *
 * Timing:
 * all callbacks run on the main render thread after packet acceptance.
 *
 * Legacy counterpart:
 * grouped `CL_ParseServerMessage` branches in the old client.
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

    // These messages are sent occasionally.
    // Text messages are also echoed to console by the implementation to match legacy behavior.
    fun processSoundMessage(msg: SoundMessage)
    fun processWeaponSoundMessage(msg: WeaponSoundMessage)
    fun processPrintMessage(msg: PrintMessage)
    fun processPrintCenterMessage(msg: PrintCenterMessage)
    fun processLayoutMessage(msg: LayoutMessage)
    fun processInventoryMessage(msg: InventoryMessage)
}
