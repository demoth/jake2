package jake2.qcommon.network.commands;

import jake2.qcommon.MSG;
import jake2.qcommon.SZ;
import jake2.qcommon.network.NetworkCommandType;
import jake2.qcommon.sizebuf_t;

public class FrameMessage extends NetworkMessage {
    /**
     * @param lastFrame what we are delta'ing from
     * @param suppressCount rate dropped packets
     */
    public FrameMessage(int frameNumber, int lastFrame, int suppressCount, int areaBitsLength, byte[] areaBits) {
        super(NetworkCommandType.svc_frame);
        this.frameNumber = frameNumber;
        this.lastFrame = lastFrame;
        this.suppressCount = suppressCount;
        this.areaBitsLength = areaBitsLength;
        this.areaBits = areaBits;
    }

    public final int frameNumber;
    public final int lastFrame;
    public final int suppressCount;
    public final int areaBitsLength;
    public final byte[] areaBits;

    @Override
    protected void sendProps(sizebuf_t buffer) {
        MSG.WriteLong(buffer, frameNumber);
        MSG.WriteLong(buffer, lastFrame); // what we are delta'ing from
        MSG.WriteByte(buffer, suppressCount); // rate dropped packets
        MSG.WriteByte(buffer, areaBitsLength);
        SZ.Write(buffer, areaBits, areaBitsLength);
    }

    @Override
    void parse(sizebuf_t buffer) {

    }
}
