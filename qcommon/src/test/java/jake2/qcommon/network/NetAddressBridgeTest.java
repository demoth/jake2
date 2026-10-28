package jake2.qcommon.network;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NetAddressBridgeTest {
    @Test
    void localParsingReturnsIndependentValuesAndUdpLoopbackRemainsRemote() {
        var first = netadr_t.fromString("localhost", 27910);
        var second = netadr_t.fromString("loopback", 27910);
        assertNotSame(first, second);
        assertNotSame(first.ip, second.ip);
        first.ip[0] = 99;
        first.port = 1234;
        first.type = NetAddrType.NA_IP;
        var third = netadr_t.fromString("LOCALHOST", 27910);
        assertArrayEquals(new byte[]{127, 0, 0, 1}, third.ip);
        assertEquals(0, third.port);
        assertTrue(third.IsLocalAddress());
        assertFalse(netadr_t.fromString("127.0.0.1:27910", 27910).IsLocalAddress());
    }

    @Test
    void copyingOwnsAllAddressBytesAndComparisonsUseTheFullAddress() {
        var source = new netadr_t();
        source.type = NetAddrType.NA_IP;
        source.ip = new byte[]{32, 1, 13, (byte)184, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};
        source.port = 27910;
        var copy = new netadr_t();
        copy.set(source);
        assertNotSame(source.ip, copy.ip);
        assertArrayEquals(source.ip, copy.ip);
        assertTrue(source.compareIp(copy));
        copy.port++;
        assertFalse(source.compareIp(copy));
        assertTrue(source.CompareBaseAdr(copy));
        copy.ip[15] = 2;
        assertFalse(source.CompareBaseAdr(copy));
        assertEquals(1, source.ip[15]);
    }
}
