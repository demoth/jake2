package jake2.qcommon.network.messages.server;

import jake2.qcommon.Defines;
import jake2.qcommon.sizebuf_t;

/**
 * Generic server information, sent as a response to {@link jake2.qcommon.network.messages.client.StringCmdMessage.NEW} client command
 */
public class ServerDataMessage extends ServerMessage {
    public int protocol;
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
        buffer.writeInt(Defines.PROTOCOL_VERSION);
        buffer.writeInt(spawnCount);
        float c = demo ? 1 : 0;
        buffer.writeByte((byte) c);
        buffer.writeString(gameName);
        buffer.writeShort(playerNumber);
        buffer.writeString(levelString);

    }

    @Override
    public void parse(sizebuf_t buffer) {
        this.protocol = buffer.readInt();
        this.spawnCount = buffer.readInt();
        this.demo = buffer.readByte() != 0;
        this.gameName = buffer.readString();
        this.playerNumber = buffer.readShort();
        this.levelString = buffer.readString();
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
