/*
 * CL_main.java
 * Copyright (C) 2004
 * 
 * $Id: CL_main.java,v 1.22 2004-02-06 15:11:57 hoz Exp $
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
import jake2.game.*;
import jake2.qcommon.*;
import jake2.server.SV_MAIN;
import jake2.sys.*;
import jake2.util.Vargs;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * CL_main
 */
public class CL_main extends CL_pred {

	////	   cl_main.c  -- client main loop

	/*
	====================
	CL_WriteDemoMessage
	
	Dumps the current net message, prefixed by the length
	====================
	*/
	static void WriteDemoMessage() {
		int swlen;

		// the first eight bytes are just packet sequencing stuff
		swlen = net_message.cursize - 8;
		
		try {
			cls.demofile.writeInt(swlen);
			//fwrite (&swlen, 4, 1, cls.demofile);
			cls.demofile.write(net_message.data, 8, swlen);
			//fwrite (net_message.data+8,	len, 1, cls.demofile);
		} catch (IOException e) {}

	}

	/*
	====================
	CL_Stop_f
	
	stop recording a demo
	====================
	*/
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

			}
			catch (IOException e) {
			}
		}
	};

	/*
	====================
	CL_Record_f

	record <demoname>

	Begins recording a demo from the current position
	====================
	*/
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
				cls.demofile = new RandomAccessFile(name, "rw");
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

			}
			catch (IOException e) {
			}
		}
	};

	static xcommand_t Setenv_f = new xcommand_t() {
		public void execute() {
//			int argc = Cmd_Argc();
//
//			if (argc > 2) {
//				char buffer[1000];
//				int i;
//
//				strcpy(buffer, Cmd_Argv(1));
//				strcat(buffer, "=");
//
//				for (i = 2; i < argc; i++) {
//					strcat(buffer, Cmd_Argv(i));
//					strcat(buffer, " ");
//				}
//
//				putenv(buffer);
//			}
//			else if (argc == 2) {
//				char * env = getenv(Cmd_Argv(1));
//
//				if (env) {
//					Com_Printf("%s=%s\n", Cmd_Argv(1), env);
//				}
//				else {
//					Com_Printf("%s undefined\n", Cmd_Argv(1), env);
//				}
//			}
		}
	};

	/*
	==================
	CL_ForwardToServer_f
	==================
	*/
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

	/*
	==================
	CL_Pause_f
	==================
	*/
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

	/*
	==================
	CL_Quit_f
	==================
	*/
	static xcommand_t Quit_f = new xcommand_t() {
		public void execute() {
			Disconnect();
			Com.Quit();
		}
	};

	/*
	=======================
	CL_SendConnectPacket
	
	We have gotten a challenge from the server, so try and
	connect.
	======================
	*/
	static void SendConnectPacket() {
		netadr_t adr = new netadr_t();
		int port;

		if (!NET.StringToAdr(cls.servername, adr)) {
			Com.Printf("Bad server address\n");
			cls.connect_time = 0;
			return;
		}
		if (adr.port == 0)
			adr.port = PORT_SERVER;
		//			adr.port = BigShort(PORT_SERVER);

		port = (int) Cvar.VariableValue("qport");
		userinfo_modified = false;

		Netchan.OutOfBandPrint(
			NS_CLIENT,
			adr,
			"connect " + PROTOCOL_VERSION + " " + port + " " + cls.challenge + " \"" + Cvar.Userinfo() + "\"\n");
	}

	/*
	=================
	CL_CheckForResend
	
	Resend a connect message if the last one has timed out
	=================
	*/
	static void CheckForResend() {
		netadr_t adr = new netadr_t();

		// if the local server is running and we aren't
		// then connect
		if (cls.state == ca_disconnected && Com.ServerState() != 0) {
			cls.state = ca_connecting;
			cls.servername = "localhost";
			// we don't need a challenge on the localhost
			CL.SendConnectPacket();
			return;
		}

		// resend if we haven't gotten a reply yet
		if (cls.state != ca_connecting)
			return;

		if (cls.realtime - cls.connect_time < 3000)
			return;

		if (!NET.StringToAdr(cls.servername, adr)) {
			Com.Printf("Bad server address\n");
			cls.state = ca_disconnected;
			return;
		}
		if (adr.port == 0)
			//			adr.port = BigShort(PORT_SERVER);
			adr.port = PORT_SERVER;

		cls.connect_time = cls.realtime; // for retransmit requests

		Com.Printf("Connecting to " + cls.servername + "...\n");

		Netchan.OutOfBandPrint(NS_CLIENT, adr, "getchallenge\n");
	}

	/*
	================
	CL_Connect_f

	================
	*/
	static xcommand_t Connect_f = new xcommand_t() {
		public void execute() {
			String server;
		
			if (Cmd.Argc() != 2) {
				Com.Printf("usage: connect <server>\n");
				return;
			}
		
			if (Com.ServerState() != 0) {
				// if running a local server, kill it and reissue
				SV_MAIN.SV_Shutdown("Server quit\n", false);
			} else {
				CL.Disconnect();
			}
		
			server = Cmd.Argv(1);
		
			NET.Config(true); // allow remote
		
			CL.Disconnect();
		
			cls.state = ca_connecting;
			//strncpy (cls.servername, server, sizeof(cls.servername)-1);
			cls.servername = server;
			cls.connect_time = -99999;
			// CL_CheckForResend() will fire immediately
		}
	};

	/*
	=====================
	CL_Rcon_f

	  Send the rest of the command line over as
	  an unconnected command.
	=====================
	*/
	static xcommand_t Rcon_f = new xcommand_t() {
		public void execute() {
				//		char	message[1024];
		//		int		i;
		//		netadr_t	to;
		//
		//		if (!rcon_client_password.string)
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
		//		strcat (message, rcon_client_password.string);
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
		//			if (!strlen(rcon_address.string))
		//			{
		//				Com_Printf ("You must either be connected,\n"
		//							"or set the 'rcon_address' cvar\n"
		//							"to issue rcon commands\n");
		//
		//				return;
		//			}
		//			NET_StringToAdr (rcon_address.string, &to);
		//			if (to.port == 0)
		//				to.port = BigShort (PORT_SERVER);
		//		}
		//	
		//		NET_SendPacket (NS_CLIENT, strlen(message)+1, message, to);
	}
	};

	/*
	=====================
	CL_ClearState
	
	=====================
	*/

	static void ClearState() {
		S.StopAllSounds();
		CL.ClearEffects();
		CL.ClearTEnts();

		// wipe the entire cl structure

		cl = new client_state_t();
		for (int i = 0; i < cl_entities.length; i++) {
			cl_entities[i] = new centity_t();
		}

		SZ.Clear(cls.netchan.message);
	}

	/*
	=====================
	CL_Disconnect
	
	Goes from a connected state to full screen console state
	Sends a disconnect message to the server
	This is also called on Com_Error, so it shouldn't cause any errors
	=====================
	*/

	static void Disconnect() {

		String fin;

		if (cls.state == ca_disconnected)
			return;

		if (cl_timedemo != null && cl_timedemo.value != 0.0f) {
			int time;

			time = (int) (Sys.Milliseconds() - cl.timedemo_start);
			if (time > 0)
				Com.Printf(
					"%i frames, %3.1f seconds: %3.1f fps\n",
					new Vargs(3).add(cl.timedemo_frames).add(time / 1000.0).add(cl.timedemo_frames * 1000.0 / time));
		}

		VectorClear(cl.refdef.blend);
		//re.CinematicSetPalette(null);

		Menu.ForceMenuOff();

		cls.connect_time = 0;

		//		SCR.StopCinematic();

		if (cls.demorecording)
			CL.Stop_f.execute();

		// send a disconnect message to the server
		fin = (char) clc_stringcmd + "disconnect";
		Netchan.Transmit(cls.netchan, fin.length(), fin.getBytes());
		Netchan.Transmit(cls.netchan, fin.length(), fin.getBytes());
		Netchan.Transmit(cls.netchan, fin.length(), fin.getBytes());

		CL.ClearState();

		// stop download
		if (cls.download != null) {
			fclose(cls.download);
			cls.download = null;
			//			fclose(cls.download);
			//			cls.download = NULL;
		}

		cls.state = ca_disconnected;
	}

	static xcommand_t Disconnect_f = new xcommand_t() {
		public void execute() {
			Com.Error(ERR_DROP, "Disconnected from server");
		}
	};

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

	/*
	=================
	CL_Changing_f
	
	Just sent as a hint to the client that they should
	drop to full console
	=================
	*/
	static xcommand_t Changing_f = new xcommand_t() {
		public void execute() {
				//ZOID
		//if we are downloading, we don't change!  
		// This so we don't suddenly stop downloading a map

	if (cls.download != null)
				return;

			SCR.BeginLoadingPlaque();
			cls.state = ca_connected; // not active anymore, but not disconnected
			Com.Printf("\nChanging map...\n");
		}
	};

	//	/*
	//	=================
	//	CL_Reconnect_f
	//
	//	The server is changing levels
	//	=================
	//	*/
	static xcommand_t Reconnect_f = new xcommand_t() {
		public void execute() {
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
	}
	};

	/*
	=================
	CL_ParseStatusMessage
	
	Handle a reply from a ping
	=================
	*/
	static void ParseStatusMessage() {
		String s;

		s = MSG.ReadString(net_message);

		Com.Printf(s + "\n");
		Menu.AddToServerList(net_from, s);
	}

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
		//		noudp = Cvar.Get ("noudp", "0", CVAR_NOSET);
		//		if (!noudp.value)
		//		{
		//			adr.type = NA_BROADCAST;
		//			adr.port = BigShort(PORT_SERVER);
		//			Netchan_OutOfBandPrint (NS_CLIENT, adr, va("info %i", PROTOCOL_VERSION));
		//		}
		//
		//		noipx = Cvar.Get ("noipx", "0", CVAR_NOSET);
		//		if (!noipx.value)
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
		//			adrstring = Cvar.VariableString (name);
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
	static xcommand_t Skins_f = new xcommand_t() {
		public void execute() {
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
	}
	};

	/*
	=================
	CL_ConnectionlessPacket
	
	Responses to broadcasts, etc
	=================
	*/
	static void ConnectionlessPacket() {
		String s;
		String c;

		MSG.BeginReading(net_message);
		MSG.ReadLong(net_message); // skip the -1

		s = MSG.ReadStringLine(net_message);

		Cmd.TokenizeString(s.toCharArray(), false);

		c = Cmd.Argv(0);

		Com.Printf(NET.AdrToString(net_from) + ": " + c + " \n");

		// server connection
		if (c.equals("client_connect")) {
			if (cls.state == ca_connected) {
				Com.Printf("Dup connect received.  Ignored.\n");
				return;
			}
			Netchan.Setup(NS_CLIENT, cls.netchan, net_from, cls.quakePort);
			MSG.WriteChar(cls.netchan.message, clc_stringcmd);
			MSG.WriteString(cls.netchan.message, "new");
			cls.state = ca_connected;
			return;
		}

		// server responding to a status broadcast
		if (c.equals("info")) {
			CL.ParseStatusMessage();
			return;
		}

		// remote command from gui front end
		if (c.equals("cmd")) {
			if (!NET.IsLocalAddress(net_from)) {
				Com.Printf("Command packet from remote host.  Ignored.\n");
				return;
			}
			s = MSG.ReadString(net_message);
			Cbuf.AddText(s);
			Cbuf.AddText("\n");
			return;
		}
		// print command from somewhere
		if (c.equals("print")) {
			s = MSG.ReadString(net_message);
			Com.Printf(s);
			return;
		}

		// ping from somewhere
		if (c.equals("ping")) {
			Netchan.OutOfBandPrint(NS_CLIENT, net_from, "ack");
			return;
		}

		// challenge from the server we are connecting to
		if (c.equals("challenge")) {
			cls.challenge = Integer.parseInt(Cmd.Argv(1));
			CL.SendConnectPacket();
			return;
		}

		// echo request from server
		if (c.equals("echo")) {
			Netchan.OutOfBandPrint(NS_CLIENT, net_from, Cmd.Argv(1));
			return;
		}

		Com.Printf("Unknown command.\n");
	}

	/*
	=================
	CL_DumpPackets

	A vain attempt to help bad TCP stacks that cause problems
	when they overflow
	=================
	*/
	static void DumpPackets() {
		while (NET.GetPacket(NS_CLIENT, net_from, net_message)) {
			Com.Printf("dumping a packet\n");
		}
	}

	/*
	=================
	CL_ReadPackets
	=================
	*/
	static void ReadPackets() {
		while (NET.GetPacket(NS_CLIENT, net_from, net_message)) {

			//
			// remote command packet
			//
			ByteBuffer buf = ByteBuffer.wrap(net_message.data);
			buf.order(ByteOrder.BIG_ENDIAN);
			if (buf.getInt(0) == -1) {
				//			if (*(int *)net_message.data == -1)
				CL.ConnectionlessPacket();
				continue;
			}

			if (cls.state == ca_disconnected || cls.state == ca_connecting)
				continue; // dump it if not connected

			if (net_message.cursize < 8) {
				Com.Printf(NET.AdrToString(net_from) + ": Runt packet\n");
				continue;
			}

			//
			// packet from server
			//
			if (!NET.CompareAdr(net_from, cls.netchan.remote_address)) {
				Com.DPrintf(NET.AdrToString(net_from) + ":sequenced packet without connection\n");
				continue;
			}
			if (!Netchan.Process(cls.netchan, net_message))
				continue; // wasn't accepted for some reason
			ParseServerMessage();
		}

		//
		// check timeout
		//
		if (cls.state >= ca_connected && cls.realtime - cls.netchan.last_received > cl_timeout.value * 1000) {
			if (++cl.timeoutcount > 5) // timeoutcount saves debugger
				{
				Com.Printf("\nServer connection timed out.\n");
				CL.Disconnect();
				return;
			}
		}
		else
			cl.timeoutcount = 0;
	}

	//	  =============================================================================

	/*
	==============
	CL_FixUpGender_f
	==============
	*/
	static void FixUpGender() {
		
			String sk;
	
			if (gender_auto.value != 0.0f) {
	
				if (gender.modified) {
					// was set directly, don't override the user
					gender.modified = false;
					return;
				}
	
				sk = skin.string;
				if (sk.startsWith("male") || sk.startsWith("cyborg"))
					Cvar.Set("gender", "male");
				else if (sk.startsWith("female") || sk.startsWith("crackhor"))
					Cvar.Set("gender", "female");
				else
				Cvar.Set("gender", "none");					
				gender.modified = false;
			}
	}

	/*
	==============
	CL_Userinfo_f
	==============
	*/
	static xcommand_t Userinfo_f = new xcommand_t() {
		public void execute() {
			Com.Printf("User info settings:\n");
			Info.Print(Cvar.Userinfo());
		}
	};

	/*
	=================
	CL_Snd_Restart_f

	Restart the sound subsystem so it can pick up
	new parameters and flush all sounds
	=================
	*/
	static xcommand_t Snd_Restart_f = new xcommand_t() {
		public void execute() {
			S.Shutdown();
			S.Init();
			CL.RegisterSounds();
		}
	};

	static int precache_check; // for autodownload of precache items
	static int precache_spawncount;
	static int precache_tex;
	static int precache_model_skin;

	static byte precache_model[]; // used for skin checking in alias models

	public static final int PLAYER_MULT = 5;

	//	   ENV_CNT is map load, ENV_CNT+1 is first env map
	public static final int ENV_CNT = (CS_PLAYERSKINS + MAX_CLIENTS * PLAYER_MULT);
	public static final int TEXTURE_CNT = (ENV_CNT + 13);

	static String env_suf[] = { "rt", "bk", "lf", "ft", "up", "dn" };

	public static void RequestNextDownload() {
		int map_checksum = 0; // for detecting cheater maps
		//char fn[MAX_OSPATH];
		String fn;

		qfiles.dmdl_t pheader;

		if (cls.state != ca_connected)
			return;

		if (SV_MAIN.allow_download.value == 0 && precache_check < ENV_CNT)
			precache_check = ENV_CNT;

		//	  ZOID
		if (precache_check == CS_MODELS) { // confirm map
			precache_check = CS_MODELS + 2; // 0 isn't used
			if (SV_MAIN.allow_download_maps.value != 0)
				if (!CheckOrDownloadFile(cl.configstrings[CS_MODELS + 1]))
					return; // started a download
		}
		if (precache_check >= CS_MODELS && precache_check < CS_MODELS + MAX_MODELS) {
			if (SV_MAIN.allow_download_models.value != 0) {
				while (precache_check < CS_MODELS + MAX_MODELS && cl.configstrings[precache_check].length() > 0) {
					if (cl.configstrings[precache_check].charAt(0) == '*' || cl.configstrings[precache_check].charAt(0) == '#') {
						precache_check++;
						continue;
					}
					if (precache_model_skin == 0) {
						if (!CheckOrDownloadFile(cl.configstrings[precache_check])) {
							precache_model_skin = 1;
							return; // started a download
						}
						precache_model_skin = 1;
					}

					// checking for skins in the model
					if (precache_model == null) {

						precache_model = FS.LoadFile(cl.configstrings[precache_check]);
						if (precache_model == null) {
							precache_model_skin = 0;
							precache_check++;
							continue; // couldn't load it
						}
						ByteBuffer bb = ByteBuffer.wrap(precache_model);
						int header = Globals.endian.LittleLong(bb.getInt());

						if (header != qfiles.IDALIASHEADER) {
							// not an alias model
							FS.FreeFile(precache_model);
							precache_model = null;
							precache_model_skin = 0;
							precache_check++;
							continue;
						}
						pheader = new qfiles.dmdl_t(ByteBuffer.wrap(precache_model));
						if (Globals.endian.LittleLong(pheader.version) != ALIAS_VERSION) {
							precache_check++;
							precache_model_skin = 0;
							continue; // couldn't load it
						}
					}

					pheader = new qfiles.dmdl_t(ByteBuffer.wrap(precache_model));

					int num_skins = Globals.endian.LittleLong(pheader.num_skins);

					while (precache_model_skin - 1 < num_skins) {
						Com.Printf("critical code section because of endian mess!");

						String name =
							new String(
								precache_model,
								Globals.endian.LittleLong(pheader.ofs_skins) + (precache_model_skin - 1) * MAX_SKINNAME,
								MAX_SKINNAME * num_skins);

						if (!CheckOrDownloadFile(name)) {
							precache_model_skin++;
							return; // started a download
						}
						precache_model_skin++;
					}
					if (precache_model != null) {
						FS.FreeFile(precache_model);
						precache_model = null;
					}
					precache_model_skin = 0;
					precache_check++;
				}
			}
			precache_check = CS_SOUNDS;
		}
		if (precache_check >= CS_SOUNDS && precache_check < CS_SOUNDS + MAX_SOUNDS) {
			if (SV_MAIN.allow_download_sounds.value != 0) {
				if (precache_check == CS_SOUNDS)
					precache_check++; // zero is blank
				while (precache_check < CS_SOUNDS + MAX_SOUNDS && cl.configstrings[precache_check].length() > 0) {
					if (cl.configstrings[precache_check].charAt(0) == '*') {
						precache_check++;
						continue;
					}
					fn = "sound/" + cl.configstrings[precache_check++];
					if (!CheckOrDownloadFile(fn))
						return; // started a download
				}
			}
			precache_check = CS_IMAGES;
		}
		if (precache_check >= CS_IMAGES && precache_check < CS_IMAGES + MAX_IMAGES) {
			if (precache_check == CS_IMAGES)
				precache_check++; // zero is blank

			while (precache_check < CS_IMAGES + MAX_IMAGES && cl.configstrings[precache_check].length() > 0) {
				fn = "pics/" + cl.configstrings[precache_check++] + ".pcx";
				if (!CheckOrDownloadFile(fn))
					return; // started a download
			}
			precache_check = CS_PLAYERSKINS;
		}
		// skins are special, since a player has three things to download:
		// model, weapon model and skin
		// so precache_check is now *3
		if (precache_check >= CS_PLAYERSKINS && precache_check < CS_PLAYERSKINS + MAX_CLIENTS * PLAYER_MULT) {
			if (SV_MAIN.allow_download_players.value != 0) {
				while (precache_check < CS_PLAYERSKINS + MAX_CLIENTS * PLAYER_MULT) {

					int i, n;
					//char model[MAX_QPATH], skin[MAX_QPATH], * p;
					String model, skin, p;

					i = (precache_check - CS_PLAYERSKINS) / PLAYER_MULT;
					n = (precache_check - CS_PLAYERSKINS) % PLAYER_MULT;

					if (cl.configstrings[CS_PLAYERSKINS + i].length() == 0) {
						precache_check = CS_PLAYERSKINS + (i + 1) * PLAYER_MULT;
						continue;
					}

					int pos = cl.configstrings[CS_PLAYERSKINS + i].indexOf('\\');
					if (pos != -1)
						pos++;
					else
						pos = 0;

					model = cl.configstrings[CS_PLAYERSKINS + i].substring(pos);

					pos = model.indexOf('/');

					if (pos == -1)
						pos = model.indexOf('\\');

					if (pos != -1) {
						skin = model.substring(pos + 1);
					}
					else
						skin = "";

					switch (n) {
						case 0 : // model
							fn = "players/" + model + "/tris.md2";
							if (!CheckOrDownloadFile(fn)) {
								precache_check = CS_PLAYERSKINS + i * PLAYER_MULT + 1;
								return; // started a download
							}
							n++;
							/*FALL THROUGH*/

						case 1 : // weapon model
							fn = "players/" + model + "/weapon.md2";
							if (!CheckOrDownloadFile(fn)) {
								precache_check = CS_PLAYERSKINS + i * PLAYER_MULT + 2;
								return; // started a download
							}
							n++;
							/*FALL THROUGH*/

						case 2 : // weapon skin
							fn = "players/" + model + "/weapon.pcx";
							if (!CheckOrDownloadFile(fn)) {
								precache_check = CS_PLAYERSKINS + i * PLAYER_MULT + 3;
								return; // started a download
							}
							n++;
							/*FALL THROUGH*/

						case 3 : // skin
							fn = "players/" + model + "/" + skin + ".pcx";
							if (!CheckOrDownloadFile(fn)) {
								precache_check = CS_PLAYERSKINS + i * PLAYER_MULT + 4;
								return; // started a download
							}
							n++;
							/*FALL THROUGH*/

						case 4 : // skin_i
							fn = "players/" + model + "/" + skin + "_i.pcx";
							if (!CheckOrDownloadFile(fn)) {
								precache_check = CS_PLAYERSKINS + i * PLAYER_MULT + 5;
								return; // started a download
							}
							// move on to next model
							precache_check = CS_PLAYERSKINS + (i + 1) * PLAYER_MULT;
					}
				}
			}
			// precache phase completed
			precache_check = ENV_CNT;
		}

		if (precache_check == ENV_CNT) {
			precache_check = ENV_CNT + 1;

			CM.intwrap iw = new CM.intwrap(map_checksum);

			CM.CM_LoadMap(cl.configstrings[CS_MODELS + 1], true, iw);
			map_checksum = iw.i;

			if (map_checksum != atoi(cl.configstrings[CS_MAPCHECKSUM])) {
				Com.Error(
					ERR_DROP,
					"Local map version differs from server: " + map_checksum + " != '" + cl.configstrings[CS_MAPCHECKSUM] + "'\n");
				return;
			}
		}

		if (precache_check > ENV_CNT && precache_check < TEXTURE_CNT) {
			if (SV_MAIN.allow_download.value != 0 && SV_MAIN.allow_download_maps.value != 0) {
				while (precache_check < TEXTURE_CNT) {
					int n = precache_check++ -ENV_CNT - 1;

					if ((n & 1) != 0)
						fn = "env/" + cl.configstrings[CS_SKY] + env_suf[n / 2] + ".pcx";
					else
						fn = "env/" + cl.configstrings[CS_SKY] + env_suf[n / 2] + ".tga";
					if (!CheckOrDownloadFile(fn))
						return; // started a download
				}
			}
			precache_check = TEXTURE_CNT;
		}

		if (precache_check == TEXTURE_CNT) {
			precache_check = TEXTURE_CNT + 1;
			precache_tex = 0;
		}

		// confirm existance of textures, download any that don't exist
		if (precache_check == TEXTURE_CNT + 1) {
			// from qcommon/cmodel.c
			// extern int numtexinfo;
			// extern mapsurface_t map_surfaces[];

			if (SV_MAIN.allow_download.value != 0 && SV_MAIN.allow_download_maps.value != 0) {
				while (precache_tex < CM.numtexinfo) {
					//char fn[MAX_OSPATH];

					fn = "textures/" + CM.map_surfaces[precache_tex++].rname + ".wal";
					if (!CheckOrDownloadFile(fn))
						return; // started a download
				}
			}
			precache_check = TEXTURE_CNT + 999;
		}

		//	  ZOID
		CL_main.RegisterSounds();
		PrepRefresh();

		MSG.WriteByte(cls.netchan.message, clc_stringcmd);
		MSG.WriteString(cls.netchan.message, "begin " + precache_spawncount + "\n");
	}

	/*
	=================
	CL_Precache_f
	
	The server will send this command right
	before allowing the client into the server
	=================
	*/
	static xcommand_t Precache_f = new xcommand_t() {
		public void execute() {
			/* Yet another hack to let old demos work
			the old precache sequence */
			
			if (Cmd.Argc() < 2) {
				
				CM.intwrap iw = new CM.intwrap(0); // for detecting cheater maps

				CM.CM_LoadMap(cl.configstrings[CS_MODELS + 1], true, iw);
				int mapchecksum = iw.i ;
				CL.RegisterSounds();
				CL.PrepRefresh();
				return;
			}

			precache_check = CS_MODELS;
			precache_spawncount = atoi(Cmd.Argv(1));
			precache_model = null;
			precache_model_skin = 0;

			RequestNextDownload();
		}
	};

	/*
	=================
	CL_InitLocal
	=================
	*/
	public static void InitLocal() {
		cls.state = Defines.ca_disconnected;
		cls.realtime = Sys.Milliseconds();

		InitInput();

		adr0 = Cvar.Get("adr0", "", CVAR_ARCHIVE);
		adr1 = Cvar.Get("adr0", "", CVAR_ARCHIVE);
		adr2 = Cvar.Get("adr0", "", CVAR_ARCHIVE);
		adr3 = Cvar.Get("adr0", "", CVAR_ARCHIVE);
		adr4 = Cvar.Get("adr0", "", CVAR_ARCHIVE);
		adr5 = Cvar.Get("adr0", "", CVAR_ARCHIVE);
		adr6 = Cvar.Get("adr0", "", CVAR_ARCHIVE);
		adr7 = Cvar.Get("adr0", "", CVAR_ARCHIVE);
		adr8 = Cvar.Get("adr0", "", CVAR_ARCHIVE);

		//
		// register our variables
		//
		cl_stereo_separation = Cvar.Get("cl_stereo_separation", "0.4", CVAR_ARCHIVE);
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

		cl_run = Cvar.Get("cl_run", "0", CVAR_ARCHIVE);
		freelook = Cvar.Get("freelook", "0", CVAR_ARCHIVE);
		lookspring = Cvar.Get("lookspring", "0", CVAR_ARCHIVE);
		lookstrafe = Cvar.Get("lookstrafe", "0", CVAR_ARCHIVE);
		sensitivity = Cvar.Get("sensitivity", "3", CVAR_ARCHIVE);

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
		Cmd.AddCommand("skins", Skins_f);

		Cmd.AddCommand("userinfo", Userinfo_f);
		Cmd.AddCommand("snd_restart", Snd_Restart_f);

		Cmd.AddCommand("changing", Changing_f);
		Cmd.AddCommand("disconnect", Disconnect_f);
		Cmd.AddCommand("record", Record_f);
		Cmd.AddCommand("stop", Stop_f);

		Cmd.AddCommand("quit", Quit_f);

		Cmd.AddCommand("connect", Connect_f);
		Cmd.AddCommand("reconnect", Reconnect_f);

		Cmd.AddCommand("rcon", Rcon_f);

		Cmd.AddCommand("setenv", Setenv_f);

		Cmd.AddCommand("precache", Precache_f);

		Cmd.AddCommand("download", Download_f);

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

	/*
	===============
	CL_WriteConfiguration
	
	Writes key bindings and archived cvars to config.cfg
	===============
	*/
	static void WriteConfiguration() {
		RandomAccessFile f;
		String path;

		if (cls.state == ca_uninitialized)
			return;

		path = FS.Gamedir() + "/config.cfg";
		f = fopen(path, "rw");
		if (f == null) {
			Com.Printf("Couldn't write config.cfg.\n");
			return;
		}
		try {
			f.writeChars("// generated by quake, do not modify\n");
		}
		catch (IOException e) {}
		//fprintf (f, "// generated by quake, do not modify\n");
		Key.WriteBindings(f);
		fclose(f);
		Cvar.WriteVariables(path);
	}

	/*
	==================
	CL_FixCvarCheats
	
	==================
	*/
	public static class cheatvar_t {
		String name;
		String value;
		cvar_t var;
	}

	public static String cheatvarsinfo[][] = { { "timescale", "1" }, {
			"timedemo", "0" }, {
			"r_drawworld", "1" }, {
			"cl_testlights", "0" }, {
			"r_fullbright", "0" }, {
			"r_drawflat", "0" }, {
			"paused", "0" }, {
			"fixedtime", "0" }, {
			"sw_draworder", "0" }, {
			"gl_lightmap", "0" }, {
			"gl_saturatelighting", "0" }, {
			null, null }
	};
	public static cheatvar_t cheatvars[];

	static {
		cheatvars = new cheatvar_t[cheatvarsinfo.length];
		for (int n = 0; n < cheatvarsinfo.length; n++) {
			cheatvars[n] = new cheatvar_t();
			cheatvars[n].name = cheatvarsinfo[n][0];
			cheatvars[n].name = cheatvarsinfo[n][1];
		}
	}

	static int numcheatvars;

	public static void FixCvarCheats() {
		int i;
		cheatvar_t var;

		if (0 == strcmp(cl.configstrings[CS_MAXCLIENTS], "1") || 0 == cl.configstrings[CS_MAXCLIENTS].length())
			return; // single player can cheat

		// find all the cvars if we haven't done it yet
		if (0 == numcheatvars) {
			while (cheatvars[numcheatvars].name != null) {
				cheatvars[numcheatvars].var = Cvar.Get(cheatvars[numcheatvars].name, cheatvars[numcheatvars].value, 0);
				numcheatvars++;
			}
		}

		// make sure they are all set to the proper values
		for (i = 0; i < numcheatvars; i++) {
			var = cheatvars[i];
			if (0 != strcmp(var.var.string, var.value)) {
				Cvar.Set(var.name, var.value);
			}
		}
	}

	//	  ============================================================================

	/*
	==================
	CL_SendCommand
	
	==================
	*/
	public static void SendCommand() {
		// get new key events
		Sys.SendKeyEvents();

		// allow mice or other external controllers to add commands
		IN.Commands();

		// process console commands
		Cbuf.Execute();

		// fix any cheating cvars
		FixCvarCheats();

		// send intentions now
		SendCmd();

		// resend a connection request if necessary
		CheckForResend();
	}

	/*
	==================
	CL_Frame
	
	==================
	*/
	private static int extratime;
	private static int lasttimecalled;

	public static void Frame(int msec) {

		extratime += msec;

		if (cl_timedemo.value == 0.0f) {
			if (cls.state == ca_connected && extratime < 100) {
				return; // don't flood packets out while connecting
			}
			if (extratime < 1000 / cl_maxfps.value) {
				return; // framerate is too high
			}
		}

		// let the mouse activate or deactivate
		IN.Frame();

		// decide the simulation time
		cls.frametime = extratime / 1000.0f;
		cl.time += extratime;
		cls.realtime = curtime;

		extratime = 0;

		if (cls.frametime > (1.0f / 5))
			cls.frametime = (1.0f / 5);

		// if in the debugger last frame, don't timeout
		if (msec > 5000)
			cls.netchan.last_received = Sys.Milliseconds();

		// fetch results from server
		CL.ReadPackets();

		// send a new command message to the server
		SendCommand();

		// predict all unacknowledged movements
		CL.PredictMovement();

		// allow rendering DLL change
		VID.CheckChanges();
		if (!cl.refresh_prepped && cls.state == ca_active)
			CL.PrepRefresh();

		SCR.UpdateScreen();

		// update audio
		S.Update(cl.refdef.vieworg, cl.v_forward, cl.v_right, cl.v_up);

		// advance local effects for next frame
		CL.RunDLights();
		CL.RunLightStyles();

		SCR.RunConsole();

		cls.framecount++;
	}

	//	  ============================================================================

	/*
	===============
	CL_Shutdown

	FIXME: this is a callback from Sys_Quit and Com_Error.  It would be better
	to run quit through here before the final handoff to the sys code.
	===============
	*/
	static boolean isdown = false;
	public static void Shutdown() {

		if (isdown) {
			System.out.print("recursive shutdown\n");
			return;
		}
		isdown = true;

		WriteConfiguration();

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

		Console.Init();	//ok

		S.Init();	//empty
		VID.Init();

		V.Init();

		Globals.net_message.data = Globals.net_message_buffer;
		Globals.net_message.maxsize = Globals.net_message_buffer.length;

		Menu.Init();

		SCR.Init();
		//Globals.cls.disable_screen = 1.0f; // don't draw yet

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
			SCR.EndLoadingPlaque(); // get rid of loading plaque
	}

}
