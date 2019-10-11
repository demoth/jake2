/*
 * Created on 30.01.2004
 *
 * To change this generated comment go to 
 * Window>Preferences>Java>Code Generation>Code Template
 */
package jake2.qcommon;

/**
 * @author rst
 */
public class TestMSG extends MSG {
	public static void main(String[] args) {
	
		byte buf_data[] = new byte[MAX_MSGLEN];
		sizebuf_t buf = new sizebuf_t();	
		
		SZ.Init(buf, buf_data, MAX_MSGLEN);
		
		WriteInt(buf, 0x80000000);
		WriteInt(buf, 0x12345678);
		WriteInt(buf, 0x7fffffff);
		WriteInt(buf, 0xffffffff);

		
		WriteByte(buf, 1);
		WriteByte(buf, 2);
		WriteByte(buf, 3);
		WriteByte(buf, 4);
		SZ.Print(buf, "[einz]\n");
		SZ.Print(buf, "[zwei]...");
		
		WriteByte(buf, 0xfe);
		WriteByte(buf, 4);
		
		WriteShort(buf, 32766);
		WriteShort(buf, 16384);
		WriteShort(buf, -32768);
		

		
		WriteFloat(buf, (float) Math.PI);
		
		System.out.println("Read:" + Integer.toHexString(ReadLong(buf)));
		System.out.println("Read:" + Integer.toHexString(ReadLong(buf)));
		System.out.println("Read:" + Integer.toHexString(ReadLong(buf)));
		System.out.println("Read:" + Integer.toHexString(ReadLong(buf)));
		
		System.out.println("Read:" + ReadByte(buf));
		System.out.println("Read:" + ReadByte(buf));
		System.out.println("Read:" + ReadByte(buf));
		System.out.println("Read:" + ReadByte(buf));
		System.out.println("Read:<" + ReadString(buf) + ">");
		
		System.out.println("Read:" + ReadByte(buf));
		System.out.println("Read:" + ReadByte(buf));
		
		System.out.println("Read:" + ReadShort(buf));
		System.out.println("Read:" + ReadShort(buf));
		System.out.println("Read:" + ReadShort(buf));

		System.out.println("Read:" + ReadFloat(buf));
	}

}
