package jake2.qcommon.network.messages.client;

import jake2.qcommon.Defines;
import jake2.qcommon.sizebuf_t;
import jake2.qcommon.usercmd_t;

public class MoveMessage extends ClientMessage {
    private int checksumIndex;
    private boolean noCompress;
    public int lastReceivedFrame;
    public usercmd_t oldestCmd;
    public usercmd_t oldCmd;
    public usercmd_t newCmd;
    public final int currentSequence;
    public boolean valid;

    private final usercmd_t nullCmd = new usercmd_t();

    protected MoveMessage(int currentSequence) {
        super(ClientMessageType.CLC_MOVE);
        this.currentSequence = currentSequence;
    }

    public MoveMessage(boolean noCompress, int lastReceivedFrame, usercmd_t oldestCmd, usercmd_t oldCmd, usercmd_t newCmd, int currentSequence) {
        this(currentSequence);
        this.noCompress = noCompress;
        this.lastReceivedFrame = lastReceivedFrame;
        this.oldestCmd = oldestCmd;
        this.oldCmd = oldCmd;
        this.newCmd = newCmd;
    }

    @Override
    protected void writeProperties(sizebuf_t buffer) {
        checksumIndex = buffer.cursize;
        // later we update with right value
        buffer.writeByte((byte) 0);
        if (noCompress) {
            buffer.writeInt(0);
        } else {
            buffer.writeInt(lastReceivedFrame);
        }

        // send this and the previous cmds in the message, so
        // if the last packet was dropped, it can be recovered
        writeDeltaUserCommand(buffer, nullCmd, oldestCmd);
        writeDeltaUserCommand(buffer, oldestCmd, oldCmd);
        writeDeltaUserCommand(buffer, oldCmd, newCmd);
        buffer.data[checksumIndex] = CRC.BlockSequenceCRCByte(buffer.data,
                checksumIndex + 1,
                buffer.cursize - checksumIndex - 1,
                currentSequence
        );
    }

    @Override
    public void parse(sizebuf_t buffer) {
        checksumIndex = buffer.readcount;
        int checksum = buffer.readByte();
        lastReceivedFrame = buffer.readInt();
        this.oldestCmd = new usercmd_t();
        this.oldCmd = new usercmd_t();
        this.newCmd = new usercmd_t();

        readDeltaUserCommand(buffer, new usercmd_t(), oldestCmd);
        readDeltaUserCommand(buffer, oldestCmd, oldCmd);
        readDeltaUserCommand(buffer, oldCmd, newCmd);

        int calculatedChecksum = CRC.BlockSequenceCRCByte(
                buffer.data,
                checksumIndex + 1,
                buffer.readcount - checksumIndex - 1,
                currentSequence);

        valid = (calculatedChecksum & 0xff) == checksum;
    }

    private static void writeDeltaUserCommand(sizebuf_t buffer, usercmd_t from, usercmd_t cmd) {

        //
        // send the movement message
        //
        int deltaFlags = getDeltaFlags(from, cmd);

        buffer.writeByte((byte) deltaFlags);

        if ((deltaFlags & Defines.CM_ANGLE1) != 0)
            buffer.writeShort(cmd.angles[0]);
        if ((deltaFlags & Defines.CM_ANGLE2) != 0)
            buffer.writeShort(cmd.angles[1]);
        if ((deltaFlags & Defines.CM_ANGLE3) != 0)
            buffer.writeShort(cmd.angles[2]);

        if ((deltaFlags & Defines.CM_FORWARD) != 0)
            buffer.writeShort(cmd.forwardmove);
        if ((deltaFlags & Defines.CM_SIDE) != 0)
            buffer.writeShort(cmd.sidemove);
        if ((deltaFlags & Defines.CM_UP) != 0)
            buffer.writeShort(cmd.upmove);

        if ((deltaFlags & Defines.CM_BUTTONS) != 0)
            buffer.writeByte(cmd.buttons);
        if ((deltaFlags & Defines.CM_IMPULSE) != 0)
            buffer.writeByte(cmd.impulse);

        buffer.writeByte(cmd.msec);
        buffer.writeByte(cmd.lightlevel);
    }

    private static int getDeltaFlags(usercmd_t from, usercmd_t cmd) {
        int deltaFlags = 0;
        if (cmd.angles[0] != from.angles[0])
            deltaFlags |= Defines.CM_ANGLE1;
        if (cmd.angles[1] != from.angles[1])
            deltaFlags |= Defines.CM_ANGLE2;
        if (cmd.angles[2] != from.angles[2])
            deltaFlags |= Defines.CM_ANGLE3;
        if (cmd.forwardmove != from.forwardmove)
            deltaFlags |= Defines.CM_FORWARD;
        if (cmd.sidemove != from.sidemove)
            deltaFlags |= Defines.CM_SIDE;
        if (cmd.upmove != from.upmove)
            deltaFlags |= Defines.CM_UP;
        if (cmd.buttons != from.buttons)
            deltaFlags |= Defines.CM_BUTTONS;
        if (cmd.impulse != from.impulse)
            deltaFlags |= Defines.CM_IMPULSE;
        return deltaFlags;
    }

    private static void readDeltaUserCommand(sizebuf_t buffer, usercmd_t from, usercmd_t move) {
        move.set(from);
        int deltaFlags = buffer.readByte();

        // read current angles
        if ((deltaFlags & Defines.CM_ANGLE1) != 0)
            move.angles[0] = buffer.readShort();
        if ((deltaFlags & Defines.CM_ANGLE2) != 0)
            move.angles[1] = buffer.readShort();
        if ((deltaFlags & Defines.CM_ANGLE3) != 0)
            move.angles[2] = buffer.readShort();

        // read movement
        if ((deltaFlags & Defines.CM_FORWARD) != 0)
            move.forwardmove = buffer.readShort();
        if ((deltaFlags & Defines.CM_SIDE) != 0)
            move.sidemove = buffer.readShort();
        if ((deltaFlags & Defines.CM_UP) != 0)
            move.upmove = buffer.readShort();

        // read buttons
        if ((deltaFlags & Defines.CM_BUTTONS) != 0)
            move.buttons = (byte) buffer.readByte();

        if ((deltaFlags & Defines.CM_IMPULSE) != 0)
            move.impulse = (byte) buffer.readByte();

        // read time to run command
        move.msec = (byte) buffer.readByte();

        // read the light level
        move.lightlevel = (byte) buffer.readByte();

    }

    @Override
    public String toString() {
        return "MoveMessage{" +
                "lastReceivedFrame=" + lastReceivedFrame +
                ", oldestCmd=" + oldestCmd +
                ", oldCmd=" + oldCmd +
                ", newCmd=" + newCmd +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MoveMessage that = (MoveMessage) o;

        if (lastReceivedFrame != that.lastReceivedFrame) return false;
        if (oldestCmd != null ? !oldestCmd.equals(that.oldestCmd) : that.oldestCmd != null) return false;
        if (oldCmd != null ? !oldCmd.equals(that.oldCmd) : that.oldCmd != null) return false;
        return newCmd != null ? newCmd.equals(that.newCmd) : that.newCmd == null;
    }

    @Override
    public int hashCode() {
        int result = lastReceivedFrame;
        result = 31 * result + (oldestCmd != null ? oldestCmd.hashCode() : 0);
        result = 31 * result + (oldCmd != null ? oldCmd.hashCode() : 0);
        result = 31 * result + (newCmd != null ? newCmd.hashCode() : 0);
        return result;
    }

    @Override
    public int getSize() {
        int result = 6;
        int deltaFlagsOldest = getDeltaFlags(nullCmd, oldestCmd);
        int deltaFlagsOld = getDeltaFlags(oldestCmd, oldCmd);
        int deltaFlagsNew = getDeltaFlags(oldCmd, newCmd);
        return result + getDeltaSize(deltaFlagsOldest) + getDeltaSize(deltaFlagsOld) + getDeltaSize(deltaFlagsNew);
    }

    private int getDeltaSize(int deltaFlags) {
        int result = 1;

        if ((deltaFlags & Defines.CM_ANGLE1) != 0)
            result += 2;
        if ((deltaFlags & Defines.CM_ANGLE2) != 0)
            result += 2;
        if ((deltaFlags & Defines.CM_ANGLE3) != 0)
            result += 2;

        if ((deltaFlags & Defines.CM_FORWARD) != 0)
            result += 2;
        if ((deltaFlags & Defines.CM_SIDE) != 0)
            result += 2;
        if ((deltaFlags & Defines.CM_UP) != 0)
            result += 2;

        if ((deltaFlags & Defines.CM_BUTTONS) != 0)
            result += 1;
        if ((deltaFlags & Defines.CM_IMPULSE) != 0)
            result += 1;

        return 2 + result;
    }
}

