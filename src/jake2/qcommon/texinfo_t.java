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
// $Id: texinfo_t.java,v 1.1 2004-01-02 17:40:54 rst Exp $

package jake2.qcommon;

import jake2.game.EndianHandler;

import java.nio.ByteBuffer;


 import jake2.util.*;
// import jake2.client.*;
// import jake2.game.*;
// import jake2.qcommon.*;
// import jake2.render.*;
// import jake2.server.*;

public class texinfo_t {
	
	// works fine.
	public texinfo_t(byte[] cmod_base, int o, int len) {
		
		byte str[] = new byte[32];
		
		ByteBuffer bb = ByteBuffer.wrap(cmod_base, o, len);
		vecs[0] = new float []{
			EndianHandler.swapFloat(bb.getFloat()),
			EndianHandler.swapFloat(bb.getFloat()),
			EndianHandler.swapFloat(bb.getFloat()),
			EndianHandler.swapFloat(bb.getFloat())};
			
		vecs[1] = new float []{
			EndianHandler.swapFloat(bb.getFloat()),
			EndianHandler.swapFloat(bb.getFloat()),
			EndianHandler.swapFloat(bb.getFloat()),
			EndianHandler.swapFloat(bb.getFloat())};

		flags = EndianHandler.swapInt(bb.getInt());
		value = EndianHandler.swapInt(bb.getInt());
		
		bb.get(str);
		texture = new String (str,0,Lib.strlen(str));
		nexttexinfo = EndianHandler.swapInt(bb.getInt());
		
	}

	public static int SIZE = 32 + 4 + 4 + 32 + 4;
	
	//float			vecs[2][4];		// [s/t][xyz offset]
	float 			vecs[][] = {{0,0,0,0},{0,0,0,0}};
	int			flags;			// miptex flags + overrides
	int			value;			// light emission, etc
	//char			texture[32];	// texture name (textures/*.wal)
	String 			texture;
	int			nexttexinfo;	// for animations, -1 = end of chain
}
