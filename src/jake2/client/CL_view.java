/*
 * CL_view.java
 * Copyright (C) 2004
 * 
 * $Id: CL_view.java,v 1.4 2004-01-31 16:56:11 rst Exp $
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

import java.util.StringTokenizer;

import jake2.qcommon.CM;
import jake2.qcommon.Com;
import jake2.sys.Sys;
import jake2.util.Vargs;





public class CL_view extends CL_input {
	
	static int num_cl_weaponmodels;
	static String[] cl_weaponmodels = new String[MAX_CLIENTWEAPONMODELS];


	/*
	=================
	CL_PrepRefresh

	Call before entering a new level, or after changing dlls
	=================
	*/
	static void PrepRefresh() {
		String mapname;
		int i;
		String name;
		float rotate;
		float[] axis = new float[3];

		if ((i=cl.configstrings[CS_MODELS+1].length()) == 0)
			return;		// no map loaded

		SCR.AddDirtyPoint(0, 0);
		SCR.AddDirtyPoint(viddef.width-1, viddef.height-1);

		// let the render dll load the map
		mapname = cl.configstrings[CS_MODELS+1].substring(5, i - 5);	// skip "maps/"
																		// cut off ".bsp"

		// register models, pics, and skins
		Com.Printf("Map: " + mapname + "\r"); 
		SCR.UpdateScreen();
		re.BeginRegistration(mapname);
		Com.Printf("                                     \r");

		// precache status bar pics
		Com.Printf("pics\r"); 
		SCR.UpdateScreen();
		SCR.TouchPics();
		Com.Printf("                                     \r");

		CL.RegisterTEntModels();

		num_cl_weaponmodels = 1;
		cl_weaponmodels[0] = "weapon.md2";

		for (i=1 ; i<MAX_MODELS && cl.configstrings[CS_MODELS+i].length() != 0 ; i++) {
			name = new String(cl.configstrings[CS_MODELS+i]);
			if (name.length() > 37) name = name.substring(0, 36);

			if (name.charAt(0) != '*')
				Com.Printf("name" + "\r"); 
			SCR.UpdateScreen();
			Sys.SendKeyEvents();	// pump message loop
			if (name.charAt(0) == '#') {
				// special player weapon model
				if (num_cl_weaponmodels < MAX_CLIENTWEAPONMODELS) {
					cl_weaponmodels[num_cl_weaponmodels] = cl.configstrings[CS_MODELS+i].substring(1);
					num_cl_weaponmodels++;
				}
			} else {				
				cl.model_draw[i] = re.RegisterModel(cl.configstrings[CS_MODELS+i]);
				if (name.charAt(0) == '*')
					cl.model_clip[i] = CM.InlineModel(cl.configstrings[CS_MODELS+i]);
				else
					cl.model_clip[i] = null;
			}
			if (name.charAt(0) != '*')
				Com.Printf("                                     \r");
		}

		Com.Printf("images\r"); 
		SCR.UpdateScreen();
		for (i=1 ; i<MAX_IMAGES && cl.configstrings[CS_IMAGES+i].length() > 0 ; i++) {
			cl.image_precache[i] = re.RegisterPic(cl.configstrings[CS_IMAGES+i]);
			Sys.SendKeyEvents();	// pump message loop
		}

		Com.Printf("                                     \r");
		for (i=0 ; i<MAX_CLIENTS ; i++) {
			if (cl.configstrings[CS_PLAYERSKINS+i].length() == 0)
				continue;
			Com.Printf("client %i\r", new Vargs(1).add(i)); 
			SCR.UpdateScreen();
			Sys.SendKeyEvents();	// pump message loop
			CL.CL_ParseClientinfo(i);
			Com.Printf("                                     \r");
		}

		CL_parse.CL_LoadClientinfo(cl.baseclientinfo, "unnamed\\male/grunt");

		// set sky textures and speed
		Com.Printf("sky\r"); 
		SCR.UpdateScreen();
		rotate = Float.parseFloat(cl.configstrings[CS_SKYROTATE]);
		StringTokenizer st = new StringTokenizer(cl.configstrings[CS_SKYAXIS]);
		axis[0] = Float.parseFloat(st.nextToken());
		axis[1] = Float.parseFloat(st.nextToken());
		axis[2] = Float.parseFloat(st.nextToken());
		re.SetSky(cl.configstrings[CS_SKY], rotate, axis);
		Com.Printf("                                     \r");

		// the renderer can now free unneeded stuff
		re.EndRegistration ();

		// clear any lines of console text
		Console.ClearNotify();

		SCR.UpdateScreen();
		cl.refresh_prepped = true;
		cl.force_refdef = true;	// make sure we have a valid refdef
	}
 
	public static void CL_AddNetgraph() {
		// TODO:implement!
		
	}

	public static void V_AddLight(float[] start, float i, float j, float k, float l) {
		// TODO:implement!
		
	}

	public static void V_AddEntity(entity_t ent) {
		// TODO:implement!
		
	}


}
