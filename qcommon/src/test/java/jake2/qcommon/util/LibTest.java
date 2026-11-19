package jake2.qcommon.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class LibTest {

    @Test
    public void testCStringSliceUsesItsOwnOffsetAndBounds() {
        byte[] bytes = {0, 'a', 'b', 0, 'x', 'y'};
        Assertions.assertEquals("ab", Lib.CtoJava(bytes, 1, 5));
        Assertions.assertEquals("a", Lib.CtoJava(bytes, 1, 1));
        Assertions.assertEquals("xy", Lib.CtoJava(bytes, 4, 2));
        Assertions.assertEquals("", Lib.CtoJava(bytes, 3, 3));
        Assertions.assertEquals("", Lib.CtoJava(bytes, bytes.length, 0));
        Assertions.assertEquals("", Lib.CtoJava(new byte[0]));
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> Lib.CtoJava(bytes, 4, 3));
    }

    @Test
    public void testCStringPreservesWhitespaceAndLatin1BeforeTerminator() {
        byte[] bytes = {' ', (byte) 0xe9, ' ', 0, 'x'};
        Assertions.assertEquals(" \u00e9 ", Lib.CtoJava(bytes));
    }

    @Test
    public void testDecodeNewLine() {
        Assertions.assertEquals("hello\nworld", Lib.decodeBackslash("hello\\nworld"));
    }

    @Test
    public void testDecode2Backslash() {
        Assertions.assertEquals("hello\\world", Lib.decodeBackslash("hello\\\\world"));
    }

    @Test
    public void testDecodeOther() {
        Assertions.assertEquals("hello\\world", Lib.decodeBackslash("hello\\tworld"));
    }

    @Test
    public void formattedVectorKeepsColumnWidthsAndPrecision() {
        Assertions.assertEquals("   -1.25     0.00    12.50", Lib.vtofsbeaty(new float[]{-1.25f, 0f, 12.5f}));
    }

    @Test
    public void testDecodeNormal() {
        Assertions.assertEquals("hello, world", Lib.decodeBackslash("hello, world"));
    }
}
