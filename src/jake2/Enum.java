/*
 * Enum.java
 * Copyright (C) 2003
 *
 * $Id: Enum.java,v 1.1 2003-12-27 21:53:45 cwei Exp $
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
package jake2;

/**
 * Enum
 * 
 * @author cwei
 */
public interface Enum {
	
	// imagetype_t 
	static final int it_skin = 0;
	static final int it_sprite = 1;
	static final int it_wall = 2;
	static final int it_pic = 3;
	static final int it_sky = 4;

	// and so on
}
