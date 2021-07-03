package jake2.qcommon.network.messages.server;

import jake2.qcommon.Com;
import jake2.qcommon.Defines;
import jake2.qcommon.MSG;
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
public class SoundMessage extends ServerMessage {
    public int flags;
    public int soundIndex;
    public float volume;
    public float attenuation;
    public float timeOffset;
    public int sendchan;
    public float[] origin;

    public int entityIndex;

    public SoundMessage() {
        super(ServerMessageType.svc_sound);
    }

    public SoundMessage(int flags, int soundIndex, float volume, float attenuation, float timeOffset, int sendchan, float[] origin) {
        this();
        this.flags = flags;
        this.soundIndex = soundIndex;
        this.volume = volume;
        this.attenuation = attenuation;
        this.timeOffset = timeOffset;
        this.sendchan = sendchan;
        this.origin = origin;
    }


    // todo: sync read & write logic (make flags private)
    @Override
    protected void writeProperties(sizebuf_t buffer) {
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
        this.flags = MSG.ReadByte(buffer);
        this.soundIndex = MSG.ReadByte(buffer);

        if ((flags & Defines.SND_VOLUME) != 0)
            volume = MSG.ReadByte(buffer) / 255.0f;
        else
            volume = Defines.DEFAULT_SOUND_PACKET_VOLUME;

        if ((flags & Defines.SND_ATTENUATION) != 0)
            attenuation = MSG.ReadByte(buffer) / 64.0f;
        else
            attenuation = Defines.DEFAULT_SOUND_PACKET_ATTENUATION;

        if ((flags & Defines.SND_OFFSET) != 0)
            timeOffset = MSG.ReadByte(buffer) / 1000.0f;
        else
            timeOffset = 0;

        if ((flags & Defines.SND_ENT) != 0) { // entity reletive
            sendchan = MSG.ReadShort(buffer);
            entityIndex = sendchan >> 3;
            if (entityIndex > Defines.MAX_EDICTS)
                Com.Error(Defines.ERR_DROP, "CL_ParseStartSoundPacket: ent = " + entityIndex);

            sendchan &= 7;
        } else {
            entityIndex = 0;
            sendchan = 0;
        }

        if ((flags & Defines.SND_POS) != 0) { // positioned in space
            float[] pos_v = new float[3];
            MSG.ReadPos(buffer, pos_v);
            // is ok. sound driver copies
            origin = pos_v;
        } else
            // use entity number
            origin = null;

    }

    @Override
    int getSize() {
        return -1;
    }
}
