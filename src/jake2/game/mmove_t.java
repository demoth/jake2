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

// Created on 11.11.2003 by RST.
// $Id: mmove_t.java,v 1.4 2004-02-14 13:24:02 rst Exp $

package jake2.game;

public class mmove_t {
	public mmove_t(int firstframe, int lastframe, mframe_t frame[], EntThinkAdapter endfunc) {
		this.firstframe= firstframe;
		this.lastframe= lastframe;
		this.frame= frame;
		this.endfunc= endfunc;
	}

	public int firstframe;
	public int lastframe;
	public mframe_t frame[]; //ptr
	public EntThinkAdapter endfunc;
}
