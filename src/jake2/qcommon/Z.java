/*
 * Z.java
 * Copyright (C) 2003
 * 
 * $Id: Z.java,v 1.8 2004-01-18 10:39:34 rst Exp $
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


/**
 * Z implements special memory management.
 */
public final class Z {

	public static xcommand_t Stats_f = new xcommand_t() {
		public void execute() {
			Com.Printf("abstract xccommand_t not overloaded.");
		}
	};
	
	static final zhead_t chain = new zhead_t();
	static final short MAGIC = 0x1d1d;
	
	static int count = 0;
	static int bytes = 0;
	
	public static byte[] Malloc(int size) {
		return TagMalloc(size, 0); 
	}
	
	static byte [] TagMalloc(int size, int tag) {
		return new byte[size];
	}
	
	public static void Free(byte[] ptr) {
	}

	public static void Free(ByteBuffer buffer) {
		buffer.clear();
		buffer.limit(0);
	}
}
