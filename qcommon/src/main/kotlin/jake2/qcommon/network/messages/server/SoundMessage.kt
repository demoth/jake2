package jake2.qcommon.network.messages.server

import jake2.qcommon.Defines.*
import jake2.qcommon.math.Vector3f

/** Logical channel (0..7); the wire packs the entity index in the upper bits. */
data class SoundSource(val entityIndex: Int, val channel: Int) {
    init {
        require(entityIndex in 0 until MAX_EDICTS) { "Invalid sound entity index" }
        require(channel in 0..7) { "Invalid sound channel" }
    }
}

/**
 * Each entity can have eight independent sound sources, like voice, weapon, feet, etc.
 * If channel & 8, the sound will be sent to everyone, not just things in the PHS.
 * FIXME: if an entity isn't in PHS, it must be forced to be sent or have the origin explicitly sent.
 *
 * Channel 0 is an auto-allocate channel; the others override anything already running on that entity/channel pair.
 * An attenuation of 0 plays at full volume everywhere in the level. Larger attenuation drops off (max 4 attenuation).
 * Time offsets range from 0.0 to 0.255 seconds, starting sounds later in the frame than they normally would.
 * If [origin] is null, use the entity origin or the midpoint of the entity box for bmodels.
 *
 * Optional bytes retain field presence, including explicitly transmitted defaults and zero values.
 * Integer wire milliseconds avoid losing a received value through float seconds.
 * The position is immutable; audio consumers copy it into their own vector storage.
 */
data class SoundMessage(
    val soundIndex: Int,
    val volumeByte: Int? = null,
    val attenuationByte: Int? = null,
    val timeOffsetMillis: Int? = null,
    val source: SoundSource? = null,
    val origin: Vector3f? = null,
    val extraFlags: Int = 0,
) : ServerMessage(ServerMessageType.svc_sound) {
    init {
        require(soundIndex in 0..255) { "Sound index exceeds unsigned byte range" }
        require(volumeByte == null || volumeByte in 0..255) { "Sound volume exceeds unsigned byte range" }
        require(attenuationByte == null || attenuationByte in 0..255) { "Sound attenuation exceeds unsigned byte range" }
        require(timeOffsetMillis == null || timeOffsetMillis in 0..255) { "Sound offset exceeds unsigned byte range" }
        require(extraFlags in 0..255 && extraFlags and KNOWN_FLAGS == 0) { "Extra flags overlap sound fields" }
    }

    val flags: Int get() = extraFlags or
        (if (volumeByte != null) SND_VOLUME else 0) or
        (if (attenuationByte != null) SND_ATTENUATION else 0) or
        (if (timeOffsetMillis != null) SND_OFFSET else 0) or
        (if (source != null) SND_ENT else 0) or
        (if (origin != null) SND_POS else 0)
    val volume: Float get() = volumeByte?.div(255f) ?: DEFAULT_SOUND_PACKET_VOLUME
    val attenuation: Float get() = attenuationByte?.div(64f) ?: DEFAULT_SOUND_PACKET_ATTENUATION
    val timeOffset: Float get() = (timeOffsetMillis ?: 0) / 1000f
    val entityIndex: Int get() = source?.entityIndex ?: 0
    val channel: Int get() = source?.channel ?: 0

    companion object {
        const val KNOWN_FLAGS = SND_VOLUME or SND_ATTENUATION or SND_OFFSET or SND_ENT or SND_POS

        /** Snapshot game arrays and retain the protocol's existing truncation/wrapping of outbound float values. */
        @JvmStatic
        fun fromGame(flags: Int, soundIndex: Int, volume: Float, attenuation: Float, timeOffset: Float,
                     packedChannel: Int, origin: FloatArray?): SoundMessage = SoundMessage(
            soundIndex = soundIndex and 255,
            volumeByte = if (flags and SND_VOLUME != 0) (volume * 255).toInt() and 255 else null,
            attenuationByte = if (flags and SND_ATTENUATION != 0) (attenuation * 64).toInt() and 255 else null,
            timeOffsetMillis = if (flags and SND_OFFSET != 0) (timeOffset * 1000).toInt() and 255 else null,
            source = if (flags and SND_ENT != 0) SoundSource(packedChannel ushr 3, packedChannel and 7) else null,
            origin = if (flags and SND_POS != 0) Vector3f(requireNotNull(origin) { "Positioned sound requires an origin" }) else null,
            extraFlags = flags and KNOWN_FLAGS.inv() and 255,
        )
    }
}
