/*
 * VID.java
 * Copyright (C) 2003
 *
 * $Id: VID.java,v 1.9 2004-01-27 20:10:29 rst Exp $
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
import jake2.game.Cmd;
import jake2.game.cvar_t;
import jake2.qcommon.*;
import jake2.render.Renderer;
import jake2.sys.*;
import jake2.sys.KBD;
import jake2.sys.RW;
import jake2.util.Vargs;

import java.awt.Dimension;

/**
 * VID is a video driver.
 * 
 * source: client/vid.h linux/vid_so.c
 * 
 * @author cwei
 */
public class VID {
	//	   Main windowed and fullscreen graphics interface module. This module
	//	   is used for both the software and OpenGL rendering versions of the
	//	   Quake refresh engine.

	// Global variables used internally by this module
	// Globals.viddef
	// global video state; used by other modules

	// Structure containing functions exported from refresh DLL
	// Globals.re;

	// Console variables that we need to access from this module
	static cvar_t vid_gamma;
	static cvar_t vid_ref;			// Name of Refresh DLL loaded
	static cvar_t vid_xpos;			// X coordinate of window position
	static cvar_t vid_ypos;			// Y coordinate of window position
	static cvar_t vid_fullscreen;

	// Global variables used internally by this module
//	void		*reflib_library;		// Handle to refresh DLL 
	static boolean reflib_active = false;
//	const char so_file[] = "/etc/quake2.conf";

	/*
	==========================================================================

	DLL GLUE

	==========================================================================
	*/

	public static void Printf(int print_level, String fmt, Vargs vargs) {
		// static qboolean inupdate;
		if (print_level == Defines.PRINT_ALL)
			Com.Printf(fmt, vargs);
		else
			Com.DPrintf(fmt, vargs);
	}

	public static void Error(int err_level, String fmt, Vargs vargs)
	{
		//static qboolean	inupdate;
		Com.Error(err_level, fmt, vargs);
	}

//	  ==========================================================================

	/*
	============
	VID_Restart_f

	Console command to re-start the video mode and refresh DLL. We do this
	simply by setting the modified flag for the vid_ref variable, which will
	cause the entire video mode and refresh DLL to be reset on the next frame.
	============
	*/
	static void Restart_f()
	{
		vid_ref.modified = true;
	}

	/*
	** VID_GetModeInfo
	*/
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

	public static boolean GetModeInfo(Dimension dim, int mode) {
		if (mode < 0 || mode >= NUM_MODES)
			return false;

		dim.width = vid_modes[mode].width;
		dim.height = vid_modes[mode].height;
		return true;
	}

	/*
	** VID_NewWindow
	*/
	public static void NewWindow(int width, int height) {
		Globals.viddef.width = width;
		Globals.viddef.height = height;
	}

	static void FreeReflib()
	{
		if (Globals.re != null) {
			KBD.Close();
			RW.IN_Shutdown();
		}

		Globals.re = null;
		reflib_active = false;
	}

	/*
	==============
	VID_LoadRefresh
	==============
	*/
	static boolean LoadRefresh( String name )
	{

		if ( reflib_active )
		{
			KBD.Close();
			RW.IN_Shutdown();

			Globals.re.Shutdown();
			FreeReflib();
		}

		Com.Printf( "------- Loading " + name + " -------\n");
		
		boolean found = false;
		
		String[] driverNames = Renderer.getDriverNames();
		for (int i = 0; i < driverNames.length; i++) {
			if (driverNames[i].equals(name)) {
				found = true;
				break;
			} 	
		}

		if (!found) {
			Com.Printf( "LoadLibrary(\"" + name +"\") failed\n");
			return false;
		}

		Com.Printf( "LoadLibrary(\"" + name +"\")\n" );
		refimport_t ri = new refimport_t() {
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
				Cbuf.ExecuteText(exec_when, text);
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
				SCR.UpdateScreen2();
			}
		};

		Globals.re = Renderer.getDriver( name, ri );
		
		if (Globals.re == null)
		{
			Com.Error(Defines.ERR_FATAL, name + " can't load but registered");
		}

		if (Globals.re.apiVersion() != Defines.API_VERSION)
		{
			FreeReflib();
			Com.Error(Defines.ERR_FATAL, name + " has incompatible api_version");
		}

		/* Init IN (Mouse) */
//		in_state.IN_CenterView_fp = IN_CenterView;
//		in_state.Key_Event_fp = Do_Key_Event;
//		in_state.viewangles = cl.viewangles;
//		in_state.in_strafe_state = &in_strafe.state;

		IN.Real_IN_Init();

		if ( !Globals.re.Init() )
		{
			Globals.re.Shutdown();
			FreeReflib();
			return false;
		}

		/* Init KBD */
		KBD.Init();

		Com.Printf( "------------------------------------\n");
		reflib_active = true;
		return true;
	}

	/*
	============
	VID_CheckChanges

	This function gets called once just before drawing each frame, and it's sole purpose in life
	is to check to see if any of the video mode parameters have changed, and if they have to 
	update the rendering DLL and/or video mode to match.
	============
	*/
	public static void CheckChanges()
	{
		cvar_t gl_mode;

		if ( vid_ref.modified )
		{
			S.StopAllSounds();
		}

		while (vid_ref.modified)
		{
			/*
			** refresh has changed
			*/
			vid_ref.modified = false;
			vid_fullscreen.modified = true;
			Globals.cl.refresh_prepped = false;
			Globals.cls.disable_screen = 1.0f; // true;

			if ( !LoadRefresh( vid_ref.string ) )
			{
				if ( vid_ref.string.equals("jogl") ) {
					Com.Printf("Refresh failed\n");
					gl_mode = Cvar.Get( "gl_mode", "0", 0 );
					if (gl_mode.value != 0.0f) {
						Com.Printf("Trying mode 0\n");
						Cvar.SetValue("gl_mode", 0);
						if ( !LoadRefresh( vid_ref.string ) )
							Com.Error(Defines.ERR_FATAL, "Couldn't fall back to jogl refresh!");
					} else
						Com.Error(Defines.ERR_FATAL, "Couldn't fall back to jogl refresh!");
				}

				Cvar.Set( "vid_ref", "jogl" );

				/*
				 * drop the console if we fail to load a refresh
				 */
				if ( Globals.cls.key_dest != Globals.key_console )
				{
					try {
						Console.ToggleConsole_f.execute();
					} catch (Exception e) {
					}
				}
			}
			Globals.cls.disable_screen = 0.0f; //false;
		}
	}

	/*
	============
	VID_Init
	============
	*/
	public static void Init()
	{
		/* Create the video variables so we know how to start the graphics drivers */
		vid_ref = Cvar.Get("vid_ref", "jogl", Cvar.ARCHIVE);
		vid_xpos = Cvar.Get("vid_xpos", "3", Cvar.ARCHIVE);
		vid_ypos = Cvar.Get("vid_ypos", "22", Cvar.ARCHIVE);
		vid_fullscreen = Cvar.Get("vid_fullscreen", "0", Cvar.ARCHIVE);
		vid_gamma = Cvar.Get( "vid_gamma", "1", Cvar.ARCHIVE );

		/* Add some console commands that we want to handle */
		Cmd.AddCommand ("vid_restart", new xcommand_t() {
			public void execute() {
				Restart_f();
			}
		});

		/* Disable the 3Dfx splash screen */
//		putenv("FX_GLIDE_NO_SPLASH=0");
		
		/* Start the graphics mode and load refresh DLL */
		CheckChanges();
	}

	/*
	============
	VID_Shutdown
	============
	*/
	public static void Shutdown()
	{
		if ( reflib_active )
		{
			KBD.Close();
			RW.IN_Shutdown();

			Globals.re.Shutdown();
			FreeReflib();
		}
	}

	public static void MenuInit() {
	}

	public static void MenuDraw() {
	}

	public static String MenuKey(int key) {
		return null;
	}

}
