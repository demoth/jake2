/*
 * Created on Nov 18, 2003
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package jake2.imageio;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * @author cwei
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class PCX {

	public static final int HEADER_SIZE = 128;

	/**
	 * <code>
		typedef struct
		{
		char        manufacturer;
		char        version;
		char        encoding;
		char        bits_per_pixel;
		unsigned short      xmin,ymin,xmax,ymax;
		unsigned short      width,height;
		unsigned char       palette[48];
		char        reserved;
		char        color_planes;
		unsigned short      bytes_per_line;
		unsigned short      palette_type;
		char        filler[58];
		unsigned char       data;                   // unbounded
		} pcx_t;
	
		</code>
	*/
	public static class Header {
		byte manufacturer;
		byte version;
		byte encoding;
		byte bitsPerPixel;
		int xmin, ymin, xmax, ymax;
		int width, height;
		byte[] palette = new byte[48];
		byte reserved;
		byte colorPlanes;
		int bytesPerLine;
		int paletteType;
		byte[] filler = new byte[58];

		public Header(byte[] headerBytes) throws IOException {
			if (headerBytes == null || headerBytes.length != 128) {
				throw new IOException("invalid quake2 pcx header");
			}
			parseHeader(headerBytes);
		}

		private void parseHeader(byte[] buffer) throws IOException {
			DataInputStream in =
				new DataInputStream(new ByteArrayInputStream(buffer));
			this.manufacturer = in.readByte();
			this.version = in.readByte();
			this.encoding = in.readByte();
			this.bitsPerPixel = in.readByte();

			this.xmin =
				(in.readUnsignedByte() << 0) + (in.readUnsignedByte() << 8);
			this.ymin =
				(in.readUnsignedByte() << 0) + (in.readUnsignedByte() << 8);
			this.xmax =
				(in.readUnsignedByte() << 0) + (in.readUnsignedByte() << 8);
			this.ymax =
				(in.readUnsignedByte() << 0) + (in.readUnsignedByte() << 8);
			this.width =
				(in.readUnsignedByte() << 0) + (in.readUnsignedByte() << 8);
			this.height =
				(in.readUnsignedByte() << 0) + (in.readUnsignedByte() << 8);

			in.readFully(this.palette);

			this.reserved = in.readByte();
			this.colorPlanes = in.readByte();

			this.bytesPerLine =
				(in.readUnsignedByte() << 0) + (in.readUnsignedByte() << 8);
			this.paletteType =
				(in.readUnsignedByte() << 0) + (in.readUnsignedByte() << 8);

			in.readFully(this.filler);

			if (this.manufacturer != 0x0a
				|| this.version != 5
				|| this.encoding != 1
				|| this.bitsPerPixel != 8
				|| this.xmax >= 640
				|| this.ymax >= 480) {
				throw new IOException("invalid quake2 pcx header");
			}
		}
	}

}
