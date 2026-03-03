package jake2.qcommon.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class LibTest {

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
    public void testDecodeNormal() {
        Assertions.assertEquals("hello, world", Lib.decodeBackslash("hello, world"));
    }
}
