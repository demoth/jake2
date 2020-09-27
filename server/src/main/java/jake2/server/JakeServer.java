package jake2.server;

import java.util.List;

public interface JakeServer {
    void update(long time);

    List<client_t> getClients();

    void SV_BroadcastPrintf(int level, String s);

    void SV_Shutdown(String message, boolean reconnect);
}
