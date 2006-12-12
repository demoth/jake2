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
package jake2.sys;

import jake2.Globals;
import jake2.client.CL_input;
import jake2.client.Key;
import jake2.game.Cmd;
import jake2.game.usercmd_t;
import jake2.qcommon.Cvar;
import jake2.qcommon.xcommand_t;
import jake2.util.Math3D;

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
		Globals.re.getKeyboardHandler().installGrabs();
		ignorefirst = true;
    }

    private static void uninstall_grabs() {
		Globals.re.getKeyboardHandler().uninstallGrabs();
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
        in_mouse = Cvar.Get("in_mouse", "1", CVAR_ARCHIVE);
        in_joystick = Cvar.Get("in_joystick", "0", CVAR_ARCHIVE);
    }

    public static void Shutdown() {
        mouse_avail = false;
    }

    public static void Real_IN_Init() {
        // mouse variables
        Globals.m_filter = Cvar.Get("m_filter", "0", 0);
        Globals.in_mouse = Cvar.Get("in_mouse", "1", CVAR_ARCHIVE);
        Globals.freelook = Cvar.Get("freelook", "1", 0);
        Globals.lookstrafe = Cvar.Get("lookstrafe", "0", 0);
        Globals.sensitivity = Cvar.Get("sensitivity", "3", 0);
        Globals.m_pitch = Cvar.Get("m_pitch", "0.022", 0);
        Globals.m_yaw = Cvar.Get("m_yaw", "0.022", 0);
        Globals.m_forward = Cvar.Get("m_forward", "1", 0);
        Globals.m_side = Cvar.Get("m_side", "0.8", 0);

        Cmd.AddCommand("+mlook", new xcommand_t() {
            public void execute() {
                MLookDown();
            }
        });
        Cmd.AddCommand("-mlook", new xcommand_t() {
            public void execute() {
                MLookUp();
            }
        });

        Cmd.AddCommand("force_centerview", new xcommand_t() {
            public void execute() {
                Force_CenterView_f();
            }
        });

        Cmd.AddCommand("togglemouse", new xcommand_t() {
            public void execute() {
                toggleMouse();
            }
        });

        IN.mouse_avail = true;
    }

    public static void Commands() {
		int i;
	
		if (!IN.mouse_avail) 
			return;
	
		KBD kbd=Globals.re.getKeyboardHandler();
		for (i=0 ; i<3 ; i++) {
			if ( (IN.mouse_buttonstate & (1<<i)) != 0 && (IN.mouse_oldbuttonstate & (1<<i)) == 0 )
				kbd.Do_Key_Event(Key.K_MOUSE1 + i, true);
	
			if ( (IN.mouse_buttonstate & (1<<i)) == 0 && (IN.mouse_oldbuttonstate & (1<<i)) != 0 )
				kbd.Do_Key_Event(Key.K_MOUSE1 + i, false);
		}
		IN.mouse_oldbuttonstate = IN.mouse_buttonstate;		
    }

    public static void Frame() {

        if (!cl.cinematicpalette_active && (!cl.refresh_prepped || cls.key_dest == key_console
                || cls.key_dest == key_menu))
            DeactivateMouse();
        else
            ActivateMouse();
    }

    public static void CenterView() {
        cl.viewangles[PITCH] = -Math3D
                .SHORT2ANGLE(cl.frame.playerstate.pmove.delta_angles[PITCH]);
    }

    public static void Move(usercmd_t cmd) {
        if (!IN.mouse_avail)
            return;

        if (Globals.m_filter.value != 0.0f) {
            KBD.mx = (KBD.mx + IN.old_mouse_x) / 2;
            KBD.my = (KBD.my + IN.old_mouse_y) / 2;
        }

        IN.old_mouse_x = KBD.mx;
        IN.old_mouse_y = KBD.my;

        KBD.mx = (int) (KBD.mx * Globals.sensitivity.value);
        KBD.my = (int) (KBD.my * Globals.sensitivity.value);

        // add mouse X/Y movement to cmd
        if ((CL_input.in_strafe.state & 1) != 0
                || ((Globals.lookstrafe.value != 0) && IN.mlooking)) {
            cmd.sidemove += Globals.m_side.value * KBD.mx;
        } else {
            Globals.cl.viewangles[YAW] -= Globals.m_yaw.value * KBD.mx;
        }

        if ((IN.mlooking || Globals.freelook.value != 0.0f)
                && (CL_input.in_strafe.state & 1) == 0) {
            Globals.cl.viewangles[PITCH] += Globals.m_pitch.value * KBD.my;
        } else {
            cmd.forwardmove -= Globals.m_forward.value * KBD.my;
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
        Globals.cl.viewangles[PITCH] = 0;
    }

}