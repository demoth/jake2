package jake2.qcommon.network.messages.client;

import jake2.qcommon.*;

public class MoveMessage extends ClientMessage {

    public boolean noCompress;
    public int lastReceivedFrame;
    public usercmd_t oldestCmd;
    public usercmd_t oldCmd;
    public usercmd_t newCmd;
    public int currentSequence;

    protected MoveMessage() {
        super(ClientMessageType.CLC_MOVE);
    }

    public MoveMessage(boolean noCompress, int lastReceivedFrame, usercmd_t oldestCmd, usercmd_t oldCmd, usercmd_t newCmd, int currentSequence) {
        this();
        this.noCompress = noCompress;
        this.lastReceivedFrame = lastReceivedFrame;
        this.oldestCmd = oldestCmd;
        this.oldCmd = oldCmd;
        this.newCmd = newCmd;
        this.currentSequence = currentSequence;
    }

    public static void WriteDeltaUsercmd(sizebuf_t buf, usercmd_t from, usercmd_t cmd) {

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

    @Override
    protected void writeProperties(sizebuf_t buffer) {
        int checksumIndex = buffer.cursize;
        MSG.WriteByte(buffer, 0); // later we update with right value
        if (noCompress) {
            MSG.WriteLong(buffer, 0);
        } else {
            MSG.WriteLong(buffer, lastReceivedFrame);
        }

        // send this and the previous cmds in the message, so
        // if the last packet was dropped, it can be recovered
        WriteDeltaUsercmd(buffer, new usercmd_t(), oldestCmd);
        WriteDeltaUsercmd(buffer, oldestCmd, oldCmd);
        WriteDeltaUsercmd(buffer, oldCmd, newCmd);
        buffer.data[checksumIndex] = CRC.BlockSequenceCRCByte(buffer.data,
                checksumIndex + 1,
                buffer.cursize - checksumIndex - 1,
                currentSequence
        );
    }

    @Override
    void parse(sizebuf_t buffer) {

    }
}
