/*
 * Created on 30.01.2004
 *
 * To change this generated comment go to
 * Window>Preferences>Java>Code Generation>Code Template
 */
package jake2.qcommon;

import org.junit.Before;
import org.junit.Test;

import static jake2.qcommon.Defines.MAX_MSGLEN;
import static jake2.qcommon.MSG.*;
import static org.junit.Assert.assertEquals;

/**
 * @author rst
 */
public class TestMSG {
    sizebuf_t buf;

    @Before
    public void setup() {
        buf = new sizebuf_t();
        SZ.Init(buf, new byte[MAX_MSGLEN], MAX_MSGLEN);
    }

    @Test
    public void testIntegers() {

        WriteInt(buf, 0x80000000);
        assertEquals(0x80000000, ReadInt(buf));

        WriteInt(buf, 0x12345678);
        assertEquals(0x12345678, ReadInt(buf));

        WriteInt(buf, 0x7fffffff);
        assertEquals(0x7fffffff, ReadInt(buf));

        WriteInt(buf, 0xffffffff);
        assertEquals(0xffffffff, ReadInt(buf));

        WriteInt(buf, Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, ReadInt(buf));

        WriteInt(buf, Integer.MIN_VALUE);
        assertEquals(Integer.MIN_VALUE, ReadInt(buf));

        WriteInt(buf, 0);
        assertEquals(0, ReadInt(buf));

        // underrun
        assertEquals(-1, ReadInt(buf));
    }

    @Test
    public void testBytes() {
        WriteByte(buf, (byte) 1);
        assertEquals((byte) 1, (byte) ReadByte(buf));

        WriteByte(buf, (byte) 200); // -56
        assertEquals((byte) 200, (byte) ReadByte(buf));

        WriteByte(buf, (byte) -200); // 56
        assertEquals((byte) -200, (byte) ReadByte(buf));

        WriteByte(buf, (byte) -100);
        assertEquals((byte) -100, (byte) ReadByte(buf));

        WriteByte(buf, (byte) 0xff); // -1
        assertEquals((byte) 0xff, (byte) ReadByte(buf));


        // fixme
        WriteByte(buf, (byte) -128); // -128
        assertEquals((byte) -128, (byte) ReadByte(buf));


        WriteByte(buf, Byte.MIN_VALUE);
        assertEquals(Byte.MIN_VALUE, ReadChar(buf));

        WriteByte(buf, Byte.MAX_VALUE);
        assertEquals(Byte.MAX_VALUE, ReadChar(buf));

        WriteByte(buf, Byte.MIN_VALUE);
        assertEquals(Byte.MIN_VALUE, (byte) ReadChar(buf));

        WriteByte(buf, Byte.MAX_VALUE);
        assertEquals(Byte.MAX_VALUE, (byte) ReadChar(buf));

        // underrun
        assertEquals(-1, ReadByte(buf));
    }


    @Test
    public void testShort() {
        WriteShort(buf, 1);
        assertEquals(1, ReadShort(buf));

        WriteShort(buf, (short) 0x00ff);
        assertEquals((short) 0x00ff, ReadShort(buf));

        WriteShort(buf, (short) 0xffff);
        assertEquals((short) 0xffff, ReadShort(buf));

        WriteShort(buf, (short) 0xff00);
        assertEquals((short) 0xff00, ReadShort(buf));


        WriteShort(buf, Short.MIN_VALUE);
        assertEquals(Short.MIN_VALUE, ReadShort(buf));

        WriteShort(buf, Short.MAX_VALUE);
        assertEquals(Short.MAX_VALUE, ReadShort(buf));


        // underrun
        assertEquals(-1, ReadShort(buf));
    }

    @Test
    public void testFloat() {
        WriteFloat(buf, 1f);
        assertEquals(1f, ReadFloat(buf), 0.00001);

        WriteFloat(buf, Float.MIN_VALUE);
        assertEquals(Float.MIN_VALUE, ReadFloat(buf), 0.00001);

        WriteFloat(buf, Short.MAX_VALUE);
        assertEquals(Short.MAX_VALUE, ReadFloat(buf), 0.00001);

        // underrun
        assertEquals(Float.NaN, ReadFloat(buf), 0.00001);
    }

    @Test
    public void testStrings() {

        MSG.WriteString(buf, "test value");
        assertEquals("test value", ReadString(buf));

        MSG.WriteString(buf, "test\nvalue\n");
        assertEquals("test\nvalue\n", ReadString(buf));

        MSG.WriteString(buf, null);
        assertEquals("", ReadString(buf));

        // underrun
        assertEquals("", ReadString(buf));
    }
}
