/*
 * KBD.java
 * Copyright (C) 2004
 * 
 * $Id: KBD.java,v 1.7 2004-12-16 22:45:55 hzi Exp $
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
package jake2.sys;


/**
 * KBD
 */
abstract public class KBD {
	
	static int win_x = 0;
	static int win_y = 0;
		
	// motion values
	public static int mx = 0;
	public static int my = 0;
	
	abstract public void Init();

	abstract public void Update();

	abstract public void Close();
	abstract public void Do_Key_Event(int key, boolean down);

	abstract public void installGrabs();
	abstract public void uninstallGrabs();
	//abstract public void centerMouse();
}

