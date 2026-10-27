package jake2.qcommon.network;

import jake2.qcommon.exec.Cvar;
import jake2.qcommon.network.messages.NetworkPacket;
import jake2.qcommon.network.messages.ConnectionlessCommand;
import jake2.qcommon.network.protocol.PacketCodec;
import jake2.qcommon.network.protocol.PacketDecodeResult;
import jake2.qcommon.network.protocol.PacketDirection;
import jake2.qcommon.network.transport.IpAddress;
import jake2.qcommon.network.transport.NetworkAddress;
import jake2.qcommon.network.transport.PacketBytes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.util.Arrays;
import java.util.concurrent.locks.LockSupport;

import static jake2.qcommon.Defines.*;
import static org.junit.jupiter.api.Assertions.*;

class NetPacketReceiveTest {
    @BeforeEach
    void setup() {
        NET.Config(false);
        NET.clearLoopbacks();
        Cvar.getInstance().clear();
        Netchan.Netchan_Init();
    }

    @AfterEach
    void cleanup() {
        NET.Config(false);
        NET.clearLoopbacks();
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

    @Test
    void socketPacketAcceptsAnExactlySizedArray() {
        byte[] bytes = sequencedPacket(MAX_MSGLEN, true);
        var decoded = assertInstanceOf(PacketDecodeResult.Decoded.class,
                PacketCodec.decode(PacketBytes.copyOf(bytes), PacketDirection.FROM_CLIENT));
        NetworkPacket packet = NetworkPacket.fromEnvelope(
                new NetworkAddress.Udp(IpAddress.fromBytes(new byte[]{127, 0, 0, 1}), 27910), decoded.getEnvelope());

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

        NET.SendPacket(NS_CLIENT, bytes.length, bytes, new netadr_t());
        NetworkPacket loopback = NET.receiveLoopbackPacket(NS_SERVER);
        assertNotNull(loopback);
        assertEquals(loopback.connectionlessMessage, packet.connectionlessMessage);
    }

    @Test
    void truncatedLoopbackHeaderIsDroppedWithoutConsumingFollowingPacket() {
        NET.SendPacket(NS_CLIENT, 8, sequencedPacket(8, false), new netadr_t());
        Netchan.sendConnectionlessPacket(NS_CLIENT, new netadr_t(), ConnectionlessCommand.getchallenge, "\n");

        assertNull(NET.receiveLoopbackPacket(NS_SERVER));
        NetworkPacket next = NET.receiveLoopbackPacket(NS_SERVER);
        assertNotNull(next);
        assertEquals("getchallenge", next.connectionlessMessage);
    }

    @Test
    void serverPrintReplySeparatesCommandFromMultilineParameters() {
        Netchan.sendConnectionlessPacket(NS_SERVER, new netadr_t(), ConnectionlessCommand.print,
                "\nFirst line\nSecond line\n");

        NetworkPacket packet = NET.receiveLoopbackPacket(NS_CLIENT);
        assertNotNull(packet);
        assertEquals("print", packet.connectionlessMessage);
        assertEquals("First line\nSecond line\n", packet.connectionlessParameters);
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
        int role = fromClient ? NS_SERVER : NS_CLIENT;
        Cvar.getInstance().find(fromClient ? "port" : "clientport", Integer.class).forceSet(0);
        assertTrue(fromClient ? NET.configureServer(true) : NET.configureClient(true));
        try (var sender = DatagramChannel.open()) {
            var destination = new InetSocketAddress("127.0.0.1", NET.udpEndpoint(role).getLocalAddress().getPort());
            assertEquals(bytes.length, sender.send(ByteBuffer.wrap(bytes), destination));
            long deadline = System.nanoTime() + 2_000_000_000L;
            while (System.nanoTime() < deadline) {
                NetworkPacket packet = NET.receiveNetworkPacket(role);
                if (packet != null) return packet;
                LockSupport.parkNanos(1_000_000);
            }
            fail("UDP datagram did not arrive");
            return null;
        }
    }
}
