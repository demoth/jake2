/*
 * Created on Nov 18, 2003
 *
 */
package jake2.imageio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * @author cwei
 *
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

		// offsets for the header entries
		static final int MANUFACTURER = 0;
		static final int VERSION = 1;
		static final int ENCODING = 2;
		static final int BITS_PER_PIXEL = 3;
		static final int XMIN = 4;
		static final int YMIN = 6;
		static final int XMAX = 8;
		static final int YMAX = 10;
		static final int WIDTH = 12;
		static final int HEIGHT = 14;
		static final int PALETTE = 16;
		static final int RESERVED = 64;
		static final int COLOR_PLANES = 65;
		static final int BYTES_PER_LINE = 66;
		static final int PALETTE_TYPE = 68;
		static final int FILLER = 70;

		// size of byte arrays
		static final int PALETTE_SIZE = 48;
		static final int FILLER_SIZE = 58;

		// buffers the header data
		ByteBuffer mem = ByteBuffer.allocate(HEADER_SIZE);

		public Header(byte[] headerBytes) throws IOException {
			if (headerBytes == null || headerBytes.length != 128) {
				throw new IOException("invalid quake2 pcx header");
			}
			mem.put(headerBytes);
			mem.order(ByteOrder.LITTLE_ENDIAN);

			checkHeader();
		}

		private void checkHeader() throws IOException {

			if (this.getManufacturer() != 0x0a
				|| this.getVersion() != 5
				|| this.getEncoding() != 1
				|| this.getBitsPerPixel() != 8
				|| this.getXmax() >= 640
				|| this.getYmax() >= 480) {
				throw new IOException("invalid quake2 pcx header");
			}
		}

		/**
		 * @return
		 */
		public final byte getBitsPerPixel() {
			return mem.get(BITS_PER_PIXEL);
		}

		/**
		 * @return
		 */
		public final int getBytesPerLine() {
			return mem.getShort(BYTES_PER_LINE) & 0xffff; // unsigned short
		}

		/**
		 * @return
		 */
		public final byte getColorPlanes() {
			return mem.get(COLOR_PLANES);
		}

		/**
		 * @return
		 */
		public final byte getEncoding() {
			return mem.get(ENCODING);
		}

		/**
		 * @return
		 */
		public final byte[] getFiller() {
			byte[] tmp = new byte[FILLER_SIZE];
			mem.get(tmp, FILLER, FILLER_SIZE);
			return tmp;
		}

		/**
		 * @return
		 */
		public final int getHeight() {
			return mem.getShort(HEIGHT) & 0xffff; // unsigned short
		}

		/**
		 * @return
		 */
		public final byte getManufacturer() {
			return mem.get(MANUFACTURER);
		}

		/**
		 * @return
		 */
		public final byte[] getPalette() {
			byte[] tmp = new byte[PALETTE_SIZE];
			mem.get(tmp, PALETTE, PALETTE_SIZE);
			return tmp;
		}

		/**
		 * @return
		 */
		public final int getPaletteType() {
			return mem.getShort(PALETTE_TYPE) & 0xffff; // unsigned short
		}

		/**
		 * @return
		 */
		public final byte getReserved() {
			return mem.get(RESERVED);
		}

		/**
		 * @return
		 */
		public final byte getVersion() {
			return mem.get(VERSION);
		}

		/**
		 * @return
		 */
		public final int getWidth() {
			return mem.getShort(WIDTH) & 0xffff; // unsigned short
		}

		/**
		 * @return
		 */
		public final int getXmax() {
			return mem.getShort(XMAX) & 0xffff; // unsigned short
		}

		/**
		 * @return
		 */
		public final int getXmin() {
			return mem.getShort(XMIN) & 0xffff; // unsigned short
		}

		/**
		 * @return
		 */
		public final int getYmax() {
			return mem.getShort(YMAX) & 0xffff; // unsigned short
		}

		/**
		 * @return
		 */
		public final int getYmin() {
			return mem.getShort(YMIN) & 0xffff; // unsigned short
		}

	}
}