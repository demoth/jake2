/*
 * CL.java
 * Copyright (C) 2003
 * 
 * $Id: CL.java,v 1.3 2003-11-28 21:16:43 rst Exp $
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
			
// S.Init();
		VID.Init();		
		V.Init();
		
		         
//		01797         net_message.data = net_message_buffer;
//		01798         net_message.maxsize = sizeof(net_message_buffer);
//		01799 
//		01800         M_Init ();      
//		01801         
//		01802         SCR_Init ();
//		01803         cls.disable_screen = true;      // don't draw yet
//		01804 
//		01805         CDAudio_Init ();
//		01806         CL_InitLocal ();
//		01807         IN_Init ();

		FS.ExecAutoexec();
		Cbuf.Execute();
	}
}
