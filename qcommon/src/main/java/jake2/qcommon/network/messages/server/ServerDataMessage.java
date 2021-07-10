package jake2.qcommon.network.messages.server;

import jake2.qcommon.Defines;
import jake2.qcommon.sizebuf_t;

/**
 * Generic server information, sent as a response to {@link jake2.qcommon.network.messages.client.StringCmdMessage.NEW} client command
 */
public class ServerDataMessage extends ServerMessage {
    public int protocol; //fixme: write as int, read as long
    public int spawnCount;
    public boolean demo;
    public String gameName;
    public int playerNumber;
    public String levelString;

    public ServerDataMessage() {
        super(ServerMessageType.svc_serverdata);
    }

    public ServerDataMessage(int protocol, int spawnCount, boolean demo, String gameName, int playerNumber, String levelString) {
        this();
        this.protocol = protocol;
        this.spawnCount = spawnCount;
        this.demo = demo;
        this.gameName = gameName;
        this.playerNumber = playerNumber;
        this.levelString = levelString;
    }


    @Override
    protected void writeProperties(sizebuf_t buffer) {
        sizebuf_t.WriteInt(buffer, Defines.PROTOCOL_VERSION);
        sizebuf_t.WriteInt(buffer, spawnCount);
        float c = demo ? 1 : 0;
        sizebuf_t.WriteByte(buffer, (byte) c);
        sizebuf_t.WriteString(buffer, gameName);
        buffer.WriteShort(playerNumber);
        sizebuf_t.WriteString(buffer, levelString);

    }

    @Override
    public void parse(sizebuf_t buffer) {
        this.protocol = sizebuf_t.ReadInt(buffer);
        this.spawnCount = sizebuf_t.ReadInt(buffer);
        this.demo = sizebuf_t.ReadByte(buffer) != 0;
        this.gameName = sizebuf_t.ReadString(buffer);
        this.playerNumber = sizebuf_t.ReadShort(buffer);
        this.levelString = sizebuf_t.ReadString(buffer);
    }

    @Override
    public int getSize() {
        return 1 + 4 + 4 + 1 + gameName.length() + 1 + 2 + levelString.length() + 1;
    }

    @Override
    public String toString() {
        return "ServerDataMessage{" +
                "protocol=" + protocol +
                ", spawnCount=" + spawnCount +
                ", demo=" + demo +
                ", gameName='" + gameName + '\'' +
                ", playerNumber=" + playerNumber +
                ", levelString='" + levelString + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ServerDataMessage that = (ServerDataMessage) o;

        if (protocol != that.protocol) return false;
        if (spawnCount != that.spawnCount) return false;
        if (demo != that.demo) return false;
        if (playerNumber != that.playerNumber) return false;
        if (gameName != null ? !gameName.equals(that.gameName) : that.gameName != null) return false;
        return levelString != null ? levelString.equals(that.levelString) : that.levelString == null;
    }

    @Override
    public int hashCode() {
        int result = protocol;
        result = 31 * result + spawnCount;
        result = 31 * result + (demo ? 1 : 0);
        result = 31 * result + (gameName != null ? gameName.hashCode() : 0);
        result = 31 * result + playerNumber;
        result = 31 * result + (levelString != null ? levelString.hashCode() : 0);
        return result;
    }
}
