/*
 * SCR.java
 * Copyright (C) 2003
 * 
 * $Id: SCR.java,v 1.33 2004-02-15 00:07:37 cwei Exp $
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
import jake2.game.Cmd;
import jake2.game.cvar_t;
import jake2.qcommon.*;
import jake2.sys.Sys;
import jake2.util.Vargs;

import java.awt.Dimension;

/**
 * SCR
 */
public final class SCR extends Globals {

	//	cl_scrn.c -- master for refresh, status bar, console, chat, notify, etc
	
	static String[][] sb_nums = 
		{
			{"num_0", "num_1", "num_2", "num_3", "num_4", "num_5",
			"num_6", "num_7", "num_8", "num_9", "num_minus"},
			{"anum_0", "anum_1", "anum_2", "anum_3", "anum_4", "anum_5",
			"anum_6", "anum_7", "anum_8", "anum_9", "anum_minus"}
		};

	/*
	  full screen console
	  put up loading plaque
	  blanked background with loading plaque
	  blanked background with menu
	  cinematics
	  full screen image for quit and victory

	  end of unit intermissions
	*/

	static float scr_con_current;	// aproaches scr_conlines at scr_conspeed
	static float scr_conlines;		// 0.0 to 1.0 lines of console to display

	static boolean scr_initialized;		// ready to draw

	static int scr_draw_loading;

	// scr_vrect ist in Globals definiert
	// position of render window on screen

	static cvar_t scr_viewsize;
	static cvar_t scr_conspeed;
	static cvar_t scr_centertime;
	static cvar_t scr_showturtle;
	static cvar_t scr_showpause;
	static cvar_t scr_printspeed;

	static cvar_t scr_netgraph;
	static cvar_t scr_timegraph;
	static cvar_t scr_debuggraph;
	static cvar_t scr_graphheight;
	static cvar_t scr_graphscale;
	static cvar_t scr_graphshift;
	static cvar_t scr_drawall;

	static dirty_t scr_dirty = new dirty_t();
	static dirty_t[] scr_old_dirty = { new dirty_t(), new dirty_t() };

	static String crosshair_pic;
	static int crosshair_width, crosshair_height;

	static class dirty_t
	{
		int x1;
		int x2;
		int y1;
		int y2;
	}

	/*
	===============================================================================

	BAR GRAPHS

	===============================================================================
	*/


//	typedef struct
//	{
//		float	value;
//		int		color;
//	} graphsamp_t;
	static class graphsamp_t {
		float value;
		int color;
	}
	static int current;
	static graphsamp_t[] values = new graphsamp_t[1024];
	
	static {
		for (int n=0; n < 1024; n++)
		values[n]= new graphsamp_t();
	}

	/*
	==============
	SCR_DebugGraph
	==============
	*/
	public static void DebugGraph(float value, int color) {
		values[current&1023].value = value;
		values[current&1023].color = color;
		current++;
	}

	/*
	==============
	SCR_DrawDebugGraph
	==============
	*/
	static void DrawDebugGraph() {
		int a, x, y, w, i, h;
		float v;
		int color;

		// draw the graph

		w = scr_vrect.width;

		x = scr_vrect.x;
		y = scr_vrect.y + scr_vrect.height;
		re.DrawFill(x, (int) (y - scr_graphheight.value), w, (int)scr_graphheight.value, 8);

		for (a = 0; a < w; a++) {
			i = (current - 1 - a + 1024) & 1023;
			v = values[i].value;
			color = values[i].color;
			v = v * scr_graphscale.value + scr_graphshift.value;

			if (v < 0)
				v += scr_graphheight.value * (1 + (int) (-v / scr_graphheight.value));
			h = (int)v % (int)scr_graphheight.value;
			re.DrawFill(x + w - 1 - a, y - h, 1, h, color);
		}
	}

	/*
	===============================================================================

	CENTER PRINTING

	===============================================================================
	*/

	// char scr_centerstring[1024];
	static String scr_centerstring;
	static float scr_centertime_start;	// for slow victory printing
	static float scr_centertime_off;
	static int scr_center_lines;
	static int scr_erase_center;

	/*
	==============
	SCR_CenterPrint

	Called for important messages that should stay in the center of the screen
	for a few moments
	==============
	*/
	static void CenterPrint(String str) {
		//char	*s;
		int s;
		StringBuffer line = new StringBuffer(64);
		int i, j, l;

		//strncpy (scr_centerstring, str, sizeof(scr_centerstring)-1);
		scr_centerstring = str;
		scr_centertime_off = scr_centertime.value;
		scr_centertime_start = cl.time;

		// count the number of lines for centering
		scr_center_lines = 1;
		s = 0;
		while (s < str.length()) {
			if (str.charAt(s) == '\n')
				scr_center_lines++;
			s++;
		}

		// echo it to the console
		Com.Printf(
			"\n\n\35\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\37\n\n");

		s = 0;
		
		if (str.length() != 0)
		{
			do {
				// scan the width of the line
				for (l = 0; l < 40 && (l + s) < str.length(); l++)
					if (str.charAt(s + l) == '\n' || str.charAt(s + l) == 0)
						break;
				for (i = 0; i < (40 - l) / 2; i++)
					line.append(' ');
	
				for (j = 0; j < l; j++) {
					line.append(str.charAt(s + j));
				}
	
				line.append('\n');
	
				Com.Printf(line.toString());
	
				while (s < str.length() && str.charAt(s) != '\n')
					s++;
	
				if (s == str.length())
					break;
				s++; // skip the \n
			}
			while (true);
		}
		Com.Printf(
			"\n\n\35\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\37\n\n");
		Console.ClearNotify();
	}

	static void DrawCenterString() {
		String cs = scr_centerstring;
		int start;
		int l;
		int j;
		int x, y;
		int remaining;

		if (cs == null)
			return;
		if (cs.length() == 0)
			return;

		// the finale prints the characters one at a time
		remaining = 9999;

		scr_erase_center = 0;
		start = 0;

		if (scr_center_lines <= 4)
			y = (int) (viddef.height * 0.35);
		else
			y = 48;

		do {
			// scan the width of the line
			for (l = 0; l < 40; l++)
				if (start + l == cs.length() - 1 || cs.charAt(start + l) == '\n')
					break;
			x = (viddef.width - l * 8) / 2;
			SCR.AddDirtyPoint(x, y);
			for (j = 0; j < l; j++, x += 8) {
				re.DrawChar(x, y, cs.charAt(start + j));
				if (remaining == 0)
					return;
				remaining--;
			}
			SCR.AddDirtyPoint(x, y + 8);

			y += 8;

			while (start < cs.length() && cs.charAt(start) != '\n')
				start++;

			if (start == cs.length())
				break;
			start++; // skip the \n
		}
		while (true);
	}

	static void CheckDrawCenterString()
	{
		scr_centertime_off -= cls.frametime;
	
		if (scr_centertime_off <= 0)
			return;

		DrawCenterString();
	}

// =============================================================================

	/*
	=================
	SCR_CalcVrect

	Sets scr_vrect, the coordinates of the rendered window
	=================
	*/
	static void CalcVrect()
	{
		int size;

		// bound viewsize
		if (scr_viewsize.value < 40)
			Cvar.Set ("viewsize","40");
		if (scr_viewsize.value > 100)
			Cvar.Set ("viewsize","100");

		size = (int)scr_viewsize.value;

		scr_vrect.width = viddef.width*size/100;
		scr_vrect.width &= ~7;

		scr_vrect.height = viddef.height*size/100;
		scr_vrect.height &= ~1;

		scr_vrect.x = (viddef.width - scr_vrect.width)/2;
		scr_vrect.y = (viddef.height - scr_vrect.height)/2;
	}


	/*
	=================
	SCR_SizeUp_f

	Keybinding command
	=================
	*/
	static void SizeUp_f()
	{
		Cvar.SetValue("viewsize",scr_viewsize.value+10);
	}


	/*
	=================
	SCR_SizeDown_f

	Keybinding command
	=================
	*/
	static void SizeDown_f()
	{
		Cvar.SetValue("viewsize",scr_viewsize.value-10);
	}

	/*
	=================
	SCR_Sky_f

	Set a specific sky and rotation speed
	=================
	*/
	static void Sky_f()
	{
		float	rotate;
		float[] axis = {0, 0, 0};

		if (Cmd.Argc() < 2)
		{
			Com.Printf("Usage: sky <basename> <rotate> <axis x y z>\n");
			return;
		}
		if (Cmd.Argc() > 2)
			rotate = Float.parseFloat(Cmd.Argv(2));
		else
			rotate = 0;
		if (Cmd.Argc() == 6)
		{
			axis[0] = Float.parseFloat(Cmd.Argv(3));
			axis[1] = Float.parseFloat(Cmd.Argv(4));
			axis[2] = Float.parseFloat(Cmd.Argv(5));
		}
		else
		{
			axis[0] = 0;
			axis[1] = 0;
			axis[2] = 1;
		}

		re.SetSky(Cmd.Argv(1), rotate, axis);
	}

// ============================================================================

	/*
	==================
	SCR_Init
	==================
	*/
	static void Init() {
		scr_viewsize = Cvar.Get("viewsize", "100", CVAR_ARCHIVE);
		scr_conspeed = Cvar.Get("scr_conspeed", "3", 0);
		scr_showturtle = Cvar.Get ("scr_showturtle", "0", 0);
		scr_showpause = Cvar.Get ("scr_showpause", "1", 0);
		scr_centertime = Cvar.Get ("scr_centertime", "2.5", 0);
		scr_printspeed = Cvar.Get ("scr_printspeed", "8", 0);
		scr_netgraph = Cvar.Get ("netgraph", "1", 0);
		scr_timegraph = Cvar.Get ("timegraph", "1", 0);
		scr_debuggraph = Cvar.Get ("debuggraph", "1", 0);
		scr_graphheight = Cvar.Get ("graphheight", "32", 0);
		scr_graphscale = Cvar.Get ("graphscale", "1", 0);
		scr_graphshift = Cvar.Get ("graphshift", "0", 0);
		scr_drawall = Cvar.Get ("scr_drawall", "1", 0);

		//
		// register our commands
		//
		Cmd.AddCommand ("timerefresh", new xcommand_t() {
			public void execute() {
				TimeRefresh_f();
			}
		});
		Cmd.AddCommand ("loading", new xcommand_t() {
			public void execute() {
				Loading_f();
			}
		});
		Cmd.AddCommand ("sizeup",  new xcommand_t() {
			public void execute() {
				SizeUp_f();
			}
		});
		Cmd.AddCommand ("sizedown",  new xcommand_t() {
			public void execute() {
				SizeDown_f();
			}
		});
		Cmd.AddCommand ("sky", new xcommand_t() {
			public void execute() {
				Sky_f();
			}
		});

		scr_initialized = true;
	}


	/*
	==============
	SCR_DrawNet
	==============
	*/
	static void DrawNet()
	{
		if (cls.netchan.outgoing_sequence - cls.netchan.incoming_acknowledged 
			< CMD_BACKUP - 1)
			return;

		re.DrawPic(scr_vrect.x+64, scr_vrect.y, "net");
	}

	/*
	==============
	SCR_DrawPause
	==============
	*/
	static void DrawPause()
	{
		Dimension dim = new Dimension();

		if (scr_showpause.value == 0)	// turn off for screenshots
			return;

		if (cl_paused.value == 0)
			return;

		re.DrawGetPicSize (dim, "pause");
		re.DrawPic ((viddef.width - dim.width) / 2, viddef.height / 2 + 8, "pause");
	}

	/*
	==============
	SCR_DrawLoading
	==============
	*/
	static void DrawLoading()
	{
		Dimension dim = new Dimension();
		
		if (scr_draw_loading == 0)
			return;

		scr_draw_loading = 0;
		re.DrawGetPicSize(dim, "loading");
		re.DrawPic ((viddef.width - dim.width)/2, (viddef.height - dim.height)/2, "loading");
	}

// =============================================================================

	/*
	==================
	SCR_RunConsole

	Scroll it up or down
	==================
	*/
	static void RunConsole() {
		// decide on the height of the console
		if (cls.key_dest == key_console)
			scr_conlines = 0.5f;		// half screen
		else
			scr_conlines = 0;				// none visible
	
		if (scr_conlines < scr_con_current)
		{
			scr_con_current -= scr_conspeed.value*cls.frametime;
			if (scr_conlines > scr_con_current)
				scr_con_current = scr_conlines;

		}
		else if (scr_conlines > scr_con_current)
		{
			scr_con_current += scr_conspeed.value*cls.frametime;
			if (scr_conlines < scr_con_current)
				scr_con_current = scr_conlines;
		}
	}

	/*
	==================
	SCR_DrawConsole
	==================
	*/
	static void DrawConsole()
	{
		Console.CheckResize();
	
		if (cls.state == ca_disconnected || cls.state == ca_connecting)
		{	// forced full screen console
			Console.DrawConsole(1.0f);
			return;
		}

		if (cls.state != ca_active || !cl.refresh_prepped)
		{	// connected, but can't render
			Console.DrawConsole(0.5f);
			re.DrawFill (0, viddef.height/2, viddef.width, viddef.height/2, 0);
			return;
		}

		if (scr_con_current != 0)
		{
			Console.DrawConsole(scr_con_current);
		}
		else
		{
			if (cls.key_dest == key_game || cls.key_dest == key_message)
			Console.DrawNotify();	// only draw notify in game
		}
	}

// =============================================================================

	/*
	================
	SCR_BeginLoadingPlaque
	================
	*/
	public static void BeginLoadingPlaque() {
		S.StopAllSounds ();
		cl.sound_prepped = false; // don't play ambients
		CDAudio.Stop();
		if (cls.disable_screen != 0)
			return;
		if (developer.value != 0)
			return;
		if (cls.state == ca_disconnected)
			return;	// if at console, don't bring up the plaque
		if (cls.key_dest == key_console)
			return;
		if (cl.cinematictime > 0)
			scr_draw_loading = 2;	// clear to balack first
		else
			scr_draw_loading = 1;
		UpdateScreen();
		cls.disable_screen = Sys.Milliseconds();
		cls.disable_servercount = cl.servercount;
	}

	/*
	================
	SCR_EndLoadingPlaque
	================
	*/
	public static void EndLoadingPlaque() {
		cls.disable_screen = 0;
		Console.ClearNotify();
	}

	/*
	================
	SCR_Loading_f
	================
	*/
	static void Loading_f()
	{
		BeginLoadingPlaque();
	}

	/*
	================
	SCR_TimeRefresh_f
	================
	*/
	static void TimeRefresh_f() {
		int i;
		int start, stop;
		float time;

		if (cls.state != ca_active)
			return;

		start = Sys.Milliseconds();

		if (Cmd.Argc() == 2) { // run without page flipping
			re.BeginFrame(0);
			for (i = 0; i < 128; i++) {
				cl.refdef.viewangles[1] = i / 128.0f * 360.0f;
				re.RenderFrame(cl.refdef);
			}
			re.EndFrame();
		} else {
			for (i = 0; i < 128; i++) {
				cl.refdef.viewangles[1] = i / 128.0f * 360.0f;

				re.BeginFrame(0);
				re.RenderFrame(cl.refdef);
				re.EndFrame();
			}
		}

		stop = Sys.Milliseconds();
		time = (stop - start) / 1000.0f;
		Com.Printf("%f seconds (%f fps)\n", new Vargs(2).add(time).add(128.0f / time));
	}

	static void DirtyScreen() {
		AddDirtyPoint(0, 0);
		AddDirtyPoint(viddef.width-1, viddef.height-1);
	}

	/*
	==============
	SCR_TileClear

	Clear any parts of the tiled background that were drawn on last frame
	==============
	*/
	static void TileClear()
	{
		int i;
		int top, bottom, left, right;
		dirty_t clear = new dirty_t();

		if (scr_drawall.value != 0)
			DirtyScreen (); // for power vr or broken page flippers...

		if (scr_con_current == 1.0f)
			return;		// full screen console
		if (scr_viewsize.value == 100)
			return;		// full screen rendering
		if (cl.cinematictime > 0)
			return;		// full screen cinematic

		// erase rect will be the union of the past three frames
		// so tripple buffering works properly
		clear = scr_dirty;
		for (i=0 ; i<2 ; i++)
		{
			if (scr_old_dirty[i].x1 < clear.x1)
				clear.x1 = scr_old_dirty[i].x1;
			if (scr_old_dirty[i].x2 > clear.x2)
				clear.x2 = scr_old_dirty[i].x2;
			if (scr_old_dirty[i].y1 < clear.y1)
				clear.y1 = scr_old_dirty[i].y1;
			if (scr_old_dirty[i].y2 > clear.y2)
				clear.y2 = scr_old_dirty[i].y2;
		}

		scr_old_dirty[1] = scr_old_dirty[0];
		scr_old_dirty[0] = scr_dirty;

		scr_dirty.x1 = 9999;
		scr_dirty.x2 = -9999;
		scr_dirty.y1 = 9999;
		scr_dirty.y2 = -9999;

		// don't bother with anything convered by the console)
		top = (int)(scr_con_current * viddef.height);
		if (top >= clear.y1)
			clear.y1 = top;

		if (clear.y2 <= clear.y1)
			return;		// nothing disturbed

		top = scr_vrect.y;
		bottom = top + scr_vrect.height-1;
		left = scr_vrect.x;
		right = left + scr_vrect.width-1;

		if (clear.y1 < top)
		{	// clear above view screen
			i = clear.y2 < top-1 ? clear.y2 : top-1;
			re.DrawTileClear (clear.x1 , clear.y1,
				clear.x2 - clear.x1 + 1, i - clear.y1+1, "backtile");
			clear.y1 = top;
		}
		if (clear.y2 > bottom)
		{	// clear below view screen
			i = clear.y1 > bottom+1 ? clear.y1 : bottom+1;
			re.DrawTileClear (clear.x1, i,
				clear.x2-clear.x1+1, clear.y2-i+1, "backtile");
			clear.y2 = bottom;
		}
		if (clear.x1 < left)
		{	// clear left of view screen
			i = clear.x2 < left-1 ? clear.x2 : left-1;
			re.DrawTileClear (clear.x1, clear.y1,
				i-clear.x1+1, clear.y2 - clear.y1 + 1, "backtile");
			clear.x1 = left;
		}
		if (clear.x2 > right)
		{	// clear left of view screen
			i = clear.x1 > right+1 ? clear.x1 : right+1;
			re.DrawTileClear (i, clear.y1,
				clear.x2-i+1, clear.y2 - clear.y1 + 1, "backtile");
			clear.x2 = right;
		}

	}


// ===============================================================

	static final int STAT_MINUS = 10; // num frame for '-' stats digit

	static final int ICON_WIDTH	= 24;
	static final int ICON_HEIGHT = 24;
	static final int CHAR_WIDTH = 16;
	static final int ICON_SPACE = 8;

	/*
	================
	SizeHUDString

	Allow embedded \n in the string
	================
	*/
	static void SizeHUDString(String string, Dimension dim) {
		int lines, width, current;

		lines = 1;
		width = 0;

		current = 0;
		for (int i = 0; i < string.length(); i++) {
			if (string.charAt(i) == '\n') {
				lines++;
				current = 0;
			} else {
				current++;
				if (current > width)
					width = current;
			}

		}

		dim.width = width * 8;
		dim.height = lines * 8;
	}

	static void DrawHUDString(String string, int x, int y, int centerwidth, int xor) {
		int margin;
		//char	line[1024];
		StringBuffer line = new StringBuffer(1024);
		int i;

		margin = x;

		for (int l = 0; l < string.length();) {
			// scan out one line of text from the string
			line = new StringBuffer(1024);
			while (string.charAt(l) != '\n')
				line.append(string.charAt(l));
			l++;

			if (centerwidth != 0)
				x = margin + (centerwidth - line.length() * 8) / 2;
			else
				x = margin;
			for (i = 0; i < line.length(); i++) {
				re.DrawChar(x, y, line.charAt(i) ^ xor);
				x += 8;
			}
			if (l < string.length()) {
				l++; // skip the \n
				x = margin;
				y += 8;
			}
		}
	}


	/*
	==============
	SCR_DrawField
	==============
	*/
	static void DrawField(int x, int y, int color, int width, int value)
	{
		char ptr;
		int ptrp;
		String num;
		int l;
		int frame;

		if (width < 1)
			return;

		// draw number string
		if (width > 5)
			width = 5;

		AddDirtyPoint(x, y);
		AddDirtyPoint(x + width * CHAR_WIDTH + 2, y + 23);

		num = "" + value;
		l = num.length();
		if (l > width)
			l = width;
		x += 2 + CHAR_WIDTH*(width - l);

		ptr = num.charAt(0);
		ptrp = 0;
		for (int i = 0; i < l; i++)
		{
			ptr = num.charAt(i);
			if (ptr == '-')
				frame = STAT_MINUS;
			else
				frame = ptr - '0';

			re.DrawPic (x,y,sb_nums[color][frame]);
			x += CHAR_WIDTH;
		}
	}

	/*
	===============
	SCR_TouchPics

	Allows rendering code to cache all needed sbar graphics
	===============
	*/
	static void TouchPics() {
		int i, j;
 
		for (i=0 ; i<2 ; i++)
			for (j=0 ; j<11 ; j++)
				re.RegisterPic(sb_nums[i][j]);
 
		if (crosshair.value != 0.0f) {
			if (crosshair.value > 3.0f || crosshair.value < 0.0f)
				crosshair.value = 3.0f;
 
		crosshair_pic = "ch" + (int)crosshair.value;
		Dimension dim = new Dimension();
		re.DrawGetPicSize(dim, crosshair_pic);
		crosshair_width = dim.width;
		crosshair_height = dim.height;
		if (crosshair_width == 0)
			crosshair_pic = "";
		}
	}


	/*
	================
	SCR_ExecuteLayoutString 

	================
	*/
	static void ExecuteLayoutString(String s)
	{
		int		x, y;
		int		value;
		String token;
		int		width;
		int		index;
		clientinfo_t	ci;

		if (cls.state != ca_active || !cl.refresh_prepped)
			return;

//		if (!s[0])
		if (s == null || s.length() == 0)
			return;

		x = 0;
		y = 0;
		width = 3;
		
		Com.ParseHelp ph = new Com.ParseHelp(s);

		while (!ph.isEof())
		{
			token = Com.Parse(ph);
			if (token.equals("xl"))
			{
				token = Com.Parse(ph);
				x = atoi(token);
				continue;
			}
			if (token.equals("xr"))
			{
				token = Com.Parse(ph);
				x = viddef.width + atoi(token);
				continue;
			}
			if (token.equals("xv"))
			{
				token = Com.Parse(ph);
				x = viddef.width/2 - 160 + atoi(token);
				continue;
			}

			if (token.equals("yt"))
			{
				token = Com.Parse(ph);
				y = atoi(token);
				continue;
			}
			if (token.equals("yb"))
			{
				token = Com.Parse(ph);
				y = viddef.height + atoi(token);
				continue;
			}
			if (token.equals("yv"))
			{
				token = Com.Parse(ph);
				y = viddef.height/2 - 120 + atoi(token);
				continue;
			}

			if (token.equals("pic"))
			{	// draw a pic from a stat number
				token = Com.Parse(ph);
				value = cl.frame.playerstate.stats[atoi(token)];
				if (value >= MAX_IMAGES)
					Com.Error (ERR_DROP, "Pic >= MAX_IMAGES");
				if (cl.configstrings[CS_IMAGES+value] != null)
				{
					AddDirtyPoint (x, y);
					AddDirtyPoint (x+23, y+23);
					re.DrawPic (x, y, cl.configstrings[CS_IMAGES+value]);
				}
				continue;
			}

			if (token.equals("client"))
			{	// draw a deathmatch client block
				int		score, ping, time;

				token = Com.Parse(ph);
				x = viddef.width/2 - 160 + atoi(token);
				token = Com.Parse(ph);
				y = viddef.height/2 - 120 + atoi(token);
				AddDirtyPoint (x, y);
				AddDirtyPoint (x+159, y+31);

				token = Com.Parse(ph);
				value = atoi(token);
				if (value >= MAX_CLIENTS || value < 0)
					Com.Error (ERR_DROP, "client >= MAX_CLIENTS");
				ci = cl.clientinfo[value];

				token = Com.Parse(ph);
				score = atoi(token);

				token = Com.Parse(ph);
				ping = atoi(token);

				token = Com.Parse(ph);
				time = atoi(token);

				Console.DrawAltString (x+32, y, ci.name);
				Console.DrawString (x+32, y+8,  "Score: ");
				Console.DrawAltString (x+32+7*8, y+8,  "" + score);
				Console.DrawString (x+32, y+16, "Ping:  " + ping);
				Console.DrawString (x+32, y+24, "Time:  " + time);

				if (ci.icon == null)
					ci = cl.baseclientinfo;
				re.DrawPic (x, y, ci.iconname);
				continue;
			}

			if (token.equals("ctf"))
			{	// draw a ctf client block
				int		score, ping;

				token = Com.Parse(ph);
				x = viddef.width/2 - 160 + atoi(token);
				token = Com.Parse(ph);
				y = viddef.height/2 - 120 + atoi(token);
				AddDirtyPoint (x, y);
				AddDirtyPoint (x+159, y+31);

				token = Com.Parse(ph);
				value = atoi(token);
				if (value >= MAX_CLIENTS || value < 0)
					Com.Error (ERR_DROP, "client >= MAX_CLIENTS");
				ci = cl.clientinfo[value];

				token = Com.Parse(ph);
				score = atoi(token);

				token = Com.Parse(ph);
				ping = atoi(token);
				if (ping > 999)
					ping = 999;

				// sprintf(block, "%3d %3d %-12.12s", score, ping, ci->name);
				String block = Com.sprintf("%3d %3d %-12.12s", new Vargs(3).add(score).add(ping).add(ci.name));

				if (value == cl.playernum)
					Console.DrawAltString (x, y, block);
				else
					Console.DrawString (x, y, block);
				continue;
			}

			if (token.equals("picn"))
			{	// draw a pic from a name
				token = Com.Parse(ph);
				AddDirtyPoint (x, y);
				AddDirtyPoint (x+23, y+23);
				re.DrawPic (x, y, token);
				continue;
			}

			if (token.equals("num"))
			{	// draw a number
				token = Com.Parse(ph);
				width = atoi(token);
				token = Com.Parse(ph);
				value = cl.frame.playerstate.stats[atoi(token)];
				DrawField (x, y, 0, width, value);
				continue;
			}

			if (token.equals("hnum"))
			{	// health number
				int		color;

				width = 3;
				value = cl.frame.playerstate.stats[STAT_HEALTH];
				if (value > 25)
					color = 0;	// green
				else if (value > 0)
					color = (cl.frame.serverframe>>2) & 1;		// flash
				else
					color = 1;

				if ((cl.frame.playerstate.stats[STAT_FLASHES] & 1) != 0)
					re.DrawPic (x, y, "field_3");

				DrawField (x, y, color, width, value);
				continue;
			}

			if (token.equals("anum"))
			{	// ammo number
				int		color;

				width = 3;
				value = cl.frame.playerstate.stats[STAT_AMMO];
				if (value > 5)
					color = 0;	// green
				else if (value >= 0)
					color = (cl.frame.serverframe>>2) & 1;		// flash
				else
					continue;	// negative number = don't show

				if ((cl.frame.playerstate.stats[STAT_FLASHES] & 4) != 0)
					re.DrawPic (x, y, "field_3");

				DrawField (x, y, color, width, value);
				continue;
			}

			if (token.equals("rnum"))
			{	// armor number
				int		color;

				width = 3;
				value = cl.frame.playerstate.stats[STAT_ARMOR];
				if (value < 1)
					continue;

				color = 0;	// green

				if ((cl.frame.playerstate.stats[STAT_FLASHES] & 2) != 0)
					re.DrawPic (x, y, "field_3");

				DrawField (x, y, color, width, value);
				continue;
			}


			if (token.equals("stat_string"))
			{
				token = Com.Parse(ph);
				index = atoi(token);
				if (index < 0 || index >= MAX_CONFIGSTRINGS)
					Com.Error (ERR_DROP, "Bad stat_string index");
				index = cl.frame.playerstate.stats[index];
				if (index < 0 || index >= MAX_CONFIGSTRINGS)
					Com.Error (ERR_DROP, "Bad stat_string index");
				Console.DrawString (x, y, cl.configstrings[index]);
				continue;
			}

			if (token.equals("cstring"))
			{
				token = Com.Parse(ph);
				DrawHUDString (token, x, y, 320, 0);
				continue;
			}

			if (token.equals("string"))
			{
				token = Com.Parse(ph);
				Console.DrawString (x, y, token);
				continue;
			}

			if (token.equals("cstring2"))
			{
				token = Com.Parse(ph);
				DrawHUDString (token, x, y, 320,0x80);
				continue;
			}

			if (token.equals("string2"))
			{
				token = Com.Parse(ph);
				Console.DrawAltString (x, y, token);
				continue;
			}

			if (token.equals("if"))
			{	// draw a number
				token = Com.Parse(ph);
				value = cl.frame.playerstate.stats[atoi(token)];
				if (value == 0)
				{	// skip to endif
//					while (s && strcmp(token, "endif") )
//					{
//						token = Com.Parse(ph);
//					}
					
					while (!ph.isEof() && !(token = Com.Parse(ph)).equals("endif"));

				}

				continue;
			}


		}
	}

	/*
	================
	SCR_DrawStats

	The status bar is a small layout program that
	is based on the stats array
	================
	*/
	static void DrawStats() {
		SCR.ExecuteLayoutString(cl.configstrings[CS_STATUSBAR]);
	}

	/*
	================
	SCR_DrawLayout

	================
	*/
	static final int STAT_LAYOUTS = 13;

	static void DrawLayout() {
		if (cl.frame.playerstate.stats[STAT_LAYOUTS] != 0)
			return;
		SCR.ExecuteLayoutString(cl.layout);
	}

	// =======================================================

	/*
	==================
	SCR_UpdateScreen

	This is called every frame, and can also be called explicitly to flush
	text to the screen.
	==================
	*/
	static void UpdateScreen2()
	{
		int numframes;
		int i;
		float[] separation = { 0, 0 };
		
		// if the screen is disabled (loading plaque is up, or vid mode changing)
		// do nothing at all
		if (cls.disable_screen != 0)
		{
			if (Sys.Milliseconds() - cls.disable_screen > 120000)
			{
				cls.disable_screen = 0;
				Com.Printf("Loading plaque timed out.\n");
			}
			return;
		}

		if (!scr_initialized || !con.initialized)
			return;				// not initialized yet

		/*
		** range check cl_camera_separation so we don't inadvertently fry someone's
		** brain
		*/
		if ( cl_stereo_separation.value > 1.0 )
			Cvar.SetValue( "cl_stereo_separation", 1.0f );
		else if ( cl_stereo_separation.value < 0 )
			Cvar.SetValue( "cl_stereo_separation", 0.0f );

		if ( cl_stereo.value != 0 )
		{
			numframes = 2;
			separation[0] = -cl_stereo_separation.value / 2;
			separation[1] =  cl_stereo_separation.value / 2;
		}		
		else
		{
			separation[0] = 0;
			separation[1] = 0;
			numframes = 1;
		}

		for ( i = 0; i < numframes; i++ )
		{
			re.BeginFrame( separation[i] );

			if (scr_draw_loading == 2)
			{	//  loading plaque over black screen
				Dimension dim = new Dimension();

				re.CinematicSetPalette(null);
				scr_draw_loading = 0; // false
				re.DrawGetPicSize (dim, "loading");
				re.DrawPic ((viddef.width-dim.width)/2, (viddef.height-dim.height)/2, "loading");
			} 
			// if a cinematic is supposed to be running, handle menus
			// and console specially
			else if (cl.cinematictime > 0)
			{
				if (cls.key_dest == key_menu)
				{
					if (cl.cinematicpalette_active)
					{
						re.CinematicSetPalette(null);
						cl.cinematicpalette_active = false;
					}
					Menu.Draw();
				}
				else if (cls.key_dest == key_console)
				{
					if (cl.cinematicpalette_active)
					{
						re.CinematicSetPalette(null);
						cl.cinematicpalette_active = false;
					}
					DrawConsole();
				}
				else
				{
					// TODO impl: cl_cin.c for cinematics
					//DrawCinematic();
				}
			}
			else 
			{
				// make sure the game palette is active
				if (cl.cinematicpalette_active)
				{
					re.CinematicSetPalette(null);
					cl.cinematicpalette_active = false;
				}

				// do 3D refresh drawing, and then update the screen
				CalcVrect();

				// clear any dirty part of the background
				TileClear();

				V.RenderView( separation[i] );

				DrawStats();
				// TODO impl this
				if ((cl.frame.playerstate.stats[STAT_LAYOUTS] & 1) != 0)
					DrawLayout();
				if ((cl.frame.playerstate.stats[STAT_LAYOUTS] & 2) != 0)
					CL.DrawInventory();

				DrawNet();
				CheckDrawCenterString();
//
//				if (scr_timegraph->value)
//					SCR_DebugGraph (cls.frametime*300, 0);
//
//				if (scr_debuggraph->value || scr_timegraph->value || scr_netgraph->value)
//					SCR_DrawDebugGraph ();
//
				DrawPause();

				DrawConsole();

				Menu.Draw ();

				DrawLoading();
			}
		}
		Globals.re.EndFrame();
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
		Globals.re.updateScreen(null);
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

	
	public static void PlayCinematic(String str) {
		// TODO: implement PlayCinematic		
	}
	
	static void FinishCinematic() {
		// tell the server to advance to the next map / cinematic
		MSG.WriteByte(cls.netchan.message, clc_stringcmd);
		SZ.Print(cls.netchan.message, "nextserver " + cl.servercount + '\n');
	}
}