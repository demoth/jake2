package org.demoth.cake.audio

import com.badlogic.gdx.math.Vector3
import jake2.qcommon.Defines

/**
 * Shared distance attenuation rule for gameplay sounds.
 *
 * This keeps `SoundMessage` and client-side effects consistent, including the legacy `ATTN_STATIC`
 * behavior tweak (stronger falloff).
 */
object SpatialSoundAttenuation {
    fun calculate(origin: Vector3, listener: Vector3, attenuation: Float): Float {
        if (attenuation <= 0f) {
            return 1f
        }
        val distance = origin.dst(listener)
        val rolloff = if (attenuation == Defines.ATTN_STATIC.toFloat()) attenuation * 2f else attenuation
        val referenceDistance = 200f
        if (distance <= referenceDistance) {
            return 1f
        }
        return (referenceDistance / (referenceDistance + rolloff * (distance - referenceDistance))).coerceIn(0f, 1f)
    }
}
