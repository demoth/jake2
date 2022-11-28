package jake2.game

import jake2.game.adapters.SuperAdapter.Companion.registerUse
import jake2.qcommon.Defines
import jake2.qcommon.network.MulticastTypes
import jake2.qcommon.network.messages.server.PointTEMessage
import jake2.qcommon.util.Lib

/**
 * QUAKED target_temp_entity (1 0 0) (-8 -8 -8) (8 8 8) 
 * Fire an origin based temp entity event to the clients. 
 * "style" type byte, see [jake2.qcommon.network.messages.server.PointTEMessage.SUBTYPES]
 */
fun targetTempEntity(self: SubgameEntity, game: GameExportsImpl) {
    self.use = tempEntityUse
}

private val tempEntityUse = registerUse("Use_Target_Tent") { self, _, _, game ->  
    game.gameImports.multicastMessage(self.s.origin, PointTEMessage(self.style, self.s.origin), MulticastTypes.MULTICAST_PVS);
}

/**
 * QUAKED target_speaker (1 0 0) (-8 -8 -8) (8 8 8) 
 * looped-on 
 * looped-off
 * reliable 
 * "noise" wav file to play 
 * "attenuation" -1 = none, send to whole level 
 * 1 = normal fighting sounds 
 * 2 = idle sound level 
 * 3 = ambient sound level 
 * "volume" 0.0 to 1.0 (1.0 default)
 *
 * Normal sounds play each time the target is used. The reliable flag can be
 * set for crucial voiceovers.
 *
 * Looped sounds are always atten 3 / vol 1, and the use function toggles it on/off. 
 * Multiple identical looping sounds will just increase volume without any speed cost.
 */
private const val LOOPED_ON = 1
private const val LOOPED_OFF = 2
private const val RELIABLE = 4
fun targetSpeaker(self: SubgameEntity, game: GameExportsImpl) {
    if (self.st.noise == null) {
        game.gameImports.dprintf("target_speaker with no noise set at ${Lib.vtos(self.s.origin)}\n")
        return
    }
    val noise: String = if (self.st.noise.contains(".wav")) self.st.noise else "${self.st.noise}.wav"
    self.noise_index = game.gameImports.soundindex(noise)
    if (self.volume == 0f)
        self.volume = 1.0f

    if (self.attenuation == 0f)
        self.attenuation = 1.0f
    else if (self.attenuation == -1f) // use -1 because 0 defaults to 1
        self.attenuation = 0f

    // check for prestarted looping sound
    if (self.hasSpawnFlag(LOOPED_ON))
        self.s.sound = self.noise_index
    self.use = targetSpeakerUse

    // must link the entity, so we get areas and clusters so
    // the server can determine who to send updates to
    game.gameImports.linkentity(self)
}

private val targetSpeakerUse = registerUse("Use_Target_Speaker") { self, _, _, game ->
    if (self.hasSpawnFlag(LOOPED_ON) && self.hasSpawnFlag(LOOPED_OFF)) { // looping sound toggles
        if (self.s.sound != 0)
            self.s.sound = 0 // turn it off
        else 
            self.s.sound = self.noise_index // start it
    } else { // normal sound
        val chan: Int = if (self.hasSpawnFlag(RELIABLE)) Defines.CHAN_VOICE or Defines.CHAN_RELIABLE else Defines.CHAN_VOICE
        // use a positioned_sound, because this entity won't normally be
        // sent to any clients because it is invisible
        game.gameImports.positioned_sound(
            self.s.origin, self, chan,
            self.noise_index, self.volume, self.attenuation, 0f
        )
    }

}