/*
 * SZ.java
 * Copyright (C) 2003
 * 
 * $Id: SZ.java,v 1.10 2003-12-29 22:31:15 rst Exp $
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

/**
 * SZ
 */
public final class SZ {

	public static void Clear(sizebuf_t buf) {
		buf.clear();
	}

	//===========================================================================
	
	public static void Init(sizebuf_t buf, byte data[], int length) {
		//memset (buf, 0, sizeof(*buf));
		buf.data = data;
		buf.maxsize = length;
	}


	/** Ask for the pointer using sizebuf_t.cursize (RST) */
	public static int GetSpace(sizebuf_t buf, int length) {
		int data;
	
		if (buf.cursize + length > buf.maxsize) {
			if (!buf.allowoverflow)
				Com.Error(Defines.ERR_FATAL, "SZ_GetSpace: overflow without allowoverflow set");
	
			if (length > buf.maxsize)
				Com.Error(Defines.ERR_FATAL, "SZ_GetSpace: " + length + " is > full buffer size");
	
			Com.Printf("SZ_GetSpace: overflow\n");
			Clear(buf);
			buf.overflowed = true;
		}
	
		data = buf.cursize;
		buf.cursize += length;
	
		return data;
	}

	public static void Write(sizebuf_t buf, byte data[], int length) {
		//memcpy(SZ_GetSpace(buf, length), data, length);
		System.arraycopy(data, 0, buf.data, GetSpace(buf, length), length);
	}

	public static void Write(sizebuf_t buf, byte data[]) {
		int length = data.length;
		//memcpy(SZ_GetSpace(buf, length), data, length);
		System.arraycopy(data, 0, buf.data, GetSpace(buf, length), length);
	}

	public static void Print(sizebuf_t buf, String data) {
	
		int length = data.length() + 1;
		byte str[] = data.getBytes();
	
		if (buf.cursize != 0) {
	
			if (buf.data[buf.cursize - 1] != 0) {
				//memcpy( SZ_GetSpace(buf, len), data, len); // no trailing 0
				System.arraycopy(data, 0, buf.data, GetSpace(buf, length), length);
			} else {
				System.arraycopy(data, 0, buf.data, GetSpace(buf, length), length);
				//memcpy(SZ_GetSpace(buf, len - 1) - 1, data, len); // write over trailing 0
			}
	
		} else
			System.arraycopy(data, 0, buf.data, GetSpace(buf, length), length);
		//memcpy(SZ_GetSpace(buf, len), data, len);
	}
}
