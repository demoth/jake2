package jake2.server;

import java.util.List;
import jake2.qcommon.exec.Command;

public interface JakeServer {
    void update(long time);

    List<ClientNetworkInfo> getClientsForInstance(String gameName);

    List<ClientNetworkInfo> getClients();

    void SV_BroadcastPrintf(int level, String s, String name);

    void SV_Shutdown(String message, boolean reconnect);

    /** Operator commands belong to the current session and are removed on shutdown. */
    void registerGameCommand(String name, Command command);
}
