/*
 * CL_main.java
 * Copyright (C) 2004
 * 
 * $Id: CL_main.java,v 1.1 2004-01-25 23:10:25 hoz Exp $
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

import java.io.IOException;
import java.io.RandomAccessFile;

import jake2.Defines;
import jake2.Globals;

import jake2.game.Cmd;
import jake2.game.entity_state_t;
import jake2.qcommon.*;
import jake2.qcommon.Cbuf;
import jake2.qcommon.FS;
import jake2.sys.CDAudio;
import jake2.sys.IN;

/**
 * CL_main
 */
public class CL_main extends CL_input {
//	/*
//	Copyright (C) 1997-2001 Id Software, Inc.
//
//	This program is free software; you can redistribute it and/or
//	modify it under the terms of the GNU General Public License
//	as published by the Free Software Foundation; either version 2
//	of the License, or (at your option) any later version.
//
//	This program is distributed in the hope that it will be useful,
//	but WITHOUT ANY WARRANTY; without even the implied warranty of
//	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  
//
//	See the GNU General Public License for more details.
//
//	You should have received a copy of the GNU General Public License
//	along with this program; if not, write to the Free Software
//	Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
//	*/
////	   cl_main.c  -- client main loop
//
//	#include "client.h"
//
//	cvar_t	*freelook;
//
//	cvar_t	*adr0;
//	cvar_t	*adr1;
//	cvar_t	*adr2;
//	cvar_t	*adr3;
//	cvar_t	*adr4;
//	cvar_t	*adr5;
//	cvar_t	*adr6;
//	cvar_t	*adr7;
//	cvar_t	*adr8;
//
//	cvar_t	*cl_stereo_separation;
//	cvar_t	*cl_stereo;
//
//	cvar_t	*rcon_client_password;
//	cvar_t	*rcon_address;
//
//	cvar_t	*cl_noskins;
//	cvar_t	*cl_autoskins;
//	cvar_t	*cl_footsteps;
//	cvar_t	*cl_timeout;
//	cvar_t	*cl_predict;
////	  cvar_t	*cl_minfps;
//	cvar_t	*cl_maxfps;
//	cvar_t	*cl_gun;
//
//	cvar_t	*cl_add_particles;
//	cvar_t	*cl_add_lights;
//	cvar_t	*cl_add_entities;
//	cvar_t	*cl_add_blend;
//
//	cvar_t	*cl_shownet;
//	cvar_t	*cl_showmiss;
//	cvar_t	*cl_showclamp;
//
//	cvar_t	*cl_paused;
//	cvar_t	*cl_timedemo;
//
//	cvar_t	*lookspring;
//	cvar_t	*lookstrafe;
//	cvar_t	*sensitivity;
//
//	cvar_t	*m_pitch;
//	cvar_t	*m_yaw;
//	cvar_t	*m_forward;
//	cvar_t	*m_side;
//
//	cvar_t	*cl_lightlevel;
//
////
////	   userinfo
////
//	cvar_t	*info_password;
//	cvar_t	*info_spectator;
//	cvar_t	*name;
//	cvar_t	*skin;
//	cvar_t	*rate;
//	cvar_t	*fov;
//	cvar_t	*msg;
//	cvar_t	*hand;
//	cvar_t	*gender;
//	cvar_t	*gender_auto;
//
//	cvar_t	*cl_vwep;
//
//	client_static_t	cls;
//	client_state_t	cl;
//
//	centity_t		cl_entities[MAX_EDICTS];
//
//	entity_state_t	cl_parse_entities[MAX_PARSE_ENTITIES];
//
//	extern	cvar_t *allow_download;
//	extern	cvar_t *allow_download_players;
//	extern	cvar_t *allow_download_models;
//	extern	cvar_t *allow_download_sounds;
//	extern	cvar_t *allow_download_maps;
//
////	  ======================================================================
//
//
//	/*
//	====================
//	CL_WriteDemoMessage
//
//	Dumps the current net message, prefixed by the length
//	====================
//	*/
//	static void WriteDemoMessage() {
//		int		len, swlen;
//
//		// the first eight bytes are just packet sequencing stuff
//		len = net_message.cursize-8;
//		swlen = LittleLong(len);
//		fwrite (&swlen, 4, 1, cls.demofile);
//		fwrite (net_message.data+8,	len, 1, cls.demofile);
//	}
	static void WriteDemoMessage() throws IOException {
		int swlen;

		// the first eight bytes are just packet sequencing stuff
		swlen = net_message.cursize - 8;

		cls.demofile.writeInt(swlen);
		//fwrite (&swlen, 4, 1, cls.demofile);
		cls.demofile.write(net_message.data, 8, swlen);
		//fwrite (net_message.data+8,	len, 1, cls.demofile);
	}	
//
//
//	/*
//	====================
//	CL_Stop_f
//
//	stop recording a demo
//	====================
//	*/
//	void CL_Stop_f (void)
//	{
//		int		len;
//
//		if (!cls.demorecording)
//		{
//			Com_Printf ("Not recording a demo.\n");
//			return;
//		}
//
////	   finish up
//		len = -1;
//		fwrite (&len, 4, 1, cls.demofile);
//		fclose (cls.demofile);
//		cls.demofile = NULL;
//		cls.demorecording = false;
//		Com_Printf ("Stopped demo.\n");
//	}
	static xcommand_t Stop_f = new xcommand_t() {
		public void execute() {
			try {

				int len;

				if (!cls.demorecording) {
					Com.Printf("Not recording a demo.\n");
					return;
				}

				//	   finish up
				len = -1;
				cls.demofile.writeInt(len);
				cls.demofile.close();
				cls.demofile = null;
				cls.demorecording = false;
				Com.Printf("Stopped demo.\n");

			} catch (IOException e) {
			}
		}
	};
//
//	/*
//	====================
//	CL_Record_f
//
//	record <demoname>
//
//	Begins recording a demo from the current position
//	====================
//	*/
//	void CL_Record_f (void)
//	{
//		char	name[MAX_OSPATH];
//		char	buf_data[MAX_MSGLEN];
//		sizebuf_t	buf;
//		int		i;
//		int		len;
//		entity_state_t	*ent;
//		entity_state_t	nullstate;
//
//		if (Cmd_Argc() != 2)
//		{
//			Com_Printf ("record <demoname>\n");
//			return;
//		}
//
//		if (cls.demorecording)
//		{
//			Com_Printf ("Already recording.\n");
//			return;
//		}
//
//		if (cls.state != ca_active)
//		{
//			Com_Printf ("You must be in a level to record.\n");
//			return;
//		}
//
//		//
//		// open the demo file
//		//
//		Com_sprintf (name, sizeof(name), "%s/demos/%s.dm2", FS_Gamedir(), Cmd_Argv(1));
//
//		Com_Printf ("recording to %s.\n", name);
//		FS_CreatePath (name);
//		cls.demofile = fopen (name, "wb");
//		if (!cls.demofile)
//		{
//			Com_Printf ("ERROR: couldn't open.\n");
//			return;
//		}
//		cls.demorecording = true;
//
//		// don't start saving messages until a non-delta compressed message is received
//		cls.demowaiting = true;
//
//		//
//		// write out messages to hold the startup information
//		//
//		SZ_Init (&buf, buf_data, sizeof(buf_data));
//
//		// send the serverdata
//		MSG_WriteByte (&buf, svc_serverdata);
//		MSG_WriteLong (&buf, PROTOCOL_VERSION);
//		MSG_WriteLong (&buf, 0x10000 + cl.servercount);
//		MSG_WriteByte (&buf, 1);	// demos are always attract loops
//		MSG_WriteString (&buf, cl.gamedir);
//		MSG_WriteShort (&buf, cl.playernum);
//
//		MSG_WriteString (&buf, cl.configstrings[CS_NAME]);
//
//		// configstrings
//		for (i=0 ; i<MAX_CONFIGSTRINGS ; i++)
//		{
//			if (cl.configstrings[i][0])
//			{
//				if (buf.cursize + strlen (cl.configstrings[i]) + 32 > buf.maxsize)
//				{	// write it out
//					len = LittleLong (buf.cursize);
//					fwrite (&len, 4, 1, cls.demofile);
//					fwrite (buf.data, buf.cursize, 1, cls.demofile);
//					buf.cursize = 0;
//				}
//
//				MSG_WriteByte (&buf, svc_configstring);
//				MSG_WriteShort (&buf, i);
//				MSG_WriteString (&buf, cl.configstrings[i]);
//			}
//
//		}
//
//		// baselines
//		memset (&nullstate, 0, sizeof(nullstate));
//		for (i=0; i<MAX_EDICTS ; i++)
//		{
//			ent = &cl_entities[i].baseline;
//			if (!ent->modelindex)
//				continue;
//
//			if (buf.cursize + 64 > buf.maxsize)
//			{	// write it out
//				len = LittleLong (buf.cursize);
//				fwrite (&len, 4, 1, cls.demofile);
//				fwrite (buf.data, buf.cursize, 1, cls.demofile);
//				buf.cursize = 0;
//			}
//
//			MSG_WriteByte (&buf, svc_spawnbaseline);		
//			MSG_WriteDeltaEntity (&nullstate, &cl_entities[i].baseline, &buf, true, true);
//		}
//
//		MSG_WriteByte (&buf, svc_stufftext);
//		MSG_WriteString (&buf, "precache\n");
//
//		// write it to the demo file
//
//		len = LittleLong (buf.cursize);
//		fwrite (&len, 4, 1, cls.demofile);
//		fwrite (buf.data, buf.cursize, 1, cls.demofile);
//
//		// the rest of the demo file will be individual frames
//	}
	static xcommand_t Record_f = new xcommand_t() {
		public void execute() {
			try {
				String name;
				byte buf_data[] = new byte[MAX_MSGLEN];
				sizebuf_t buf = new sizebuf_t();
				int i;
				int len;
				entity_state_t ent;
				entity_state_t nullstate = new entity_state_t();

				if (Cmd.Argc() != 2) {
					Com.Printf("record <demoname>\n");
					return;
				}

				if (cls.demorecording) {
					Com.Printf("Already recording.\n");
					return;
				}

				if (cls.state != ca_active) {
					Com.Printf("You must be in a level to record.\n");
					return;
				}

				//
				// open the demo file
				//
				name = FS.Gamedir() + "/demos/" + Cmd.Argv(1) + ".dm2";

				Com.Printf("recording to " + name + ".\n");
				FS.CreatePath(name);
				cls.demofile = new RandomAccessFile(name, "wb");
				if (cls.demofile == null) {
					Com.Printf("ERROR: couldn't open.\n");
					return;
				}
				cls.demorecording = true;

				// don't start saving messages until a non-delta compressed message is received
				cls.demowaiting = true;

				//
				// write out messages to hold the startup information
				//
				SZ.Init(buf, buf_data, MAX_MSGLEN);

				// send the serverdata
				MSG.WriteByte(buf, svc_serverdata);
				MSG.WriteInt(buf, PROTOCOL_VERSION);
				MSG.WriteInt(buf, 0x10000 + cl.servercount);
				MSG.WriteByte(buf, 1); // demos are always attract loops
				MSG.WriteString(buf, cl.gamedir);
				MSG.WriteShort(buf, cl.playernum);

				MSG.WriteString(buf, cl.configstrings[CS_NAME]);

				// configstrings
				for (i = 0; i < MAX_CONFIGSTRINGS; i++) {
					if (cl.configstrings[i] != "") {
						if (buf.cursize + cl.configstrings[i].length() + 32 > buf.maxsize) { // write it out
							//len = LittleLong(buf.cursize);
							//fwrite(& len, 4, 1, cls.demofile);
							cls.demofile.writeInt(buf.cursize);
							//fwrite(buf.data, buf.cursize, 1, cls.demofile);
							cls.demofile.write(buf.data, 0, buf.cursize);
							buf.cursize = 0;
						}

						MSG.WriteByte(buf, svc_configstring);
						MSG.WriteShort(buf, i);
						MSG.WriteString(buf, cl.configstrings[i]);
					}

				}

				// baselines
				//memset( nullstate, 0, sizeof(nullstate));
				for (i = 0; i < MAX_EDICTS; i++) {
					ent = cl_entities[i].baseline;
					if (ent.modelindex == 0)
						continue;

					if (buf.cursize + 64 > buf.maxsize) { // write it out
						//len = LittleLong(buf.cursize);
						//fwrite(& len, 4, 1, cls.demofile);
						cls.demofile.writeInt(buf.cursize);
						//fwrite(buf.data, buf.cursize, 1, cls.demofile);
						cls.demofile.write(buf.data, 0, buf.cursize);
						buf.cursize = 0;
					}

					MSG.WriteByte(buf, svc_spawnbaseline);
					MSG.WriteDeltaEntity(nullstate, cl_entities[i].baseline, buf, true, true);
				}

				MSG.WriteByte(buf, svc_stufftext);
				MSG.WriteString(buf, "precache\n");

				// write it to the demo file

				//len = LittleLong(buf.cursize);
				//fwrite(& len, 4, 1, cls.demofile);
				cls.demofile.writeInt(buf.cursize);
				//fwrite(buf.data, buf.cursize, 1, cls.demofile);
				cls.demofile.write(buf.data, 0, buf.cursize);
				// the rest of the demo file will be individual frames

			} catch (IOException e) {
			}
		}
	};
//
////	  ======================================================================
//
//	/*
//	===================
//	Cmd_ForwardToServer
//
//	adds the current command line as a clc_stringcmd to the client message.
//	things like godmode, noclip, etc, are commands directed to the server,
//	so when they are typed in at the console, they will need to be forwarded.
//	===================
//	*/
//	void Cmd_ForwardToServer (void)
//	{
//		char	*cmd;
//
//		cmd = Cmd_Argv(0);
//		if (cls.state <= ca_connected || *cmd == '-' || *cmd == '+')
//		{
//			Com_Printf ("Unknown command \"%s\"\n", cmd);
//			return;
//		}
//
//		MSG_WriteByte (&cls.netchan.message, clc_stringcmd);
//		SZ_Print (&cls.netchan.message, cmd);
//		if (Cmd_Argc() > 1)
//		{
//			SZ_Print (&cls.netchan.message, " ");
//			SZ_Print (&cls.netchan.message, Cmd_Args());
//		}
//	}
	static void ForwardToServer() {
		String cmd;

		cmd = Cmd.Argv(0);
		if (cls.state <= ca_connected || cmd.charAt(0) == '-' || cmd.charAt(0) == '+') {
			Com.Printf("Unknown command \"" + cmd + "\"\n");
			return;
		}

		MSG.WriteByte(cls.netchan.message, clc_stringcmd);
		SZ.Print(cls.netchan.message, cmd);
		if (Cmd.Argc() > 1) {
			SZ.Print(cls.netchan.message, " ");
			SZ.Print(cls.netchan.message, Cmd.Args());
		}
	};	
//
//	void CL_Setenv_f( void )
//	{
//		int argc = Cmd_Argc();
//
//		if ( argc > 2 )
//		{
//			char buffer[1000];
//			int i;
//
//			strcpy( buffer, Cmd_Argv(1) );
//			strcat( buffer, "=" );
//
//			for ( i = 2; i < argc; i++ )
//			{
//				strcat( buffer, Cmd_Argv( i ) );
//				strcat( buffer, " " );
//			}
//
//			putenv( buffer );
//		}
//		else if ( argc == 2 )
//		{
//			char *env = getenv( Cmd_Argv(1) );
//
//			if ( env )
//			{
//				Com_Printf( "%s=%s\n", Cmd_Argv(1), env );
//			}
//			else
//			{
//				Com_Printf( "%s undefined\n", Cmd_Argv(1), env );
//			}
//		}
//	}
//
//
//	/*
//	==================
//	CL_ForwardToServer_f
//	==================
//	*/
//	void CL_ForwardToServer_f (void)
//	{
//		if (cls.state != ca_connected && cls.state != ca_active)
//		{
//			Com_Printf ("Can't \"%s\", not connected\n", Cmd_Argv(0));
//			return;
//		}
//	
//		// don't forward the first argument
//		if (Cmd_Argc() > 1)
//		{
//			MSG_WriteByte (&cls.netchan.message, clc_stringcmd);
//			SZ_Print (&cls.netchan.message, Cmd_Args());
//		}
//	}
	static xcommand_t ForwardToServer_f = new xcommand_t() {
		public void execute() {
			if (cls.state != ca_connected && cls.state != ca_active) {
				Com.Printf("Can't \"" + Cmd.Argv(0) + "\", not connected\n");
				return;
			}

			// don't forward the first argument
			if (Cmd.Argc() > 1) {
				MSG.WriteByte(cls.netchan.message, clc_stringcmd);
				SZ.Print(cls.netchan.message, Cmd.Args());
			}
		}
	};	
//
//
//	/*
//	==================
//	CL_Pause_f
//	==================
//	*/
//	void CL_Pause_f (void)
//	{
//		// never pause in multiplayer
//		if (Cvar_VariableValue ("maxclients") > 1 || !Com_ServerState ())
//		{
//			Cvar_SetValue ("paused", 0);
//			return;
//		}
//
//		Cvar_SetValue ("paused", !cl_paused->value);
//	}
	static xcommand_t Pause_f = new xcommand_t() {
		public void execute() {
				// never pause in multiplayer

	if (Cvar.VariableValue("maxclients") > 1 || Com.ServerState() == 0) {
				Cvar.SetValue("paused", 0);
				return;
			}

			Cvar.SetValue("paused", cl_paused.value);
		}
	};
//
//	/*
//	==================
//	CL_Quit_f
//	==================
//	*/
	static xcommand_t Quit_f = new xcommand_t() {
		public void execute() {
			Disconnect();
			Com.Quit();
		}
	};
//
//
//	/*
//	=======================
//	CL_SendConnectPacket
//
//	We have gotten a challenge from the server, so try and
//	connect.
//	======================
//	*/
//	void CL_SendConnectPacket (void)
//	{
//		netadr_t	adr;
//		int		port;
//
//		if (!NET_StringToAdr (cls.servername, &adr))
//		{
//			Com_Printf ("Bad server address\n");
//			cls.connect_time = 0;
//			return;
//		}
//		if (adr.port == 0)
//			adr.port = BigShort (PORT_SERVER);
//
//		port = Cvar_VariableValue ("qport");
//		userinfo_modified = false;
//
//		Netchan_OutOfBandPrint (NS_CLIENT, adr, "connect %i %i %i \"%s\"\n",
//			PROTOCOL_VERSION, port, cls.challenge, Cvar_Userinfo() );
//	}
//
//	/*
//	=================
//	CL_CheckForResend
//
//	Resend a connect message if the last one has timed out
//	=================
//	*/
//	void CL_CheckForResend (void)
//	{
//		netadr_t	adr;
//
//		// if the local server is running and we aren't
//		// then connect
//		if (cls.state == ca_disconnected && Com_ServerState() )
//		{
//			cls.state = ca_connecting;
//			strncpy (cls.servername, "localhost", sizeof(cls.servername)-1);
//			// we don't need a challenge on the localhost
//			CL_SendConnectPacket ();
//			return;
////			cls.connect_time = -99999;	// CL_CheckForResend() will fire immediately
//		}
//
//		// resend if we haven't gotten a reply yet
//		if (cls.state != ca_connecting)
//			return;
//
//		if (cls.realtime - cls.connect_time < 3000)
//			return;
//
//		if (!NET_StringToAdr (cls.servername, &adr))
//		{
//			Com_Printf ("Bad server address\n");
//			cls.state = ca_disconnected;
//			return;
//		}
//		if (adr.port == 0)
//			adr.port = BigShort (PORT_SERVER);
//
//		cls.connect_time = cls.realtime;	// for retransmit requests
//
//		Com_Printf ("Connecting to %s...\n", cls.servername);
//
//		Netchan_OutOfBandPrint (NS_CLIENT, adr, "getchallenge\n");
//	}
//
//
//	/*
//	================
//	CL_Connect_f
//
//	================
//	*/
//	void CL_Connect_f (void)
//	{
//		char	*server;
//
//		if (Cmd_Argc() != 2)
//		{
//			Com_Printf ("usage: connect <server>\n");
//			return;	
//		}
//	
//		if (Com_ServerState ())
//		{	// if running a local server, kill it and reissue
//			SV_Shutdown (va("Server quit\n", msg), false);
//		}
//		else
//		{
//			CL_Disconnect ();
//		}
//
//		server = Cmd_Argv (1);
//
//		NET_Config (true);		// allow remote
//
//		CL_Disconnect ();
//
//		cls.state = ca_connecting;
//		strncpy (cls.servername, server, sizeof(cls.servername)-1);
//		cls.connect_time = -99999;	// CL_CheckForResend() will fire immediately
//	}
//
//
//	/*
//	=====================
//	CL_Rcon_f
//
//	  Send the rest of the command line over as
//	  an unconnected command.
//	=====================
//	*/
//	void CL_Rcon_f (void)
//	{
//		char	message[1024];
//		int		i;
//		netadr_t	to;
//
//		if (!rcon_client_password->string)
//		{
//			Com_Printf ("You must set 'rcon_password' before\n"
//						"issuing an rcon command.\n");
//			return;
//		}
//
//		message[0] = (char)255;
//		message[1] = (char)255;
//		message[2] = (char)255;
//		message[3] = (char)255;
//		message[4] = 0;
//
//		NET_Config (true);		// allow remote
//
//		strcat (message, "rcon ");
//
//		strcat (message, rcon_client_password->string);
//		strcat (message, " ");
//
//		for (i=1 ; i<Cmd_Argc() ; i++)
//		{
//			strcat (message, Cmd_Argv(i));
//			strcat (message, " ");
//		}
//
//		if (cls.state >= ca_connected)
//			to = cls.netchan.remote_address;
//		else
//		{
//			if (!strlen(rcon_address->string))
//			{
//				Com_Printf ("You must either be connected,\n"
//							"or set the 'rcon_address' cvar\n"
//							"to issue rcon commands\n");
//
//				return;
//			}
//			NET_StringToAdr (rcon_address->string, &to);
//			if (to.port == 0)
//				to.port = BigShort (PORT_SERVER);
//		}
//	
//		NET_SendPacket (NS_CLIENT, strlen(message)+1, message, to);
//	}
//
//
//	/*
//	=====================
//	CL_ClearState
//
//	=====================
//	*/
//	void CL_ClearState (void)
//	{
//		S_StopAllSounds ();
//		CL_ClearEffects ();
//		CL_ClearTEnts ();
//
////	   wipe the entire cl structure
//		memset (&cl, 0, sizeof(cl));
//		memset (&cl_entities, 0, sizeof(cl_entities));
//
//		SZ_Clear (&cls.netchan.message);
//
//	}
//
//	/*
//	=====================
//	CL_Disconnect
//
//	Goes from a connected state to full screen console state
//	Sends a disconnect message to the server
//	This is also called on Com_Error, so it shouldn't cause any errors
//	=====================
//	*/
	static void Disconnect() {
//		byte	final[32];
//
//		if (cls.state == ca_disconnected)
//			return;
//
//		if (cl_timedemo && cl_timedemo->value)
//		{
//			int	time;
//		
//			time = Sys_Milliseconds () - cl.timedemo_start;
//			if (time > 0)
//				Com_Printf ("%i frames, %3.1f seconds: %3.1f fps\n", cl.timedemo_frames,
//				time/1000.0, cl.timedemo_frames*1000.0 / time);
//		}
//
//		VectorClear (cl.refdef.blend);
//		re.CinematicSetPalette(NULL);
//
//		M_ForceMenuOff ();
//
//		cls.connect_time = 0;
//
//		SCR_StopCinematic ();
//
//		if (cls.demorecording)
//			CL_Stop_f ();
//
//		// send a disconnect message to the server
//		final[0] = clc_stringcmd;
//		strcpy ((char *)final+1, "disconnect");
//		Netchan_Transmit (&cls.netchan, strlen(final), final);
//		Netchan_Transmit (&cls.netchan, strlen(final), final);
//		Netchan_Transmit (&cls.netchan, strlen(final), final);
//
//		CL_ClearState ();
//
//		// stop download
//		if (cls.download) {
//			fclose(cls.download);
//			cls.download = NULL;
//		}
//
//		cls.state = ca_disconnected;
	}
//
//	void CL_Disconnect_f (void)
//	{
//		Com_Error (ERR_DROP, "Disconnected from server");
//	}
//
//
//	/*
//	====================
//	CL_Packet_f
//
//	packet <destination> <contents>
//
//	Contents allows \n escape character
//	====================
//	*/
//	void CL_Packet_f (void)
//	{
//		char	send[2048];
//		int		i, l;
//		char	*in, *out;
//		netadr_t	adr;
//
//		if (Cmd_Argc() != 3)
//		{
//			Com_Printf ("packet <destination> <contents>\n");
//			return;
//		}
//
//		NET_Config (true);		// allow remote
//
//		if (!NET_StringToAdr (Cmd_Argv(1), &adr))
//		{
//			Com_Printf ("Bad address\n");
//			return;
//		}
//		if (!adr.port)
//			adr.port = BigShort (PORT_SERVER);
//
//		in = Cmd_Argv(2);
//		out = send+4;
//		send[0] = send[1] = send[2] = send[3] = (char)0xff;
//
//		l = strlen (in);
//		for (i=0 ; i<l ; i++)
//		{
//			if (in[i] == '\\' && in[i+1] == 'n')
//			{
//				*out++ = '\n';
//				i++;
//			}
//			else
//				*out++ = in[i];
//		}
//		*out = 0;
//
//		NET_SendPacket (NS_CLIENT, out-send, send, adr);
//	}
//
//	/*
//	=================
//	CL_Changing_f
//
//	Just sent as a hint to the client that they should
//	drop to full console
//	=================
//	*/
//	void CL_Changing_f (void)
//	{
//		//ZOID
//		//if we are downloading, we don't change!  This so we don't suddenly stop downloading a map
//		if (cls.download)
//			return;
//
//		SCR_BeginLoadingPlaque ();
//		cls.state = ca_connected;	// not active anymore, but not disconnected
//		Com_Printf ("\nChanging map...\n");
//	}
//
//
//	/*
//	=================
//	CL_Reconnect_f
//
//	The server is changing levels
//	=================
//	*/
//	void CL_Reconnect_f (void)
//	{
//		//ZOID
//		//if we are downloading, we don't change!  This so we don't suddenly stop downloading a map
//		if (cls.download)
//			return;
//
//		S_StopAllSounds ();
//		if (cls.state == ca_connected) {
//			Com_Printf ("reconnecting...\n");
//			cls.state = ca_connected;
//			MSG_WriteChar (&cls.netchan.message, clc_stringcmd);
//			MSG_WriteString (&cls.netchan.message, "new");		
//			return;
//		}
//
//		if (*cls.servername) {
//			if (cls.state >= ca_connected) {
//				CL_Disconnect();
//				cls.connect_time = cls.realtime - 1500;
//			} else
//				cls.connect_time = -99999; // fire immediately
//
//			cls.state = ca_connecting;
//			Com_Printf ("reconnecting...\n");
//		}
//	}
//
//	/*
//	=================
//	CL_ParseStatusMessage
//
//	Handle a reply from a ping
//	=================
//	*/
//	void CL_ParseStatusMessage (void)
//	{
//		char	*s;
//
//		s = MSG_ReadString(&net_message);
//
//		Com_Printf ("%s\n", s);
//		M_AddToServerList (net_from, s);
//	}
//
//
//	/*
//	=================
//	CL_PingServers_f
//	=================
//	*/
	static xcommand_t PingServers_f = new xcommand_t() {
		public void execute() {
//		int			i;
//		netadr_t	adr;
//		char		name[32];
//		char		*adrstring;
//		cvar_t		*noudp;
//		cvar_t		*noipx;
//
//		NET_Config (true);		// allow remote
//
//		// send a broadcast packet
//		Com_Printf ("pinging broadcast...\n");
//
//		noudp = Cvar_Get ("noudp", "0", CVAR_NOSET);
//		if (!noudp->value)
//		{
//			adr.type = NA_BROADCAST;
//			adr.port = BigShort(PORT_SERVER);
//			Netchan_OutOfBandPrint (NS_CLIENT, adr, va("info %i", PROTOCOL_VERSION));
//		}
//
//		noipx = Cvar_Get ("noipx", "0", CVAR_NOSET);
//		if (!noipx->value)
//		{
//			adr.type = NA_BROADCAST_IPX;
//			adr.port = BigShort(PORT_SERVER);
//			Netchan_OutOfBandPrint (NS_CLIENT, adr, va("info %i", PROTOCOL_VERSION));
//		}
//
//		// send a packet to each address book entry
//		for (i=0 ; i<16 ; i++)
//		{
//			Com_sprintf (name, sizeof(name), "adr%i", i);
//			adrstring = Cvar_VariableString (name);
//			if (!adrstring || !adrstring[0])
//				continue;
//
//			Com_Printf ("pinging %s...\n", adrstring);
//			if (!NET_StringToAdr (adrstring, &adr))
//			{
//				Com_Printf ("Bad address: %s\n", adrstring);
//				continue;
//			}
//			if (!adr.port)
//				adr.port = BigShort(PORT_SERVER);
//			Netchan_OutOfBandPrint (NS_CLIENT, adr, va("info %i", PROTOCOL_VERSION));
//		}
		}
	};
//
//
//	/*
//	=================
//	CL_Skins_f
//
//	Load or download any custom player skins and models
//	=================
//	*/
//	void CL_Skins_f (void)
//	{
//		int		i;
//
//		for (i=0 ; i<MAX_CLIENTS ; i++)
//		{
//			if (!cl.configstrings[CS_PLAYERSKINS+i][0])
//				continue;
//			Com_Printf ("client %i: %s\n", i, cl.configstrings[CS_PLAYERSKINS+i]); 
//			SCR_UpdateScreen ();
//			Sys_SendKeyEvents ();	// pump message loop
//			CL_ParseClientinfo (i);
//		}
//	}
//
//
//	/*
//	=================
//	CL_ConnectionlessPacket
//
//	Responses to broadcasts, etc
//	=================
//	*/
//	void CL_ConnectionlessPacket (void)
//	{
//		char	*s;
//		char	*c;
//	
//		MSG_BeginReading (&net_message);
//		MSG_ReadLong (&net_message);	// skip the -1
//
//		s = MSG_ReadStringLine (&net_message);
//
//		Cmd_TokenizeString (s, false);
//
//		c = Cmd_Argv(0);
//
//		Com_Printf ("%s: %s\n", NET_AdrToString (net_from), c);
//
//		// server connection
//		if (!strcmp(c, "client_connect"))
//		{
//			if (cls.state == ca_connected)
//			{
//				Com_Printf ("Dup connect received.  Ignored.\n");
//				return;
//			}
//			Netchan_Setup (NS_CLIENT, &cls.netchan, net_from, cls.quakePort);
//			MSG_WriteChar (&cls.netchan.message, clc_stringcmd);
//			MSG_WriteString (&cls.netchan.message, "new");	
//			cls.state = ca_connected;
//			return;
//		}
//
//		// server responding to a status broadcast
//		if (!strcmp(c, "info"))
//		{
//			CL_ParseStatusMessage ();
//			return;
//		}
//
//		// remote command from gui front end
//		if (!strcmp(c, "cmd"))
//		{
//			if (!NET_IsLocalAddress(net_from))
//			{
//				Com_Printf ("Command packet from remote host.  Ignored.\n");
//				return;
//			}
//			s = MSG_ReadString (&net_message);
//			Cbuf_AddText (s);
//			Cbuf_AddText ("\n");
//			return;
//		}
//		// print command from somewhere
//		if (!strcmp(c, "print"))
//		{
//			s = MSG_ReadString (&net_message);
//			Com_Printf ("%s", s);
//			return;
//		}
//
//		// ping from somewhere
//		if (!strcmp(c, "ping"))
//		{
//			Netchan_OutOfBandPrint (NS_CLIENT, net_from, "ack");
//			return;
//		}
//
//		// challenge from the server we are connecting to
//		if (!strcmp(c, "challenge"))
//		{
//			cls.challenge = atoi(Cmd_Argv(1));
//			CL_SendConnectPacket ();
//			return;
//		}
//
//		// echo request from server
//		if (!strcmp(c, "echo"))
//		{
//			Netchan_OutOfBandPrint (NS_CLIENT, net_from, "%s", Cmd_Argv(1) );
//			return;
//		}
//
//		Com_Printf ("Unknown command.\n");
//	}
//
//
//	/*
//	=================
//	CL_DumpPackets
//
//	A vain attempt to help bad TCP stacks that cause problems
//	when they overflow
//	=================
//	*/
//	void CL_DumpPackets (void)
//	{
//		while (NET_GetPacket (NS_CLIENT, &net_from, &net_message))
//		{
//			Com_Printf ("dumnping a packet\n");
//		}
//	}
//
//	/*
//	=================
//	CL_ReadPackets
//	=================
//	*/
//	void CL_ReadPackets (void)
//	{
//		while (NET_GetPacket (NS_CLIENT, &net_from, &net_message))
//		{
////		Com_Printf ("packet\n");
//			//
//			// remote command packet
//			//
//			if (*(int *)net_message.data == -1)
//			{
//				CL_ConnectionlessPacket ();
//				continue;
//			}
//
//			if (cls.state == ca_disconnected || cls.state == ca_connecting)
//				continue;		// dump it if not connected
//
//			if (net_message.cursize < 8)
//			{
//				Com_Printf ("%s: Runt packet\n",NET_AdrToString(net_from));
//				continue;
//			}
//
//			//
//			// packet from server
//			//
//			if (!NET_CompareAdr (net_from, cls.netchan.remote_address))
//			{
//				Com_DPrintf ("%s:sequenced packet without connection\n"
//					,NET_AdrToString(net_from));
//				continue;
//			}
//			if (!Netchan_Process(&cls.netchan, &net_message))
//				continue;		// wasn't accepted for some reason
//			CL_ParseServerMessage ();
//		}
//
//		//
//		// check timeout
//		//
//		if (cls.state >= ca_connected
//		 && cls.realtime - cls.netchan.last_received > cl_timeout->value*1000)
//		{
//			if (++cl.timeoutcount > 5)	// timeoutcount saves debugger
//			{
//				Com_Printf ("\nServer connection timed out.\n");
//				CL_Disconnect ();
//				return;
//			}
//		}
//		else
//			cl.timeoutcount = 0;
//	
//	}
//
//
////	  =============================================================================
//
//	/*
//	==============
//	CL_FixUpGender_f
//	==============
//	*/
//	void CL_FixUpGender(void)
//	{
//		char *p;
//		char sk[80];
//
//		if (gender_auto->value) {
//
//			if (gender->modified) {
//				// was set directly, don't override the user
//				gender->modified = false;
//				return;
//			}
//
//			strncpy(sk, skin->string, sizeof(sk) - 1);
//			if ((p = strchr(sk, '/')) != NULL)
//				*p = 0;
//			if (Q_stricmp(sk, "male") == 0 || Q_stricmp(sk, "cyborg") == 0)
//				Cvar_Set ("gender", "male");
//			else if (Q_stricmp(sk, "female") == 0 || Q_stricmp(sk, "crackhor") == 0)
//				Cvar_Set ("gender", "female");
//			else
//				Cvar_Set ("gender", "none");
//			gender->modified = false;
//		}
//	}
//
//	/*
//	==============
//	CL_Userinfo_f
//	==============
//	*/
//	void CL_Userinfo_f (void)
//	{
//		Com_Printf ("User info settings:\n");
//		Info_Print (Cvar_Userinfo());
//	}
//
//	/*
//	=================
//	CL_Snd_Restart_f
//
//	Restart the sound subsystem so it can pick up
//	new parameters and flush all sounds
//	=================
//	*/
//	void CL_Snd_Restart_f (void)
//	{
//		S_Shutdown ();
//		S_Init ();
//		CL_RegisterSounds ();
//	}
//
//	int precache_check; // for autodownload of precache items
//	int precache_spawncount;
//	int precache_tex;
//	int precache_model_skin;
//
//	byte *precache_model; // used for skin checking in alias models
//
//	#define PLAYER_MULT 5
//
////	   ENV_CNT is map load, ENV_CNT+1 is first env map
//	#define ENV_CNT (CS_PLAYERSKINS + MAX_CLIENTS * PLAYER_MULT)
//	#define TEXTURE_CNT (ENV_CNT+13)
//
//	static const char *env_suf[6] = {"rt", "bk", "lf", "ft", "up", "dn"};
//
//	void CL_RequestNextDownload (void)
//	{
//		unsigned	map_checksum;		// for detecting cheater maps
//		char fn[MAX_OSPATH];
//		dmdl_t *pheader;
//
//		if (cls.state != ca_connected)
//			return;
//
//		if (!allow_download->value && precache_check < ENV_CNT)
//			precache_check = ENV_CNT;
//
////	  ZOID
//		if (precache_check == CS_MODELS) { // confirm map
//			precache_check = CS_MODELS+2; // 0 isn't used
//			if (allow_download_maps->value)
//				if (!CL_CheckOrDownloadFile(cl.configstrings[CS_MODELS+1]))
//					return; // started a download
//		}
//		if (precache_check >= CS_MODELS && precache_check < CS_MODELS+MAX_MODELS) {
//			if (allow_download_models->value) {
//				while (precache_check < CS_MODELS+MAX_MODELS &&
//					cl.configstrings[precache_check][0]) {
//					if (cl.configstrings[precache_check][0] == '*' ||
//						cl.configstrings[precache_check][0] == '#') {
//						precache_check++;
//						continue;
//					}
//					if (precache_model_skin == 0) {
//						if (!CL_CheckOrDownloadFile(cl.configstrings[precache_check])) {
//							precache_model_skin = 1;
//							return; // started a download
//						}
//						precache_model_skin = 1;
//					}
//
//					// checking for skins in the model
//					if (!precache_model) {
//
//						FS_LoadFile (cl.configstrings[precache_check], (void **)&precache_model);
//						if (!precache_model) {
//							precache_model_skin = 0;
//							precache_check++;
//							continue; // couldn't load it
//						}
//						if (LittleLong(*(unsigned *)precache_model) != IDALIASHEADER) {
//							// not an alias model
//							FS_FreeFile(precache_model);
//							precache_model = 0;
//							precache_model_skin = 0;
//							precache_check++;
//							continue;
//						}
//						pheader = (dmdl_t *)precache_model;
//						if (LittleLong (pheader->version) != ALIAS_VERSION) {
//							precache_check++;
//							precache_model_skin = 0;
//							continue; // couldn't load it
//						}
//					}
//
//					pheader = (dmdl_t *)precache_model;
//
//					while (precache_model_skin - 1 < LittleLong(pheader->num_skins)) {
//						if (!CL_CheckOrDownloadFile((char *)precache_model +
//							LittleLong(pheader->ofs_skins) + 
//							(precache_model_skin - 1)*MAX_SKINNAME)) {
//							precache_model_skin++;
//							return; // started a download
//						}
//						precache_model_skin++;
//					}
//					if (precache_model) { 
//						FS_FreeFile(precache_model);
//						precache_model = 0;
//					}
//					precache_model_skin = 0;
//					precache_check++;
//				}
//			}
//			precache_check = CS_SOUNDS;
//		}
//		if (precache_check >= CS_SOUNDS && precache_check < CS_SOUNDS+MAX_SOUNDS) { 
//			if (allow_download_sounds->value) {
//				if (precache_check == CS_SOUNDS)
//					precache_check++; // zero is blank
//				while (precache_check < CS_SOUNDS+MAX_SOUNDS &&
//					cl.configstrings[precache_check][0]) {
//					if (cl.configstrings[precache_check][0] == '*') {
//						precache_check++;
//						continue;
//					}
//					Com_sprintf(fn, sizeof(fn), "sound/%s", cl.configstrings[precache_check++]);
//					if (!CL_CheckOrDownloadFile(fn))
//						return; // started a download
//				}
//			}
//			precache_check = CS_IMAGES;
//		}
//		if (precache_check >= CS_IMAGES && precache_check < CS_IMAGES+MAX_IMAGES) {
//			if (precache_check == CS_IMAGES)
//				precache_check++; // zero is blank
//			while (precache_check < CS_IMAGES+MAX_IMAGES &&
//				cl.configstrings[precache_check][0]) {
//				Com_sprintf(fn, sizeof(fn), "pics/%s.pcx", cl.configstrings[precache_check++]);
//				if (!CL_CheckOrDownloadFile(fn))
//					return; // started a download
//			}
//			precache_check = CS_PLAYERSKINS;
//		}
//		// skins are special, since a player has three things to download:
//		// model, weapon model and skin
//		// so precache_check is now *3
//		if (precache_check >= CS_PLAYERSKINS && precache_check < CS_PLAYERSKINS + MAX_CLIENTS * PLAYER_MULT) {
//			if (allow_download_players->value) {
//				while (precache_check < CS_PLAYERSKINS + MAX_CLIENTS * PLAYER_MULT) {
//					int i, n;
//					char model[MAX_QPATH], skin[MAX_QPATH], *p;
//
//					i = (precache_check - CS_PLAYERSKINS)/PLAYER_MULT;
//					n = (precache_check - CS_PLAYERSKINS)%PLAYER_MULT;
//
//					if (!cl.configstrings[CS_PLAYERSKINS+i][0]) {
//						precache_check = CS_PLAYERSKINS + (i + 1) * PLAYER_MULT;
//						continue;
//					}
//
//					if ((p = strchr(cl.configstrings[CS_PLAYERSKINS+i], '\\')) != NULL)
//						p++;
//					else
//						p = cl.configstrings[CS_PLAYERSKINS+i];
//					strcpy(model, p);
//					p = strchr(model, '/');
//					if (!p)
//						p = strchr(model, '\\');
//					if (p) {
//						*p++ = 0;
//						strcpy(skin, p);
//					} else
//						*skin = 0;
//
//					switch (n) {
//					case 0: // model
//						Com_sprintf(fn, sizeof(fn), "players/%s/tris.md2", model);
//						if (!CL_CheckOrDownloadFile(fn)) {
//							precache_check = CS_PLAYERSKINS + i * PLAYER_MULT + 1;
//							return; // started a download
//						}
//						n++;
//						/*FALL THROUGH*/
//
//					case 1: // weapon model
//						Com_sprintf(fn, sizeof(fn), "players/%s/weapon.md2", model);
//						if (!CL_CheckOrDownloadFile(fn)) {
//							precache_check = CS_PLAYERSKINS + i * PLAYER_MULT + 2;
//							return; // started a download
//						}
//						n++;
//						/*FALL THROUGH*/
//
//					case 2: // weapon skin
//						Com_sprintf(fn, sizeof(fn), "players/%s/weapon.pcx", model);
//						if (!CL_CheckOrDownloadFile(fn)) {
//							precache_check = CS_PLAYERSKINS + i * PLAYER_MULT + 3;
//							return; // started a download
//						}
//						n++;
//						/*FALL THROUGH*/
//
//					case 3: // skin
//						Com_sprintf(fn, sizeof(fn), "players/%s/%s.pcx", model, skin);
//						if (!CL_CheckOrDownloadFile(fn)) {
//							precache_check = CS_PLAYERSKINS + i * PLAYER_MULT + 4;
//							return; // started a download
//						}
//						n++;
//						/*FALL THROUGH*/
//
//					case 4: // skin_i
//						Com_sprintf(fn, sizeof(fn), "players/%s/%s_i.pcx", model, skin);
//						if (!CL_CheckOrDownloadFile(fn)) {
//							precache_check = CS_PLAYERSKINS + i * PLAYER_MULT + 5;
//							return; // started a download
//						}
//						// move on to next model
//						precache_check = CS_PLAYERSKINS + (i + 1) * PLAYER_MULT;
//					}
//				}
//			}
//			// precache phase completed
//			precache_check = ENV_CNT;
//		}
//
//		if (precache_check == ENV_CNT) {
//			precache_check = ENV_CNT + 1;
//
//			CM_LoadMap (cl.configstrings[CS_MODELS+1], true, &map_checksum);
//
//			if (map_checksum != atoi(cl.configstrings[CS_MAPCHECKSUM])) {
//				Com_Error (ERR_DROP, "Local map version differs from server: %i != '%s'\n",
//					map_checksum, cl.configstrings[CS_MAPCHECKSUM]);
//				return;
//			}
//		}
//
//		if (precache_check > ENV_CNT && precache_check < TEXTURE_CNT) {
//			if (allow_download->value && allow_download_maps->value) {
//				while (precache_check < TEXTURE_CNT) {
//					int n = precache_check++ - ENV_CNT - 1;
//
//					if (n & 1)
//						Com_sprintf(fn, sizeof(fn), "env/%s%s.pcx", 
//							cl.configstrings[CS_SKY], env_suf[n/2]);
//					else
//						Com_sprintf(fn, sizeof(fn), "env/%s%s.tga", 
//							cl.configstrings[CS_SKY], env_suf[n/2]);
//					if (!CL_CheckOrDownloadFile(fn))
//						return; // started a download
//				}
//			}
//			precache_check = TEXTURE_CNT;
//		}
//
//		if (precache_check == TEXTURE_CNT) {
//			precache_check = TEXTURE_CNT+1;
//			precache_tex = 0;
//		}
//
//		// confirm existance of textures, download any that don't exist
//		if (precache_check == TEXTURE_CNT+1) {
//			// from qcommon/cmodel.c
//			extern int			numtexinfo;
//			extern mapsurface_t	map_surfaces[];
//
//			if (allow_download->value && allow_download_maps->value) {
//				while (precache_tex < numtexinfo) {
//					char fn[MAX_OSPATH];
//
//					sprintf(fn, "textures/%s.wal", map_surfaces[precache_tex++].rname);
//					if (!CL_CheckOrDownloadFile(fn))
//						return; // started a download
//				}
//			}
//			precache_check = TEXTURE_CNT+999;
//		}
//
////	  ZOID
//		CL_RegisterSounds ();
//		CL_PrepRefresh ();
//
//		MSG_WriteByte (&cls.netchan.message, clc_stringcmd);
//		MSG_WriteString (&cls.netchan.message, va("begin %i\n", precache_spawncount) );
//	}
//
//	/*
//	=================
//	CL_Precache_f
//
//	The server will send this command right
//	before allowing the client into the server
//	=================
//	*/
//	void CL_Precache_f (void)
//	{
//		//Yet another hack to let old demos work
//		//the old precache sequence
//		if (Cmd_Argc() < 2) {
//			unsigned	map_checksum;		// for detecting cheater maps
//
//			CM_LoadMap (cl.configstrings[CS_MODELS+1], true, &map_checksum);
//			CL_RegisterSounds ();
//			CL_PrepRefresh ();
//			return;
//		}
//
//		precache_check = CS_MODELS;
//		precache_spawncount = atoi(Cmd_Argv(1));
//		precache_model = 0;
//		precache_model_skin = 0;
//
//		CL_RequestNextDownload();
//	}
//
//
//	/*
//	=================
//	CL_InitLocal
//	=================
//	*/
//	void CL_InitLocal (void)
//	{
//		cls.state = ca_disconnected;
//		cls.realtime = Sys_Milliseconds ();
//
//		CL_InitInput ();
//
//		adr0 = Cvar_Get( "adr0", "", CVAR_ARCHIVE );
//		adr1 = Cvar_Get( "adr1", "", CVAR_ARCHIVE );
//		adr2 = Cvar_Get( "adr2", "", CVAR_ARCHIVE );
//		adr3 = Cvar_Get( "adr3", "", CVAR_ARCHIVE );
//		adr4 = Cvar_Get( "adr4", "", CVAR_ARCHIVE );
//		adr5 = Cvar_Get( "adr5", "", CVAR_ARCHIVE );
//		adr6 = Cvar_Get( "adr6", "", CVAR_ARCHIVE );
//		adr7 = Cvar_Get( "adr7", "", CVAR_ARCHIVE );
//		adr8 = Cvar_Get( "adr8", "", CVAR_ARCHIVE );
//
////
////	   register our variables
////
//		cl_stereo_separation = Cvar_Get( "cl_stereo_separation", "0.4", CVAR_ARCHIVE );
//		cl_stereo = Cvar_Get( "cl_stereo", "0", 0 );
//
//		cl_add_blend = Cvar_Get ("cl_blend", "1", 0);
//		cl_add_lights = Cvar_Get ("cl_lights", "1", 0);
//		cl_add_particles = Cvar_Get ("cl_particles", "1", 0);
//		cl_add_entities = Cvar_Get ("cl_entities", "1", 0);
//		cl_gun = Cvar_Get ("cl_gun", "1", 0);
//		cl_footsteps = Cvar_Get ("cl_footsteps", "1", 0);
//		cl_noskins = Cvar_Get ("cl_noskins", "0", 0);
//		cl_autoskins = Cvar_Get ("cl_autoskins", "0", 0);
//		cl_predict = Cvar_Get ("cl_predict", "1", 0);
////		cl_minfps = Cvar_Get ("cl_minfps", "5", 0);
//		cl_maxfps = Cvar_Get ("cl_maxfps", "90", 0);
//
//		cl_upspeed = Cvar_Get ("cl_upspeed", "200", 0);
//		cl_forwardspeed = Cvar_Get ("cl_forwardspeed", "200", 0);
//		cl_sidespeed = Cvar_Get ("cl_sidespeed", "200", 0);
//		cl_yawspeed = Cvar_Get ("cl_yawspeed", "140", 0);
//		cl_pitchspeed = Cvar_Get ("cl_pitchspeed", "150", 0);
//		cl_anglespeedkey = Cvar_Get ("cl_anglespeedkey", "1.5", 0);
//
//		cl_run = Cvar_Get ("cl_run", "0", CVAR_ARCHIVE);
//		freelook = Cvar_Get( "freelook", "0", CVAR_ARCHIVE );
//		lookspring = Cvar_Get ("lookspring", "0", CVAR_ARCHIVE);
//		lookstrafe = Cvar_Get ("lookstrafe", "0", CVAR_ARCHIVE);
//		sensitivity = Cvar_Get ("sensitivity", "3", CVAR_ARCHIVE);
//
//		m_pitch = Cvar_Get ("m_pitch", "0.022", CVAR_ARCHIVE);
//		m_yaw = Cvar_Get ("m_yaw", "0.022", 0);
//		m_forward = Cvar_Get ("m_forward", "1", 0);
//		m_side = Cvar_Get ("m_side", "1", 0);
//
//		cl_shownet = Cvar_Get ("cl_shownet", "0", 0);
//		cl_showmiss = Cvar_Get ("cl_showmiss", "0", 0);
//		cl_showclamp = Cvar_Get ("showclamp", "0", 0);
//		cl_timeout = Cvar_Get ("cl_timeout", "120", 0);
//		cl_paused = Cvar_Get ("paused", "0", 0);
//		cl_timedemo = Cvar_Get ("timedemo", "0", 0);
//
//		rcon_client_password = Cvar_Get ("rcon_password", "", 0);
//		rcon_address = Cvar_Get ("rcon_address", "", 0);
//
//		cl_lightlevel = Cvar_Get ("r_lightlevel", "0", 0);
//
//		//
//		// userinfo
//		//
//		info_password = Cvar_Get ("password", "", CVAR_USERINFO);
//		info_spectator = Cvar_Get ("spectator", "0", CVAR_USERINFO);
//		name = Cvar_Get ("name", "unnamed", CVAR_USERINFO | CVAR_ARCHIVE);
//		skin = Cvar_Get ("skin", "male/grunt", CVAR_USERINFO | CVAR_ARCHIVE);
//		rate = Cvar_Get ("rate", "25000", CVAR_USERINFO | CVAR_ARCHIVE);	// FIXME
//		msg = Cvar_Get ("msg", "1", CVAR_USERINFO | CVAR_ARCHIVE);
//		hand = Cvar_Get ("hand", "0", CVAR_USERINFO | CVAR_ARCHIVE);
//		fov = Cvar_Get ("fov", "90", CVAR_USERINFO | CVAR_ARCHIVE);
//		gender = Cvar_Get ("gender", "male", CVAR_USERINFO | CVAR_ARCHIVE);
//		gender_auto = Cvar_Get ("gender_auto", "1", CVAR_ARCHIVE);
//		gender->modified = false; // clear this so we know when user sets it manually
//
//		cl_vwep = Cvar_Get ("cl_vwep", "1", CVAR_ARCHIVE);
//
//
//		//
//		// register our commands
//		//
//		Cmd_AddCommand ("cmd", CL_ForwardToServer_f);
//		Cmd_AddCommand ("pause", CL_Pause_f);
//		Cmd_AddCommand ("pingservers", CL_PingServers_f);
//		Cmd_AddCommand ("skins", CL_Skins_f);
//
//		Cmd_AddCommand ("userinfo", CL_Userinfo_f);
//		Cmd_AddCommand ("snd_restart", CL_Snd_Restart_f);
//
//		Cmd_AddCommand ("changing", CL_Changing_f);
//		Cmd_AddCommand ("disconnect", CL_Disconnect_f);
//		Cmd_AddCommand ("record", CL_Record_f);
//		Cmd_AddCommand ("stop", CL_Stop_f);
//
//		Cmd_AddCommand ("quit", CL_Quit_f);
//
//		Cmd_AddCommand ("connect", CL_Connect_f);
//		Cmd_AddCommand ("reconnect", CL_Reconnect_f);
//
//		Cmd_AddCommand ("rcon", CL_Rcon_f);
//
////		Cmd_AddCommand ("packet", CL_Packet_f); // this is dangerous to leave in
//
//		Cmd_AddCommand ("setenv", CL_Setenv_f );
//
//		Cmd_AddCommand ("precache", CL_Precache_f);
//
//		Cmd_AddCommand ("download", CL_Download_f);
//
//		//
//		// forward to server commands
//		//
//		// the only thing this does is allow command completion
//		// to work -- all unknown commands are automatically
//		// forwarded to the server
//		Cmd_AddCommand ("wave", NULL);
//		Cmd_AddCommand ("inven", NULL);
//		Cmd_AddCommand ("kill", NULL);
//		Cmd_AddCommand ("use", NULL);
//		Cmd_AddCommand ("drop", NULL);
//		Cmd_AddCommand ("say", NULL);
//		Cmd_AddCommand ("say_team", NULL);
//		Cmd_AddCommand ("info", NULL);
//		Cmd_AddCommand ("prog", NULL);
//		Cmd_AddCommand ("give", NULL);
//		Cmd_AddCommand ("god", NULL);
//		Cmd_AddCommand ("notarget", NULL);
//		Cmd_AddCommand ("noclip", NULL);
//		Cmd_AddCommand ("invuse", NULL);
//		Cmd_AddCommand ("invprev", NULL);
//		Cmd_AddCommand ("invnext", NULL);
//		Cmd_AddCommand ("invdrop", NULL);
//		Cmd_AddCommand ("weapnext", NULL);
//		Cmd_AddCommand ("weapprev", NULL);
//	}
	public static void InitLocal() {
		cls.state = Defines.ca_disconnected;
		cls.realtime = System.currentTimeMillis();

		InitInput();

		adr0 = Cvar.Get("adr0", "", Cvar.ARCHIVE);
		adr1 = Cvar.Get("adr0", "", Cvar.ARCHIVE);
		adr2 = Cvar.Get("adr0", "", Cvar.ARCHIVE);
		adr3 = Cvar.Get("adr0", "", Cvar.ARCHIVE);
		adr4 = Cvar.Get("adr0", "", Cvar.ARCHIVE);
		adr5 = Cvar.Get("adr0", "", Cvar.ARCHIVE);
		adr6 = Cvar.Get("adr0", "", Cvar.ARCHIVE);
		adr7 = Cvar.Get("adr0", "", Cvar.ARCHIVE);
		adr8 = Cvar.Get("adr0", "", Cvar.ARCHIVE);

		//
		// register our variables
		//
		cl_stereo_separation = Cvar.Get("cl_stereo_separation", "0.4", Cvar.ARCHIVE);
		cl_stereo = Cvar.Get("cl_stereo", "0", 0);

		cl_add_blend = Cvar.Get("cl_blend", "1", 0);
		cl_add_lights = Cvar.Get("cl_lights", "1", 0);
		cl_add_particles = Cvar.Get("cl_particles", "1", 0);
		cl_add_entities = Cvar.Get("cl_entities", "1", 0);
		cl_gun = Cvar.Get("cl_gun", "1", 0);
		cl_footsteps = Cvar.Get("cl_footsteps", "1", 0);
		cl_noskins = Cvar.Get("cl_noskins", "0", 0);
		cl_autoskins = Cvar.Get("cl_autoskins", "0", 0);
		cl_predict = Cvar.Get("cl_predict", "1", 0);

		cl_maxfps = Cvar.Get("cl_maxfps", "90", 0);

		cl_upspeed = Cvar.Get("cl_upspeed", "200", 0);
		cl_forwardspeed = Cvar.Get("cl_forwardspeed", "200", 0);
		cl_sidespeed = Cvar.Get("cl_sidespeed", "200", 0);
		cl_yawspeed = Cvar.Get("cl_yawspeed", "140", 0);
		cl_pitchspeed = Cvar.Get("cl_pitchspeed", "150", 0);
		cl_anglespeedkey = Cvar.Get("cl_anglespeedkey", "1.5", 0);

		cl_run = Cvar.Get("cl_run", "0", Cvar.ARCHIVE);
		freelook = Cvar.Get("freelook", "0", Cvar.ARCHIVE);
		lookspring = Cvar.Get("lookspring", "0", Cvar.ARCHIVE);
		lookstrafe = Cvar.Get("lookstrafe", "0", Cvar.ARCHIVE);
		sensitivity = Cvar.Get("sensitivity", "3", Cvar.ARCHIVE);

		m_pitch = Cvar.Get("m_pitch", "0.022", CVAR_ARCHIVE);
		m_yaw = Cvar.Get("m_yaw", "0.022", 0);
		m_forward = Cvar.Get("m_forward", "1", 0);
		m_side = Cvar.Get("m_side", "1", 0);

		cl_shownet = Cvar.Get("cl_shownet", "0", 0);
		cl_showmiss = Cvar.Get("cl_showmiss", "0", 0);

		cl_showclamp = Cvar.Get("showclamp", "0", 0);
		cl_timeout = Cvar.Get("cl_timeout", "120", 0);
		cl_paused = Cvar.Get("paused", "0", 0);
		cl_timedemo = Cvar.Get("timedemo", "0", 0);

		rcon_client_password = Cvar.Get("rcon_password", "", 0);
		rcon_address = Cvar.Get("rcon_address", "", 0);

		cl_lightlevel = Cvar.Get("r_lightlevel", "0", 0);

		//
		// userinfo
		//
		info_password = Cvar.Get("password", "", CVAR_USERINFO);
		info_spectator = Cvar.Get("spectator", "0", CVAR_USERINFO);
		name = Cvar.Get("name", "unnamed", CVAR_USERINFO | CVAR_ARCHIVE);
		skin = Cvar.Get("skin", "male/grunt", CVAR_USERINFO | CVAR_ARCHIVE);
		rate = Cvar.Get("rate", "25000", CVAR_USERINFO | CVAR_ARCHIVE); // FIXME
		msg = Cvar.Get("msg", "1", CVAR_USERINFO | CVAR_ARCHIVE);
		hand = Cvar.Get("hand", "0", CVAR_USERINFO | CVAR_ARCHIVE);
		fov = Cvar.Get("fov", "90", CVAR_USERINFO | CVAR_ARCHIVE);
		gender = Cvar.Get("gender", "male", CVAR_USERINFO | CVAR_ARCHIVE);
		gender_auto = Cvar.Get("gender_auto", "1", CVAR_ARCHIVE);
		gender.modified = false; // clear this so we know when user sets it manually

		cl_vwep = Cvar.Get("cl_vwep", "1", CVAR_ARCHIVE);

		//
		// register our commands
		//
		Cmd.AddCommand("cmd", ForwardToServer_f);

		Cmd.AddCommand("pause", Pause_f);
		Cmd.AddCommand("pingservers", PingServers_f);
		/*		Cmd.AddCommand("skins", CL_Skins_f);
		
				Cmd.AddCommand("userinfo", CL_Userinfo_f);
				Cmd.AddCommand("snd_restart", CL_Snd_Restart_f);
		
				Cmd.AddCommand("changing", CL_Changing_f);
				Cmd.AddCommand("disconnect", CL_Disconnect_f);
				*/
		Cmd.AddCommand("record", Record_f);
		Cmd.AddCommand("stop", Stop_f);
		Cmd.AddCommand("quit", Quit_f);
		
				/*Cmd.AddCommand("connect", CL_Connect_f);
				Cmd.AddCommand("reconnect", CL_Reconnect_f);
		
				Cmd.AddCommand("rcon", CL_Rcon_f);
		
				//      Cmd.AddCommand ("packet", CL_Packet_f); // this is dangerous to leave in
		
				Cmd.AddCommand("setenv", CL_Setenv_f);
		
				Cmd.AddCommand("precache", CL_Precache_f);
		
				Cmd.AddCommand("download", CL_Download_f);
		*/
		//
		// forward to server commands
		//
		// the only thing this does is allow command completion
		// to work -- all unknown commands are automatically
		// forwarded to the server
		Cmd.AddCommand("wave", null);
		Cmd.AddCommand("inven", null);
		Cmd.AddCommand("kill", null);
		Cmd.AddCommand("use", null);
		Cmd.AddCommand("drop", null);
		Cmd.AddCommand("say", null);
		Cmd.AddCommand("say_team", null);
		Cmd.AddCommand("info", null);
		Cmd.AddCommand("prog", null);
		Cmd.AddCommand("give", null);
		Cmd.AddCommand("god", null);
		Cmd.AddCommand("notarget", null);
		Cmd.AddCommand("noclip", null);
		Cmd.AddCommand("invuse", null);
		Cmd.AddCommand("invprev", null);
		Cmd.AddCommand("invnext", null);
		Cmd.AddCommand("invdrop", null);
		Cmd.AddCommand("weapnext", null);
		Cmd.AddCommand("weapprev", null);

	}

//
//
//
//	/*
//	===============
//	CL_WriteConfiguration
//
//	Writes key bindings and archived cvars to config.cfg
//	===============
//	*/
	static void WriteConfiguration() {
//		FILE	*f;
//		char	path[MAX_QPATH];
//
//		if (cls.state == ca_uninitialized)
//			return;
//
//		Com_sprintf (path, sizeof(path),"%s/config.cfg",FS_Gamedir());
//		f = fopen (path, "w");
//		if (!f)
//		{
//			Com_Printf ("Couldn't write config.cfg.\n");
//			return;
//		}
//
//		fprintf (f, "// generated by quake, do not modify\n");
//		Key_WriteBindings (f);
//		fclose (f);
//
//		Cvar_WriteVariables (path);
	}
//
//
//	/*
//	==================
//	CL_FixCvarCheats
//
//	==================
//	*/
//
//	typedef struct
//	{
//		char	*name;
//		char	*value;
//		cvar_t	*var;
//	} cheatvar_t;
//
//	cheatvar_t	cheatvars[] = {
//		{"timescale", "1"},
//		{"timedemo", "0"},
//		{"r_drawworld", "1"},
//		{"cl_testlights", "0"},
//		{"r_fullbright", "0"},
//		{"r_drawflat", "0"},
//		{"paused", "0"},
//		{"fixedtime", "0"},
//		{"sw_draworder", "0"},
//		{"gl_lightmap", "0"},
//		{"gl_saturatelighting", "0"},
//		{NULL, NULL}
//	};
//
//	int		numcheatvars;
//
//	void CL_FixCvarCheats (void)
//	{
//		int			i;
//		cheatvar_t	*var;
//
//		if ( !strcmp(cl.configstrings[CS_MAXCLIENTS], "1") 
//			|| !cl.configstrings[CS_MAXCLIENTS][0] )
//			return;		// single player can cheat
//
//		// find all the cvars if we haven't done it yet
//		if (!numcheatvars)
//		{
//			while (cheatvars[numcheatvars].name)
//			{
//				cheatvars[numcheatvars].var = Cvar_Get (cheatvars[numcheatvars].name,
//						cheatvars[numcheatvars].value, 0);
//				numcheatvars++;
//			}
//		}
//
//		// make sure they are all set to the proper values
//		for (i=0, var = cheatvars ; i<numcheatvars ; i++, var++)
//		{
//			if ( strcmp (var->var->string, var->value) )
//			{
//				Cvar_Set (var->name, var->value);
//			}
//		}
//	}
//
////	  ============================================================================
//
//	/*
//	==================
//	CL_SendCommand
//
//	==================
//	*/
//	void CL_SendCommand (void)
//	{
//		// get new key events
//		Sys_SendKeyEvents ();
//
//		// allow mice or other external controllers to add commands
//		IN_Commands ();
//
//		// process console commands
//		Cbuf_Execute ();
//
//		// fix any cheating cvars
//		CL_FixCvarCheats ();
//
//		// send intentions now
//		CL_SendCmd ();
//
//		// resend a connection request if necessary
//		CL_CheckForResend ();
//	}
//
//
//	/*
//	==================
//	CL_Frame
//
//	==================
//	*/
	public static void Frame(long msec) {
//		static int	extratime;
//		static int  lasttimecalled;
//
//		extratime += msec;
//
//		if (!cl_timedemo->value)
//		{
//			if (cls.state == ca_connected && extratime < 100)
//				return;			// don't flood packets out while connecting
//			if (extratime < 1000/cl_maxfps->value)
//				return;			// framerate is too high
//		}
//
//		// let the mouse activate or deactivate
//		IN_Frame ();
//
//		// decide the simulation time
//		cls.frametime = extratime/1000.0;
//		cl.time += extratime;
//		cls.realtime = curtime;
//
//		extratime = 0;
//	#if 0
//		if (cls.frametime > (1.0 / cl_minfps->value))
//			cls.frametime = (1.0 / cl_minfps->value);
//	#else
//		if (cls.frametime > (1.0 / 5))
//			cls.frametime = (1.0 / 5);
//	#endif
//
//		// if in the debugger last frame, don't timeout
//		if (msec > 5000)
//			cls.netchan.last_received = Sys_Milliseconds ();
//
//		// fetch results from server
//		CL_ReadPackets ();
//
//		// send a new command message to the server
//		CL_SendCommand ();
//
//		// predict all unacknowledged movements
//		CL_PredictMovement ();
//
//		// allow rendering DLL change
//		VID_CheckChanges ();
//		if (!cl.refresh_prepped && cls.state == ca_active)
//			CL_PrepRefresh ();
//
//
//		SCR_UpdateScreen ();
//
//
//		// update audio
//		S_Update (cl.refdef.vieworg, cl.v_forward, cl.v_right, cl.v_up);
//	
//		CDAudio_Update();
//
//		// advance local effects for next frame
//		CL_RunDLights ();
//		CL_RunLightStyles ();
////		SCR_RunCinematic ();
//		SCR_RunConsole ();
//
//		cls.framecount++;
//
//
	}
//
//
////	  ============================================================================
//

//
//
//	/*
//	===============
//	CL_Shutdown
//
//	FIXME: this is a callback from Sys_Quit and Com_Error.  It would be better
//	to run quit through here before the final handoff to the sys code.
//	===============
//	*/
	static boolean isdown = false;
	public static void Shutdown() {

		if (isdown) {
			System.out.print("recursive shutdown\n");
			return;
		}
		isdown = true;

		WriteConfiguration(); 
		
		CDAudio.Shutdown();
		S.Shutdown();
		IN.Shutdown();
		VID.Shutdown();
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
	
		Menu.Init();
	
		SCR.Init();
		Globals.cls.disable_screen = 1.0f; // don't draw yet
	
		CDAudio.Init();
		CL.InitLocal();
		IN.Init();
	
		FS.ExecAutoexec();
		Cbuf.Execute();
	}

	/**
	 * Called after an ERR_DROP was thrown.
	 */
	public static void Drop() {
		if (Globals.cls.state == Defines.ca_uninitialized)
			return;
		if (Globals.cls.state == Defines.ca_disconnected)
			return;
	
		CL.Disconnect();
	
		// drop loading plaque unless this is the initial game start
		if (Globals.cls.disable_servercount != -1)
			SCR.EndLoadingPlaque();	// get rid of loading plaque
	}
}
