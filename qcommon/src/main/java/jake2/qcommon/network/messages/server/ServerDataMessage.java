package jake2.qcommon.network.messages.server;

import jake2.qcommon.Defines;
import jake2.qcommon.MSG;
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
        MSG.WriteInt(buffer, Defines.PROTOCOL_VERSION);
        MSG.WriteInt(buffer, spawnCount);
        float c = demo ? 1 : 0;
        MSG.WriteByte(buffer, (byte) c);
        MSG.WriteString(buffer, gameName);
        MSG.WriteShort(buffer, playerNumber);
        MSG.WriteString(buffer, levelString);

    }

    @Override
    public void parse(sizebuf_t buffer) {
        this.protocol = MSG.ReadInt(buffer);
        this.spawnCount = MSG.ReadInt(buffer);
        this.demo = MSG.ReadByte(buffer) != 0;
        this.gameName = MSG.ReadString(buffer);
        this.playerNumber = MSG.ReadShort(buffer);
        this.levelString = MSG.ReadString(buffer);
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
