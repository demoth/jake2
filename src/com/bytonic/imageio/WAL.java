/*
 * Created on Nov 18, 2003
 *
 */
package com.bytonic.imageio;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * @author cwei
 *
 */
public class WAL {

	public static final int HEADER_SIZE = 100;

	/*		struct wal_header
			{
				char    name[32];        // name of the texture
		 
				uint32  width;           // width (in pixels) of the largest mipmap level
				uint32  height;          // height (in pixels) of the largest mipmap level
		 
				int32   offset[4];       // byte offset of the start of each of the 4 mipmap levels
		
				char    next_name[32];   // name of the next texture in the animation
		
				uint32  flags;           // ?
				uint32  contents;        // ?
				uint32  value;           // ?
			};
	*/
	public static class Header {

		// size of byte arrays
		static final int NAME_SIZE = 32;
		static final int OFFSET_SIZE = 4;

		String name;
		int width;
		int height;
		int[] offset; // file offsets for the 4 mipmap images
		String nextName;
		int flags; // unused
		int contents; // unused
		int value; // unused

		public Header(byte[] headerBytes) {
			if (headerBytes == null || headerBytes.length != HEADER_SIZE) {
				throw new IllegalArgumentException("invalid quake2 wal header");
			}

			ByteBuffer b = ByteBuffer.wrap(headerBytes);
			// is stored as little endian
			b.order(ByteOrder.LITTLE_ENDIAN);

			byte[] tmp = new byte[NAME_SIZE];
			// fill header

			// name
			b.get(tmp);
			try {
				name = new String(tmp, "ISO-8859-1");
			} catch (UnsupportedEncodingException e) {
				name = new String(tmp);
			}
			// width
			width = b.getInt();
			assert(width >= 0) : "unsigned int bug"; // true means ok.
			// height
			height = b.getInt();
			assert(height >= 0) : "unsigned int bug"; // true means ok.
			// 4 offsets
			offset =
				new int[] { b.getInt(), b.getInt(), b.getInt(), b.getInt()};
			// nextName
			b.get(tmp);
			try {
				nextName = new String(tmp, "ISO-8859-1");
			} catch (UnsupportedEncodingException e1) {
				name = new String(tmp);
			}
			// unused entries
			flags = b.getInt();
			contents = b.getInt();
			value = b.getInt();

			// check some attributes
			checkHeader();
		}

		private void checkHeader() {
			// start of mipmaps
			int mipmap0 = HEADER_SIZE;
			int mipmap1 = mipmap0 + getWidth() * getHeight();
			int mipmap2 = mipmap1 + getWidth() / 2 * getHeight() / 2;
			int mipmap3 = mipmap2 + getWidth() / 4 * getHeight() / 4;

			if (offset[0] != mipmap0
				|| offset[1] != mipmap1
				|| offset[2] != mipmap2
				|| offset[3] != mipmap3) {
				throw new IllegalArgumentException("invalid quake2 wal header");
			}
		}

		/**
		 * @return
		 */
		public int getContents() {
			return contents;
		}

		/**
		 * @return
		 */
		public int getFlags() {
			return flags;
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
		public String getName() {
			return name;
		}

		/**
		 * @return
		 */
		public String getNextName() {
			return nextName;
		}

		/**
		 * @return
		 */
		public int getOffset(int index) {
			if (index < 0 || index > 3) {
				throw new ArrayIndexOutOfBoundsException("mipmap offset range is 0 to 3");
			}
			return offset[index];
		}

		/**
		 * @return
		 */
		public int getValue() {
			return value;
		}

		/**
		 * @return
		 */
		public int getWidth() {
			return width;
		}

	}
}