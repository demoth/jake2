/*
 * Globals.java
 * Copyright (C) 2003
 * 
 * $Id: Globals.java,v 1.10 2003-11-29 13:48:49 rst Exp $
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

import jake2.client.*;
import jake2.client.client_static_t;
import jake2.game.*;
import jake2.qcommon.sizebuf_t;
import jake2.client.*;

import java.io.FileWriter;

/**
 * Globals ist the collection of global variables and constants.
 * It is more elegant to use these vars by inheritance to separate 
 * it with eclipse refactoring later.
 * 
 * As consequence you dont have to touch that much code this time. 
 */
public class Globals {

	/*
	 * global constants
	 */
	public static final int ERR_FATAL = 0;
	public static final int MAX_ALIAS_NAME = 32;
	public static final int MAX_NUM_ARGVS = 50;
	public static final int MAX_TOKEN_CHARS = 128;
	public static final int MAX_STRING_TOKENS = 80;
	public static final int MAX_MSGLEN = 1400;

	public static final String BUILDSTRING = "Java";
	public static final String CPUSTRING = "jvm";
	public static final String __DATE__ = "2003";

	public static final float VERSION = 3.21f;

	public static final String BASEDIRNAME = "baseq2";

	/*
	 * global variables
	 */
	public static boolean bigendien = false;
	public static boolean cmd_wait;

	public static int com_argc;
	public static int c_traces;
	public static int c_brush_traces;
	public static int c_pointcontents;

	public static String[] com_argv = new String[MAX_NUM_ARGVS];

	public static cvar_t adr0;
	public static cvar_t adr1;
	public static cvar_t adr2;
	public static cvar_t adr3;
	public static cvar_t adr4;
	public static cvar_t adr5;
	public static cvar_t adr6;
	public static cvar_t adr7;
	public static cvar_t adr8;
	public static cvar_t cl_add_blend;
	public static cvar_t cl_add_entities;
	public static cvar_t cl_add_lights;
	public static cvar_t cl_add_particles;
	public static cvar_t cl_anglespeedkey;
	public static cvar_t cl_autoskins;
	public static cvar_t cl_footsteps;
	public static cvar_t cl_forwardspeed;
	public static cvar_t cl_gun;
	public static cvar_t cl_maxfps;
	public static cvar_t cl_noskins;
	public static cvar_t cl_pitchspeed;
	public static cvar_t cl_predict;
	public static cvar_t cl_run;
	public static cvar_t cl_sidespeed;
	public static cvar_t cl_stereo;
	public static cvar_t cl_stereo_separation;
	public static cvar_t cl_timedemo;
	public static cvar_t cl_timeout;
	public static cvar_t cl_upspeed;
	public static cvar_t cl_yawspeed;
	public static cvar_t dedicated;
	public static cvar_t developer;
	public static cvar_t fixedtime;
	public static cvar_t freelook;
	public static cvar_t host_speeds;
	public static cvar_t log_stats;
	public static cvar_t logfile_active;
	public static cvar_t lookspring;
	public static cvar_t lookstrafe;
	public static cvar_t nostdout;
	public static cvar_t sensitivity;
	public static cvar_t showtrace;
	public static cvar_t timescale;

	public static sizebuf_t cmd_text;
	public static sizebuf_t net_message;

	public static byte[] cmd_text_buf = new byte[8192];
	public static byte[] net_message_buffer = new byte[MAX_MSGLEN];

	public static cmdalias_t cmd_alias;

	public static long time_before_game;
	public static long time_after_game;
	public static long time_before_ref;
	public static long time_after_ref;

	public static FileWriter log_stats_file = null;

	public static EndianHandler endian = null;

	public static cvar_t m_pitch;
	public static cvar_t m_yaw;
	public static cvar_t m_forward;
	public static cvar_t m_side;

	public static cvar_t cl_lightlevel;

	//
	//	   userinfo
	//
	public static cvar_t info_password;
	public static cvar_t info_spectator;
	public static cvar_t name;
	public static cvar_t skin;
	public static cvar_t rate;
	public static cvar_t fov;
	public static cvar_t msg;
	public static cvar_t hand;
	public static cvar_t gender;
	public static cvar_t gender_auto;

	public static cvar_t cl_vwep;

	public static client_static_t cls;
	public static client_state_t cl;

	public static centity_t cl_entities[] = new centity_t[Defines.MAX_EDICTS];

	public static entity_state_t cl_parse_entities[] = new entity_state_t[Defines.MAX_PARSE_ENTITIES];

}
