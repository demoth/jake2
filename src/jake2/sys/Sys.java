/*
 * Sys.java
 * Copyright (C) 2003
 * 
 * $Id: Sys.java,v 1.6 2003-12-11 15:20:03 hoz Exp $
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
package jake2.sys;

import jake2.client.CL;

/**
 * Sys
 */
public final class Sys {
	
	/**
	 * @param error
	 */
	public static void Error(String error) {
//		00099         va_list     argptr;
//		00100         char        string[1024];
//		00101 
//		00102 // change stdin to non blocking
//		00103         fcntl (0, F_SETFL, fcntl (0, F_GETFL, 0) & ~FNDELAY);
//		00104 
		CL.Shutdown();
//		00106     
//		00107         va_start (argptr,error);
//		00108         vsprintf (string,error,argptr);
//		00109         va_end (argptr);
//		00110         fprintf(stderr, "Error: %s\n", string);
		System.err.println("Error: " + error);
//		00111 
		System.exit(1);
			
	}
	
	public static void Quit() {
		CL.Shutdown();
//	00093         fcntl (0, F_SETFL, fcntl (0, F_GETFL, 0) & ~FNDELAY);
		System.exit(0);
	}
	
}
