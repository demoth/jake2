package jake2.qcommon.network;

import jake2.qcommon.MainCommon;
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.network.messages.ConnectionlessCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import static jake2.qcommon.Defines.*;
import static org.junit.jupiter.api.Assertions.*;

class NetSocketOwnershipTest {
    private OwnedNetworkPair pair;
    @BeforeEach
    void setup() {
        Cvar.getInstance().clear();
        MainCommon.registerCvars(false);
        Netchan.Netchan_Init();
        pair = new OwnedNetworkPair();
        Cvar.getInstance().find("port", Integer.class).forceSet(0);
        Cvar.getInstance().find("clientport", Integer.class).forceSet(0);
    }

    @AfterEach
    void cleanup() {
        pair.close();
        Cvar.getInstance().clear();
    }

    @Test
    void clientTeardownPreservesServerAndServerTeardownPreservesClient() {
        assertTrue(pair.server.configure(true));
        var server = pair.serverTransport.getUdpEndpoint();
        assertNull(pair.clientTransport.getUdpEndpoint());
        assertTrue(pair.client.configure(true));
        pair.client.configure(false);
        assertSame(server, pair.serverTransport.getUdpEndpoint());
        assertTrue(server.isOpen());
        assertNull(pair.clientTransport.getUdpEndpoint());
        assertTrue(pair.client.configure(true));
        var client = pair.clientTransport.getUdpEndpoint();
        pair.server.configure(false);
        assertSame(client, pair.clientTransport.getUdpEndpoint());
        assertTrue(client.isOpen());
        assertNull(pair.serverTransport.getUdpEndpoint());
    }

    @Test
    void failedServerBindReleasesChannelAndAllowsRetry() throws Exception {
        try (var occupied = new DatagramSocket(0)) {
            Cvar.getInstance().find("port", Integer.class).forceSet(occupied.getLocalPort());
            assertFalse(pair.server.configure(true));
            assertNull(pair.serverTransport.getUdpEndpoint());
        }
        assertTrue(pair.server.configure(true));
    }

    @ParameterizedTest
    @ValueSource(ints = {PORT_ANY, 0})
    void explicitBindAddressSupportsEphemeralPorts(int port) throws Exception {
        Cvar.getInstance().find("ip", String.class).forceSet("127.0.0.1");
        Cvar.getInstance().find("port", Integer.class).forceSet(port);

        assertTrue(pair.server.configure(true));
        assertEquals(InetAddress.getByName("127.0.0.1"), pair.serverTransport.getUdpEndpoint().getLocalAddress().getIp().toInetAddress());
        assertTrue(pair.serverTransport.getUdpEndpoint().getLocalAddress().getPort() > 0);
    }

    @Test
    void occupiedClientPortFallsBackOnExplicitBindAddress() throws Exception {
        Cvar.getInstance().find("ip", String.class).forceSet("127.0.0.1");
        try (var occupied = new DatagramSocket(new InetSocketAddress("127.0.0.1", 0))) {
            Cvar.getInstance().find("clientport", Integer.class).forceSet(occupied.getLocalPort());

            assertTrue(pair.client.configure(true));
            var client = pair.clientTransport.getUdpEndpoint();
            assertEquals(occupied.getLocalAddress(), client.getLocalAddress().getIp().toInetAddress());
            assertTrue(client.getLocalAddress().getPort() > 0);
            assertNotEquals(occupied.getLocalPort(), client.getLocalAddress().getPort());
            assertTrue(pair.clientTransport.getUdpEndpoint().isOpen());
        }
    }

    @Test
    void loopbackWorksWithoutSocketsAndSessionResetDiscardsBothQueues() {
        pair.client.sendConnectionless(new netadr_t(), ConnectionlessCommand.getchallenge, "\n");
        assertNotNull(pair.server.receive());
        pair.server.sendConnectionless(new netadr_t(), ConnectionlessCommand.client_connect, "");
        pair.client.sendConnectionless(new netadr_t(), ConnectionlessCommand.getchallenge, "\n");
        pair.clearLocalTraffic();
        assertNull(pair.client.receive());
        assertNull(pair.server.receive());
    }
}
