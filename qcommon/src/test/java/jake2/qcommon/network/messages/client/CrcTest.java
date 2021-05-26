package jake2.qcommon.network.messages.client;

import org.junit.Assert;
import org.junit.Test;

import static jake2.qcommon.network.messages.client.CRC.BlockSequenceCRCByte;
import static jake2.qcommon.network.messages.client.CRC.CRC_Block;

public class CrcTest {

    private static final byte[] TEST_DATA = {
            (byte) 0x71,
            (byte) 0xa9,
            (byte) 0x05,
            (byte) 0xce,
            (byte) 0x8d,
            (byte) 0x75,
            (byte) 0x28,
            (byte) 0xc8,
            (byte) 0xba,
            (byte) 0x97,

            (byte) 0x45,
            (byte) 0xe9,
            (byte) 0x8a,
            (byte) 0xe0,
            (byte) 0x37,
            (byte) 0xbd,
            (byte) 0x6c,
            (byte) 0x6d,
            (byte) 0x67,
            (byte) 0x4a,
            (byte) 0x21};

    @Test
    public void testCRC_Block() {
        Assert.assertEquals(53183, CRC_Block(TEST_DATA, 21) & 0xffff);
    }

    @Test
    public void testBlockSequenceCRCByte() {
        int[] expected = {
                215,
                252,
                164,
                202,
                201
        };

        for (int n = 0; n < 5; n++) {
            Assert.assertEquals(expected[n], BlockSequenceCRCByte(TEST_DATA, 0, 21, n * 10) & 0xff);
        }
    }
}
