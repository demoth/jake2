/*
 * console_t.java
 * Copyright (C) 2003
 * 
 * $Id: console_t.java,v 1.1 2004-07-07 19:58:52 hzi Exp $
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
package jake2.client;

import jake2.Defines;

/**
 * console_t
 */
public final class console_t {
	boolean initialized;
	byte[] text = new byte[Defines.CON_TEXTSIZE];
	int current;	// line where next message will be printed
	int x;			// offset in current line for next print
	int display;	// bottom of console displays this line

	int ormask;		// high bit mask for colored characters

	int linewidth;	// characters across screen
	int totallines;	// total lines in console scrollback

	float cursorspeed;

	int vislines;

	float[] times = new float[Defines.NUM_CON_TIMES];	// cls.realtime time the line was generated
										// for transparent notify lines	
}
