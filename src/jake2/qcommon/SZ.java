/*
 * SZ.java
 * Copyright (C) 2003
 * 
 * $Id: SZ.java,v 1.6 2003-12-02 10:07:36 hoz Exp $
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
		System.arraycopy(data, 0, GetSpace(buf, length), 0, length);
//	00917         memcpy (SZ_GetSpace(buf,length),data,length);           
	}
	
	public static byte[] GetSpace(sizebuf_t buf, int length) {

		byte[] data = null;
         
		if (buf.cursize + length > buf.maxsize) {
			if (!buf.allowoverflow)
				Com.Error(Defines.ERR_FATAL, "SZ_GetSpace: overflow without allowoverflow set");

			if (length > buf.maxsize)
				Com.Error(Defines.ERR_FATAL, "SZ_GetSpace: " + length + " is > full buffer size");
                         
			Com.Printf("SZ_GetSpace: overflow\n");
			SZ.Clear(buf); 
			buf.overflowed = true;
		}
 
		//data = buf.data + buf.cursize;
		
		buf.cursize += length;
       
		return data;
	}

	public static void Clear(sizebuf_t buf) {
		buf.cursize = 0;
		buf.overflowed = false;
	}
}
