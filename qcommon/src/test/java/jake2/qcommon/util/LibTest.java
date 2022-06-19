package jake2.qcommon.util;

import org.junit.Assert;
import org.junit.Test;

public class LibTest {

    @Test
    public void testDecodeNewLine() {
        Assert.assertEquals("hello\nworld", Lib.decodeBackslash("hello\\nworld"));
    }

    @Test
    public void testDecode2Backslash() {
        Assert.assertEquals("hello\\world", Lib.decodeBackslash("hello\\\\world"));
    }

    @Test
    public void testDecodeOther() {
        Assert.assertEquals("hello\\world", Lib.decodeBackslash("hello\\tworld"));
    }

    @Test
    public void testDecodeNormal() {
        Assert.assertEquals("hello, world", Lib.decodeBackslash("hello, world"));
    }
}
