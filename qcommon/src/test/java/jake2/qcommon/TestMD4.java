package jake2.qcommon;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestMD4 {

    @Test
    public void testKnownDigestVectors() {
        assertDigest("", "31d6cfe0d16ae931b73c59d7e0c089c0");
        assertDigest("a", "bde52cb31de33e46245e05fbdbd6fb24");
        assertDigest("abc", "a448017aaf21d8525fc10ae87aa6729d");
        assertDigest("message digest", "d9130a8164549fe818874806e1c7014b");
        assertDigest("abcdefghijklmnopqrstuvwxyz", "d79e1c308aa5bbcdeea8ed63df412da9");
    }

    @Test
    public void testBlockChecksum() {
        byte[] data = {
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
            (byte) 0x21
        };

        assertEquals(0x738bb9aa, MD4.Com_BlockChecksum(data, data.length));
    }

    private void assertDigest(String input, String expectedHexDigest) {
        MD4 md4 = new MD4();
        md4.engineUpdate(input.getBytes(StandardCharsets.US_ASCII), 0, input.length());
        assertEquals(expectedHexDigest, toHex(md4.engineDigest()));
    }

    private String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 0xF, 16));
            result.append(Character.forDigit(value & 0xF, 16));
        }
        return result.toString();
    }
}
