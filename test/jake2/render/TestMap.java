/*
 * TestMap.java
 * Copyright (C) 2003
 *
 * $Id: TestMap.java,v 1.1 2004-01-22 01:51:57 cwei Exp $
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
import jake2.client.VID;
import jake2.client.entity_t;
import jake2.client.lightstyle_t;
import jake2.client.refdef_t;
import jake2.client.refexport_t;
import jake2.client.refimport_t;
import jake2.client.viddef_t;
import jake2.game.Cmd;
import jake2.game.cvar_t;
import jake2.qcommon.Cvar;
import jake2.qcommon.FS;
import jake2.qcommon.Qcommon;
import jake2.qcommon.qfiles;
import jake2.qcommon.xcommand_t;
import jake2.sys.KBD;
import jake2.util.Lib;
import jake2.util.Math3D;
import jake2.util.Vargs;

import java.awt.Dimension;
import java.util.Arrays;

/**
 * TestMap
 *  
 * @author cwei
 */
public class TestMap
{

	String[] args;

	refexport_t re;
	refimport_t ri;
	viddef_t viddef;
	int framecount = 0;

	public TestMap(String[] args)
	{
		this.args = args;
	}

	public static void main(String[] args)
	{

		TestMap test = new TestMap(args);
		test.init();
		test.run();
	}

	void init()
	{

		// only for testing
		// a simple refimport_t implementation
		ri = new refimport_t()
		{
			public void Sys_Error(int err_level, String str)
			{
				VID.Error(err_level, str, null);
			}

			public void Sys_Error(int err_level, String str, Vargs vargs)
			{
				VID.Error(err_level, str, vargs);
			}

			public void Cmd_AddCommand(String name, xcommand_t cmd)
			{
				Cmd.AddCommand(name, cmd);
			}

			public void Cmd_RemoveCommand(String name)
			{
				Cmd.RemoveCommand(name);
			}

			public int Cmd_Argc()
			{
				return Cmd.Argc();
			}

			public String Cmd_Argv(int i)
			{
				return Cmd.Argv(i);
			}

			public void Cmd_ExecuteText(int exec_when, String text)
			{
				// TODO implement Cbuf_ExecuteText
			}

			public void Con_Printf(int print_level, String str)
			{
				VID.Printf(print_level, str, null);
			}

			public void Con_Printf(int print_level, String str, Vargs vargs)
			{
				VID.Printf(print_level, str, vargs);
			}

			public byte[] FS_LoadFile(String name)
			{
				return FS.LoadFile(name);
			}

			public int FS_FileLength(String name)
			{
				return FS.FileLength(name);
			}

			public void FS_FreeFile(byte[] buf)
			{
				FS.FreeFile(buf);
			}

			public String FS_Gamedir()
			{
				return FS.Gamedir();
			}

			public cvar_t Cvar_Get(String name, String value, int flags)
			{
				return Cvar.Get(name, value, flags);
			}

			public cvar_t Cvar_Set(String name, String value)
			{
				return Cvar.Set(name, value);
			}

			public void Cvar_SetValue(String name, float value)
			{
				Cvar.SetValue(name, value);
			}

			public boolean Vid_GetModeInfo(Dimension dim, int mode)
			{
				return VID.GetModeInfo(dim, mode);
			}

			public void Vid_MenuInit()
			{
				VID.MenuInit();
			}

			public void Vid_NewWindow(int width, int height)
			{
				VID.NewWindow(width, height);
			}

			public void updateScreenCallback()
			{
				TestMap.this.updateScreen();
			}
		};

		Qcommon.Init(new String[] { "TestMap $Id: TestMap.java,v 1.1 2004-01-22 01:51:57 cwei Exp $" });
		// sehr wichtig !!!
		VID.Shutdown();

		this.re = Renderer.getDriver("jogl", ri);

		re.Init();

		viddef = Globals.viddef;
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

		testMap();

		drawString(10, viddef.height - 16, text);
		
		re.EndFrame();
		framecount++;
	}

	long startTime;

	void run()
	{
		// register the map
		re.BeginRegistration("base1");
		//re.EndRegistration();
		
		startTime = System.currentTimeMillis();
		while (true)
		{
			re.updateScreen();
			KBD.Update();
			try {
				Thread.sleep(5);
			}
			catch (InterruptedException e) {
			}
		}
	}

	//		===================================================================

	private float yaw = 0;

	private float fov_x = 90;
	
	private refdef_t refdef;
	
	private void testMap()
	{

		if ( refdef == null ) {
			refdef = new refdef_t();

			refdef.x = 0;
			refdef.y = 0;
			refdef.width = viddef.width;
			refdef.height = viddef.height;
			refdef.fov_x = fov_x;
			refdef.fov_y = CalcFov(fov_x, refdef.width, refdef.height);
			refdef.vieworg = new float[] {0, 0, 0};
			refdef.viewangles = new float[] {0, 0, 0};

			refdef.blend = new float[] { 1.0f, 0.5f, 0.25f, 0.5f };

			refdef.areabits = null; // draw all
//			refdef.areabits = new byte[Defines.MAX_MAP_AREAS / 8];
//			Arrays.fill(refdef.areabits, (byte) 0xFF);

			refdef.num_entities = 0;
			refdef.entities = null;

			lightstyle_t light = new lightstyle_t();
			light.rgb = new float[] {0, 0, 0.8f};
			light.white = 1.0f;

			refdef.lightstyles = new lightstyle_t[] { light };
		}

		refdef.time = time() * 0.001f;
		
		refdef.viewangles[0] = -10;
		refdef.viewangles[1] = 140 + 20 * (float)Math.sin(time() * 0.001f);

		refdef.vieworg[0] = 120 + 50 * (float)Math.sin(time() * 0.001f);
		refdef.vieworg[1] = -200 + 50 * (float)Math.sin(time() * 0.002f);
		refdef.vieworg[2] = 50;

		re.RenderFrame(refdef);
	}

	private float CalcFov(float fov_x, float width, float height)
	{
		double a;
		double x;

		if (fov_x < 1 || fov_x > 179)
			ri.Sys_Error(Defines.ERR_DROP, "Bad fov: " + fov_x);

		x = width / Math.tan(fov_x / 360 * Math.PI);

		a = Math.atan(height / x);

		a = a * 360 / Math.PI;

		return (float) a;
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
}
