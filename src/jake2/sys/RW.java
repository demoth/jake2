/*
 * RW.java
 * Copyright (C) 2004
 * 
 * $Id: RW.java,v 1.4 2004-02-15 01:19:41 hoz Exp $
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
import jake2.client.CL;
import jake2.client.Key;
import jake2.game.Cmd;
import jake2.game.usercmd_t;
import jake2.qcommon.Cvar;
import jake2.qcommon.xcommand_t;

/**
 * RW
 */
public final class RW extends Globals {
	static int mouse_buttonstate;
	static int mouse_oldbuttonstate;
	static int old_mouse_x;
	static int old_mouse_y;
	static boolean mlooking;
	
	static void IN_MLookDown() { 
		mlooking = true; 
	}
 
	static void IN_MLookUp() {
		mlooking = false;
		IN.CenterView();
	}
 
	static void IN_Init(/*in_state_t in_state_p*/) {
		int mtype;
		int i;

//		in_state = in_state_p;

		// mouse variables
		m_filter = Cvar.Get("m_filter", "0", 0);
		in_mouse = Cvar.Get("in_mouse", "1", CVAR_ARCHIVE);
		freelook = Cvar.Get("freelook", "0", 0 );
		lookstrafe = Cvar.Get("lookstrafe", "0", 0);
		sensitivity = Cvar.Get("sensitivity", "3", 0);
		m_pitch = Cvar.Get("m_pitch", "0.022", 0);
		m_yaw = Cvar.Get("m_yaw", "0.022", 0);
		m_forward = Cvar.Get("m_forward", "1", 0);
		m_side = Cvar.Get("m_side", "0.8", 0);

		Cmd.AddCommand("+mlook", new xcommand_t() {
			public void execute() {IN_MLookDown();}});
		Cmd.AddCommand("-mlook", new xcommand_t() {
			public void execute() {IN_MLookUp();}});
			
		Cmd.AddCommand ("force_centerview", new xcommand_t() {
			public void execute() {Force_CenterView_f();}});

		IN.mouse_avail = true;
	}
	
	static void Force_CenterView_f() {
		cl.viewangles[PITCH] = 0;
	}
	 
	public static void IN_Shutdown() {
		IN.mouse_avail = false;
	}
	 
	static void IN_Frame() {
	}
 
	static void IN_Activate(boolean active) {
		if (active)
			IN.ActivateMouse();
		else
			IN.DeactivateMouse ();
	}
	
	static void IN_Commands() {
		int i;
   
		if (!IN.mouse_avail) 
			return;
 
		for (i=0 ; i<3 ; i++) {
			if ( (mouse_buttonstate & (1<<i)) != 0 && (mouse_oldbuttonstate & (1<<i)) == 0 )
				KBD.Do_Key_Event(Key.K_MOUSE1 + i, true);

			if ( (mouse_buttonstate & (1<<i)) == 0 && (mouse_oldbuttonstate & (1<<i)) != 0 )
				KBD.Do_Key_Event(Key.K_MOUSE1 + i, false);
		}
		mouse_oldbuttonstate = mouse_buttonstate;
	}

	/*
	===========
	IN_Move
	===========
	*/
	static void IN_Move(usercmd_t cmd) {
		if (!IN.mouse_avail)
			return;

		if (m_filter.value != 0.0f) {
			KBD.mx = (KBD.mx + old_mouse_x) / 2;
			KBD.my = (KBD.my + old_mouse_y) / 2;
		}

		old_mouse_x = KBD.mx;
		old_mouse_y = KBD.my;

		KBD.mx = (int)(KBD.mx * sensitivity.value);
		KBD.my = (int)(KBD.my * sensitivity.value);

		// add mouse X/Y movement to cmd
		if ( (CL.in_strafe.state & 1) != 0 || ((lookstrafe.value != 0) && mlooking )) {
			cmd.sidemove += m_side.value * KBD.mx;
		} else {
			cl.viewangles[YAW] -= m_yaw.value * KBD.mx;
		}

		if ( (mlooking || freelook.value != 0.0f) && (CL.in_strafe.state & 1) == 0) {
			cl.viewangles[PITCH] += m_pitch.value * KBD.my;
		} else {
			cmd.forwardmove -= m_forward.value * KBD.my;
		}
		KBD.mx = KBD.my = 0;
	}	
}
