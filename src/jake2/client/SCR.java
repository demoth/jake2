/*
 * SCR.java
 * Copyright (C) 2003
 * 
 * $Id: SCR.java,v 1.8 2004-01-25 21:45:45 rst Exp $
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
		Com.Error(Defines.ERR_FATAL, "BeginLoadingPlaque not implemented!");
	}

	public static void BeginLoadingPlaque()
	{
		//TODO: implement!
		Com.Error(Defines.ERR_FATAL, "BeginLoadingPlaque not implemented!");		
	}

	public static void DebugGraph(float value, int color)
	{
		//TODO:
		Com.Error(Defines.ERR_FATAL, "method not implemented.");
	}
}
