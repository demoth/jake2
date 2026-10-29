package jake2.qcommon.network.messages.client

import jake2.qcommon.usercmd_t

/** Signed 16-bit command angles, retained as immutable components rather than a shared engine array. */
data class CommandAngles(val pitch: Int = 0, val yaw: Int = 0, val roll: Int = 0) {
    init {
        require(pitch in Short.MIN_VALUE..Short.MAX_VALUE && yaw in Short.MIN_VALUE..Short.MAX_VALUE && roll in Short.MIN_VALUE..Short.MAX_VALUE)
    }
    operator fun get(index: Int): Int = when (index) {
        0 -> pitch
        1 -> yaw
        2 -> roll
        else -> throw IndexOutOfBoundsException("Command angle $index")
    }
}

/** Owned command state. Byte fields are unsigned Ints; movement and angle fields are signed shorts. */
data class UserCommand(
    val msec: Int = 0,
    val buttons: Int = 0,
    val angles: CommandAngles = CommandAngles(),
    val forwardmove: Int = 0,
    val sidemove: Int = 0,
    val upmove: Int = 0,
    val impulse: Int = 0,
    /** Light level the player is standing on. */
    val lightlevel: Int = 0,
) {
    init {
        require(msec in 0..255 && buttons in 0..255 && impulse in 0..255 && lightlevel in 0..255)
        require(forwardmove in Short.MIN_VALUE..Short.MAX_VALUE && sidemove in Short.MIN_VALUE..Short.MAX_VALUE && upmove in Short.MIN_VALUE..Short.MAX_VALUE)
    }

    /** Game callbacks receive their own mutable command, never the network snapshot's storage. */
    fun toEngineCommand(): usercmd_t = usercmd_t().also(::copyTo)

    fun copyTo(target: usercmd_t) {
        target.msec = msec.toByte(); target.buttons = buttons.toByte()
        target.angles[0] = angles.pitch.toShort(); target.angles[1] = angles.yaw.toShort(); target.angles[2] = angles.roll.toShort()
        target.forwardmove = forwardmove.toShort(); target.sidemove = sidemove.toShort(); target.upmove = upmove.toShort()
        target.impulse = impulse.toByte(); target.lightlevel = lightlevel.toByte()
    }

    companion object {
        @JvmStatic fun fromEngine(command: usercmd_t): UserCommand = UserCommand(
            command.msec.toInt() and 255, command.buttons.toInt() and 255,
            CommandAngles(command.angles[0].toInt(), command.angles[1].toInt(), command.angles[2].toInt()),
            command.forwardmove.toInt(), command.sidemove.toInt(), command.upmove.toInt(),
            command.impulse.toInt() and 255, command.lightlevel.toInt() and 255,
        )
    }
}

/**
 * Send this command and the previous two so a dropped packet can be recovered.
 * Checksum validity is immutable decode metadata; encoding recalculates the checksum using its explicit sequence context.
 */
data class MoveMessage(
    val lastReceivedFrame: Int,
    val oldestCmd: UserCommand,
    val oldCmd: UserCommand,
    val newCmd: UserCommand,
    val checksumValid: Boolean = true,
) : ClientMessage(ClientMessageType.CLC_MOVE) {
    companion object {
        /**
         * When noCompress is set, send an invalid lastReceivedFrame marker so the server sends the next world
         * snapshot as a full frame instead of delta-compressing against a frame we could not reconstruct locally.
         */
        @JvmStatic
        fun fromInput(noCompress: Boolean, lastReceivedFrame: Int, oldestCmd: usercmd_t, oldCmd: usercmd_t, newCmd: usercmd_t): MoveMessage =
            MoveMessage(if (noCompress) 0 else lastReceivedFrame,
                UserCommand.fromEngine(oldestCmd), UserCommand.fromEngine(oldCmd), UserCommand.fromEngine(newCmd))
    }
}
