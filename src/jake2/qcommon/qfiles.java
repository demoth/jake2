/*
 * qfiles.java
 * Copyright (C) 2003
 *
 * $Id: qfiles.java,v 1.1 2003-12-27 16:19:53 cwei Exp $
 */ 
 /*
Copyright (C) 1997-2001 Id Software, Inc.

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

*/
package jake2.qcommon;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * qfiles
 * 
 * @author cwei
 */
public class qfiles {
	
	
//	/*
//	========================================================================
//
//	PCX files are used for as many images as possible
//
//	========================================================================
//	*/
//
//	typedef struct
//	{
//		char	manufacturer;
//		char	version;
//		char	encoding;
//		char	bits_per_pixel;
//		unsigned short	xmin,ymin,xmax,ymax;
//		unsigned short	hres,vres;
//		unsigned char	palette[48];
//		char	reserved;
//		char	color_planes;
//		unsigned short	bytes_per_line;
//		unsigned short	palette_type;
//		char	filler[58];
//		unsigned char	data;			// unbounded
//	} pcx_t;
	

	public static class pcx_t {

		// size of byte arrays
		static final int PALETTE_SIZE = 48;
		static final int FILLER_SIZE = 58;

		public byte manufacturer;
		public byte version;
		public byte encoding;
		public byte bits_per_pixel;
		public int xmin, ymin, xmax, ymax; // unsigned short
		public int hres, vres; // unsigned short
		public byte[] palette; //unsigned byte
		public byte reserved;
		public byte color_planes;
		public int bytes_per_line; // unsigned short
		public int palette_type; // unsigned short
		public byte[] filler;
		public ByteBuffer data;
		

		public pcx_t(byte[] dataBytes) {
			this(ByteBuffer.wrap(dataBytes));
		}
		
		public pcx_t(ByteBuffer b) {
			// is stored as little endian
			b.order(ByteOrder.LITTLE_ENDIAN);
			
			// fill header
			manufacturer = b.get();
			version = b.get();
			encoding = b.get();
			bits_per_pixel = b.get();
			xmin = b.getShort() & 0xffff;
			ymin = b.getShort() & 0xffff;
			xmax = b.getShort() & 0xffff;
			ymax = b.getShort() & 0xffff;
			hres = b.getShort() & 0xffff;
			vres = b.getShort() & 0xffff;
			b.get(palette = new byte[PALETTE_SIZE]);
			reserved = b.get();
			color_planes = b.get();
			bytes_per_line = b.getShort() & 0xffff;
			palette_type = b.getShort() & 0xffff;
			b.get(filler = new byte[FILLER_SIZE]);
			
			// fill data
			data = b.slice();
		}
	}

}
