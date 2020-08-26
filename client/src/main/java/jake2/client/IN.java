/*
 * IN.java
 * Copyright (C) 2003
 * 
 * $Id: IN.java,v 1.8 2006-12-12 15:20:30 cawe Exp $
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

import jake2.qcommon.Globals;
import jake2.qcommon.exec.Cmd;
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.usercmd_t;
import jake2.qcommon.util.Math3D;

import java.util.List;

/**
 * IN
 */
public final class IN extends Globals {

    static boolean mouse_avail = true;

    static boolean mouse_active = false;

    static boolean ignorefirst = false;

    static int mouse_buttonstate;

    static int mouse_oldbuttonstate;

    static int old_mouse_x;

    static int old_mouse_y;

    static boolean mlooking;

    public static void ActivateMouse() {
        if (!mouse_avail)
            return;
        if (!mouse_active) {
            KBD.mx = KBD.my = 0; // don't spazz
            install_grabs();
            mouse_active = true;
        }
    }

    public static void DeactivateMouse() {
        // if (!mouse_avail || c == null) return;
        if (mouse_active) {
            uninstall_grabs();
            mouse_active = false;
        }
    }

    private static void install_grabs() {
		ClientGlobals.re.getKeyboardHandler().installGrabs();
		ignorefirst = true;
    }

    private static void uninstall_grabs() {
		ClientGlobals.re.getKeyboardHandler().uninstallGrabs();
    }

    public static void toggleMouse() {
        if (mouse_avail) {
            mouse_avail = false;
            DeactivateMouse();
        } else {
            mouse_avail = true;
            ActivateMouse();
        }
    }

    public static void Init() {
        ClientGlobals.in_mouse = Cvar.getInstance().Get("in_mouse", "1", CVAR_ARCHIVE);
        ClientGlobals.in_joystick = Cvar.getInstance().Get("in_joystick", "0", CVAR_ARCHIVE);
    }

    public static void Shutdown() {
        mouse_avail = false;
    }

    public static void Real_IN_Init() {
        // mouse variables
        ClientGlobals.m_filter = Cvar.getInstance().Get("m_filter", "0", 0);
        ClientGlobals.in_mouse = Cvar.getInstance().Get("in_mouse", "1", CVAR_ARCHIVE);
        ClientGlobals.freelook = Cvar.getInstance().Get("freelook", "1", 0);
        ClientGlobals.lookstrafe = Cvar.getInstance().Get("lookstrafe", "0", 0);
        ClientGlobals.sensitivity = Cvar.getInstance().Get("sensitivity", "3", 0);
        ClientGlobals.m_pitch = Cvar.getInstance().Get("m_pitch", "0.022", 0);
        ClientGlobals.m_yaw = Cvar.getInstance().Get("m_yaw", "0.022", 0);
        ClientGlobals.m_forward = Cvar.getInstance().Get("m_forward", "1", 0);
        ClientGlobals.m_side = Cvar.getInstance().Get("m_side", "0.8", 0);

        Cmd.AddCommand("+mlook", (List<String> args) -> MLookDown());
        Cmd.AddCommand("-mlook", (List<String> args) -> MLookUp());
        Cmd.AddCommand("force_centerview", (List<String> args) -> Force_CenterView_f());
        Cmd.AddCommand("togglemouse", (List<String> args) -> toggleMouse());

        IN.mouse_avail = true;
    }

    public static void Commands() {
		int i;
	
		if (!IN.mouse_avail) 
			return;
	
		KBD kbd= ClientGlobals.re.getKeyboardHandler();
		for (i=0 ; i<3 ; i++) {
			if ( (IN.mouse_buttonstate & (1<<i)) != 0 && (IN.mouse_oldbuttonstate & (1<<i)) == 0 )
				kbd.Do_Key_Event(Key.K_MOUSE1 + i, true);
	
			if ( (IN.mouse_buttonstate & (1<<i)) == 0 && (IN.mouse_oldbuttonstate & (1<<i)) != 0 )
				kbd.Do_Key_Event(Key.K_MOUSE1 + i, false);
		}
		IN.mouse_oldbuttonstate = IN.mouse_buttonstate;		
    }

    public static void Frame() {

        if (!ClientGlobals.cl.cinematicpalette_active && (!ClientGlobals.cl.refresh_prepped || ClientGlobals.cls.key_dest == key_console
                || ClientGlobals.cls.key_dest == key_menu))
            DeactivateMouse();
        else
            ActivateMouse();
    }

    public static void CenterView() {
        ClientGlobals.cl.viewangles[PITCH] = -Math3D
                .SHORT2ANGLE(ClientGlobals.cl.frame.playerstate.pmove.delta_angles[PITCH]);
    }

    public static void Move(usercmd_t cmd) {
        if (!IN.mouse_avail)
            return;

        if (ClientGlobals.m_filter.value != 0.0f) {
            KBD.mx = (KBD.mx + IN.old_mouse_x) / 2;
            KBD.my = (KBD.my + IN.old_mouse_y) / 2;
        }

        IN.old_mouse_x = KBD.mx;
        IN.old_mouse_y = KBD.my;

        KBD.mx = (int) (KBD.mx * ClientGlobals.sensitivity.value);
        KBD.my = (int) (KBD.my * ClientGlobals.sensitivity.value);

        // add mouse X/Y movement to cmd
        if ((CL_input.in_strafe.state & 1) != 0
                || ((ClientGlobals.lookstrafe.value != 0) && IN.mlooking)) {
            cmd.sidemove += ClientGlobals.m_side.value * KBD.mx;
        } else {
            ClientGlobals.cl.viewangles[YAW] -= ClientGlobals.m_yaw.value * KBD.mx;
        }

        if ((IN.mlooking || ClientGlobals.freelook.value != 0.0f)
                && (CL_input.in_strafe.state & 1) == 0) {
            ClientGlobals.cl.viewangles[PITCH] += ClientGlobals.m_pitch.value * KBD.my;
        } else {
            cmd.forwardmove -= ClientGlobals.m_forward.value * KBD.my;
        }
        KBD.mx = KBD.my = 0;
    }

    static void MLookDown() {
        mlooking = true;
    }

    static void MLookUp() {
        mlooking = false;
        CenterView();
    }

    static void Force_CenterView_f() {
        ClientGlobals.cl.viewangles[PITCH] = 0;
    }

}