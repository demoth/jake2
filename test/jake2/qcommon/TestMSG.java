/*
 * Created on 30.01.2004
 *
 * To change this generated comment go to 
 * Window>Preferences>Java>Code Generation>Code Template
 */
package jake2.qcommon;

import jake2.Defines;
/**
 * @author rst
 */
public class TestMSG extends MSG {
	public static void main(String[] args) {
	
		byte buf_data[] = new byte[Defines.MAX_MSGLEN];
		sizebuf_t buf = new sizebuf_t();	
		
		SZ.Init(buf, buf_data, Defines.MAX_MSGLEN);
		
		MSG.WriteInt(buf, 0x80000000);
		MSG.WriteInt(buf, 0x12345678);
		MSG.WriteInt(buf, 0x7fffffff);
		MSG.WriteInt(buf, 0xffffffff);

		
		MSG.WriteByte(buf, 1);
		MSG.WriteByte(buf, 2);
		MSG.WriteByte(buf, 3);
		MSG.WriteByte(buf, 4);
		SZ.Print(buf, "[einz]\n");
		SZ.Print(buf, "[zwei]...");
		
		MSG.WriteByte(buf, 0xfe);
		MSG.WriteByte(buf, 4);
		
		MSG.WriteShort(buf, 32766);
		MSG.WriteShort(buf, 16384);
		MSG.WriteShort(buf, -32768);
		

		
		MSG.WriteFloat(buf, (float) Math.PI);
		
		System.out.println("Read:" + Integer.toHexString(MSG.ReadLong(buf)));
		System.out.println("Read:" + Integer.toHexString(MSG.ReadLong(buf)));
		System.out.println("Read:" + Integer.toHexString(MSG.ReadLong(buf)));
		System.out.println("Read:" + Integer.toHexString(MSG.ReadLong(buf)));
		
		System.out.println("Read:" + MSG.ReadByte(buf));
		System.out.println("Read:" + MSG.ReadByte(buf));
		System.out.println("Read:" + MSG.ReadByte(buf));
		System.out.println("Read:" + MSG.ReadByte(buf));
		System.out.println("Read:<" + MSG.ReadString(buf) + ">");
		
		System.out.println("Read:" + MSG.ReadByte(buf));
		System.out.println("Read:" + MSG.ReadByte(buf));
		
		System.out.println("Read:" + MSG.ReadShort(buf));
		System.out.println("Read:" + MSG.ReadShort(buf));
		System.out.println("Read:" + MSG.ReadShort(buf));		

		System.out.println("Read:" + MSG.ReadFloat(buf));
	}

}
