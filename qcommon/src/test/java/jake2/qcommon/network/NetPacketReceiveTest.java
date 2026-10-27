package jake2.qcommon.network;

import jake2.qcommon.exec.Cvar;
import jake2.qcommon.network.messages.NetworkPacket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Arrays;

import static jake2.qcommon.Defines.MAX_MSGLEN;
import static org.junit.jupiter.api.Assertions.*;

class NetPacketReceiveTest {
    @BeforeEach
    void setup() {
        Cvar.getInstance().clear();
        Netchan.Netchan_Init();
    }

    @AfterEach
    void cleanup() {
        Cvar.getInstance().clear();
    }

    @ParameterizedTest
    @CsvSource({"1399, false", "1400, false", "1399, true", "1400, true"})
    void receivesWholeDatagramAtSizeBoundary(int length, boolean fromClient) throws Exception {
        byte[] bytes = sequencedPacket(length, fromClient);
        NetworkPacket packet = receive(bytes, fromClient);

        assertNotNull(packet);
        assertEquals(length, packet.buffer.cursize);
        assertArrayEquals(bytes, Arrays.copyOf(packet.buffer.data, packet.buffer.cursize));
        assertEquals(7, packet.sequence);
        assertEquals(3, packet.sequenceAck);
        assertEquals(fromClient ? 42 : 0, packet.qport);
        assertEquals(NetAddrType.NA_IP, packet.from.type);
    }

    @ParameterizedTest
    @ValueSource(ints = {1401, 4096})
    void rejectsOversizedDatagramsInsteadOfAcceptingTruncatedPayload(int length) throws Exception {
        assertNull(receive(sequencedPacket(length, false), false));
    }

    @Test
    void socketPacketAcceptsAnExactlySizedArray() {
        byte[] bytes = sequencedPacket(MAX_MSGLEN, true);
        NetworkPacket packet = NetworkPacket.fromSocket(true, bytes, bytes.length,
                new byte[]{127, 0, 0, 1}, 27910);

        assertEquals(MAX_MSGLEN, packet.buffer.cursize);
        assertEquals(7, packet.sequence);
        assertEquals(42, packet.qport);
    }

    @Test
    void connectionlessStringEndsAtDatagramBoundaryWithoutTerminator() throws Exception {
        byte[] bytes = new byte[MAX_MSGLEN];
        Arrays.fill(bytes, (byte) 'x');
        ByteBuffer.wrap(bytes).putInt(-1);

        NetworkPacket packet = receive(bytes, true);
        assertNotNull(packet);
        assertTrue(packet.isConnectionless());
        assertEquals("x".repeat(MAX_MSGLEN - 4), packet.connectionlessMessage);

        NetworkPacket loopback = NetworkPacket.fromLoopback(true, bytes, bytes.length);
        assertEquals(loopback.connectionlessMessage, packet.connectionlessMessage);
    }

    private static byte[] sequencedPacket(int length, boolean fromClient) {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) 0x5a);
        ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(7).putInt(3);
        if (fromClient) header.putShort((short) 42);
        return bytes;
    }

    private static NetworkPacket receive(byte[] bytes, boolean fromClient) throws Exception {
        try (var receiver = DatagramChannel.open();
             var sender = DatagramChannel.open();
             var selector = Selector.open()) {
            receiver.bind(new InetSocketAddress("127.0.0.1", 0));
            receiver.configureBlocking(false);
            receiver.register(selector, SelectionKey.OP_READ);
            assertEquals(bytes.length, sender.send(ByteBuffer.wrap(bytes), receiver.getLocalAddress()));

            long deadline = System.nanoTime() + 2_000_000_000L;
            while (selector.selectedKeys().isEmpty() && System.nanoTime() < deadline) {
                selector.select(100);
            }
            assertFalse(selector.selectedKeys().isEmpty(), "UDP datagram did not arrive");
            return NET.receiveNetworkPacket(receiver.socket(), receiver, new NET.loopback_t(), fromClient);
        }
    }
}
