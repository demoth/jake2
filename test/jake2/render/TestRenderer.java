/*
 * TestRenderer.java
 * Copyright (C) 2003
 *
 * $Id: TestRenderer.java,v 1.3 2003-12-29 01:58:41 cwei Exp $
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
import jake2.game.Cmd;
import jake2.game.cvar_t;
import jake2.qcommon.*;
import jake2.util.Vargs;

/**
 * TestRenderer
 * 
 * @author cwei
 */
public class TestRenderer {

	String[] args;

	refexport_t re;

	public TestRenderer(String[] args) {
		this.args = args;
	}

	public static void main(String[] args) {

		TestRenderer test = new TestRenderer(args);
		test.init();
		test.run();
	}

	void init() {

		// only for testing
		// a simple refimport_t implementation
		refimport_t rimp = new refimport_t() {
			public void Sys_Error(int err_level, String str) {
				VID.Error(err_level, str, null);
			}

			public void Sys_Error(int err_level, String str, Vargs vargs) {
				VID.Error(err_level, str, vargs);
			}

			public void Cmd_AddCommand(String name, xcommand_t cmd) {
				Cmd.AddCommand(name, cmd);
			}

			public void Cmd_RemoveCommand(String name) {
				// TODO implement Cmd_RemoveCommand(String name)
			}

			public int Cmd_Argc() {
				return Cmd.Argc();
			}

			public String Cmd_Argv(int i) {
				return Cmd.Argv(i);
			}

			public void Cmd_ExecuteText(int exec_when, String text) {
				// TODO implement Cbuf_ExecuteText
			}

			public void Con_Printf(int print_level, String str) {
				VID.Printf(print_level, str, null);
			}

			public void Con_Printf(int print_level, String str, Vargs vargs) {
				VID.Printf(print_level, str, vargs);
			}

			public byte[] FS_LoadFile(String name) {
				return FS.LoadFile(name);
			}

			public int FS_FileLength(String name) {
				return FS.FileLength(name);
			}
			
			public void FS_FreeFile(byte[] buf) {
				FS.FreeFile(buf);
			}

			public String FS_Gamedir() {
				return FS.Gamedir();
			}

			public cvar_t Cvar_Get(String name, String value, int flags) {
				return Cvar.Get(name, value, flags);
			}

			public cvar_t Cvar_Set(String name, String value) {
				return Cvar.Set(name, value);
			}

			public void Cvar_SetValue(String name, float value) {
				Cvar.SetValue(name, value);
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

			public void updateScreenCallback() {
				TestRenderer.this.updateScreen();
			}
		};

		//Class.forName("jake2.render.JoglRenderer");
		String[] names = Renderer.getDriverNames();
		System.out.println("Registered Drivers: " + Arrays.asList(names));

		this.re = Renderer.getDriver("jogl", rimp);

		System.out.println("Use driver: " + re);
		System.out.println();
				
		Qcommon.Init(new String[] {"TestRenderer"});

		re.Init();
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
		}
	}

	void updateScreen() {
		re.BeginFrame(0.0f);
		re.DrawPic(
			(int) (Math.random() * VID.viddef.width),
			(int) (Math.random() * VID.viddef.height),
			"conback");
		re.EndFrame();
	}

	void run() {
		while (true) {
			re.updateScreen();
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
			}
		}
	}

}
