/*
 * VID.java
 * Copyright (C) 2003
 *
 * $Id: VID.java,v 1.2 2003-11-25 15:31:38 cwei Exp $
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

/**
 * VID is a video driver.
 * 
 * source: client/vid.h linux/vid_so.c
 * 
 * @author cwei
 */
public class VID {

	static final vidmode_t vid_modes[] =
		{
			new vidmode_t("Mode 0: 320x240", 320, 240, 0),
			new vidmode_t("Mode 1: 400x300", 400, 300, 1),
			new vidmode_t("Mode 2: 512x384", 512, 384, 2),
			new vidmode_t("Mode 3: 640x480", 640, 480, 3),
			new vidmode_t("Mode 4: 800x600", 800, 600, 4),
			new vidmode_t("Mode 5: 960x720", 960, 720, 5),
			new vidmode_t("Mode 6: 1024x768", 1024, 768, 6),
			new vidmode_t("Mode 7: 1152x864", 1152, 864, 7),
			new vidmode_t("Mode 8: 1280x1024", 1280, 1024, 8),
			new vidmode_t("Mode 9: 1600x1200", 1600, 1200, 9),
			new vidmode_t("Mode 10: 2048x1536", 2048, 1536, 10)};

	static final int NUM_MODES = vid_modes.length;
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

	/**
	 * @param dim
	 * @param mode
	 * @return
	 */
	public static boolean GetModeInfo(Dimension dim, int mode) {
		if ( mode < 0 || mode >= NUM_MODES )  return false;
		
		dim.width  = vid_modes[mode].width;
		dim.height = vid_modes[mode].height;
		return true;
	}

	/**
	 * @param width
	 * @param height
	 */
	public static void NewWindow(int width, int height) {
		// TODO Auto-generated method stub

	}

}
