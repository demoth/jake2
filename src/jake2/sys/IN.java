/*
 * IN.java
 * Copyright (C) 2003
 * 
 * $Id: IN.java,v 1.17 2004-02-02 21:19:39 hoz Exp $
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

import jake2.Globals;
import jake2.game.usercmd_t;
import jake2.qcommon.Cvar;

import java.awt.*;

import javax.swing.ImageIcon;

/**
 * IN
 */
public final class IN extends Globals {

	static Component c = null;
	static Cursor emptyCursor = null;

	static boolean mouse_avail = true;
	static boolean mouse_active = false;
	static boolean ignorefirst = false;
	
	static float[] viewangles = cl.viewangles;

	public static void ActivateMouse() {
		//		if (!mouse_avail || c == null) return;
		if (!mouse_active) {
			KBD.mx = KBD.my = 0; // don't spazz
			install_grabs();
			mouse_active = true;
		}
	}

	public static void DeactivateMouse() {
		//		if (!mouse_avail || c == null) return;
		if (mouse_active) {
			uninstall_grabs();
			mouse_active = false;
		}
	}

	private static void install_grabs() {
		if (emptyCursor == null) {
			ImageIcon emptyIcon = new ImageIcon(new byte[0]);
			emptyCursor = c.getToolkit().createCustomCursor(emptyIcon.getImage(), new Point(0, 0), "emptyCursor");
		}
		c.setCursor(emptyCursor);
		KBD.centerMouse();

		ignorefirst = true;
	}

	private static void uninstall_grabs() {
		c.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
	}

	public static void toggleMouse() {
		if (mouse_active)
			DeactivateMouse();
		else
			ActivateMouse();
	}

	public static void Init() {
		in_mouse = Cvar.Get("in_mouse", "1", CVAR_ARCHIVE);
		in_joystick = Cvar.Get("in_joystick", "0", CVAR_ARCHIVE);
	}

	public static void Shutdown() {
		RW.IN_Shutdown();
	}

	public static void Real_IN_Init() {
		RW.IN_Init();
	}

	public static void Commands() {
		RW.IN_Commands();
	}

	public static void Frame() {

		if ( !cl.refresh_prepped || cls.key_dest == key_console || cls.key_dest == key_menu)
			RW.IN_Activate(false);
		else
			RW.IN_Activate(true);

		RW.IN_Frame();
	}

	public static void Activate(boolean active) {
	}

	public static void CenterView() {
		cl.viewangles[PITCH] = -SHORT2ANGLE(cl.frame.playerstate.pmove.delta_angles[PITCH]);
	}
	
	public static void Move(usercmd_t cmd) {
		RW.IN_Move(cmd);
	}

}
