/*
 * RW.java
 * Copyright (C) 2004
 * 
 * $Id: RW.java,v 1.1 2004-01-12 21:52:52 hoz Exp $
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
 * RW
 */
public final class RW {

	static void IN_MLookDown() { 
//	00285         mlooking = true; 
	}
 
	static void IN_MLookUp() {
//	00290         mlooking = false;
//	00291         in_state->IN_CenterView_fp ();
	}
 
	static void IN_Init(/*in_state_t in_state_p*/) {
//	00296         int mtype;
//	00297         int i;
//	00298 
//	00299         in_state = in_state_p;
//	00300 
//	00301         // mouse variables
//	00302         m_filter = ri.Cvar_Get ("m_filter", "0", 0);
//	00303     in_mouse = ri.Cvar_Get ("in_mouse", "0", CVAR_ARCHIVE);
//	00304     in_dgamouse = ri.Cvar_Get ("in_dgamouse", "1", CVAR_ARCHIVE);
//	00305         freelook = ri.Cvar_Get( "freelook", "0", 0 );
//	00306         lookstrafe = ri.Cvar_Get ("lookstrafe", "0", 0);
//	00307         sensitivity = ri.Cvar_Get ("sensitivity", "3", 0);
//	00308         m_pitch = ri.Cvar_Get ("m_pitch", "0.022", 0);
//	00309         m_yaw = ri.Cvar_Get ("m_yaw", "0.022", 0);
//	00310         m_forward = ri.Cvar_Get ("m_forward", "1", 0);
//	00311         m_side = ri.Cvar_Get ("m_side", "0.8", 0);
//	00312 
//	00313         ri.Cmd_AddCommand ("+mlook", RW_IN_MLookDown);
//	00314         ri.Cmd_AddCommand ("-mlook", RW_IN_MLookUp);
//	00315 
//	00316         ri.Cmd_AddCommand ("force_centerview", Force_CenterView_f);
//	00317 
//	00318         mouse_avail = true;
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
//	00333         int i;
//	00334    
//	00335         if (!mouse_avail) 
//	00336                 return;
//	00337    
//	00338         for (i=0 ; i<3 ; i++) {
//	00339                 if ( (mouse_buttonstate & (1<<i)) && !(mouse_oldbuttonstate & (1<<i)) )
//	00340                         in_state->Key_Event_fp (K_MOUSE1 + i, true);
//	00341 
//	00342                 if ( !(mouse_buttonstate & (1<<i)) && (mouse_oldbuttonstate & (1<<i)) )
//	00343                         in_state->Key_Event_fp (K_MOUSE1 + i, false);
//	00344         }
//	00345         mouse_oldbuttonstate = mouse_buttonstate;
	}

//	00348 /*
//	00349 ===========
//	00350 IN_Move
//	00351 ===========
//	00352 */
	static void IN_Move(/*usercmd_t *cmd*/) {
//	00355         if (!mouse_avail)
//	00356                 return;
//	00357    
//	00358         if (m_filter->value)
//	00359         {
//	00360                 mx = (mx + old_mouse_x) * 0.5;
//	00361                 my = (my + old_mouse_y) * 0.5;
//	00362         }
//	00363 
//	00364         old_mouse_x = mx;
//	00365         old_mouse_y = my;
//	00366 
//	00367         mx *= sensitivity->value;
//	00368         my *= sensitivity->value;
//	00369 
//	00370 // add mouse X/Y movement to cmd
//	00371         if ( (*in_state->in_strafe_state & 1) || 
//	00372                 (lookstrafe->value && mlooking ))
//	00373                 cmd->sidemove += m_side->value * mx;
//	00374         else
//	00375                 in_state->viewangles[YAW] -= m_yaw->value * mx;
//	00376 
//	00377         if ( (mlooking || freelook->value) && 
//	00378                 !(*in_state->in_strafe_state & 1))
//	00379         {
//	00380                 in_state->viewangles[PITCH] += m_pitch->value * my;
//	00381         }
//	00382         else
//	00383         {
//	00384                 cmd->forwardmove -= m_forward->value * my;
//	00385         }
//	00386         mx = my = 0;
	}	
}
