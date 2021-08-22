package jake2.server;

import java.util.List;

public interface JakeServer {
    void update(long time);

    List<client_t> getClientsForInstance(String gameName);

    List<client_t> getClients();

    void SV_BroadcastPrintf(int level, String s, String name);

    void SV_Shutdown(String message, boolean reconnect);
}
