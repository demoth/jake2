package jake2.qcommon.network.messages.server

/**
 * Effect for player firing (very similar to [MuzzleFlash2Message]).
 * @property entityIndex index of the owner (player).
 * @property flashType weapon index and silenced flag packed together into one byte.
 */
data class WeaponSoundMessage(val entityIndex: Int, val flashType: Int) : ServerMessage(ServerMessageType.svc_muzzleflash)

/**
 * Firing effect for monsters (very similar to [WeaponSoundMessage]).
 * @property entityIndex index of the owner.
 */
data class MuzzleFlash2Message(val entityIndex: Int, val flashType: Int) : ServerMessage(ServerMessageType.svc_muzzleflash2)
