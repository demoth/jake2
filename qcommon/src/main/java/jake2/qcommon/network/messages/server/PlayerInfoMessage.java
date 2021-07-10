package jake2.qcommon.network.messages.server;

import jake2.qcommon.Defines;
import jake2.qcommon.player_state_t;
import jake2.qcommon.sizebuf_t;

public class PlayerInfoMessage extends ServerMessage {
    private player_state_t previousState;
    // determine what needs to be sent
    public int deltaFlags;
    public int statbits;
    public player_state_t currentState;

    public PlayerInfoMessage() {
        super(ServerMessageType.svc_playerinfo);
    }

    public PlayerInfoMessage(player_state_t previousState, player_state_t currentState) {
        this();
        this.previousState = previousState;
        this.currentState = currentState;
    }

    // determine what needs to be sent
    private void computeDeltaFlags() {
        if (currentState.pmove.pm_type != previousState.pmove.pm_type)
            deltaFlags |= Defines.PS_M_TYPE;

        if (currentState.pmove.origin[0] != previousState.pmove.origin[0]
                || currentState.pmove.origin[1] != previousState.pmove.origin[1]
                || currentState.pmove.origin[2] != previousState.pmove.origin[2])
            deltaFlags |= Defines.PS_M_ORIGIN;

        if (currentState.pmove.velocity[0] != previousState.pmove.velocity[0]
                || currentState.pmove.velocity[1] != previousState.pmove.velocity[1]
                || currentState.pmove.velocity[2] != previousState.pmove.velocity[2])
            deltaFlags |= Defines.PS_M_VELOCITY;

        if (currentState.pmove.pm_time != previousState.pmove.pm_time)
            deltaFlags |= Defines.PS_M_TIME;

        if (currentState.pmove.pm_flags != previousState.pmove.pm_flags)
            deltaFlags |= Defines.PS_M_FLAGS;

        if (currentState.pmove.gravity != previousState.pmove.gravity)
            deltaFlags |= Defines.PS_M_GRAVITY;

        if (currentState.pmove.delta_angles[0] != previousState.pmove.delta_angles[0]
                || currentState.pmove.delta_angles[1] != previousState.pmove.delta_angles[1]
                || currentState.pmove.delta_angles[2] != previousState.pmove.delta_angles[2])
            deltaFlags |= Defines.PS_M_DELTA_ANGLES;

        if (currentState.viewoffset[0] != previousState.viewoffset[0]
                || currentState.viewoffset[1] != previousState.viewoffset[1]
                || currentState.viewoffset[2] != previousState.viewoffset[2])
            deltaFlags |= Defines.PS_VIEWOFFSET;

        if (currentState.viewangles[0] != previousState.viewangles[0]
                || currentState.viewangles[1] != previousState.viewangles[1]
                || currentState.viewangles[2] != previousState.viewangles[2])
            deltaFlags |= Defines.PS_VIEWANGLES;

        if (currentState.kick_angles[0] != previousState.kick_angles[0]
                || currentState.kick_angles[1] != previousState.kick_angles[1]
                || currentState.kick_angles[2] != previousState.kick_angles[2])
            deltaFlags |= Defines.PS_KICKANGLES;

        if (currentState.blend[0] != previousState.blend[0] || currentState.blend[1] != previousState.blend[1]
                || currentState.blend[2] != previousState.blend[2] || currentState.blend[3] != previousState.blend[3])
            deltaFlags |= Defines.PS_BLEND;

        if (currentState.fov != previousState.fov)
            deltaFlags |= Defines.PS_FOV;

        // always sent?
        deltaFlags |= Defines.PS_WEAPONINDEX;

        if (currentState.gunframe != previousState.gunframe)
            deltaFlags |= Defines.PS_WEAPONFRAME;

        if (currentState.rdflags != previousState.rdflags)
            deltaFlags |= Defines.PS_RDFLAGS;

        for (int i = 0; i < Defines.MAX_STATS; i++) {
            if (currentState.stats[i] != previousState.stats[i]) {
                statbits |= 1 << i;
            }
        }
    }

    @Override
    protected void writeProperties(sizebuf_t buffer) {
        computeDeltaFlags();
        buffer.WriteShort(deltaFlags);

        // write the pmove_state_t
        if ((deltaFlags & Defines.PS_M_TYPE) != 0)
            sizebuf_t.WriteByte(buffer, (byte) currentState.pmove.pm_type);

        if ((deltaFlags & Defines.PS_M_ORIGIN) != 0) {
            buffer.WriteShort(currentState.pmove.origin[0]);
            buffer.WriteShort(currentState.pmove.origin[1]);
            buffer.WriteShort(currentState.pmove.origin[2]);
        }

        if ((deltaFlags & Defines.PS_M_VELOCITY) != 0) {
            buffer.WriteShort(currentState.pmove.velocity[0]);
            buffer.WriteShort(currentState.pmove.velocity[1]);
            buffer.WriteShort(currentState.pmove.velocity[2]);
        }

        if ((deltaFlags & Defines.PS_M_TIME) != 0)
            sizebuf_t.WriteByte(buffer, currentState.pmove.pm_time);

        if ((deltaFlags & Defines.PS_M_FLAGS) != 0)
            sizebuf_t.WriteByte(buffer, currentState.pmove.pm_flags);

        if ((deltaFlags & Defines.PS_M_GRAVITY) != 0)
            buffer.WriteShort(currentState.pmove.gravity);

        if ((deltaFlags & Defines.PS_M_DELTA_ANGLES) != 0) {
            buffer.WriteShort(currentState.pmove.delta_angles[0]);
            buffer.WriteShort(currentState.pmove.delta_angles[1]);
            buffer.WriteShort(currentState.pmove.delta_angles[2]);
        }

        // write the rest of the player_state_t
        if ((deltaFlags & Defines.PS_VIEWOFFSET) != 0) {
            sizebuf_t.WriteByte(buffer, (byte) (currentState.viewoffset[0] * 4));
            sizebuf_t.WriteByte(buffer, (byte) (currentState.viewoffset[1] * 4));
            sizebuf_t.WriteByte(buffer, (byte) (currentState.viewoffset[2] * 4));
        }

        if ((deltaFlags & Defines.PS_VIEWANGLES) != 0) {
            sizebuf_t.WriteAngle16(buffer, currentState.viewangles[0]);
            sizebuf_t.WriteAngle16(buffer, currentState.viewangles[1]);
            sizebuf_t.WriteAngle16(buffer, currentState.viewangles[2]);
        }

        if ((deltaFlags & Defines.PS_KICKANGLES) != 0) {
            sizebuf_t.WriteByte(buffer, (byte) (currentState.kick_angles[0] * 4));
            sizebuf_t.WriteByte(buffer, (byte) (currentState.kick_angles[1] * 4));
            sizebuf_t.WriteByte(buffer, (byte) (currentState.kick_angles[2] * 4));
        }

        if ((deltaFlags & Defines.PS_WEAPONINDEX) != 0) {
            sizebuf_t.WriteByte(buffer, (byte) currentState.gunindex);
        }

        if ((deltaFlags & Defines.PS_WEAPONFRAME) != 0) {
            sizebuf_t.WriteByte(buffer, (byte) currentState.gunframe);
            sizebuf_t.WriteByte(buffer, (byte) (currentState.gunoffset[0] * 4));
            sizebuf_t.WriteByte(buffer, (byte) (currentState.gunoffset[1] * 4));
            sizebuf_t.WriteByte(buffer, (byte) (currentState.gunoffset[2] * 4));
            sizebuf_t.WriteByte(buffer, (byte) (currentState.gunangles[0] * 4));
            sizebuf_t.WriteByte(buffer, (byte) (currentState.gunangles[1] * 4));
            sizebuf_t.WriteByte(buffer, (byte) (currentState.gunangles[2] * 4));
        }

        if ((deltaFlags & Defines.PS_BLEND) != 0) {
            sizebuf_t.WriteByte(buffer, (byte) (currentState.blend[0] * 255));
            sizebuf_t.WriteByte(buffer, (byte) (currentState.blend[1] * 255));
            sizebuf_t.WriteByte(buffer, (byte) (currentState.blend[2] * 255));
            sizebuf_t.WriteByte(buffer, (byte) (currentState.blend[3] * 255));
        }
        if ((deltaFlags & Defines.PS_FOV) != 0)
            sizebuf_t.WriteByte(buffer, (byte) currentState.fov);

        if ((deltaFlags & Defines.PS_RDFLAGS) != 0)
            sizebuf_t.WriteByte(buffer, (byte) currentState.rdflags);

        sizebuf_t.WriteInt(buffer, statbits);
        for (int i = 0; i < Defines.MAX_STATS; i++) {
            if ((statbits & (1 << i)) != 0)
                buffer.WriteShort(currentState.stats[i]);
        }
    }

    @Override
    public void parse(sizebuf_t buffer) {
        this.deltaFlags = sizebuf_t.ReadShort(buffer);
        this.currentState = new player_state_t();
        if ((deltaFlags & Defines.PS_M_TYPE) != 0) {
            this.currentState.pmove.pm_type = sizebuf_t.ReadByte(buffer);
        }
        if ((deltaFlags & Defines.PS_M_ORIGIN) != 0) {
            this.currentState.pmove.origin = new short[3];
            this.currentState.pmove.origin[0] = sizebuf_t.ReadShort(buffer);
            this.currentState.pmove.origin[1] = sizebuf_t.ReadShort(buffer);
            this.currentState.pmove.origin[2] = sizebuf_t.ReadShort(buffer);
        }
        if ((deltaFlags & Defines.PS_M_VELOCITY) != 0) {
            this.currentState.pmove.velocity = new short[3];
            this.currentState.pmove.velocity[0] = sizebuf_t.ReadShort(buffer);
            this.currentState.pmove.velocity[1] = sizebuf_t.ReadShort(buffer);
            this.currentState.pmove.velocity[2] = sizebuf_t.ReadShort(buffer);
        }
        if ((deltaFlags & Defines.PS_M_TIME) != 0) {
            this.currentState.pmove.pm_time = (byte) sizebuf_t.ReadByte(buffer);
        }
        if ((deltaFlags & Defines.PS_M_FLAGS) != 0) {
            this.currentState.pmove.pm_flags = (byte) sizebuf_t.ReadByte(buffer);
        }
        if ((deltaFlags & Defines.PS_M_GRAVITY) != 0) {
            this.currentState.pmove.gravity = sizebuf_t.ReadShort(buffer);
        }
        if ((deltaFlags & Defines.PS_M_DELTA_ANGLES) != 0) {
            this.currentState.pmove.delta_angles = new short[3];
            this.currentState.pmove.delta_angles[0] = sizebuf_t.ReadShort(buffer);
            this.currentState.pmove.delta_angles[1] = sizebuf_t.ReadShort(buffer);
            this.currentState.pmove.delta_angles[2] = sizebuf_t.ReadShort(buffer);
        }
        if ((deltaFlags & Defines.PS_VIEWOFFSET) != 0) {
            this.currentState.viewoffset = new float[3];
            this.currentState.viewoffset[0] = sizebuf_t.ReadSignedByte(buffer) * 0.25f;
            this.currentState.viewoffset[1] = sizebuf_t.ReadSignedByte(buffer) * 0.25f;
            this.currentState.viewoffset[2] = sizebuf_t.ReadSignedByte(buffer) * 0.25f;
        }
        if ((deltaFlags & Defines.PS_VIEWANGLES) != 0) {
            this.currentState.viewangles = new float[3];
            this.currentState.viewangles[0] = sizebuf_t.ReadAngleShort(buffer);
            this.currentState.viewangles[1] = sizebuf_t.ReadAngleShort(buffer);
            this.currentState.viewangles[2] = sizebuf_t.ReadAngleShort(buffer);
        }
        if ((deltaFlags & Defines.PS_KICKANGLES) != 0) {
            this.currentState.kick_angles = new float[3];
            this.currentState.kick_angles[0] = sizebuf_t.ReadSignedByte(buffer) * 0.25f;
            this.currentState.kick_angles[1] = sizebuf_t.ReadSignedByte(buffer) * 0.25f;
            this.currentState.kick_angles[2] = sizebuf_t.ReadSignedByte(buffer) * 0.25f;
        }
        if ((deltaFlags & Defines.PS_WEAPONINDEX) != 0) {
            this.currentState.gunindex = sizebuf_t.ReadByte(buffer);
        }
        if ((deltaFlags & Defines.PS_WEAPONFRAME) != 0) {
            this.currentState.gunframe = sizebuf_t.ReadByte(buffer);
            this.currentState.gunoffset = new float[3];
            this.currentState.gunoffset[0] = sizebuf_t.ReadSignedByte(buffer) * 0.25f;
            this.currentState.gunoffset[1] = sizebuf_t.ReadSignedByte(buffer) * 0.25f;
            this.currentState.gunoffset[2] = sizebuf_t.ReadSignedByte(buffer) * 0.25f;
            this.currentState.gunangles = new float[3];
            this.currentState.gunangles[0] = sizebuf_t.ReadSignedByte(buffer) * 0.25f;
            this.currentState.gunangles[1] = sizebuf_t.ReadSignedByte(buffer) * 0.25f;
            this.currentState.gunangles[2] = sizebuf_t.ReadSignedByte(buffer) * 0.25f;
        }
        if ((deltaFlags & Defines.PS_BLEND) != 0) {
            this.currentState.blend = new float[4];
            this.currentState.blend[0] = sizebuf_t.ReadByte(buffer) / 255.0f;
            this.currentState.blend[1] = sizebuf_t.ReadByte(buffer) / 255.0f;
            this.currentState.blend[2] = sizebuf_t.ReadByte(buffer) / 255.0f;
            this.currentState.blend[3] = sizebuf_t.ReadByte(buffer) / 255.0f;
        }
        if ((deltaFlags & Defines.PS_FOV) != 0) {
            this.currentState.fov = (float) sizebuf_t.ReadByte(buffer);
        }
        if ((deltaFlags & Defines.PS_RDFLAGS) != 0) {
            this.currentState.rdflags = sizebuf_t.ReadByte(buffer);
        }
        // parse stats
        statbits = sizebuf_t.ReadInt(buffer);
        this.currentState.stats = new short[Defines.MAX_STATS];
        for (int i = 0; i < Defines.MAX_STATS; i++) {
            if ((statbits & (1 << i)) != 0) {
                this.currentState.stats[i] = sizebuf_t.ReadShort(buffer);
            }
        }
    }

    @Override
    public int getSize() {
        computeDeltaFlags();
        int result = 3;

        // write the pmove_state_t
        if ((deltaFlags & Defines.PS_M_TYPE) != 0) {
            result += 1;
        }

        if ((deltaFlags & Defines.PS_M_ORIGIN) != 0) {
            result += 6;
        }

        if ((deltaFlags & Defines.PS_M_VELOCITY) != 0) {
            result += 6;
        }

        if ((deltaFlags & Defines.PS_M_TIME) != 0)
            result += 1;

        if ((deltaFlags & Defines.PS_M_FLAGS) != 0)
            result += 1;

        if ((deltaFlags & Defines.PS_M_GRAVITY) != 0)
            result += 2;

        if ((deltaFlags & Defines.PS_M_DELTA_ANGLES) != 0) {
            result += 6;
        }

        // write the rest of the player_state_t
        if ((deltaFlags & Defines.PS_VIEWOFFSET) != 0) {
            result += 3;
        }

        if ((deltaFlags & Defines.PS_VIEWANGLES) != 0) {
            result += 6;
        }

        if ((deltaFlags & Defines.PS_KICKANGLES) != 0) {
            result += 3;
        }

        if ((deltaFlags & Defines.PS_WEAPONINDEX) != 0) {
            result += 1;
        }

        if ((deltaFlags & Defines.PS_WEAPONFRAME) != 0) {
            result += 7;
        }

        if ((deltaFlags & Defines.PS_BLEND) != 0) {
            result += 4;
        }

        if ((deltaFlags & Defines.PS_FOV) != 0) {
            result += 1;
        }

        if ((deltaFlags & Defines.PS_RDFLAGS) != 0) {
            result += 1;
        }

        result += 4;
        for (int i = 0; i < Defines.MAX_STATS; i++) {
            if ((statbits & (1 << i)) != 0)
                result += 2;
        }
        return result;
    }

    @Override
    public String toString() {
        return "PlayerInfoMessage{" +
                "currentState=" + currentState +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayerInfoMessage)) return false;

        PlayerInfoMessage that = (PlayerInfoMessage) o;

        return currentState != null ? currentState.equals(that.currentState) : that.currentState == null;
    }

    @Override
    public int hashCode() {
        return currentState != null ? currentState.hashCode() : 0;
    }
}