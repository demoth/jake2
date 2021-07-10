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

        sizebuf_t.WriteInt(buf, 0x80000000);
        assertEquals(0x80000000, sizebuf_t.ReadInt(buf));

        sizebuf_t.WriteInt(buf, 0x12345678);
        assertEquals(0x12345678, sizebuf_t.ReadInt(buf));

        sizebuf_t.WriteInt(buf, 0x7fffffff);
        assertEquals(0x7fffffff, sizebuf_t.ReadInt(buf));

        sizebuf_t.WriteInt(buf, 0xffffffff);
        assertEquals(0xffffffff, sizebuf_t.ReadInt(buf));

        sizebuf_t.WriteInt(buf, Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, sizebuf_t.ReadInt(buf));

        sizebuf_t.WriteInt(buf, Integer.MIN_VALUE);
        assertEquals(Integer.MIN_VALUE, sizebuf_t.ReadInt(buf));

        sizebuf_t.WriteInt(buf, 0);
        assertEquals(0, sizebuf_t.ReadInt(buf));

        // underrun
        assertEquals(-1, sizebuf_t.ReadInt(buf));
    }

    @Test
    public void testBytes() {
        sizebuf_t.WriteByte(buf, (byte) 1);
        assertEquals((byte) 1, (byte) sizebuf_t.ReadByte(buf));

        sizebuf_t.WriteByte(buf, (byte) -1);
        assertEquals((byte) -1, (byte) sizebuf_t.ReadByte(buf));

        sizebuf_t.WriteByte(buf, (byte) 200); // -56
        assertEquals((byte) 200, (byte) sizebuf_t.ReadByte(buf));

        sizebuf_t.WriteByte(buf, (byte) -200); // 56
        assertEquals((byte) -200, (byte) sizebuf_t.ReadByte(buf));

        sizebuf_t.WriteByte(buf, (byte) -100);
        assertEquals((byte) -100, (byte) sizebuf_t.ReadByte(buf));

        sizebuf_t.WriteByte(buf, (byte) 0xff); // -1
        assertEquals((byte) 0xff, (byte) sizebuf_t.ReadByte(buf));

        sizebuf_t.WriteByte(buf, (byte) -128);
        assertEquals((byte) -128, (byte) sizebuf_t.ReadByte(buf));

        sizebuf_t.WriteByte(buf, Byte.MIN_VALUE);
        assertEquals(Byte.MIN_VALUE, sizebuf_t.ReadSignedByte(buf));

        sizebuf_t.WriteByte(buf, Byte.MAX_VALUE);
        assertEquals(Byte.MAX_VALUE, sizebuf_t.ReadSignedByte(buf));

        sizebuf_t.WriteByte(buf, Byte.MIN_VALUE);
        assertEquals(Byte.MIN_VALUE, sizebuf_t.ReadSignedByte(buf));

        sizebuf_t.WriteByte(buf, Byte.MAX_VALUE);
        assertEquals(Byte.MAX_VALUE, sizebuf_t.ReadSignedByte(buf));

        // underrun
        assertEquals(-1, sizebuf_t.ReadByte(buf));
    }


    @Test
    public void testShort() {
        buf.WriteShort(1);
        assertEquals(1, sizebuf_t.ReadShort(buf));

        buf.WriteShort((short) 0x00ff);
        assertEquals((short) 0x00ff, sizebuf_t.ReadShort(buf));

        buf.WriteShort((short) 0xffff);
        assertEquals((short) 0xffff, sizebuf_t.ReadShort(buf));

        buf.WriteShort((short) 0xff00);
        assertEquals((short) 0xff00, sizebuf_t.ReadShort(buf));


        buf.WriteShort(Short.MIN_VALUE);
        assertEquals(Short.MIN_VALUE, sizebuf_t.ReadShort(buf));

        buf.WriteShort(Short.MAX_VALUE);
        assertEquals(Short.MAX_VALUE, sizebuf_t.ReadShort(buf));


        // underrun
        assertEquals(-1, sizebuf_t.ReadShort(buf));
    }

    @Test
    public void testFloat() {
        sizebuf_t.WriteFloat(buf, 1f);
        assertEquals(1f, sizebuf_t.ReadFloat(buf), 0.00001);

        sizebuf_t.WriteFloat(buf, Float.MIN_VALUE);
        assertEquals(Float.MIN_VALUE, sizebuf_t.ReadFloat(buf), 0.00001);

        sizebuf_t.WriteFloat(buf, Short.MAX_VALUE);
        assertEquals(Short.MAX_VALUE, sizebuf_t.ReadFloat(buf), 0.00001);

        // underrun
        assertEquals(Float.NaN, sizebuf_t.ReadFloat(buf), 0.00001);
    }

    @Test
    public void testStrings() {

        sizebuf_t.WriteString(buf, "test value");
        assertEquals("test value", sizebuf_t.ReadString(buf));

        sizebuf_t.WriteString(buf, "test\nvalue\n");
        assertEquals("test\nvalue\n", sizebuf_t.ReadString(buf));

        sizebuf_t.WriteString(buf, null);
        assertEquals("", sizebuf_t.ReadString(buf));

        // underrun
        assertEquals("", sizebuf_t.ReadString(buf));
    }
}
