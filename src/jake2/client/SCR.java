/*
 * SCR.java
 * Copyright (C) 2003
 * 
 * $Id: SCR.java,v 1.11 2004-01-27 20:10:29 rst Exp $
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

import jake2.Defines;
import jake2.Globals;
import jake2.qcommon.Com;

/**
 * SCR
 */
public final class SCR {
	
	static dirty_t scr_dirty = new dirty_t();
	
	public static void Init() {
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
