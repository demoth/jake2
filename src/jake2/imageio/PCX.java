/*
 * Created on Nov 18, 2003
 *
 */
package jake2.imageio;

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

		// size of byte arrays
		static final int PALETTE_SIZE = 48;
		static final int FILLER_SIZE = 58;

		byte manufacturer;
		byte version;
		byte encoding;
		byte bitsPerPixel;
		int xmin, ymin, xmax, ymax; // unsigned short
		int width, height; // unsigned short
		byte[] palette; //unsigned byte
		byte reserved;
		byte colorPlanes;
		int bytesPerLine; // unsigned short
		int paletteType; // unsigned short
		byte[] filler;
		


		public Header(byte[] headerBytes) {
			if (headerBytes == null || headerBytes.length != 128) {
				throw new IllegalArgumentException("invalid quake2 pcx header");
			}

			ByteBuffer b = ByteBuffer.wrap(headerBytes);
			// is stored as little endian
			b.order(ByteOrder.LITTLE_ENDIAN);
			
			// fill header
			manufacturer = b.get();
			version = b.get();
			encoding = b.get();
			bitsPerPixel = b.get();
			xmin = b.getShort() & 0xffff;
			ymin = b.getShort() & 0xffff;
			xmax = b.getShort() & 0xffff;
			ymax = b.getShort() & 0xffff;
			width = b.getShort() & 0xffff;
			height = b.getShort() & 0xffff;
			b.get(palette = new byte[PALETTE_SIZE]);
			reserved = b.get();
			colorPlanes = b.get();
			bytesPerLine = b.getShort() & 0xffff;
			paletteType = b.getShort() & 0xffff;
			b.get(filler = new byte[FILLER_SIZE]);

			// check some attributes
			checkHeader();
		}

		private void checkHeader() {

			if (this.getManufacturer() != 0x0a
				|| this.getVersion() != 5
				|| this.getEncoding() != 1
				|| this.getBitsPerPixel() != 8
				|| this.getXmax() >= 640
				|| this.getYmax() >= 480) {
				throw new IllegalArgumentException("invalid quake2 pcx header");
			}
		}
		
		/**
		 * @return
		 */
		public byte getBitsPerPixel() {
			return bitsPerPixel;
		}

		/**
		 * @return
		 */
		public int getBytesPerLine() {
			return bytesPerLine;
		}

		/**
		 * @return
		 */
		public byte getColorPlanes() {
			return colorPlanes;
		}

		/**
		 * @return
		 */
		public byte getEncoding() {
			return encoding;
		}

		/**
		 * @return
		 */
		public byte[] getFiller() {
			return filler;
		}

		/**
		 * @return
		 */
		public int getHeight() {
			return height;
		}

		/**
		 * @return
		 */
		public byte getManufacturer() {
			return manufacturer;
		}

		/**
		 * @return
		 */
		public byte[] getPalette() {
			return palette;
		}

		/**
		 * @return
		 */
		public int getPaletteType() {
			return paletteType;
		}

		/**
		 * @return
		 */
		public byte getReserved() {
			return reserved;
		}

		/**
		 * @return
		 */
		public byte getVersion() {
			return version;
		}

		/**
		 * @return
		 */
		public int getWidth() {
			return width;
		}

		/**
		 * @return
		 */
		public int getXmax() {
			return xmax;
		}

		/**
		 * @return
		 */
		public int getXmin() {
			return xmin;
		}

		/**
		 * @return
		 */
		public int getYmax() {
			return ymax;
		}

		/**
		 * @return
		 */
		public int getYmin() {
			return ymin;
		}
	}
}