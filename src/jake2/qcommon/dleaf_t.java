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
// $Id: dleaf_t.java,v 1.2 2004-01-02 22:29:01 rst Exp $

package jake2.qcommon;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import jake2.*;
import jake2.client.*;
import jake2.game.*;
import jake2.render.*;
import jake2.server.*;

public class dleaf_t {

	public dleaf_t(byte[] cmod_base, int i, int j) {
		ByteBuffer bb = ByteBuffer.wrap(cmod_base, i, j);
		
		bb.order(ByteOrder.LITTLE_ENDIAN);

		contents = bb.getInt();
		cluster = bb.getShort();
		area = bb.getShort();
		
		mins[0] = bb.getShort();
		mins[1] = bb.getShort();
		mins[2] = bb.getShort();

		maxs[0] = bb.getShort();
		maxs[1] = bb.getShort();
		maxs[2] = bb.getShort();
		
		firstleafface = bb.getShort() &0xffff;
		numleaffaces = bb.getShort() &0xffff;
		
		firstleafbrush = bb.getShort() &0xffff;
		numleafbrushes = bb.getShort() &0xffff;
	}

	public static int SIZE = 4 +  8 * 2 + 4 * 2;

	int contents; // OR of all brushes (not needed?)

	short cluster;
	short area;

	short mins[] = { 0, 0, 0 }; // for frustum culling
	short maxs[] = { 0, 0, 0 };

	/*
	unsigned short	firstleafface;
	unsigned short	numleaffaces;
	
	unsigned short	firstleafbrush;
	unsigned short	numleafbrushes;
	*/
	int firstleafface;
	int numleaffaces;

	int firstleafbrush;
	int numleafbrushes;
}
