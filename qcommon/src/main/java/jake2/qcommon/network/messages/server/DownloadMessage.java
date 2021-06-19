package jake2.qcommon.network.messages.server;

import jake2.qcommon.MSG;
import jake2.qcommon.SZ;
import jake2.qcommon.sizebuf_t;

/**
 * Transmits chunk of media (maps, skins, sounds, etc)
 */
public class DownloadMessage extends ServerMessage {

    public byte[] data;
    public int percentage;

    public DownloadMessage() {
        super(ServerMessageType.svc_download);
    }

    public DownloadMessage(byte[] data, int percentage) {
        this();
        this.data = data;
        this.percentage = percentage;
    }

    @Override
    protected void writeProperties(sizebuf_t buffer) {
        if (data != null) {
            MSG.WriteShort(buffer, data.length);
            MSG.WriteByte(buffer, percentage);
            SZ.Write(buffer, data, data.length);
        } else {
            MSG.WriteShort(buffer, -1);
            MSG.WriteByte(buffer, 0);
        }
    }

    @Override
    void parse(sizebuf_t buffer) {
        int size = MSG.ReadShort(buffer);
        percentage = MSG.ReadByte(buffer);
        if (size != -1) {
            data = new byte[size];
            MSG.ReadData(buffer, data, size);
        }
    }
}
