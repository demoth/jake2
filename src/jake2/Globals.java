/*
 * Globals.java
 * Copyright (C) 2003
 * 
 * $Id: Globals.java,v 1.2 2003-11-18 21:11:29 hoz Exp $
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
package jake2;

import jake2.game.EndianHandler;
import jake2.game.cvar_t;
import jake2.qcommon.sizebuf_t;

import java.io.FileWriter;

/**
 * Globals ist the collection of global variables and constants.
 */
public final class Globals {

	/*
	 * global constants
	 */
	public static final int ERR_FATAL = 0;
	public static final int MAX_NUM_ARGVS = 50;
	public static final int MAX_TOKEN_CHARS = 128;
	public static final int MAX_STRING_TOKENS = 80;

	public static final String BUILDSTRING = "Java";
	public static final String CPUSTRING = "jvm";
	public static final String __DATE__ = "2003";

	public static final float VERSION = 3.21f;
	
	/*
	 * global variables
	 */
	public static boolean bigendien = false;
	public static boolean cmd_wait;
	
	public static int com_argc;
	public static int c_traces;
	public static int c_brush_traces;
	public static int c_pointcontents;
	
	public static String[] com_argv;

	public static cvar_t dedicated;
	public static cvar_t developer;
	public static cvar_t fixedtime;
	public static cvar_t host_speeds;
	public static cvar_t log_stats;
	public static cvar_t logfile_active;
	public static cvar_t nostdout;
	public static cvar_t showtrace;
	public static cvar_t timescale;
	
	public static sizebuf_t cmd_text;
	
	public static byte[] cmd_text_buf = new byte[8192];

	public static long time_before_game;
	public static long time_after_game;
	public static long time_before_ref;
	public static long time_after_ref;

	public static FileWriter log_stats_file = null;
	
	public static EndianHandler endian = null;

	static {
		com_argv = new String[MAX_NUM_ARGVS];
	}
}
