package jake2.server;

import java.util.List;

public interface JakeServer {
    boolean isPaused();
    List<client_t> getClients();
}
