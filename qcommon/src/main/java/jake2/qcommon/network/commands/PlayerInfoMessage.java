package jake2.qcommon.network.commands;

import jake2.qcommon.Defines;
import jake2.qcommon.MSG;
import jake2.qcommon.network.NetworkCommandType;
import jake2.qcommon.sizebuf_t;

public class PlayerInfoMessage extends NetworkMessage {
    public PlayerInfoMessage(int messageFlags, int pmType, short[] pmOrigin, short[] pmVelocity, byte pmTime, byte pmFlags, short pmGravity, short[] pmDeltaAngles, float[] viewOffset, float[] viewAngles, float[] kickAngles, int gunIndex, int gunFrame, float[] gunOffset, float[] gunAngles, float[] blend, float fov, int rdFlags, int statsMask, short[] stats) {
        super(NetworkCommandType.svc_playerinfo);
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

    // determine what needs to be sent
    public final int messageFlags;
    public final int pmType;
    public final short[] pmOrigin;
    public final short[] pmVelocity;
    public final byte pmTime;
    public final byte pmFlags;
    public final short pmGravity;
    public final short[] pmDeltaAngles;
    public final float[] viewOffset;
    public final float[] viewAngles;
    public final float[] kickAngles;
    public final int gunIndex;
    public final int gunFrame;
    public final float[] gunOffset;
    public final float[] gunAngles;
    public final float[] blend;
    public final float fov;
    public final int rdFlags;
    public final int statsMask;
    public final short[] stats;

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

    }
}
