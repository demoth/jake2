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

/*
 * $Id: BigEndianHandler.java,v 1.5 2004-02-29 00:51:04 rst Exp $
 */
package jake2.game;


/**
 * BigEndianHandler
 */
public final class BigEndianHandler extends EndianHandler {

	/* (non-Javadoc)
	 * @see quake2.EndianHandler#BigFloat(float)
	 */
	public float BigFloat(float f) {
		return f;
	}

	/* (non-Javadoc)
	 * @see quake2.EndianHandler#BigShort(short)
	 */
	public short BigShort(short s) {
		return s;
	}

	/* (non-Javadoc)
	 * @see quake2.EndianHandler#BigLong(int)
	 */
	public int BigLong(int i) {
		return i;
	}

	/* (non-Javadoc)
	 * @see quake2.EndianHandler#LittleFloat(float)
	 */
	public float LittleFloat(float f) {
		return swapFloat(f);
	}

	/* (non-Javadoc)
	 * @see quake2.EndianHandler#LittleShort(short)
	 */
	public short LittleShort(short s) {
		return swapShort(s);
	}

	/* (non-Javadoc)
	 * @see quake2.EndianHandler#LittleLong(int)
	 */
	public int LittleLong(int i) {
		return swapInt(i);
	}

}
