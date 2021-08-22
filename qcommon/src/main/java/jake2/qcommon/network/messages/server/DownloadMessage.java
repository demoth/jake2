package jake2.qcommon.network.messages.server;

import jake2.qcommon.sizebuf_t;

import java.util.Arrays;

/**
 * Transmits chunk of media (maps, skins, sounds, etc)
 *
 * Fixme: Warning: data.length is limited by signed short (32k)
 */
public class DownloadMessage extends ServerMessage {

    public byte[] data;
    public byte percentage;

    public DownloadMessage() {
        super(ServerMessageType.svc_download);
    }

    public DownloadMessage(byte[] data, byte percentage) {
        this();
        this.data = data;
        this.percentage = percentage;
    }

    @Override
    protected void writeProperties(sizebuf_t buffer) {
        if (data != null) {
            buffer.writeShort(data.length);
            buffer.writeByte(percentage);
            buffer.writeBytes(data, data.length);
        } else {
            buffer.writeShort(-1);
            buffer.writeByte((byte) 0);
        }
    }

    @Override
    public void parse(sizebuf_t buffer) {
        int size = buffer.readShort();
        percentage = (byte) buffer.readByte();
        // fixme: signed or unsigned?
        if (size != -1 && size != -32640) {
            data = new byte[size];
            buffer.readData(data, size);
        }
    }

    @Override
    public int getSize() {
        if (data != null)
            return 4 + data.length;
        else
            return 4;
    }

    @Override
    public String toString() {
        return "DownloadMessage{" +
                "percentage=" + percentage +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DownloadMessage that = (DownloadMessage) o;

        if (percentage != that.percentage) return false;
        return Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(data);
        result = 31 * result + percentage;
        return result;
    }
}
