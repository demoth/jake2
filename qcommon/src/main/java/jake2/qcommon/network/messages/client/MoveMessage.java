package jake2.qcommon.network.messages.client;

import jake2.qcommon.Defines;
import jake2.qcommon.MSG;
import jake2.qcommon.sizebuf_t;
import jake2.qcommon.usercmd_t;

public class MoveMessage extends ClientMessage {
    private int checksumIndex;
    private boolean noCompress;
    public int lastReceivedFrame;
    public usercmd_t oldestCmd;
    public usercmd_t oldCmd;
    public usercmd_t newCmd;
    private final int currentSequence;
    public boolean valid;

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
        MSG.WriteByte(buffer, 0); // later we update with right value
        if (noCompress) {
            MSG.WriteLong(buffer, 0);
        } else {
            MSG.WriteLong(buffer, lastReceivedFrame);
        }

        // send this and the previous cmds in the message, so
        // if the last packet was dropped, it can be recovered
        writeDeltaUserCommand(buffer, new usercmd_t(), oldestCmd);
        writeDeltaUserCommand(buffer, oldestCmd, oldCmd);
        writeDeltaUserCommand(buffer, oldCmd, newCmd);
        buffer.data[checksumIndex] = CRC.BlockSequenceCRCByte(buffer.data,
                checksumIndex + 1,
                buffer.cursize - checksumIndex - 1,
                currentSequence
        );
    }

    @Override
    void parse(sizebuf_t buffer) {
        checksumIndex = buffer.readcount;
        int checksum = MSG.ReadByte(buffer);
        lastReceivedFrame = MSG.ReadLong(buffer);
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

    private static void writeDeltaUserCommand(sizebuf_t buf, usercmd_t from, usercmd_t cmd) {

        //
        // send the movement message
        //
        int bits = 0;
        if (cmd.angles[0] != from.angles[0])
            bits |= Defines.CM_ANGLE1;
        if (cmd.angles[1] != from.angles[1])
            bits |= Defines.CM_ANGLE2;
        if (cmd.angles[2] != from.angles[2])
            bits |= Defines.CM_ANGLE3;
        if (cmd.forwardmove != from.forwardmove)
            bits |= Defines.CM_FORWARD;
        if (cmd.sidemove != from.sidemove)
            bits |= Defines.CM_SIDE;
        if (cmd.upmove != from.upmove)
            bits |= Defines.CM_UP;
        if (cmd.buttons != from.buttons)
            bits |= Defines.CM_BUTTONS;
        if (cmd.impulse != from.impulse)
            bits |= Defines.CM_IMPULSE;

        MSG.WriteByte(buf, bits);

        if ((bits & Defines.CM_ANGLE1) != 0)
            MSG.WriteShort(buf, cmd.angles[0]);
        if ((bits & Defines.CM_ANGLE2) != 0)
            MSG.WriteShort(buf, cmd.angles[1]);
        if ((bits & Defines.CM_ANGLE3) != 0)
            MSG.WriteShort(buf, cmd.angles[2]);

        if ((bits & Defines.CM_FORWARD) != 0)
            MSG.WriteShort(buf, cmd.forwardmove);
        if ((bits & Defines.CM_SIDE) != 0)
            MSG.WriteShort(buf, cmd.sidemove);
        if ((bits & Defines.CM_UP) != 0)
            MSG.WriteShort(buf, cmd.upmove);

        if ((bits & Defines.CM_BUTTONS) != 0)
            MSG.WriteByte(buf, cmd.buttons);
        if ((bits & Defines.CM_IMPULSE) != 0)
            MSG.WriteByte(buf, cmd.impulse);

        MSG.WriteByte(buf, cmd.msec);
        MSG.WriteByte(buf, cmd.lightlevel);
    }

    private static void readDeltaUserCommand(sizebuf_t buffer, usercmd_t from, usercmd_t move) {
        move.set(from);
        int bits = MSG.ReadByte(buffer);

        // read current angles
        if ((bits & Defines.CM_ANGLE1) != 0)
            move.angles[0] = MSG.ReadShort(buffer);
        if ((bits & Defines.CM_ANGLE2) != 0)
            move.angles[1] = MSG.ReadShort(buffer);
        if ((bits & Defines.CM_ANGLE3) != 0)
            move.angles[2] = MSG.ReadShort(buffer);

        // read movement
        if ((bits & Defines.CM_FORWARD) != 0)
            move.forwardmove = MSG.ReadShort(buffer);
        if ((bits & Defines.CM_SIDE) != 0)
            move.sidemove = MSG.ReadShort(buffer);
        if ((bits & Defines.CM_UP) != 0)
            move.upmove = MSG.ReadShort(buffer);

        // read buttons
        if ((bits & Defines.CM_BUTTONS) != 0)
            move.buttons = (byte) MSG.ReadByte(buffer);

        if ((bits & Defines.CM_IMPULSE) != 0)
            move.impulse = (byte) MSG.ReadByte(buffer);

        // read time to run command
        move.msec = (byte) MSG.ReadByte(buffer);

        // read the light level
        move.lightlevel = (byte) MSG.ReadByte(buffer);

    }
}
