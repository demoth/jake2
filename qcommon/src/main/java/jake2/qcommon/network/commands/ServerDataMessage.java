package jake2.qcommon.network.commands;

import jake2.qcommon.Defines;
import jake2.qcommon.MSG;
import jake2.qcommon.network.NetworkCommandType;
import jake2.qcommon.sizebuf_t;

public class ServerDataMessage extends NetworkMessage {
    public int protocol; //fixme: write as int, read as long
    public int spawnCount;
    public boolean demo;
    public String gameName;
    public int playerNumber;
    public String levelString;

    public ServerDataMessage() {
        super(NetworkCommandType.svc_serverdata);
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
        MSG.WriteLong(buffer, spawnCount);
        MSG.WriteByte(buffer, demo ? 1 : 0);
        MSG.WriteString(buffer, gameName);
        MSG.WriteShort(buffer, playerNumber);
        MSG.WriteString(buffer, levelString);

    }

    @Override
    void parse(sizebuf_t buffer) {
        this.protocol = MSG.ReadLong(buffer);
        this.spawnCount = MSG.ReadLong(buffer);
        this.demo = MSG.ReadByte(buffer) != 0;
        this.gameName = MSG.ReadString(buffer);
        this.playerNumber = MSG.ReadShort(buffer);
        this.levelString = MSG.ReadString(buffer);
    }
}
