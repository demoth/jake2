/*
 * Cvar.java
 * Copyright (C) 2003
 * 
 * $Id: Cvar.java,v 1.2 2003-11-24 19:06:30 cwei Exp $
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

import jake2.game.cvar_t;

/**
 * Cvar implements console variables. The original code is
 * located in cvar.c
 * TODO complete Cvar interface 
 */
public final class Cvar {
	
	public static final int ARCHIVE = 1;	// set to cause it to be saved to vars.rc
	static final int USERINFO = 2;	// added to userinfo  when changed
	static final int SERVERINFO = 4;// added to serverinfo when changed
	static final int NOSET = 8;		// don't allow change from console at all,
									// but can be set from the command line
	static final int LATCH = 16;	// save changes until server restart
	

	/**
	 * @param var_name
	 * @param var_value
	 * @param flags
	 * @return
	 * TODO implement Cvar.Get()
	 */
	public static cvar_t Get(String var_name, String var_value, int flags) {
		return new cvar_t();
	}
		
	/**
	 * 
	 */
	static void Init() {
	}
}
