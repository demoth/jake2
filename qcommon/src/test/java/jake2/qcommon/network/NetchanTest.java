package jake2.qcommon.network;

import jake2.qcommon.Globals;
import jake2.qcommon.network.messages.NetworkPacket;
import jake2.qcommon.sizebuf_t;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static jake2.qcommon.Defines.NS_SERVER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetchanTest {

    @BeforeEach
    void setUp() {
        Globals.curtime = 0;
        Netchan.Netchan_Init();
    }

    @Test
    void recordsSentPacketSizeAfterPacketIsBuilt() {
        netchan_t netchan = new netchan_t();
        netchan.setup(NS_SERVER, new netadr_t(), 0);

        Globals.curtime = 100;
        netchan.transmit(null);

        assertTrue(netchan.last_sent_size >= 8, "header-only packet should include sequence and ack");
    }

    @Test
    void smoothsPingFromAcknowledgedPacketSequence() {
        netchan_t netchan = new netchan_t();
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
        sizebuf_t buffer = new sizebuf_t(8);
        buffer.writeInt(sequence);
        buffer.writeInt(sequenceAck);
        return NetworkPacket.fromLoopback(false, buffer.data, buffer.cursize);
    }
}
