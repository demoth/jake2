package jake2.qcommon.network;

import jake2.qcommon.Globals;
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.network.messages.client.StringCmdMessage;
import jake2.qcommon.network.messages.NetworkPacket;
import jake2.qcommon.network.protocol.PacketCodec;
import jake2.qcommon.network.protocol.PacketDecodeResult;
import jake2.qcommon.network.protocol.PacketDirection;
import jake2.qcommon.network.transport.IpAddress;
import jake2.qcommon.network.transport.NetworkAddress;
import jake2.qcommon.network.transport.PacketBytes;
import jake2.qcommon.sizebuf_t;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static jake2.qcommon.Defines.*;
import static org.junit.jupiter.api.Assertions.*;

class NetchanTest {

    @BeforeEach
    void setUp() {
        NET.Config(false);
        NET.clearLoopbacks();
        Cvar.getInstance().clear();
        Globals.curtime = 0;
        Netchan.Netchan_Init();
    }

    @AfterEach
    void cleanup() {
        NET.Config(false);
        NET.clearLoopbacks();
        Cvar.getInstance().clear();
    }

    @Test
    void outgoingClientPacketUsesItsSetupQport() {
        Netchan.qport.forceSet(77);
        var channel = new netchan_t();
        channel.setup(NS_CLIENT, new netadr_t(), 1234);
        assertTrue(channel.transmit(null));
        assertEquals(1234, NET.receiveLoopbackPacket(NS_SERVER).qport);
    }

    @Test
    void unavailableTransportDoesNotAdvanceOrLoseStagedMessages() {
        var channel = new netchan_t(() -> Globals.curtime);
        var remote = new netadr_t();
        remote.type = NetAddrType.NA_IP;
        remote.port = 27910;
        channel.setup(NS_CLIENT, remote, 7);
        channel.reliablePending.add(new StringCmdMessage("new"));
        Globals.curtime = 100;
        assertFalse(channel.transmit(null));
        assertEquals(1, channel.outgoing_sequence);
        assertEquals(1, channel.reliablePending.size());
        assertEquals(0, channel.last_sent_size);
        assertEquals(0, channel.last_sent);
        assertEquals(0, channel.reliable_sent_flag);

        channel.remote_address = new netadr_t();
        assertTrue(channel.transmit(null));
        assertEquals(2, channel.outgoing_sequence);
        assertTrue(channel.reliablePending.isEmpty());
        assertEquals(100, channel.last_sent);
    }

    @Test
    void unexpectedSendFailureReleasesPreparedTransaction() {
        var channel = new netchan_t();
        channel.setup(NS_CLIENT, new netadr_t(), 7);
        channel.reliablePending.add(new StringCmdMessage("new"));
        channel.remote_address = null;
        assertThrows(NullPointerException.class, () -> channel.transmit(null));
        assertEquals(1, channel.outgoing_sequence);
        assertEquals(1, channel.reliablePending.size());
        channel.remote_address = new netadr_t();
        assertTrue(channel.transmit(null));
    }

    @Test
    void recordsSentPacketSizeAfterPacketIsBuilt() {
        netchan_t netchan = new netchan_t(() -> Globals.curtime);
        netchan.setup(NS_SERVER, new netadr_t(), 0);

        Globals.curtime = 100;
        netchan.transmit(null);

        assertTrue(netchan.last_sent_size >= 8, "header-only packet should include sequence and ack");
    }

    @Test
    void smoothsPingFromAcknowledgedPacketSequence() {
        netchan_t netchan = new netchan_t(() -> Globals.curtime);
        netchan.setup(NS_SERVER, new netadr_t(), 0);

        Globals.curtime = 100;
        netchan.transmit(null); // sequence 1
        Globals.curtime = 160;
        assertTrue(netchan.accept(packet(1, 1)));
        assertEquals(60, netchan.smoothed_ping_ms);

        Globals.curtime = 200;
        netchan.transmit(null); // sequence 2
        Globals.curtime = 300;
        assertTrue(netchan.accept(packet(2, 2)));
        assertEquals(70, netchan.smoothed_ping_ms);
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
