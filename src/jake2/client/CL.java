/*
 * CL.java
 * Copyright (C) 2003
 * 
 * $Id: CL.java,v 1.7 2003-11-28 23:07:38 rst Exp $
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

import jake2.Enums;
import jake2.Globals;
import jake2.qcommon.*;
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
		Globals.cls.state = Enums.ca_disconnected;
		Globals.cls.realtime = System.currentTimeMillis();
		
		InitInput();

		Globals.adr0 = Cvar.Get( "adr0", "", Cvar.ARCHIVE );
		Globals.adr1 = Cvar.Get( "adr0", "", Cvar.ARCHIVE );
		Globals.adr2 = Cvar.Get( "adr0", "", Cvar.ARCHIVE );
		Globals.adr3 = Cvar.Get( "adr0", "", Cvar.ARCHIVE );
		Globals.adr4 = Cvar.Get( "adr0", "", Cvar.ARCHIVE );
		Globals.adr5 = Cvar.Get( "adr0", "", Cvar.ARCHIVE );
		Globals.adr6 = Cvar.Get( "adr0", "", Cvar.ARCHIVE );
		Globals.adr7 = Cvar.Get( "adr0", "", Cvar.ARCHIVE );
		Globals.adr8 = Cvar.Get( "adr0", "", Cvar.ARCHIVE );

		//
		// register our variables
		//
		Globals.cl_stereo_separation = Cvar.Get( "cl_stereo_separation", "0.4", Cvar.ARCHIVE );
		Globals.cl_stereo = Cvar.Get( "cl_stereo", "0", 0 );

		Globals.cl_add_blend = Cvar.Get ("cl_blend", "1", 0);
		Globals.cl_add_lights = Cvar.Get ("cl_lights", "1", 0);
		Globals.cl_add_particles = Cvar.Get ("cl_particles", "1", 0);
		Globals.cl_add_entities = Cvar.Get ("cl_entities", "1", 0);
		Globals.cl_gun = Cvar.Get ("cl_gun", "1", 0);
		Globals.cl_footsteps = Cvar.Get ("cl_footsteps", "1", 0);
		Globals.cl_noskins = Cvar.Get ("cl_noskins", "0", 0);
		Globals.cl_autoskins = Cvar.Get ("cl_autoskins", "0", 0);
		Globals.cl_predict = Cvar.Get ("cl_predict", "1", 0);

		Globals.cl_maxfps = Cvar.Get ("cl_maxfps", "90", 0);

		Globals.cl_upspeed = Cvar.Get ("cl_upspeed", "200", 0);
		Globals.cl_forwardspeed = Cvar.Get ("cl_forwardspeed", "200", 0);
		Globals.cl_sidespeed = Cvar.Get ("cl_sidespeed", "200", 0);
		Globals.cl_yawspeed = Cvar.Get ("cl_yawspeed", "140", 0);
		Globals.cl_pitchspeed = Cvar.Get ("cl_pitchspeed", "150", 0);
		Globals.cl_anglespeedkey = Cvar.Get ("cl_anglespeedkey", "1.5", 0);

		Globals.cl_run = Cvar.Get ("cl_run", "0", Cvar.ARCHIVE);
		Globals.freelook = Cvar.Get( "freelook", "0", Cvar.ARCHIVE );
		Globals.lookspring = Cvar.Get ("lookspring", "0", Cvar.ARCHIVE);
		Globals.lookstrafe = Cvar.Get ("lookstrafe", "0", Cvar.ARCHIVE);
		Globals.sensitivity = Cvar.Get ("sensitivity", "3", Cvar.ARCHIVE);

//		01453         m_pitch = Cvar_Get ("m_pitch", "0.022", CVAR_ARCHIVE);
//		01454         m_yaw = Cvar_Get ("m_yaw", "0.022", 0);
//		01455         m_forward = Cvar_Get ("m_forward", "1", 0);
//		01456         m_side = Cvar_Get ("m_side", "1", 0);
//		01457 
//		01458         cl_shownet = Cvar_Get ("cl_shownet", "0", 0);
//		01459         cl_showmiss = Cvar_Get ("cl_showmiss", "0", 0);
//		01460         cl_showclamp = Cvar_Get ("showclamp", "0", 0);
//		01461         cl_timeout = Cvar_Get ("cl_timeout", "120", 0);
//		01462         cl_paused = Cvar_Get ("paused", "0", 0);
//		01463         cl_timedemo = Cvar_Get ("timedemo", "0", 0);
//		01464 
//		01465         rcon_client_password = Cvar_Get ("rcon_password", "", 0);
//		01466         rcon_address = Cvar_Get ("rcon_address", "", 0);
//		01467 
//		01468         cl_lightlevel = Cvar_Get ("r_lightlevel", "0", 0);
//		01469 
//		01470         //
//		01471         // userinfo
//		01472         //
//		01473         info_password = Cvar_Get ("password", "", CVAR_USERINFO);
//		01474         info_spectator = Cvar_Get ("spectator", "0", CVAR_USERINFO);
//		01475         name = Cvar_Get ("name", "unnamed", CVAR_USERINFO | CVAR_ARCHIVE);
//		01476         skin = Cvar_Get ("skin", "male/grunt", CVAR_USERINFO | CVAR_ARCHIVE);
//		01477         rate = Cvar_Get ("rate", "25000", CVAR_USERINFO | CVAR_ARCHIVE);        // FIXME
//		01478         msg = Cvar_Get ("msg", "1", CVAR_USERINFO | CVAR_ARCHIVE);
//		01479         hand = Cvar_Get ("hand", "0", CVAR_USERINFO | CVAR_ARCHIVE);
//		01480         fov = Cvar_Get ("fov", "90", CVAR_USERINFO | CVAR_ARCHIVE);
//		01481         gender = Cvar_Get ("gender", "male", CVAR_USERINFO | CVAR_ARCHIVE);
//		01482         gender_auto = Cvar_Get ("gender_auto", "1", CVAR_ARCHIVE);
//		01483         gender->modified = false; // clear this so we know when user sets it manually
//		01484 
//		01485         cl_vwep = Cvar_Get ("cl_vwep", "1", CVAR_ARCHIVE);
//		01486 
//		01487 
//		01488         //
//		01489         // register our commands
//		01490         //
//		01491         Cmd_AddCommand ("cmd", CL_ForwardToServer_f);
//		01492         Cmd_AddCommand ("pause", CL_Pause_f);
//		01493         Cmd_AddCommand ("pingservers", CL_PingServers_f);
//		01494         Cmd_AddCommand ("skins", CL_Skins_f);
//		01495 
//		01496         Cmd_AddCommand ("userinfo", CL_Userinfo_f);
//		01497         Cmd_AddCommand ("snd_restart", CL_Snd_Restart_f);
//		01498 
//		01499         Cmd_AddCommand ("changing", CL_Changing_f);
//		01500         Cmd_AddCommand ("disconnect", CL_Disconnect_f);
//		01501         Cmd_AddCommand ("record", CL_Record_f);
//		01502         Cmd_AddCommand ("stop", CL_Stop_f);
//		01503 
//		01504         Cmd_AddCommand ("quit", CL_Quit_f);
//		01505 
//		01506         Cmd_AddCommand ("connect", CL_Connect_f);
//		01507         Cmd_AddCommand ("reconnect", CL_Reconnect_f);
//		01508 
//		01509         Cmd_AddCommand ("rcon", CL_Rcon_f);
//		01510 
//		01511 //      Cmd_AddCommand ("packet", CL_Packet_f); // this is dangerous to leave in
//		01512 
//		01513         Cmd_AddCommand ("setenv", CL_Setenv_f );
//		01514 
//		01515         Cmd_AddCommand ("precache", CL_Precache_f);
//		01516 
//		01517         Cmd_AddCommand ("download", CL_Download_f);
//		01518 
//		01519         //
//		01520         // forward to server commands
//		01521         //
//		01522         // the only thing this does is allow command completion
//		01523         // to work -- all unknown commands are automatically
//		01524         // forwarded to the server
//		01525         Cmd_AddCommand ("wave", NULL);
//		01526         Cmd_AddCommand ("inven", NULL);
//		01527         Cmd_AddCommand ("kill", NULL);
//		01528         Cmd_AddCommand ("use", NULL);
//		01529         Cmd_AddCommand ("drop", NULL);
//		01530         Cmd_AddCommand ("say", NULL);
//		01531         Cmd_AddCommand ("say_team", NULL);
//		01532         Cmd_AddCommand ("info", NULL);
//		01533         Cmd_AddCommand ("prog", NULL);
//		01534         Cmd_AddCommand ("give", NULL);
//		01535         Cmd_AddCommand ("god", NULL);
//		01536         Cmd_AddCommand ("notarget", NULL);
//		01537         Cmd_AddCommand ("noclip", NULL);
//		01538         Cmd_AddCommand ("invuse", NULL);
//		01539         Cmd_AddCommand ("invprev", NULL);
//		01540         Cmd_AddCommand ("invnext", NULL);
//		01541         Cmd_AddCommand ("invdrop", NULL);
//		01542         Cmd_AddCommand ("weapnext", NULL);
//		01543         Cmd_AddCommand ("weapprev", NULL);		
	}
	
	public static void InitInput() {
	}
}
