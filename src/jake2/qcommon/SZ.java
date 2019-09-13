/*
 * SZ.java
 * Copyright (C) 2003
 * 
 * $Id: SZ.java,v 1.6 2005-12-18 22:10:07 cawe Exp $
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

import jake2.Defines;
import jake2.util.Lib;

/**
 * SZ
 */
public final class SZ {

	public static void Init(sizebuf_t buf, byte data[], int length) {
	  // TODO check this. cwei
	  buf.readcount = 0;

	  buf.data = data;
		buf.maxsize = length;
		buf.cursize = 0;
		buf.allowoverflow = buf.overflowed = false;
	}


	/** Ask for the pointer using sizebuf_t.cursize (RST) */
	static int GetSpace(sizebuf_t buf, int length) {
		int oldsize;
	
		if (buf.cursize + length > buf.maxsize) {
			if (!buf.allowoverflow)
				Com.Error(Defines.ERR_FATAL, "SZ_GetSpace: overflow without allowoverflow set");
	
			if (length > buf.maxsize)
				Com.Error(Defines.ERR_FATAL, "SZ_GetSpace: " + length + " is > full buffer size");
	
			Com.Printf("SZ_GetSpace: overflow\n");
			buf.clear();
			buf.overflowed = true;
		}
	
		oldsize = buf.cursize;
		buf.cursize += length;
	
		return oldsize;
	}

	public static void Write(sizebuf_t buf, byte data[], int length) {
		//memcpy(SZ_GetSpace(buf, length), data, length);
		System.arraycopy(data, 0, buf.data, GetSpace(buf, length), length);
	}

	public static void Write(sizebuf_t buf, byte data[], int offset, int length) {
		System.arraycopy(data, offset, buf.data, GetSpace(buf, length), length);
	}

	public static void Write(sizebuf_t buf, byte data[]) {
		int length = data.length;
		//memcpy(SZ_GetSpace(buf, length), data, length);
		System.arraycopy(data, 0, buf.data, GetSpace(buf, length), length);
	}

	// 
	public static void Print(sizebuf_t buf, String data) {
	    Com.dprintln("SZ.print():<" + data + ">" );
		int length = data.length();
		byte str[] = Lib.stringToBytes(data);
	
		if (buf.cursize != 0) {
	
			if (buf.data[buf.cursize - 1] != 0) {
				//memcpy( SZ_GetSpace(buf, len), data, len); // no trailing 0
				System.arraycopy(str, 0, buf.data, GetSpace(buf, length+1), length);
			} else {
				System.arraycopy(str, 0, buf.data, GetSpace(buf, length)-1, length);
				//memcpy(SZ_GetSpace(buf, len - 1) - 1, data, len); // write over trailing 0
			}
		} else
			// first print.
			System.arraycopy(str, 0, buf.data, GetSpace(buf, length), length);
		//memcpy(SZ_GetSpace(buf, len), data, len);
		
		buf.data[buf.cursize - 1]=0;
	}
}
