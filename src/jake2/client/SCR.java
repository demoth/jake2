/*
 * SCR.java
 * Copyright (C) 2003
 * 
 * $Id: SCR.java,v 1.13 2004-01-28 10:03:06 hoz Exp $
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

import java.awt.Dimension;

import jake2.Defines;
import jake2.Globals;
import jake2.qcommon.Com;
import jake2.util.Vargs;

/**
 * SCR
 */
public final class SCR extends Globals {
	
	static dirty_t scr_dirty = new dirty_t();
	static String crosshair_pic;
	static int crosshair_height;
	static int crosshair_width;
	static String[][] sb_nums = 
		{
			{"num_0", "num_1", "num_2", "num_3", "num_4", "num_5",
			"num_6", "num_7", "num_8", "num_9", "num_minus"},
			{"anum_0", "anum_1", "anum_2", "anum_3", "anum_4", "anum_5",
			"anum_6", "anum_7", "anum_8", "anum_9", "anum_minus"}
		};
	
	public static void Init() {
	}
	
	static void TouchPics() {
		int i, j;
 
		for (i=0 ; i<2 ; i++)
			for (j=0 ; j<11 ; j++)
				re.RegisterPic(sb_nums[i][j]);
 
		if (crosshair.value != 0.0f) {
			if (crosshair.value > 3.0f || crosshair.value < 0.0f)
				crosshair.value = 3.0f;
 
		crosshair_pic = Com.sprintf ("ch%i", new Vargs(1).add((int)(crosshair.value)));
		Dimension dim = new Dimension();
		re.DrawGetPicSize(dim, crosshair_pic);
		crosshair_width = dim.width;
		crosshair_height = dim.height;
		if (crosshair_width == 0)
			crosshair_pic = "";
		}
	}
	
	/*
	=================
	SCR_DrawCrosshair
	=================
	*/
	static void DrawCrosshair() {
		if (crosshair.value == 0.0f)
			return;

		if (crosshair.modified) {
			crosshair.modified = false;
			SCR.TouchPics();
		}

		if (crosshair_pic.length() == 0)
			return;

		re.DrawPic(scr_vrect.x + ((scr_vrect.width - crosshair_width)>>1),
			scr_vrect.y + ((scr_vrect.height - crosshair_height)>>1), crosshair_pic);
	}
	
	// wird anstelle von der richtigen UpdateScreen benoetigt
	public static void UpdateScreen() {
		Globals.re.updateScreen();
	}
	
	// hier muss der code der orig UpdateScreen rein
	public static void UpdateScreen2() {
		Globals.re.BeginFrame(0.0f);
		
		Globals.re.DrawStretchPic(0, 0 , Globals.viddef.width, Globals.viddef.height, "conback");
		
		Globals.re.EndFrame();
	}

	public static void EndLoadingPlaque() {
		//TODO: implement!
		//Com.Error(Defines.ERR_FATAL, "EndLoadingPlaque not implemented!");
	}

	public static void BeginLoadingPlaque()
	{
		//TODO: implement!
		Com.Error(Defines.ERR_FATAL, "BeginLoadingPlaque not implemented!");		
	}

	public static void DebugGraph(float value, int color)
	{
		//TODO: implent all den mist 
		//TODO: shut up
		Com.Error(Defines.ERR_FATAL, "method not implemented.");
	}



	public static void DirtyScreen() {
		// TODO: implement
		
	}
	
	/*
	=================
	SCR_AddDirtyPoint
	=================
	*/
	static void AddDirtyPoint(int x, int y) {
		if (x < scr_dirty.x1)
			scr_dirty.x1 = x;
		if (x > scr_dirty.x2)
			scr_dirty.x2 = x;
		if (y < scr_dirty.y1)
			scr_dirty.y1 = y;
		if (y > scr_dirty.y2)
			scr_dirty.y2 = y;
	}
}

class dirty_t {
	int x1;
	int x2;
	int y1;
	int y2;

}
