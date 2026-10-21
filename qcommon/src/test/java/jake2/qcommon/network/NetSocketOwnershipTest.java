package jake2.qcommon.network;

import jake2.qcommon.MainCommon;
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.network.messages.ConnectionlessCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.DatagramSocket;

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
        var server = NET.ip_sockets[NS_SERVER];
        assertNull(NET.ip_sockets[NS_CLIENT]);
        assertTrue(NET.configureClient(true));
        NET.configureClient(false);
        assertSame(server, NET.ip_sockets[NS_SERVER]);
        assertFalse(server.isClosed());
        assertNull(NET.ip_channels[NS_CLIENT]);
        assertTrue(NET.configureClient(true));
        var client = NET.ip_sockets[NS_CLIENT];
        NET.configureServer(false);
        assertSame(client, NET.ip_sockets[NS_CLIENT]);
        assertFalse(client.isClosed());
        assertNull(NET.ip_channels[NS_SERVER]);
    }

    @Test
    void failedServerBindReleasesChannelAndAllowsRetry() throws Exception {
        try (var occupied = new DatagramSocket(0)) {
            Cvar.getInstance().find("port", Integer.class).forceSet(occupied.getLocalPort());
            assertFalse(NET.configureServer(true));
            assertNull(NET.ip_sockets[NS_SERVER]);
            assertNull(NET.ip_channels[NS_SERVER]);
        }
        assertTrue(NET.configureServer(true));
    }

    @Test
    void loopbackWorksWithoutSocketsAndSessionResetDiscardsBothQueues() {
        Netchan.sendConnectionlessPacket(NS_CLIENT, new netadr_t(), ConnectionlessCommand.getchallenge, "\n");
        assertNotNull(NET.receiveNetworkPacket(null, null, NET.loopbacks[NS_SERVER], true));
        Netchan.sendConnectionlessPacket(NS_SERVER, new netadr_t(), ConnectionlessCommand.client_connect, "");
        Netchan.sendConnectionlessPacket(NS_CLIENT, new netadr_t(), ConnectionlessCommand.getchallenge, "\n");
        NET.clearLoopbacks();
        assertNull(NET.receiveNetworkPacket(null, null, NET.loopbacks[NS_CLIENT], false));
        assertNull(NET.receiveNetworkPacket(null, null, NET.loopbacks[NS_SERVER], true));
    }
}
