package jake2.qcommon.network.commands;

import jake2.qcommon.Defines;
import jake2.qcommon.MSG;
import jake2.qcommon.network.NetworkCommandType;
import jake2.qcommon.sizebuf_t;

public class ServerDataMessage extends NetworkMessage {
    public ServerDataMessage(int spawnCount, boolean demo, String gameName, int playerNumber, String levelString) {
        super(NetworkCommandType.svc_serverdata);
        this.spawnCount = spawnCount;
        this.demo = demo;
        this.gameName = gameName;
        this.playerNumber = playerNumber;
        this.levelString = levelString;
    }

    public final int spawnCount;
    public final boolean demo;
    public final String gameName;
    public final int playerNumber;
    public final String levelString;


    @Override
    protected void sendProps(sizebuf_t buffer) {
        MSG.WriteInt(buffer, Defines.PROTOCOL_VERSION);
        MSG.WriteLong(buffer, spawnCount);
        MSG.WriteByte(buffer, demo ? 1 : 0);
        MSG.WriteString(buffer, gameName);
        MSG.WriteShort(buffer, playerNumber);
        MSG.WriteString(buffer, levelString);

    }

    @Override
    void parse(sizebuf_t buffer) {

    }
}
