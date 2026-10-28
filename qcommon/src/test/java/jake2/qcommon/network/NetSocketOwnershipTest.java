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
    @BeforeEach
    void setup() {
        NET.Config(false);
        NET.clearLoopbacks();
        Cvar.getInstance().clear();
        MainCommon.registerCvars(false);
        Netchan.Netchan_Init();
        Cvar.getInstance().find("port", Integer.class).forceSet(0);
        Cvar.getInstance().find("clientport", Integer.class).forceSet(0);
    }

    @AfterEach
    void cleanup() {
        NET.Config(false);
        NET.clearLoopbacks();
        Cvar.getInstance().clear();
    }

    @Test
    void clientTeardownPreservesServerAndServerTeardownPreservesClient() {
        assertTrue(NET.configureServer(true));
        var server = NET.udpEndpoint(NS_SERVER);
        assertNull(NET.udpEndpoint(NS_CLIENT));
        assertTrue(NET.configureClient(true));
        NET.configureClient(false);
        assertSame(server, NET.udpEndpoint(NS_SERVER));
        assertTrue(server.isOpen());
        assertNull(NET.udpEndpoint(NS_CLIENT));
        assertTrue(NET.configureClient(true));
        var client = NET.udpEndpoint(NS_CLIENT);
        NET.configureServer(false);
        assertSame(client, NET.udpEndpoint(NS_CLIENT));
        assertTrue(client.isOpen());
        assertNull(NET.udpEndpoint(NS_SERVER));
    }

    @Test
    void failedServerBindReleasesChannelAndAllowsRetry() throws Exception {
        try (var occupied = new DatagramSocket(0)) {
            Cvar.getInstance().find("port", Integer.class).forceSet(occupied.getLocalPort());
            assertFalse(NET.configureServer(true));
            assertNull(NET.udpEndpoint(NS_SERVER));
        }
        assertTrue(NET.configureServer(true));
    }

    @ParameterizedTest
    @ValueSource(ints = {PORT_ANY, 0})
    void explicitBindAddressSupportsEphemeralPorts(int port) throws Exception {
        Cvar.getInstance().find("ip", String.class).forceSet("127.0.0.1");
        Cvar.getInstance().find("port", Integer.class).forceSet(port);

        assertTrue(NET.configureServer(true));
        assertEquals(InetAddress.getByName("127.0.0.1"), NET.udpEndpoint(NS_SERVER).getLocalAddress().getIp().toInetAddress());
        assertTrue(NET.udpEndpoint(NS_SERVER).getLocalAddress().getPort() > 0);
    }

    @Test
    void occupiedClientPortFallsBackOnExplicitBindAddress() throws Exception {
        Cvar.getInstance().find("ip", String.class).forceSet("127.0.0.1");
        try (var occupied = new DatagramSocket(new InetSocketAddress("127.0.0.1", 0))) {
            Cvar.getInstance().find("clientport", Integer.class).forceSet(occupied.getLocalPort());

            assertTrue(NET.configureClient(true));
            var client = NET.udpEndpoint(NS_CLIENT);
            assertEquals(occupied.getLocalAddress(), client.getLocalAddress().getIp().toInetAddress());
            assertTrue(client.getLocalAddress().getPort() > 0);
            assertNotEquals(occupied.getLocalPort(), client.getLocalAddress().getPort());
            assertTrue(NET.udpEndpoint(NS_CLIENT).isOpen());
        }
    }

    @Test
    void loopbackWorksWithoutSocketsAndSessionResetDiscardsBothQueues() {
        NET.sendConnectionlessPacket(NS_CLIENT, new netadr_t(), ConnectionlessCommand.getchallenge, "\n");
        assertNotNull(NET.receiveLoopbackPacket(NS_SERVER));
        NET.sendConnectionlessPacket(NS_SERVER, new netadr_t(), ConnectionlessCommand.client_connect, "");
        NET.sendConnectionlessPacket(NS_CLIENT, new netadr_t(), ConnectionlessCommand.getchallenge, "\n");
        NET.clearLoopbacks();
        assertNull(NET.receiveLoopbackPacket(NS_CLIENT));
        assertNull(NET.receiveLoopbackPacket(NS_SERVER));
    }
}
