package jake2.qcommon.network;

import jake2.qcommon.network.runtime.NetworkRole;
import jake2.qcommon.network.runtime.NetworkTransport;
import jake2.qcommon.network.runtime.PacketNetwork;
import jake2.qcommon.network.transport.LoopbackLink;

/** Private networking resources for one regression fixture; no process-wide role owners. */
final class OwnedNetworkPair implements AutoCloseable {
    private final LoopbackLink link = new LoopbackLink();
    final NetworkTransport clientTransport = new NetworkTransport(link.getFirst(), link.getSecond().getLocalAddress());
    final NetworkTransport serverTransport = new NetworkTransport(link.getSecond(), link.getFirst().getLocalAddress());
    final PacketNetwork client = new PacketNetwork(clientTransport, NetworkRole.CLIENT);
    final PacketNetwork server = new PacketNetwork(serverTransport, NetworkRole.SERVER);

    void clearLocalTraffic() {
        link.getFirst().clearIncoming();
        link.getSecond().clearIncoming();
    }

    @Override
    public void close() {
        try { client.close(); } finally { server.close(); }
    }
}
