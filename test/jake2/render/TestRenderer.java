/*
 * TestRenderer.java
 * Copyright (C) 2003
 *
 * $Id: TestRenderer.java,v 1.6 2004-01-03 20:24:48 cwei Exp $
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
		re.DrawStretchPic(0,0,VID.viddef.width, VID.viddef.height, "conback");
		
		String text = "Hallo Jake2 :-)";
		
		for (int i = 0; i < text.length(); i++) {
			re.DrawChar(10 + 8 * i, VID.viddef.height/2, (int)text.charAt(i));
		}
		
//		re.DrawPic(
//			(int) (Math.random() * VID.viddef.width / 2),
//			(int) (Math.random() * VID.viddef.height / 2),
//			"loading");
		
		testWorld();
		
		re.EndFrame();
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

	 private void testWorld() {
	 	
	 	viddef_t vid = VID.viddef;

		 refdef_t refdef = new refdef_t();

		 refdef.x = vid.width / 2;
		 refdef.y = vid.height / 2 - 72;
		 refdef.width = 144;
		 refdef.height = 168;
		 refdef.fov_x = 40;
		 refdef.fov_y = CalcFov(refdef.fov_x, refdef.width, refdef.height);
		 refdef.time = 1.0f * 0.001f;

		 if (true /* s_pmi[s_player_model_box.curvalue].skindisplaynames */) {
			int maxframe = 29;
			entity_t entity = new entity_t();
			String modelName = "players/female/tris.md2";
			String modelSkin = "players/female/athena.pcx";
			String modelImage = "/players/female/athena_i.pcx";

//			 Com_sprintf(
//				 scratch,
//				 sizeof(scratch),
//				 "players/%s/tris.md2",
//				 s_pmi[s_player_model_box.curvalue].directory);
			 entity.model = re.RegisterModel(modelName);
//			 Com_sprintf(
//				 scratch,
//				 sizeof(scratch),
//				 "players/%s/%s.pcx",
//				 s_pmi[s_player_model_box.curvalue].directory,
//				 s_pmi[s_player_model_box
//					 .curvalue]
//					 .skindisplaynames[s_player_skin_box
//					 .curvalue]);

			 entity.skin = re.RegisterSkin(modelSkin);
			 entity.flags = Defines.RF_FULLBRIGHT;
			 entity.origin[0] = 80;
			 entity.origin[1] = 0;
			 entity.origin[2] = 0;
			 Math3D.VectorCopy(entity.origin, entity.oldorigin);
			 entity.frame = 0;
			 entity.oldframe = 0;
			 entity.backlerp = 0.0f;
			 entity.angles[1] = yaw++;
			 if (++yaw > 360)
				 yaw -= 360;
//
			 refdef.areabits = null;
//			 refdef.num_entities = 1;
			 refdef.num_entities = 1;
			 refdef.entities = new entity_t[] { entity };
			 refdef.lightstyles = null;
			 refdef.rdflags = Defines.RDF_NOWORLDMODEL;
//
//			 Menu_Draw(& s_player_config_menu);
//
//			 M_DrawTextBox(
//				 (refdef.x) * (320.0F / viddef.width) - 8,
//				 (viddef.height / 2) * (240.0F / viddef.height) - 77,
//				 refdef.width / 8,
//				 refdef.height / 8);
			 refdef.height += 4;
//
			 re.RenderFrame(refdef);
//
//			 Com_sprintf(
//				 scratch,
//				 sizeof(scratch),
//				 "/players/%s/%s_i.pcx",
//				 s_pmi[s_player_model_box.curvalue].directory,
//				 s_pmi[s_player_model_box
//					 .curvalue]
//					 .skindisplaynames[s_player_skin_box
//					 .curvalue]);
			 re.DrawPic(/*s_player_config_menu.x*/ refdef.x - 40, refdef.y, modelImage);
		 }
	 }
	
	 private float CalcFov(float fov_x, float width, float height) {
		 double a;
		 double x;

		 if (fov_x < 1 || fov_x > 179)
			 ri.Sys_Error(Defines.ERR_DROP, "Bad fov: " + fov_x);

		 x = width / Math.tan(fov_x / 360 * Math.PI);

		 a = Math.atan(height / x);

		 a = a * 360 / Math.PI;

		 return new Double(a).floatValue();
	 }

}
