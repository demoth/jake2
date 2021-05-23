package jake2.qcommon.network.commands;

import jake2.qcommon.Defines;
import jake2.qcommon.Globals;
import jake2.qcommon.MSG;
import jake2.qcommon.network.NetworkCommandType;
import jake2.qcommon.sizebuf_t;

public class PlayerInfoMessage extends NetworkMessage {
    // determine what needs to be sent
    public int messageFlags;
    public Integer pmType = null;
    public short[] pmOrigin = null;
    public short[] pmVelocity = null;
    public Byte pmTime = null;
    public Byte pmFlags = null;
    public Short pmGravity = null;
    public short[] pmDeltaAngles = null;
    public float[] viewOffset = null;
    public float[] viewAngles = null;
    public float[] kickAngles = null;
    public Integer gunIndex = null;
    public Integer gunFrame = null;
    public float[] gunOffset = null;
    public float[] gunAngles = null;
    public float[] blend = null;
    public Float fov = null;
    public Integer rdFlags = null;
    public Integer statsMask = null;
    public short[] stats = null;

    public PlayerInfoMessage() {
        super(NetworkCommandType.svc_playerinfo);
    }

    public PlayerInfoMessage(int messageFlags, int pmType, short[] pmOrigin, short[] pmVelocity, byte pmTime, byte pmFlags, short pmGravity, short[] pmDeltaAngles, float[] viewOffset, float[] viewAngles, float[] kickAngles, int gunIndex, int gunFrame, float[] gunOffset, float[] gunAngles, float[] blend, float fov, int rdFlags, int statsMask, short[] stats) {
        this();
        this.messageFlags = messageFlags;
        this.pmType = pmType;
        this.pmOrigin = pmOrigin;
        this.pmVelocity = pmVelocity;
        this.pmTime = pmTime;
        this.pmFlags = pmFlags;
        this.pmGravity = pmGravity;
        this.pmDeltaAngles = pmDeltaAngles;
        this.viewOffset = viewOffset;
        this.viewAngles = viewAngles;
        this.kickAngles = kickAngles;
        this.gunIndex = gunIndex;
        this.gunFrame = gunFrame;
        this.gunOffset = gunOffset;
        this.gunAngles = gunAngles;
        this.blend = blend;
        this.fov = fov;
        this.rdFlags = rdFlags;
        this.statsMask = statsMask;
        this.stats = stats;
    }


    @Override
    protected void writeProperties(sizebuf_t buffer) {
        MSG.WriteShort(buffer, messageFlags);

        // write the pmove_state_t
        if ((messageFlags & Defines.PS_M_TYPE) != 0)
            MSG.WriteByte(buffer, pmType);

        if ((messageFlags & Defines.PS_M_ORIGIN) != 0) {
            MSG.WriteShort(buffer, pmOrigin[0]);
            MSG.WriteShort(buffer, pmOrigin[1]);
            MSG.WriteShort(buffer, pmOrigin[2]);
        }

        if ((messageFlags & Defines.PS_M_VELOCITY) != 0) {
            MSG.WriteShort(buffer, pmVelocity[0]);
            MSG.WriteShort(buffer, pmVelocity[1]);
            MSG.WriteShort(buffer, pmVelocity[2]);
        }

        if ((messageFlags & Defines.PS_M_TIME) != 0)
            MSG.WriteByte(buffer, pmTime);

        if ((messageFlags & Defines.PS_M_FLAGS) != 0)
            MSG.WriteByte(buffer, pmFlags);

        if ((messageFlags & Defines.PS_M_GRAVITY) != 0)
            MSG.WriteShort(buffer, pmGravity);

        if ((messageFlags & Defines.PS_M_DELTA_ANGLES) != 0) {
            MSG.WriteShort(buffer, pmDeltaAngles[0]);
            MSG.WriteShort(buffer, pmDeltaAngles[1]);
            MSG.WriteShort(buffer, pmDeltaAngles[2]);
        }

        // write the rest of the player_state_t
        if ((messageFlags & Defines.PS_VIEWOFFSET) != 0) {
            MSG.WriteChar(buffer, viewOffset[0] * 4);
            MSG.WriteChar(buffer, viewOffset[1] * 4);
            MSG.WriteChar(buffer, viewOffset[2] * 4);
        }

        if ((messageFlags & Defines.PS_VIEWANGLES) != 0) {
            MSG.WriteAngle16(buffer, viewAngles[0]);
            MSG.WriteAngle16(buffer, viewAngles[1]);
            MSG.WriteAngle16(buffer, viewAngles[2]);
        }

        if ((messageFlags & Defines.PS_KICKANGLES) != 0) {
            MSG.WriteChar(buffer, kickAngles[0] * 4);
            MSG.WriteChar(buffer, kickAngles[1] * 4);
            MSG.WriteChar(buffer, kickAngles[2] * 4);
        }

        if ((messageFlags & Defines.PS_WEAPONINDEX) != 0) {
            MSG.WriteByte(buffer, gunIndex);
        }

        if ((messageFlags & Defines.PS_WEAPONFRAME) != 0) {
            MSG.WriteByte(buffer, gunFrame);
            MSG.WriteChar(buffer, gunOffset[0] * 4);
            MSG.WriteChar(buffer, gunOffset[1] * 4);
            MSG.WriteChar(buffer, gunOffset[2] * 4);
            MSG.WriteChar(buffer, gunAngles[0] * 4);
            MSG.WriteChar(buffer, gunAngles[1] * 4);
            MSG.WriteChar(buffer, gunAngles[2] * 4);
        }

        if ((messageFlags & Defines.PS_BLEND) != 0) {
            MSG.WriteByte(buffer, blend[0] * 255);
            MSG.WriteByte(buffer, blend[1] * 255);
            MSG.WriteByte(buffer, blend[2] * 255);
            MSG.WriteByte(buffer, blend[3] * 255);
        }
        if ((messageFlags & Defines.PS_FOV) != 0)
            MSG.WriteByte(buffer, fov);
        if ((messageFlags & Defines.PS_RDFLAGS) != 0)
            MSG.WriteByte(buffer, rdFlags);

        MSG.WriteLong(buffer, statsMask);
        for (int i = 0; i < Defines.MAX_STATS; i++) {
            if ((statsMask & (1 << i)) != 0)
                MSG.WriteShort(buffer, stats[i]);
        }
    }

    @Override
    void parse(sizebuf_t buffer) {
        this.messageFlags = MSG.ReadShort(buffer);
        if ((messageFlags & Defines.PS_M_TYPE) != 0) {
            this.pmType = MSG.ReadByte(buffer);
        }
        if ((messageFlags & Defines.PS_M_ORIGIN) != 0) {
            this.pmOrigin = new short[3];
            this.pmOrigin[0] = MSG.ReadShort(buffer);
            this.pmOrigin[1] = MSG.ReadShort(buffer);
            this.pmOrigin[2] = MSG.ReadShort(buffer);
        }
        if ((messageFlags & Defines.PS_M_VELOCITY) != 0) {
            this.pmVelocity = new short[3];
            this.pmVelocity[0] = MSG.ReadShort(buffer);
            this.pmVelocity[1] = MSG.ReadShort(buffer);
            this.pmVelocity[2] = MSG.ReadShort(buffer);
        }
        if ((messageFlags & Defines.PS_M_TIME) != 0) {
            this.pmTime = (byte) MSG.ReadByte(buffer);
        }
        if ((messageFlags & Defines.PS_M_FLAGS) != 0) {
            this.pmFlags = (byte) MSG.ReadByte(buffer);
        }
        if ((messageFlags & Defines.PS_M_GRAVITY) != 0) {
            this.pmGravity = MSG.ReadShort(buffer);
        }
        if ((messageFlags & Defines.PS_M_DELTA_ANGLES) != 0) {
            this.pmDeltaAngles = new short[3];
            this.pmDeltaAngles[0] = MSG.ReadShort(buffer);
            this.pmDeltaAngles[1] = MSG.ReadShort(buffer);
            this.pmDeltaAngles[2] = MSG.ReadShort(buffer);
        }
        if ((messageFlags & Defines.PS_VIEWOFFSET) != 0) {
            this.viewOffset = new float[3];
            this.viewOffset[0] = MSG.ReadChar(buffer) * 0.25f;
            this.viewOffset[1] = MSG.ReadChar(buffer) * 0.25f;
            this.viewOffset[2] = MSG.ReadChar(buffer) * 0.25f;
        }
        if ((messageFlags & Defines.PS_VIEWANGLES) != 0) {
            this.viewAngles = new float[3];
            this.viewAngles[0] = MSG.ReadAngle16(buffer);
            this.viewAngles[1] = MSG.ReadAngle16(buffer);
            this.viewAngles[2] = MSG.ReadAngle16(buffer);
        }
        if ((messageFlags & Defines.PS_KICKANGLES) != 0) {
            this.kickAngles = new float[3];
            this.kickAngles[0] = MSG.ReadChar(buffer) * 0.25f;
            this.kickAngles[1] = MSG.ReadChar(buffer) * 0.25f;
            this.kickAngles[2] = MSG.ReadChar(buffer) * 0.25f;
        }
        if ((messageFlags & Defines.PS_WEAPONINDEX) != 0) {
            this.gunIndex = MSG.ReadByte(buffer);
        }
        if ((messageFlags & Defines.PS_WEAPONFRAME) != 0) {
            this.gunFrame = MSG.ReadByte(buffer);
            this.gunOffset = new float[3];
            this.gunOffset[0] = MSG.ReadChar(buffer) * 0.25f;
            this.gunOffset[1] = MSG.ReadChar(buffer) * 0.25f;
            this.gunOffset[2] = MSG.ReadChar(buffer) * 0.25f;
            this.gunAngles = new float[3];
            this.gunAngles[0] = MSG.ReadChar(buffer) * 0.25f;
            this.gunAngles[1] = MSG.ReadChar(buffer) * 0.25f;
            this.gunAngles[2] = MSG.ReadChar(buffer) * 0.25f;
        }
        if ((messageFlags & Defines.PS_BLEND) != 0) {
            this.blend = new float[4];
            this.blend[0] = MSG.ReadByte(buffer) / 255.0f;
            this.blend[1] = MSG.ReadByte(buffer) / 255.0f;
            this.blend[2] = MSG.ReadByte(buffer) / 255.0f;
            this.blend[3] = MSG.ReadByte(buffer) / 255.0f;
        }
        if ((messageFlags & Defines.PS_FOV) != 0) {
            this.fov = (float) MSG.ReadByte(buffer);
        }
        if ((messageFlags & Defines.PS_RDFLAGS) != 0) {
            this.rdFlags = MSG.ReadByte(buffer);
        }
        // parse stats
        this.statsMask = MSG.ReadLong(buffer);
        this.stats = new short[Defines.MAX_STATS];
        for (int i = 0; i < Defines.MAX_STATS; i++) {
            if ((statsMask & (1 << i)) != 0) {
                this.stats[i] = MSG.ReadShort(Globals.net_message);
            }
        }
    }
}