package jake2.qcommon.network;

import jake2.qcommon.Globals;
import jake2.qcommon.network.protocol.ChannelRole;
import jake2.qcommon.network.transport.SendResult;
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.network.messages.client.StringCmdMessage;
import jake2.qcommon.network.messages.NetworkPacket;
import jake2.qcommon.network.protocol.PacketCodec;
import jake2.qcommon.network.protocol.PacketDecodeResult;
import jake2.qcommon.network.protocol.PacketDirection;
import jake2.qcommon.network.transport.IpAddress;
import jake2.qcommon.network.transport.LoopbackLink;
import jake2.qcommon.network.runtime.NetworkTransport;
import jake2.qcommon.network.runtime.PacketNetwork;
import jake2.qcommon.network.runtime.NetworkRole;
import jake2.qcommon.network.transport.NetworkAddress;
import jake2.qcommon.network.transport.PacketBytes;
import jake2.qcommon.sizebuf_t;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static jake2.qcommon.Defines.*;
import static org.junit.jupiter.api.Assertions.*;

class NetchanTest {
    private NetworkTransport client;
    private NetworkTransport server;
    private PacketNetwork serverPackets;

    @BeforeEach
    void setUp() {
        var link = new LoopbackLink();
        client = new NetworkTransport(link.getFirst(), link.getSecond().getLocalAddress());
        server = new NetworkTransport(link.getSecond(), link.getFirst().getLocalAddress());
        serverPackets = new PacketNetwork(server, NetworkRole.SERVER);
        Cvar.getInstance().clear();
        Globals.curtime = 0;
        Netchan.Netchan_Init();
    }

    @AfterEach
    void cleanup() throws Exception {
        try { client.close(); } finally { server.close(); }
        Cvar.getInstance().clear();
    }

    @Test
    void outgoingClientPacketUsesItsSetupQport() {
        Netchan.qport.forceSet(77);
        var channel = new ConnectionChannel(ChannelRole.CLIENT, client.getLocalPeer(), client::send, () -> (long) Globals.curtime);
        channel.setup(channel.getRemoteAddress(), 1234);
        assertTrue(channel.transmit(null));
        assertEquals(1234, serverPackets.receive().qport);
    }

    @Test
    void unavailableSenderDoesNotAdvanceOrLoseStagedMessages() {
        boolean[] available = {false};
        var channel = new ConnectionChannel(ChannelRole.CLIENT, client.getLocalPeer(),
                (to, bytes) -> available[0] ? client.send(to, bytes)
                        : new SendResult.Failed(new java.io.IOException("unavailable")), () -> (long) Globals.curtime);
        channel.setup(channel.getRemoteAddress(), 7);
        channel.queueReliable(new StringCmdMessage("new"));
        Globals.curtime = 100;
        assertFalse(channel.transmit(null));
        assertEquals(1, channel.getOutgoingSequence());
        assertEquals(1, channel.getQueuedReliable().size());
        assertEquals(0, channel.getLastSentSize());
        assertEquals(0, channel.getLastSentMillis());
        assertEquals(0, channel.getReliableSentFlag());

        available[0] = true;
        assertTrue(channel.transmit(null));
        assertEquals(2, channel.getOutgoingSequence());
        assertTrue(channel.getQueuedReliable().isEmpty());
        assertEquals(100, channel.getLastSentMillis());
    }

    @Test
    void unexpectedSendFailureReleasesPreparedTransaction() {
        boolean[] fail = {true};
        var channel = new ConnectionChannel(ChannelRole.CLIENT, client.getLocalPeer(), (to, bytes) -> {
            if (fail[0]) throw new IllegalStateException("injected send failure");
            return client.send(to, bytes);
        }, () -> (long) Globals.curtime);
        channel.setup(channel.getRemoteAddress(), 7);
        channel.queueReliable(new StringCmdMessage("new"));
        assertThrows(IllegalStateException.class, () -> channel.transmit(null));
        assertEquals(1, channel.getOutgoingSequence());
        assertEquals(1, channel.getQueuedReliable().size());
        fail[0] = false;
        assertTrue(channel.transmit(null));
    }

    @Test
    void recordsSentPacketSizeAfterPacketIsBuilt() {
        ConnectionChannel netchan = new ConnectionChannel(ChannelRole.SERVER, server.getLocalPeer(), server::send, () -> (long) Globals.curtime);
        netchan.setup(netchan.getRemoteAddress(), 0);

        Globals.curtime = 100;
        netchan.transmit(null);

        assertTrue(netchan.getLastSentSize() >= 8, "header-only packet should include sequence and ack");
    }

    @Test
    void smoothsPingFromAcknowledgedPacketSequence() {
        ConnectionChannel netchan = new ConnectionChannel(ChannelRole.SERVER, server.getLocalPeer(), server::send, () -> (long) Globals.curtime);
        netchan.setup(netchan.getRemoteAddress(), 0);

        Globals.curtime = 100;
        netchan.transmit(null); // sequence 1
        Globals.curtime = 160;
        assertTrue(netchan.accept(packet(1, 1)));
        assertEquals(60, netchan.getSmoothedPingMillis());

        Globals.curtime = 200;
        netchan.transmit(null); // sequence 2
        Globals.curtime = 300;
        assertTrue(netchan.accept(packet(2, 2)));
        assertEquals(70, netchan.getSmoothedPingMillis());
    }

    private NetworkPacket packet(int sequence, int sequenceAck) {
        sizebuf_t buffer = new sizebuf_t(10);
        buffer.writeInt(sequence);
        buffer.writeInt(sequenceAck);
        buffer.writeShort(0);
        var decoded = (PacketDecodeResult.Decoded) PacketCodec.decode(
                PacketBytes.copyOf(buffer.data, buffer.cursize), PacketDirection.FROM_CLIENT);
        return NetworkPacket.fromEnvelope(
                new NetworkAddress.Udp(IpAddress.fromBytes(new byte[]{127, 0, 0, 1}), 27910), decoded.getEnvelope());
    }
}
