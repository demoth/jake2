/*
 * TestRenderer.java
 * Copyright (C) 2003
 *
 * $Id: TestRenderer.java,v 1.15 2004-01-09 00:44:43 cwei Exp $
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

import jake2.Defines;
import jake2.Globals;
import jake2.client.*;
import jake2.game.Cmd;
import jake2.game.cvar_t;
import jake2.qcommon.*;
import jake2.util.Math3D;
import jake2.util.Vargs;

/**
 * TestRenderer
 * 
 * @author cwei
 */
public class TestRenderer {

	String[] args;

	refexport_t re;
	refimport_t ri;
	viddef_t viddef;
	int framecount = 0;

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

		this.re = Renderer.getDriver("jogl", ri);

		System.out.println("Use driver: " + re);
		System.out.println();
				
		Qcommon.Init(new String[] {"TestRenderer"});

		re.Init();
	}

	void updateScreen() {
		re.BeginFrame(0.0f);
		viddef = Globals.viddef;
		re.DrawStretchPic(0,0,viddef.width, viddef.height, "conback");
		
		String text = "Hallo Jake2 :-)";
		
		for (int i = 0; i < text.length(); i++) {
			re.DrawChar(10 + 8 * i, viddef.height/2, (int)text.charAt(i));
		}
		
		Dimension wal = new Dimension();
		re.DrawGetPicSize(wal, "/textures/e1u1/basemap.wal");

//		re.DrawPic(0, viddef.height - wal.height, "/textures/e1u1/basemap.wal");
//		re.DrawPic(0, 0, "/sprites/s_explo2_" + (framecount / 2  % 13) + ".pcx");
		
		
		switch ((framecount / 500) % 1) {
			case 0 :
				testModel();
				break;
			case 1 :
				testBeam();
				break;
			case 2 :
				testSprites();
				break;
		}
		re.EndFrame();
		framecount++;
	}


	void run() {
		while (true) {
			re.updateScreen();
			try {
				Thread.sleep(15);
			} catch (InterruptedException e) {
			}
		}
	}
	
//	===================================================================

	private int yaw = 0;

	private void testModel() {

		refdef_t refdef = new refdef_t();

		refdef.x = viddef.width/ 2;
		refdef.y = viddef.height / 2 - 72;
		refdef.width = 144 * 2;
		refdef.height = 168 * 2;
		refdef.fov_x = 40;
		refdef.fov_y = CalcFov(refdef.fov_x, refdef.width, refdef.height);
		refdef.time = 1.0f * 0.001f;

		int maxframe = 29;
		entity_t entity = new entity_t();
		String modelName = "players/female/tris.md2";

		String modelSkin = "players/female/athena.pcx";

		String modelImage = "/players/female/athena_i.pcx";
		String modelImage1 = "/players/female/brianna_i.pcx";
		String modelImage2 = "/players/female/cobalt_i.pcx";
		String modelImage3 = "/players/female/lotus_i.pcx";

		entity.model = re.RegisterModel(modelName);

		drawString(refdef.x, refdef.y - 20, (entity.model != null) ? modelName : "DEBUG: NullModel");

		entity.skin = re.RegisterSkin(modelSkin);
		entity.flags = Defines.RF_FULLBRIGHT;
		entity.origin[0] = 80;
		entity.origin[1] = 0;
		entity.origin[2] = 0;
		Math3D.VectorCopy(entity.origin, entity.oldorigin);
		entity.frame = (framecount / 3) % ((qfiles.dmdl_t)entity.model.extradata).num_frames;
		entity.oldframe = 0;
		entity.backlerp = 0.0f;
		entity.angles[1] = yaw++;
		if (++yaw > 360)
			yaw -= 360;

		refdef.areabits = null;
		refdef.num_entities = 1;
		refdef.entities = new entity_t[] { entity };
		refdef.lightstyles = null;
		refdef.rdflags = Defines.RDF_NOWORLDMODEL;

		//			 Menu_Draw(& s_player_config_menu);

		M_DrawTextBox(
			(int) ((refdef.x) * (320.0F / viddef.width) - 8),
			(int) ((viddef.height / 2) * (240.0F / viddef.height) - 77),
			refdef.width / 8,
			refdef.height / 8);
		refdef.height += 4;

		re.RenderFrame(refdef);

		re.DrawPic(refdef.x - 80, refdef.y, modelImage);
		re.DrawPic(refdef.x - 80, refdef.y + 47, modelImage1);
		re.DrawPic(refdef.x - 80, refdef.y + 94, modelImage2);
		re.DrawPic(refdef.x - 80, refdef.y + 141, modelImage3);
	}
	
	
	private String[] sprites = {
		"sprites/s_bfg1.sp2",
		"sprites/s_bfg2.sp2",
		"sprites/s_bfg3.sp2",
		"sprites/s_explod.sp2",
		"sprites/s_explo2.sp2",
		"sprites/s_explo3.sp2",
		"sprites/s_flash.sp2",
		"sprites/s_bubble.sp2",
	};
	
	private int spriteCount = 0;
	private boolean loading = true;

	private void testSprites() {
		
		if (loading) {
			
			re.DrawPic(viddef.width / 2 - 50, viddef.height / 2, "loading");
			String name = sprites[spriteCount];
			
			drawString(viddef.width / 2 - 50, viddef.height / 2 + 50, name);
			
			re.RegisterModel(name);
			loading = ++spriteCount < sprites.length;
			return;
		}
		

		refdef_t refdef = new refdef_t();

		refdef.x = viddef.width/ 2;
		refdef.y = viddef.height / 2 - 72;
		refdef.width = 144 * 2;
		refdef.height = 168 * 2;
		refdef.fov_x = 40;
		refdef.fov_y = CalcFov(refdef.fov_x, refdef.width, refdef.height);
		refdef.time = 1.0f * 0.001f;

		int maxframe = 29;
		entity_t entity = new entity_t();

		String modelName = sprites[(framecount / 30) % sprites.length];
		drawString(refdef.x, refdef.y - 20, modelName);

		entity.model = re.RegisterModel(modelName);

		entity.flags = Defines.RF_FULLBRIGHT;
		entity.origin[0] = 80 - (framecount % 200) + 200;
		entity.origin[1] = 0 + (float)(40 * Math.sin(Math.toRadians(framecount)));
		entity.origin[2] = 0 + 20;
		Math3D.VectorCopy(entity.origin, entity.oldorigin);
		entity.frame = framecount / 2;
		entity.oldframe = 0;
		entity.backlerp = 0.0f;

		refdef.areabits = null;
		refdef.num_entities = 1;
		refdef.entities = new entity_t[] { entity };
		refdef.lightstyles = null;
		refdef.rdflags = Defines.RDF_NOWORLDMODEL;

		M_DrawTextBox(
			(int) ((refdef.x) * (320.0F / viddef.width) - 8),
			(int) ((viddef.height / 2) * (240.0F / viddef.height) - 77),
			refdef.width / 8,
			refdef.height / 8);
		refdef.height += 4;

		re.RenderFrame(refdef);
		
	}
	
	private void testBeam() {

		refdef_t refdef = new refdef_t();

		refdef.x = viddef.width/ 2;
		refdef.y = viddef.height / 2 - 72;
		refdef.width = 144 * 2;
		refdef.height = 168 * 2;
		refdef.fov_x = 40;
		refdef.fov_y = CalcFov(refdef.fov_x, refdef.width, refdef.height);
		refdef.time = 1.0f * 0.001f;

		int maxframe = 29;
		entity_t entity = new entity_t();

		drawString(refdef.x, refdef.y - 20, "Beam Test");

		entity.flags = Defines.RF_BEAM;
		entity.origin[0] = 200;
		entity.origin[1] = 0 + (float)(80 * Math.sin(4 * Math.toRadians(framecount)));
		entity.origin[2] = 20 + (float)(40 * Math.cos(4 * Math.toRadians(framecount)));
		
		entity.oldorigin[0] = 20;
		entity.oldorigin[1] = 0; // + (float)(40 * Math.sin(Math.toRadians(framecount)));
		entity.oldorigin[2] = -20; // + 20;
		
		entity.frame = 3;
		entity.oldframe = 0;
		entity.backlerp = 0.0f;
		// the four beam colors are encoded in 32 bits of skinnum (hack)
		entity.alpha = 0.6f;
		
		int[] color = { 0xd0, 0xd1, 0xe0, 0xb0 };
		
		entity.skinnum = color[framecount / 2 % 4];
		entity.model = null;

		refdef.areabits = null;
		refdef.num_entities = 1;
		refdef.entities = new entity_t[] { entity };
		refdef.lightstyles = null;
		refdef.rdflags = Defines.RDF_NOWORLDMODEL;

		M_DrawTextBox(
			(int) ((refdef.x) * (320.0F / viddef.width) - 8),
			(int) ((viddef.height / 2) * (240.0F / viddef.height) - 77),
			refdef.width / 8,
			refdef.height / 8);
		refdef.height += 4;

		re.RenderFrame(refdef);
	}
	
	private float CalcFov(float fov_x, float width, float height) {
		double a;
		double x;

		if (fov_x < 1 || fov_x > 179)
			ri.Sys_Error(Defines.ERR_DROP, "Bad fov: " + fov_x);

		x = width / Math.tan(fov_x / 360 * Math.PI);

		a = Math.atan(height / x);

		a = a * 360 / Math.PI;

		return (float) a; //new Double(a).floatValue();
	}

	private void drawString(int x, int y, String text) {
		for (int i = 0; i < text.length(); i++) {
			re.DrawChar(x + 8 * i, y, (int)text.charAt(i));
		}
	}


	private void M_DrawTextBox(int x, int y, int width, int lines) {
		int cx, cy;
		int n;

		// draw left side
		cx = x;
		cy = y;
		M_DrawCharacter(cx, cy, 1);
		for (n = 0; n < lines; n++) {
			cy += 8;
			M_DrawCharacter(cx, cy, 4);
		}
		M_DrawCharacter(cx, cy + 8, 7);

		// draw middle
		cx += 8;
		while (width > 0) {
			cy = y;
			M_DrawCharacter(cx, cy, 2);
			for (n = 0; n < lines; n++) {
				cy += 8;
				M_DrawCharacter(cx, cy, 5);
			}
			M_DrawCharacter(cx, cy + 8, 8);
			width -= 1;
			cx += 8;
		}

		// draw right side
		cy = y;
		M_DrawCharacter(cx, cy, 3);
		for (n = 0; n < lines; n++) {
			cy += 8;
			M_DrawCharacter(cx, cy, 6);
		}
		M_DrawCharacter(cx, cy + 8, 9);
	}

	/*
	================
	M_DrawCharacter
	
	Draws one solid graphics character
	cx and cy are in 320*240 coordinates, and will be centered on
	higher res screens.
	================
	*/
	private void M_DrawCharacter(int cx, int cy, int num) {
		re.DrawChar(
			cx + ((viddef.width - 320) >> 1),
			cy + ((viddef.height - 240) >> 1),
			num);
	}


}
