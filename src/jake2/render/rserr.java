/*
 * rserr.java
 * Copyright (C) 2003
 *
 * $Id: rserr.java,v 1.1 2003-11-24 18:25:11 cwei Exp $
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
package jake2.render;

/**
 * rserr
 * Definition at line 110 of file gl_local.h.
 * Referenced by R_SetMode(), and SWimp_SetMode().
 * 
 * @author cwei
 */
public interface rserr {
	static final int ok = 0;
	static final int invalid_fullscreen = 1;
	static final int invalid_mode = 2;
	static final int unknown = 3;
}
