/*
 * CL.java
 * Copyright (C) 2003
 * 
 * $Id: CL.java,v 1.4 2003-11-28 21:47:54 hoz Exp $
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

import jake2.Globals;
import jake2.qcommon.Cbuf;
import jake2.qcommon.FS;
import jake2.sys.CDAudio;
import jake2.sys.IN;

/**
 * CL
 */
public final class CL {

	/**
	 * @param msec
	 */
	public static void Frame(long msec) {
	}
	
	/**
	 * initialize client subsystem
	 */
	public static void Init() {
		if (Globals.dedicated.value != 0.0f)
			return; // nothing running on the client
			
		// all archived variables will now be loaded
		
		Console.Init();
			
		S.Init();
		VID.Init();
			
		V.Init();
			         
		Globals.net_message.data = Globals.net_message_buffer;
		Globals.net_message.maxsize = Globals.net_message_buffer.length;

		M.Init();      

		SCR.Init();
		Globals.cls.disable_screen = 1.0f;      // don't draw yet

		CDAudio.Init();
		InitLocal();
		IN.Init();

		FS.ExecAutoexec();
		Cbuf.Execute();
	}
	
	public static void InitLocal() {
	}
}
