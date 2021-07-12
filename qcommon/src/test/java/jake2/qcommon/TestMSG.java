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
    sizebuf_t buffer;

    @Before
    public void setup() {
        buffer = new sizebuf_t();
        buffer.init(new byte[MAX_MSGLEN], MAX_MSGLEN);
    }

    @Test
    public void testIntegers() {

        buffer.writeInt(0x80000000);
        assertEquals(0x80000000, buffer.readInt());

        buffer.writeInt(0x12345678);
        assertEquals(0x12345678, buffer.readInt());

        buffer.writeInt(0x7fffffff);
        assertEquals(0x7fffffff, buffer.readInt());

        buffer.writeInt(0xffffffff);
        assertEquals(0xffffffff, buffer.readInt());

        buffer.writeInt(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, buffer.readInt());

        buffer.writeInt(Integer.MIN_VALUE);
        assertEquals(Integer.MIN_VALUE, buffer.readInt());

        buffer.writeInt(0);
        assertEquals(0, buffer.readInt());

        // underrun
        assertEquals(-1, buffer.readInt());
    }

    @Test
    public void testBytes() {
        buffer.writeByte((byte) 1);
        assertEquals((byte) 1, (byte) buffer.readByte());

        buffer.writeByte((byte) -1);
        assertEquals((byte) -1, (byte) buffer.readByte());

        buffer.writeByte((byte) 200); // -56
        assertEquals((byte) 200, (byte) buffer.readByte());

        buffer.writeByte((byte) -200); // 56
        assertEquals((byte) -200, (byte) buffer.readByte());

        buffer.writeByte((byte) -100);
        assertEquals((byte) -100, (byte) buffer.readByte());

        buffer.writeByte((byte) 0xff); // -1
        assertEquals((byte) 0xff, (byte) buffer.readByte());

        buffer.writeByte((byte) -128);
        assertEquals((byte) -128, (byte) buffer.readByte());

        buffer.writeByte(Byte.MIN_VALUE);
        assertEquals(Byte.MIN_VALUE, buffer.readSignedByte());

        buffer.writeByte(Byte.MAX_VALUE);
        assertEquals(Byte.MAX_VALUE, buffer.readSignedByte());

        buffer.writeByte(Byte.MIN_VALUE);
        assertEquals(Byte.MIN_VALUE, buffer.readSignedByte());

        buffer.writeByte(Byte.MAX_VALUE);
        assertEquals(Byte.MAX_VALUE, buffer.readSignedByte());

        // underrun
        assertEquals(-1, buffer.readByte());
    }


    @Test
    public void testShort() {
        buffer.writeShort(1);
        assertEquals(1, buffer.readShort());

        buffer.writeShort((short) 0x00ff);
        assertEquals((short) 0x00ff, buffer.readShort());

        buffer.writeShort((short) 0xffff);
        assertEquals((short) 0xffff, buffer.readShort());

        buffer.writeShort((short) 0xff00);
        assertEquals((short) 0xff00, buffer.readShort());


        buffer.writeShort(Short.MIN_VALUE);
        assertEquals(Short.MIN_VALUE, buffer.readShort());

        buffer.writeShort(Short.MAX_VALUE);
        assertEquals(Short.MAX_VALUE, buffer.readShort());


        // underrun
        assertEquals(-1, buffer.readShort());
    }

    @Test
    public void testFloat() {
        buffer.writeFloat(1f);
        assertEquals(1f, buffer.readFloat(), 0.00001);

        buffer.writeFloat(Float.MIN_VALUE);
        assertEquals(Float.MIN_VALUE, buffer.readFloat(), 0.00001);

        buffer.writeFloat(Short.MAX_VALUE);
        assertEquals(Short.MAX_VALUE, buffer.readFloat(), 0.00001);

        // underrun
        assertEquals(Float.NaN, buffer.readFloat(), 0.00001);
    }

    @Test
    public void testStrings() {

        buffer.writeString("test value");
        assertEquals("test value", buffer.readString());

        buffer.writeString("test\nvalue\n");
        assertEquals("test\nvalue\n", buffer.readString());

        buffer.writeString(null);
        assertEquals("", buffer.readString());

        // underrun
        assertEquals("", buffer.readString());
    }
}
