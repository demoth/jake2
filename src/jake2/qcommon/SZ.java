/*
 * SZ.java
 * Copyright (C) 2003
 * 
 * $Id: SZ.java,v 1.8 2003-12-04 19:59:00 rst Exp $
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

	/**
	 * @param buf
	 * @param data
	 * @param length
	 */
	public static void Init(sizebuf_t buf, byte[] data, int length) {
		buf = new sizebuf_t();
		buf.data = data;
		buf.maxsize = length;
	}
	
	public static void Print(sizebuf_t buf, byte[] data) {
	}
	
	public static void Write(sizebuf_t buf, byte[] data, int length) {
		System.arraycopy(data, 0, buf.data, GetSpace(buf, length), length);
	}
	
	/**
	 * @param buf
	 * @param length
	 * @return the write position instead of the pointer
	 */
	public static int GetSpace(sizebuf_t buf, int length) {
        
		if (buf.cursize + length > buf.maxsize) {
			if (!buf.allowoverflow)
				Com.Error(Defines.ERR_FATAL, "SZ_GetSpace: overflow without allowoverflow set");

			if (length > buf.maxsize)
				Com.Error(Defines.ERR_FATAL, "SZ_GetSpace: " + length + " is > full buffer size");
                         
			Com.Printf("SZ_GetSpace: overflow\n");
			SZ.Clear(buf); 
			buf.overflowed = true;
		}
 
		int data = buf.cursize;
		
		buf.cursize += length;
       
		return data;
	}

	public static void Clear(sizebuf_t buf) {
		buf.cursize = 0;
		buf.overflowed = false;
	}

	//===========================================================================
	
	public static void SZ_Init(sizebuf_t buf, byte data[], int length) {
		//memset (buf, 0, sizeof(*buf));
		buf.data = data;
		buf.maxsize = length;
	}

	public static void SZ_Clear(sizebuf_t buf) {
		buf.cursize = 0;
		buf.overflowed = false;
	}

	/** Ask for the pointer using sizebuf_t.cursize (RST) */
	public static int SZ_GetSpace(sizebuf_t buf, int length) {
		int data;
	
		if (buf.cursize + length > buf.maxsize) {
			if (!buf.allowoverflow)
				Com.Error(Defines.ERR_FATAL, "SZ_GetSpace: overflow without allowoverflow set");
	
			if (length > buf.maxsize)
				Com.Error(Defines.ERR_FATAL, "SZ_GetSpace: " + length + " is > full buffer size");
	
			Com.Printf("SZ_GetSpace: overflow\n");
			SZ_Clear(buf);
			buf.overflowed = true;
		}
	
		data = buf.cursize;
		buf.cursize += length;
	
		return data;
	}

	public static void SZ_Write(sizebuf_t buf, byte data[], int length) {
		//memcpy(SZ_GetSpace(buf, length), data, length);
		System.arraycopy(data, 0, buf.data, SZ_GetSpace(buf, length), length);
	}

	public static void SZ_Write(sizebuf_t buf, byte data[]) {
		int length = data.length;
		//memcpy(SZ_GetSpace(buf, length), data, length);
		System.arraycopy(data, 0, buf.data, SZ_GetSpace(buf, length), length);
	}

	public static void SZ_Print(sizebuf_t buf, String data) {
	
		int length = data.length() + 1;
		byte str[] = data.getBytes();
	
		if (buf.cursize != 0) {
	
			if (buf.data[buf.cursize - 1] != 0) {
				//memcpy( SZ_GetSpace(buf, len), data, len); // no trailing 0
				System.arraycopy(data, 0, buf.data, SZ_GetSpace(buf, length), length);
			} else {
				System.arraycopy(data, 0, buf.data, SZ_GetSpace(buf, length), length);
				//memcpy(SZ_GetSpace(buf, len - 1) - 1, data, len); // write over trailing 0
			}
	
		} else
			System.arraycopy(data, 0, buf.data, SZ_GetSpace(buf, length), length);
		//memcpy(SZ_GetSpace(buf, len), data, len);
	}
}
