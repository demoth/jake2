/*
 * TestRenderer.java
 * Copyright (C) 2003
 *
 * $Id: TestRenderer.java,v 1.1 2003-11-25 14:57:20 cwei Exp $
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

import java.awt.Dimension;
import java.util.Arrays;

import jake2.client.*;
import jake2.game.cvar_t;
import jake2.qcommon.*;
import jake2.util.Vargs;

/**
 * TestRenderer
 * 
 * @author cwei
 */
public class TestRenderer {

	public static void main(String[] args) {
		// only for testing
		// a simple refimport_t implementation
		refimport_t rimp = new refimport_t() {
			public void Sys_Error(int err_level, String str, Vargs vargs) {
				Com.Printf(str, vargs);
			}

			public void Cmd_AddCommand(String name, xcommand_t cmd) {
			}

			public void Cmd_RemoveCommand(String name) {
			}

			public int Cmd_Argc() {
				return 0;
			}

			public String Cmd_Argv(int i) {
				return null;
			}

			public void Cmd_ExecuteText(int exec_when, String text) {
			}

			public void Con_Printf(int print_level, String str, Vargs vargs) {
				Com.Printf(str, vargs);
			}

			public int FS_LoadFile(String name, byte[] buf) {
				return 0;
			}

			public void FS_FreeFile(byte[] buf) {
			}

			public String FS_Gamedir() {
				return "../../unpack2";
			}

			public cvar_t Cvar_Get(String name, String value, int flags) {
				return null;
			}

			public cvar_t Cvar_Set(String name, String value) {
				return null;
			}

			public void Cvar_SetValue(String name, float value) {
			}

			public boolean Vid_GetModeInfo(Dimension dim, int mode) {
				return VID.GetModeInfo(dim, mode);
			}

			public void Vid_MenuInit() {
				VID.MenuInit();
			}

			public void Vid_NewWindow(int width, int height) {
				VID.NewWindow(width, height);
			}
		};

		try {
			//Class.forName("jake2.render.JoglRenderer");
			String[] names = Renderer.getDriverNames();
			System.out.println("Registered Drivers: " + Arrays.asList(names));

			refexport_t re = Renderer.getDriver("jogl", rimp);

			System.out.println("Use driver: " + re);
			
			re.Init();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
