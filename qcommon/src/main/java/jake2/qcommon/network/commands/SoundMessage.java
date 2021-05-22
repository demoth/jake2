package jake2.qcommon.network.commands;

import jake2.qcommon.Defines;
import jake2.qcommon.MSG;
import jake2.qcommon.network.NetworkCommandType;
import jake2.qcommon.sizebuf_t;

/**
 * Each entity can have eight independent sound sources, like voice,
 * weapon, feet, etc.
 * <p>
 * If channel & 8, the sound will be sent to everyone, not just
 * things in the PHS.
 * <p>
 * FIXME: if entity isn't in PHS, they must be forced to be sent or have the origin explicitly sent.
 * <p>
 * Channel 0 is an auto-allocate channel, the others override anything
 * already running on that entity/channel pair.
 * <p>
 * An attenuation of 0 will play full volume everywhere in the level.
 * Larger attenuation will drop off.  (max 4 attenuation)
 * <p>
 * Timeofs can range from 0.0 to 0.1 to cause sounds to be started
 * later in the frame than they normally would.
 * <p>
 * If origin is null, the origin is determined from the entity origin
 * or the midpoint of the entity box for bmodels.
 * ==================
 */
public class SoundMessage extends NetworkMessage {
    public SoundMessage(int flags, int soundIndex, float volume, float attenuation, float timeOffset, int sendchan, float[] origin) {
        super(NetworkCommandType.svc_sound);
        this.flags = flags;
        this.soundIndex = soundIndex;
        this.volume = volume;
        this.attenuation = attenuation;
        this.timeOffset = timeOffset;
        this.sendchan = sendchan;
        this.origin = origin;
    }

    public final int flags;
    public final int soundIndex;
    public final float volume;
    public final float attenuation;
    public final float timeOffset;
    public final int sendchan;
    public final float[] origin;

    @Override
    protected void sendProps(sizebuf_t buffer) {
        MSG.WriteByte(buffer, flags);
        MSG.WriteByte(buffer, soundIndex);

        if ((flags & Defines.SND_VOLUME) != 0)
            MSG.WriteByte(buffer, volume * 255);

        if ((flags & Defines.SND_ATTENUATION) != 0)
            MSG.WriteByte(buffer, attenuation * 64);

        if ((flags & Defines.SND_OFFSET) != 0)
            MSG.WriteByte(buffer, timeOffset * 1000);

        if ((flags & Defines.SND_ENT) != 0)
            MSG.WriteShort(buffer, sendchan);

        if ((flags & Defines.SND_POS) != 0)
            MSG.WritePos(buffer, origin);

    }

    @Override
    void parse(sizebuf_t buffer) {

    }
}
