package jake2.qcommon.network.messages.server;

import jake2.qcommon.SZ;
import jake2.qcommon.sizebuf_t;

import java.util.Arrays;

import static jake2.qcommon.Defines.MAX_MAP_AREAS;

/**
 * Generic information about frame.
 * Sent along together with {@link PacketEntitiesMessage} and {@link PacketEntitiesMessage} every server frame.
 */
public class FrameHeaderMessage extends ServerMessage {
    public int frameNumber;
    public int lastFrame;
    public int suppressCount;
    private int areaBitsLength;
    public byte[] areaBits;

    public FrameHeaderMessage() {
        super(ServerMessageType.svc_frame);
    }

    /**
     * @param lastFrame what we are delta'ing from
     * @param suppressCount rate dropped packets
     */
    public FrameHeaderMessage(int frameNumber, int lastFrame, int suppressCount, int areaBitsLength, byte[] areaBits) {
        this();
        this.frameNumber = frameNumber;
        this.lastFrame = lastFrame;
        this.suppressCount = suppressCount;
        this.areaBitsLength = areaBitsLength;
        this.areaBits = areaBits;
    }

    @Override
    protected void writeProperties(sizebuf_t buffer) {
        sizebuf_t.WriteInt(buffer, frameNumber);
        // what we are delta'ing from
        sizebuf_t.WriteInt(buffer, lastFrame);
        // rate dropped packets
        sizebuf_t.WriteByte(buffer, (byte) suppressCount);
        sizebuf_t.WriteByte(buffer, (byte) areaBitsLength);
        SZ.Write(buffer, areaBits, areaBitsLength);
    }

    @Override
    public void parse(sizebuf_t buffer) {
        frameNumber = sizebuf_t.ReadInt(buffer);
        lastFrame = sizebuf_t.ReadInt(buffer);
        // BIG HACK to let old demos continue to work
        // if (ClientGlobals.cls.serverProtocol != 26)
        // fixme: do not read otherwise?
        suppressCount = sizebuf_t.ReadByte(buffer);
        areaBitsLength = sizebuf_t.ReadByte(buffer);
        areaBits = new byte[MAX_MAP_AREAS / 8];
        sizebuf_t.ReadData(buffer, areaBits, areaBitsLength);
    }

    @Override
    public int getSize() {
        return 1 + 10 + areaBitsLength;
    }

    @Override
    public String toString() {
        return "FrameMessage{" +
                "frameNumber=" + frameNumber +
                ", lastFrame=" + lastFrame +
                ", suppressCount=" + suppressCount +
                ", areaBitsLength=" + areaBitsLength +
                ", areaBits=" + Arrays.toString(areaBits) +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FrameHeaderMessage that = (FrameHeaderMessage) o;

        if (frameNumber != that.frameNumber) return false;
        if (lastFrame != that.lastFrame) return false;
        if (suppressCount != that.suppressCount) return false;
        if (areaBitsLength != that.areaBitsLength) return false;

        // due to custom equals implementation - didn't bother with hashcode
        for (int i = 0; i < areaBitsLength; i++) {
            if (areaBits[i] != that.areaBits[i])
                return false;
        }
        return true;
    }

}
