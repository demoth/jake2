/*
 * Z.java
 * Copyright (C) 2003
 * 
 * $Id: Z.java,v 1.3 2003-11-26 17:02:45 cwei Exp $
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


/**
 * Z implements special memory management.
 */
public final class Z {

	public static xcommand_t Stats_f = new xcommand_t() {
		public void execute() {
//			TODO Auto-generated method stub
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
/*		zhead_t z;
		try {
			z = new zhead_t();
			z.buf = new byte[size]
		} catch (Throwable e) {
			Com.Error(Globals.ERR_FATAL, "Z_Malloc: failed on allocation of " + size + "bytes");
		}
		count++;
		bytes+=size;
		z.magic = MAGIC;
		z.tag = tag;
		z.size = size;

		01179 
		01180         z->next = z_chain.next;
		01181         z->prev = &z_chain;
		01182         z_chain.next->prev = z;
		01183         z_chain.next = z;
		01184 
		01185         return (void *) (z + 1);*/
		return new byte[size];
	}
	
	/**
	 * @param ptr
	 */
	public static void Free(byte[] ptr) {
	}
}
