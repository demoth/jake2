/*
 * DancingQueens.java
 * Copyright (C) 2003
 *
 * $Id: DancingQueens.java,v 1.9 2004-02-11 17:29:52 cwei Exp $
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

import jake2.Defines;
import jake2.Globals;
import jake2.client.*;
import jake2.game.Cmd;
import jake2.game.cvar_t;
import jake2.qcommon.*;
import jake2.sys.IN;
import jake2.sys.KBD;
import jake2.util.Math3D;
import jake2.util.Vargs;

import java.awt.Dimension;
import java.util.Arrays;


/**
 * DancingQueens
 *  
 * @author cwei
 */
public class DancingQueens
{
	String[] args;

	refexport_t re;
	refimport_t ri;
	viddef_t viddef;
	int framecount = 0;

	public DancingQueens(String[] args) {
		this.args = args;
	}

	public static void main(String[] args) {

		DancingQueens test = new DancingQueens(args);
		test.init();
		test.run();
	}

	void init() {

		// only for testing
		// a simple refimport_t implementation
		ri = new refimport_t() {
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
				Cmd.RemoveCommand(name);
			}

			public int Cmd_Argc() {
				return Cmd.Argc();
			}

			public String Cmd_Argv(int i) {
				return Cmd.Argv(i);
			}

			public void Cmd_ExecuteText(int exec_when, String text) {
				Cbuf.ExecuteText(exec_when, text);
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
				DancingQueens.this.updateScreen();
			}
		};


		Qcommon.InitForTestMap(new String[] {"DancingQueens"});
		// sehr wichtig !!!
		VID.Shutdown();

		String[] names = Renderer.getDriverNames();
		System.out.println("Registered Drivers: " + Arrays.asList(names));

		this.re = Renderer.getDriver("jogl", ri);

		System.out.println("Use driver: " + re);
		System.out.println();
		
		re.Init();
		
		Cmd.AddCommand("togglemouse", togglemouse);
		Cbuf.AddText("bind t togglemouse");
		Cbuf.Execute();
		Globals.cls.key_dest = Defines.key_game;
		Globals.cls.state = Defines.ca_active;
						
		viddef = Globals.viddef;
		fov_y = Math3D.CalcFov(fov_x, viddef.width, viddef.height);
	}

	float fps = 0.0f;
	long start = 0;

	void updateScreen()
	{
		re.BeginFrame(0.0f);

		if (framecount % 500 == 0)
		{
			long time = System.currentTimeMillis();
			fps = 500000.0f / (time - start);
			start = time;
		}
		String text = fps + " fps";

		testModel();

		drawString(10, viddef.height - 16, text);

		re.EndFrame();
		framecount++;
	}

	long startTime;

	void run()
	{
		startTime = System.currentTimeMillis();
		while (true)
		{
			re.updateScreen(null);
			KBD.Update();
			Cbuf.Execute();
		}
	}
	
//	===================================================================

	private float yaw = 0;
	private entity_t[] models;
	
	private final static String[] skinNames = {
		"players/female/athena",
		"players/female/lotus",
		"players/female/venus",
		"players/female/voodoo",
		"players/female/cobalt",
		"players/female/lotus",
		"players/female/brianna"
	};
		
	private float fov_x = 50;
	private float fov_y;

	private void testModel() {

		refdef_t refdef = new refdef_t();

		refdef.x = 0;
		refdef.y = 0;
		refdef.width = viddef.width;
		refdef.height = viddef.height;
		refdef.fov_x = fov_x;
		refdef.fov_y = fov_y;
		refdef.time = 1.0f * 0.001f;

		if (models == null) {
			models = new entity_t[12]; // model count
			entity_t m = null;
			for (int i = 0; i < models.length; i++)
			{
				m = getModel(skinNames[i % skinNames.length]);
				m.origin[0] += 30 * i;
				m.origin[1] += ((i % 4)) * 30 - 20;
				models[i] = m;
			}
		}


		yaw = time() * 0.1f;
		if (yaw > 360)
			yaw -= 360;
		if (yaw < 0)
			yaw += 360;
			
		for (int i = 0; i < models.length; i++)
		{
			models[i].frame = (time() / 70) % models[i].model.numframes;
			models[i].angles[1] = yaw;
			models[i].origin[0] += KBD.my;
			models[i].origin[1] += KBD.mx;
			
		}

		refdef.areabits = null;
		refdef.num_entities = models.length;
		refdef.entities = models;

		refdef.lightstyles = null;
		refdef.rdflags = Defines.RDF_NOWORLDMODEL;

		re.RenderFrame(refdef);
	}
	
	private entity_t getModel(String name) {
		entity_t entity = new entity_t();
		String modelName = "players/female/tris.md2";
		String modelSkin = name +".pcx";

		entity.model = re.RegisterModel(modelName);
		entity.skin = re.RegisterSkin(modelSkin);
		entity.flags = Defines.RF_FULLBRIGHT;
		entity.origin[0] = 80;
		entity.origin[1] = 0;
		entity.origin[2] = 0;
		Math3D.VectorCopy(entity.origin, entity.oldorigin);
		entity.frame = 0;
		entity.oldframe = 0;
		entity.backlerp = 0.0f;
		return entity;
	}
	
	
	private void drawString(int x, int y, String text)
	{
		for (int i = 0; i < text.length(); i++)
		{
			re.DrawChar(x + 8 * i, y, (int) text.charAt(i));
		}
	}

	private final int time()
	{
		return (int) (System.currentTimeMillis() - startTime);
	}
	
	static xcommand_t togglemouse = new xcommand_t() {
		public void execute() {
			IN.toggleMouse();
		}
	};
}