/*
 * VID.java
 * Copyright (C) 2003
 *
 * $Id: VID.java,v 1.1 2003-11-21 23:32:06 cwei Exp $
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

/**
 * VID is a video driver.
 * 
 * source: client/vid.h linux/vid_so.c
 * 
 * @author cwei
 */
public class VID {
	// TODO implement VID;
	// es fehlen noch funktionen aus vid_so.c

	//	vid.h -- video driver defs

	/* typedef struct vrect_s
	 {
		 int				x,y,width,height;
	 } vrect_t;
	
	 typedef struct
	 {
		 unsigned		width, height;			// coordinates from main game
	 } viddef_t;
	
	 extern	viddef_t	viddef;				// global video state
	*/

	//	Video module initialisation etc
	public static void Init() {
	}

	public static void Shutdown() {
	}

	public static void CheckChanges() {
	}

	public static void MenuInit() {
	}

	public static void MenuDraw() {
	}

	public static String MenuKey(int key) {
		return null;
	}

}
