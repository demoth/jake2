package org.demoth.cake.audio

import com.badlogic.gdx.audio.Sound
import java.util.IdentityHashMap

internal object SoundDurationRegistry {
    private val durationsBySound = IdentityHashMap<Sound, Int?>()

    fun register(sound: Sound, durationMs: Int?) {
        synchronized(durationsBySound) {
            durationsBySound[sound] = durationMs
        }
    }

    fun unregister(sound: Sound) {
        synchronized(durationsBySound) {
            durationsBySound.remove(sound)
        }
    }

    fun durationMs(sound: Sound): Int? {
        synchronized(durationsBySound) {
            return durationsBySound[sound]
        }
    }
}
