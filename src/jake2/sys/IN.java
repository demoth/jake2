/*
 * IN.java
 * Copyright (C) 2003
 * 
 * $Id: IN.java,v 1.6 2004-01-09 09:48:59 hoz Exp $
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
 * IN
 */
public final class IN {
	
	static boolean mouse_active = false;
	
	public static void ActivateMouse() {
//	00488         if (!mouse_avail || !dpy || !win)
//	00489                 return;
//	00490 
		if (!mouse_active) {
//	00492                 mx = my = 0; // don't spazz
//	00493                 install_grabs();
			mouse_active = true;
		}
	}
	
	public static void DeactivateMouse() {
//	00477         if (!mouse_avail || !dpy || !win)
//	00478                 return;
//	00479 
		if (mouse_active) {
//	00481                 uninstall_grabs();
			mouse_active = false;
		}
	}
	
	public static void toggleMouse() {
		if (mouse_active) DeactivateMouse();
		else ActivateMouse();
	}
		
	public static void Init() {
	}
	
	public static void Shutdown() {
	}
}
