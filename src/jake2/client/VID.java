/*
 * VID.java
 * Copyright (C) 2003
 *
 * $Id: VID.java,v 1.20 2004-06-15 16:26:50 cwei Exp $
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
import jake2.sound.*;
import jake2.sys.IN;
import jake2.sys.KBD;
import jake2.util.Vargs;

import java.awt.Dimension;

/**
 * VID is a video driver.
 * 
 * source: client/vid.h linux/vid_so.c
 * 
 * @author cwei
 */
public class VID extends Globals {
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
	// void *reflib_library;		// Handle to refresh DLL 
	static boolean reflib_active = false;
	// const char so_file[] = "/etc/quake2.conf";

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

	// ==========================================================================

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
			IN.Shutdown();
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
			IN.Shutdown();

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

		if ( !Globals.re.Init((int)vid_xpos.value, (int)vid_ypos.value) )
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
		vid_ref = Cvar.Get("vid_ref", "fastjogl", CVAR_ARCHIVE);
		vid_xpos = Cvar.Get("vid_xpos", "3", CVAR_ARCHIVE);
		vid_ypos = Cvar.Get("vid_ypos", "22", CVAR_ARCHIVE);
		vid_fullscreen = Cvar.Get("vid_fullscreen", "0", CVAR_ARCHIVE);
		vid_gamma = Cvar.Get( "vid_gamma", "1", CVAR_ARCHIVE );

		/* Add some console commands that we want to handle */
		Cmd.AddCommand ("vid_restart", new xcommand_t() {
			public void execute() {
				Restart_f();
			}
		});

		/* Disable the 3Dfx splash screen */
		// putenv("FX_GLIDE_NO_SPLASH=0");
		
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
			IN.Shutdown();

			Globals.re.Shutdown();
			FreeReflib();
		}
	}

	// ==========================================================================
	// 
	//	vid_menu.c
	//
	// ==========================================================================

//	#define REF_SOFT	0
//	#define REF_SOFTX11	1
//	#define REF_MESA3D  2
//	#define REF_3DFXGL 3
//	#define REF_OPENGLX	4
	static final int REF_OPENGL_JOGL = 0;
	static final int REF_OPENGL_FASTJOGL =1;
//	#define REF_MESA3DGLX 5

//	extern cvar_t *vid_ref;
//	extern cvar_t *vid_fullscreen;
//	extern cvar_t *vid_gamma;
//	extern cvar_t *scr_viewsize;

	static cvar_t gl_mode;
	static cvar_t gl_driver;
	static cvar_t gl_picmip;
	static cvar_t gl_ext_palettedtexture;

	static cvar_t sw_mode;
	static cvar_t sw_stipplealpha;

	static cvar_t _windowed_mouse;

	/*
	====================================================================

	MENU INTERACTION

	====================================================================
	*/
	static final int SOFTWARE_MENU  = 0;
	static final int OPENGL_MENU  = 1;

	static Menu.menuframework_s  s_software_menu = new Menu.menuframework_s();
	static Menu.menuframework_s	s_opengl_menu = new Menu.menuframework_s();
	static Menu.menuframework_s s_current_menu; // referenz
	static int s_current_menu_index = 1; // default is the openGL menu

	static Menu.menulist_s[] s_mode_list = new Menu.menulist_s[2];
	static {
		s_mode_list[0] = new Menu.menulist_s();
		s_mode_list[1] = new Menu.menulist_s();
	}
	static Menu.menulist_s[] s_ref_list = new Menu.menulist_s[2];
	static {
		s_ref_list[0] = new Menu.menulist_s();
		s_ref_list[1] = new Menu.menulist_s();
	}
	static Menu.menuslider_s s_tq_slider = new Menu.menuslider_s();
	static Menu.menuslider_s[] s_screensize_slider = new Menu.menuslider_s[2];
	static {
		s_screensize_slider[0] = new Menu.menuslider_s();
		s_screensize_slider[1] = new Menu.menuslider_s();
	}
	static Menu.menuslider_s[] s_brightness_slider = new Menu.menuslider_s[2];
	static {
		s_brightness_slider[0] = new Menu.menuslider_s();
		s_brightness_slider[1] = new Menu.menuslider_s();
	}
	static Menu.menulist_s[] s_fs_box = new Menu.menulist_s[2];
	static {
		s_fs_box[0] = new Menu.menulist_s();
		s_fs_box[1] = new Menu.menulist_s();
	}
	static Menu.menulist_s s_stipple_box = new Menu.menulist_s();
	static Menu.menulist_s s_paletted_texture_box = new Menu.menulist_s();
	static Menu.menulist_s s_windowed_mouse = new Menu.menulist_s();
	static Menu.menuaction_s[] s_apply_action = new Menu.menuaction_s[2];
	static {
		s_apply_action[0] = new Menu.menuaction_s();
		s_apply_action[1] = new Menu.menuaction_s();
	}
	static Menu.menuaction_s[] s_defaults_action= new Menu.menuaction_s[2];
	static {
		s_defaults_action[0] = new Menu.menuaction_s();
		s_defaults_action[1] = new Menu.menuaction_s();
	}

	static void DriverCallback( Object unused )
	{
		s_ref_list[1 - s_current_menu_index].curvalue = s_ref_list[s_current_menu_index].curvalue;

		if ( s_ref_list[s_current_menu_index].curvalue < 2 )
		{
			// we only use opengl today
			s_current_menu = s_opengl_menu; // s_software_menu;
			s_current_menu_index = 1; // 0;
		}
		else
		{
			s_current_menu = s_opengl_menu;
			s_current_menu_index = 1;
		}
	}

	static void ScreenSizeCallback( Object s )
	{
		Menu.menuslider_s slider = (Menu.menuslider_s) s;

		Cvar.SetValue( "viewsize", slider.curvalue * 10 );
	}

	static void BrightnessCallback( Object s )
	{
		Menu.menuslider_s slider = (Menu.menuslider_s) s;

		if ( s_current_menu_index == 0)
			s_brightness_slider[1].curvalue = s_brightness_slider[0].curvalue;
		else
			s_brightness_slider[0].curvalue = s_brightness_slider[1].curvalue;

		// if ( stricmp( vid_ref.string, "soft" ) == 0 ||
		//	stricmp( vid_ref.string, "softx" ) == 0 )
		if ( vid_ref.string.equalsIgnoreCase("soft") ||
			 vid_ref.string.equalsIgnoreCase("softx") )
		{
			float gamma = ( 0.8f - ( slider.curvalue/10.0f - 0.5f ) ) + 0.5f;

			Cvar.SetValue( "vid_gamma", gamma );
		}
	}

	static void ResetDefaults( Object unused )
	{
		MenuInit();
	}

	static void ApplyChanges( Object unused )
	{
		/*
		** make values consistent
		*/
		s_fs_box[1 - s_current_menu_index].curvalue = s_fs_box[s_current_menu_index].curvalue;
		s_brightness_slider[1 - s_current_menu_index].curvalue = s_brightness_slider[s_current_menu_index].curvalue;
		s_ref_list[1 - s_current_menu_index].curvalue = s_ref_list[s_current_menu_index].curvalue;

		/*
		** invert sense so greater = brighter, and scale to a range of 0.5 to 1.3
		*/
		float gamma = ( 0.8f - ( s_brightness_slider[s_current_menu_index].curvalue/10.0f - 0.5f ) ) + 0.5f;

		Cvar.SetValue( "vid_gamma", gamma );
		Cvar.SetValue( "sw_stipplealpha", s_stipple_box.curvalue );
		Cvar.SetValue( "gl_picmip", 3 - s_tq_slider.curvalue );
		Cvar.SetValue( "vid_fullscreen", s_fs_box[s_current_menu_index].curvalue );
		Cvar.SetValue( "gl_ext_palettedtexture", s_paletted_texture_box.curvalue );
		Cvar.SetValue( "sw_mode", s_mode_list[SOFTWARE_MENU].curvalue );
		Cvar.SetValue( "gl_mode", s_mode_list[OPENGL_MENU].curvalue );
		Cvar.SetValue( "_windowed_mouse", s_windowed_mouse.curvalue);

		switch ( s_ref_list[s_current_menu_index].curvalue )
		{
//		case REF_SOFT:
//			Cvar_Set( "vid_ref", "soft" );
//			break;
//		case REF_SOFTX11:
//			Cvar_Set( "vid_ref", "softx" );
//			break;
//
//		case REF_MESA3D :
//			Cvar_Set( "vid_ref", "gl" );
//			Cvar_Set( "gl_driver", "libMesaGL.so.2" );
//			if (gl_driver->modified)
//				vid_ref->modified = true;
//			break;
//
//		case REF_OPENGLX :
//			Cvar_Set( "vid_ref", "glx" );
//			Cvar_Set( "gl_driver", "libGL.so" );
//			if (gl_driver->modified)
//				vid_ref->modified = true;
//			break;
//
//		case REF_MESA3DGLX :
//			Cvar_Set( "vid_ref", "glx" );
//			Cvar_Set( "gl_driver", "libMesaGL.so.2" );
//			if (gl_driver->modified)
//				vid_ref->modified = true;
//			break;
//
//		case REF_3DFXGL :
//			Cvar_Set( "vid_ref", "gl" );
//			Cvar_Set( "gl_driver", "lib3dfxgl.so" );
//			if (gl_driver->modified)
//				vid_ref->modified = true;
//			break;
		case REF_OPENGL_JOGL :
			Cvar.Set( "vid_ref", "jogl" );
			Cvar.Set( "gl_driver", "jogl" );
			if (gl_driver.modified)
				vid_ref.modified = true;
			break;
		case REF_OPENGL_FASTJOGL :
				Cvar.Set( "vid_ref", "fastjogl" );
				Cvar.Set( "gl_driver", "fastjogl" );
				if (gl_driver.modified)
					vid_ref.modified = true;
				break;
		}

		Menu.ForceMenuOff();
	}

	static final String[] resolutions = 
	{
		"[320 240  ]",
		"[400 300  ]",
		"[512 384  ]",
		"[640 480  ]",
		"[800 600  ]",
		"[960 720  ]",
		"[1024 768 ]",
		"[1152 864 ]",
		"[1280 1024]",
		"[1600 1200]",
		"[2048 1536]",
		null
	};
	static final String[] refs =
	{
		// "[software       ]",
		// "[software X11   ]",
		// "[Mesa 3-D 3DFX  ]",
		// "[3DFXGL Miniport]",
		// "[OpenGL glX     ]",
		// "[Mesa 3-D glX   ]",
		"[OpenGL jogl    ]",
		"[OpenGL fastjogl]",
		null
	};
	static final String[] yesno_names =
	{
		"no",
		"yes",
		null
	};

	/*
	** VID_MenuInit
	*/
	public static void MenuInit()
	{
		int i;

		if ( gl_driver == null )
			gl_driver = Cvar.Get( "gl_driver", "jogl", 0 );
		if ( gl_picmip == null )
			gl_picmip = Cvar.Get( "gl_picmip", "0", 0 );
		if ( gl_mode == null)
			gl_mode = Cvar.Get( "gl_mode", "3", 0 );
		if ( sw_mode == null )
			sw_mode = Cvar.Get( "sw_mode", "0", 0 );
		if ( gl_ext_palettedtexture == null )
			gl_ext_palettedtexture = Cvar.Get( "gl_ext_palettedtexture", "1", CVAR_ARCHIVE );

		if ( sw_stipplealpha == null )
			sw_stipplealpha = Cvar.Get( "sw_stipplealpha", "0", CVAR_ARCHIVE );

		if ( _windowed_mouse == null)
			_windowed_mouse = Cvar.Get( "_windowed_mouse", "0", CVAR_ARCHIVE );

		s_mode_list[SOFTWARE_MENU].curvalue = (int)sw_mode.value;
		s_mode_list[OPENGL_MENU].curvalue = (int)gl_mode.value;

		if ( SCR.scr_viewsize == null )
			SCR.scr_viewsize = Cvar.Get ("viewsize", "100", CVAR_ARCHIVE);

		s_screensize_slider[SOFTWARE_MENU].curvalue = (int)(SCR.scr_viewsize.value/10);
		s_screensize_slider[OPENGL_MENU].curvalue = (int)(SCR.scr_viewsize.value/10);

//		if ( strcmp( vid_ref->string, "soft" ) == 0)
//		{
//			s_current_menu_index = SOFTWARE_MENU;
//			s_ref_list[0].curvalue = s_ref_list[1].curvalue = REF_SOFT;
//		}
		if ( vid_ref.string.equalsIgnoreCase("jogl"))
		{
			s_current_menu_index = OPENGL_MENU;
			s_ref_list[0].curvalue = s_ref_list[1].curvalue = REF_OPENGL_JOGL;
		}
		else if ( vid_ref.string.equalsIgnoreCase("fastjogl"))
		{
			s_current_menu_index = OPENGL_MENU;
			s_ref_list[0].curvalue = s_ref_list[1].curvalue = REF_OPENGL_FASTJOGL;
		}
//		else if (strcmp( vid_ref->string, "softx" ) == 0 ) 
//		{
//			s_current_menu_index = SOFTWARE_MENU;
//			s_ref_list[0].curvalue = s_ref_list[1].curvalue = REF_SOFTX11;
//		}
//		else if ( strcmp( vid_ref->string, "gl" ) == 0 )
//		{
//			s_current_menu_index = OPENGL_MENU;
//			if ( strcmp( gl_driver->string, "lib3dfxgl.so" ) == 0 )
//				s_ref_list[s_current_menu_index].curvalue = REF_3DFXGL;
//			else
//				s_ref_list[s_current_menu_index].curvalue = REF_MESA3D;
//		}
//		else if ( strcmp( vid_ref->string, "glx" ) == 0 )
//		{
//			s_current_menu_index = OPENGL_MENU;
//			if ( strcmp( gl_driver->string, "libMesaGL.so.2" ) == 0 )
//				s_ref_list[s_current_menu_index].curvalue = REF_MESA3DGLX;
//			else
//				s_ref_list[s_current_menu_index].curvalue = REF_OPENGLX;
//		}
//
		s_software_menu.x = (int)(viddef.width * 0.50f);
		s_software_menu.nitems = 0;
		s_opengl_menu.x = (int)(viddef.width * 0.50f);
		s_opengl_menu.nitems = 0;

		for ( i = 0; i < 2; i++ )
		{
			s_ref_list[i].type = MTYPE_SPINCONTROL;
			s_ref_list[i].name = "driver";
			s_ref_list[i].x = 0;
			s_ref_list[i].y = 0;
			s_ref_list[i].callback = new Menu.mcallback() {
				public void execute(Object self) {
					DriverCallback(self);
				}
			};
			s_ref_list[i].itemnames = refs;

			s_mode_list[i].type = MTYPE_SPINCONTROL;
			s_mode_list[i].name = "video mode";
			s_mode_list[i].x = 0;
			s_mode_list[i].y = 10;
			s_mode_list[i].itemnames = resolutions;

			s_screensize_slider[i].type	= MTYPE_SLIDER;
			s_screensize_slider[i].x		= 0;
			s_screensize_slider[i].y		= 20;
			s_screensize_slider[i].name	= "screen size";
			s_screensize_slider[i].minvalue = 3;
			s_screensize_slider[i].maxvalue = 12;
			s_screensize_slider[i].callback = new Menu.mcallback() {
				public void execute(Object self) {
					ScreenSizeCallback(self);
				}
			};
			s_brightness_slider[i].type	= MTYPE_SLIDER;
			s_brightness_slider[i].x	= 0;
			s_brightness_slider[i].y	= 30;
			s_brightness_slider[i].name	= "brightness";
			s_brightness_slider[i].callback =  new Menu.mcallback() {
				public void execute(Object self) {
					BrightnessCallback(self);
				}
			};
			s_brightness_slider[i].minvalue = 5;
			s_brightness_slider[i].maxvalue = 13;
			s_brightness_slider[i].curvalue = ( 1.3f - vid_gamma.value + 0.5f ) * 10;

			s_fs_box[i].type = MTYPE_SPINCONTROL;
			s_fs_box[i].x	= 0;
			s_fs_box[i].y	= 40;
			s_fs_box[i].name	= "fullscreen";
			s_fs_box[i].itemnames = yesno_names;
			s_fs_box[i].curvalue = (int)vid_fullscreen.value;

			s_defaults_action[i].type = MTYPE_ACTION;
			s_defaults_action[i].name = "reset to default";
			s_defaults_action[i].x    = 0;
			s_defaults_action[i].y    = 90;
			s_defaults_action[i].callback = new Menu.mcallback() {
				public void execute(Object self) {
					ResetDefaults(self);
				}
			};

			s_apply_action[i].type = MTYPE_ACTION;
			s_apply_action[i].name = "apply";
			s_apply_action[i].x    = 0;
			s_apply_action[i].y    = 100;
			s_apply_action[i].callback = new Menu.mcallback() {
				public void execute(Object self) {
					ApplyChanges(self);
				}
			};
		}

		s_stipple_box.type = MTYPE_SPINCONTROL;
		s_stipple_box.x	= 0;
		s_stipple_box.y	= 60;
		s_stipple_box.name	= "stipple alpha";
		s_stipple_box.curvalue = (int)sw_stipplealpha.value;
		s_stipple_box.itemnames = yesno_names;

		s_windowed_mouse.type = MTYPE_SPINCONTROL;
		s_windowed_mouse.x  = 0;
		s_windowed_mouse.y  = 72;
		s_windowed_mouse.name   = "windowed mouse";
		s_windowed_mouse.curvalue = (int)_windowed_mouse.value;
		s_windowed_mouse.itemnames = yesno_names;

		s_tq_slider.type	= MTYPE_SLIDER;
		s_tq_slider.x		= 0;
		s_tq_slider.y		= 60;
		s_tq_slider.name	= "texture quality";
		s_tq_slider.minvalue = 0;
		s_tq_slider.maxvalue = 3;
		s_tq_slider.curvalue = 3 - gl_picmip.value;

		s_paletted_texture_box.type = MTYPE_SPINCONTROL;
		s_paletted_texture_box.x	= 0;
		s_paletted_texture_box.y	= 70;
		s_paletted_texture_box.name	= "8-bit textures";
		s_paletted_texture_box.itemnames = yesno_names;
		s_paletted_texture_box.curvalue = (int)gl_ext_palettedtexture.value;

		Menu.Menu_AddItem( s_software_menu, s_ref_list[SOFTWARE_MENU] );
		Menu.Menu_AddItem( s_software_menu, s_mode_list[SOFTWARE_MENU] );
		Menu.Menu_AddItem( s_software_menu, s_screensize_slider[SOFTWARE_MENU] );
		Menu.Menu_AddItem( s_software_menu, s_brightness_slider[SOFTWARE_MENU] );
		Menu.Menu_AddItem( s_software_menu, s_fs_box[SOFTWARE_MENU] );
		Menu.Menu_AddItem( s_software_menu, s_stipple_box );
		Menu.Menu_AddItem( s_software_menu, s_windowed_mouse );

		Menu.Menu_AddItem( s_opengl_menu, s_ref_list[OPENGL_MENU] );
		Menu.Menu_AddItem( s_opengl_menu, s_mode_list[OPENGL_MENU] );
		Menu.Menu_AddItem( s_opengl_menu, s_screensize_slider[OPENGL_MENU] );
		Menu.Menu_AddItem( s_opengl_menu, s_brightness_slider[OPENGL_MENU] );
		Menu.Menu_AddItem( s_opengl_menu, s_fs_box[OPENGL_MENU] );
		Menu.Menu_AddItem( s_opengl_menu, s_tq_slider );
		Menu.Menu_AddItem( s_opengl_menu, s_paletted_texture_box );

		Menu.Menu_AddItem( s_software_menu, s_defaults_action[SOFTWARE_MENU] );
		Menu.Menu_AddItem( s_software_menu, s_apply_action[SOFTWARE_MENU] );
		Menu.Menu_AddItem( s_opengl_menu, s_defaults_action[OPENGL_MENU] );
		Menu.Menu_AddItem( s_opengl_menu, s_apply_action[OPENGL_MENU] );

		Menu.Menu_Center( s_software_menu );
		Menu.Menu_Center( s_opengl_menu );
		s_opengl_menu.x -= 8;
		s_software_menu.x -= 8;
	}

	/*
	================
	VID_MenuDraw
	================
	*/
	static void MenuDraw()
	{

		if ( s_current_menu_index == 0 )
			s_current_menu = s_software_menu;
		else
			s_current_menu = s_opengl_menu;

		/*
		** draw the banner
		*/
		Dimension dim = new Dimension();
		re.DrawGetPicSize( dim, "m_banner_video" );
		re.DrawPic( viddef.width / 2 - dim.width / 2, viddef.height /2 - 110, "m_banner_video" );

		/*
		** move cursor to a reasonable starting position
		*/
		Menu.Menu_AdjustCursor( s_current_menu, 1 );

		/*
		** draw the menu
		*/
		Menu.Menu_Draw( s_current_menu );
	}

	/*
	================
	VID_MenuKey
	================
	*/
	static String MenuKey( int key )
	{
		Menu.menuframework_s m = s_current_menu;
		final String sound = "misc/menu1.wav";

		switch ( key )
		{
		case K_ESCAPE:
			Menu.PopMenu();
			return null;
		case K_UPARROW:
			m.cursor--;
			Menu.Menu_AdjustCursor( m, -1 );
			break;
		case K_DOWNARROW:
			m.cursor++;
			Menu.Menu_AdjustCursor( m, 1 );
			break;
		case K_LEFTARROW:
			Menu.Menu_SlideItem( m, -1 );
			break;
		case K_RIGHTARROW:
			Menu.Menu_SlideItem( m, 1 );
			break;
		case K_ENTER:
			Menu.Menu_SelectItem( m );
			break;
		}

		return sound;
	}

}
