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

// Created on 02.01.2004 by RST.
// $Id: texinfo_t.java,v 1.5 2004-02-26 13:25:35 hoz Exp $

package jake2.qcommon;

import jake2.util.Lib;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class texinfo_t {

	// works fine.
	public texinfo_t(byte[] cmod_base, int o, int len) {
		this(ByteBuffer.wrap(cmod_base, o, len).order(ByteOrder.LITTLE_ENDIAN));
	}

	public texinfo_t(ByteBuffer bb) {

		byte str[] = new byte[32];

		vecs[0] = new float[] { bb.getFloat(), bb.getFloat(), bb.getFloat(), bb.getFloat()};
		vecs[1] = new float[] { bb.getFloat(), bb.getFloat(), bb.getFloat(), bb.getFloat()};

		flags = bb.getInt();
		value = bb.getInt();

		bb.get(str);
		texture = new String(str, 0, Lib.strlen(str));
		nexttexinfo = bb.getInt();
	}

	public static final int SIZE = 32 + 4 + 4 + 32 + 4;

	//float			vecs[2][4];		// [s/t][xyz offset]
	public float vecs[][] = {
		 { 0, 0, 0, 0 },
		 { 0, 0, 0, 0 }
	};
	public int flags; // miptex flags + overrides
	public int value; // light emission, etc
	//char			texture[32];	// texture name (textures/*.wal)
	public String texture="";
	public int nexttexinfo; // for animations, -1 = end of chain
}
