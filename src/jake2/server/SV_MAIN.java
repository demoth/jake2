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

// Created on 13.01.2004 by RST.
// $Id: SV_MAIN.java,v 1.6 2004-08-29 21:39:25 hzi Exp $

package jake2.server;

import jake2.Defines;
import jake2.Globals;
import jake2.game.*;
import jake2.qcommon.*;
import jake2.sys.NET;
import jake2.sys.Sys;
import jake2.util.Lib;

import java.io.IOException;

public class SV_MAIN extends SV_GAME {

	static netadr_t master_adr[] = new netadr_t[MAX_MASTERS]; // address of group servers
	static {
		for (int i = 0; i < MAX_MASTERS; i++) {
			master_adr[i] = new netadr_t();
		}
	}
	public static client_t sv_client; // current client

	public static cvar_t sv_paused;
	public static cvar_t sv_timedemo;

	public static cvar_t sv_enforcetime;

	public static cvar_t timeout; // seconds without any message
	public static cvar_t zombietime; // seconds to sink messages after disconnect

	public static cvar_t rcon_password; // password for remote server commands

	public static cvar_t allow_download;
	public static cvar_t allow_download_players;
	public static cvar_t allow_download_models;
	public static cvar_t allow_download_sounds;
	public static cvar_t allow_download_maps;

	public static cvar_t sv_airaccelerate;

	public static cvar_t sv_noreload; // don't reload level state when reentering

	public static cvar_t maxclients; // FIXME: rename sv_maxclients
	public static cvar_t sv_showclamp;

	public static cvar_t hostname;
	public static cvar_t public_server; // should heartbeats be sent

	public static cvar_t sv_reconnect_limit; // minimum seconds between connect messages

	//============================================================================

	/*
	=====================
	SV_DropClient
	
	Called when the player is totally leaving the server, either willingly
	or unwillingly.  This is NOT called if the entire server is quiting
	or crashing.
	=====================
	*/
	public static void SV_DropClient(client_t drop) {
		// add the disconnect
		MSG.WriteByte(drop.netchan.message, Defines.svc_disconnect);

		if (drop.state == Defines.cs_spawned) {
			// call the prog function for removing a client
			// this will remove the body, among other things
			PlayerClient.ClientDisconnect(drop.edict);
		}

		if (drop.download != null) {
			FS.FreeFile(drop.download);
			drop.download = null;
		}

		drop.state = Defines.cs_zombie; // become free in a few seconds
		drop.name = "";
	}

	/*
	==============================================================================
	
	CONNECTIONLESS COMMANDS
	
	==============================================================================
	*/

	/*
	===============
	SV_StatusString
	
	Builds the string that is sent as heartbeats and status replies
	===============
	*/
	public static String SV_StatusString() {
		String player;
		String status = "";
		int i;
		client_t cl;
		int statusLength;
		int playerLength;

		status = Cvar.Serverinfo() + "\n";

		for (i = 0; i < maxclients.value; i++) {
			cl = svs.clients[i];
			if (cl.state == Defines.cs_connected || cl.state == Defines.cs_spawned) {
				player = "" + cl.edict.client.ps.stats[Defines.STAT_FRAGS] + " " + cl.ping + "\"" + cl.name + "\"\n";

				playerLength = player.length();
				statusLength = status.length();

				if (statusLength + playerLength >= 1024)
					break; // can't hold any more

				status += player;
			}
		}

		return status;
	}

	/*
	================
	SVC_Status
	
	Responds with all the info that qplug or qspy can see
	================
	*/
	public static void SVC_Status() {
		Netchan.OutOfBandPrint(NS_SERVER, Netchan.net_from, "print\n" + SV_StatusString());
	}

	/*
	================
	SVC_Ack
	
	================
	*/
	public static void SVC_Ack() {
		Com.Printf("Ping acknowledge from " + NET.AdrToString(Netchan.net_from) + "\n");
	}

	/*
	================
	SVC_Info
	
	Responds with short info for broadcast scans
	The second parameter should be the current protocol version number.
	================
	*/
	public static void SVC_Info() {
		String string;
		int i, count;
		int version;

		if (maxclients.value == 1)
			return; // ignore in single player

		version = atoi(Cmd.Argv(1));

		if (version != PROTOCOL_VERSION)
			string = hostname.string + ": wrong version\n";
		else {
			count = 0;
			for (i = 0; i < maxclients.value; i++)
				if (svs.clients[i].state >= cs_connected)
					count++;

			string = hostname.string + " " + sv.name + " " + count + "/" + (int) maxclients.value + "\n";
		}

		Netchan.OutOfBandPrint(NS_SERVER, Netchan.net_from, "info\n" + string);
	}

	/*
	================
	SVC_Ping
	
	Just responds with an acknowledgement
	================
	*/
	public static void SVC_Ping() {
		Netchan.OutOfBandPrint(NS_SERVER, Netchan.net_from, "ack");
	}

	/*
	=================
	SVC_GetChallenge
	
	Returns a challenge number that can be used
	in a subsequent client_connect command.
	We do this to prevent denial of service attacks that
	flood the server with invalid connection IPs.  With a
	challenge, they must give a valid IP address.
	=================
	*/
	public static void SVC_GetChallenge() {
		int i;
		int oldest;
		int oldestTime;

		oldest = 0;
		oldestTime = 0x7fffffff;

		// see if we already have a challenge for this ip
		for (i = 0; i < MAX_CHALLENGES; i++) {
			if (NET.NET_CompareBaseAdr(Netchan.net_from, svs.challenges[i].adr))
				break;
			if (svs.challenges[i].time < oldestTime) {
				oldestTime = svs.challenges[i].time;
				oldest = i;
			}
		}

		if (i == MAX_CHALLENGES) {
			// overwrite the oldest
			svs.challenges[oldest].challenge = rand() & 0x7fff;
			svs.challenges[oldest].adr = Netchan.net_from;
			svs.challenges[oldest].time = (int) Globals.curtime;
			i = oldest;
		}

		// send it back
		Netchan.OutOfBandPrint(NS_SERVER, Netchan.net_from, "challenge " + svs.challenges[i].challenge);
	}

	/*
	==================
	SVC_DirectConnect
	
	A connection request that did not come from the master
	==================
	*/
	public static void SVC_DirectConnect() {
		//char		userinfo[MAX_INFO_STRING];
		String userinfo;
		netadr_t adr;
		int i;
		client_t cl;
		
		edict_t ent;
		int edictnum;
		int version;
		int qport;

		adr = Netchan.net_from;

		Com.DPrintf("SVC_DirectConnect ()\n");

		version = atoi(Cmd.Argv(1));
		if (version != PROTOCOL_VERSION) {
			Netchan.OutOfBandPrint(NS_SERVER, adr, "print\nServer is version " + VERSION + "\n");
			Com.DPrintf("    rejected connect from version " + version + "\n");
			return;
		}

		qport = atoi(Cmd.Argv(2));
		int challenge = atoi(Cmd.Argv(3));
		userinfo = Cmd.Argv(4);

		//userinfo[sizeof(userinfo) - 1] = 0;

		// force the IP key/value pair so the game can filter based on ip
		userinfo = Info.Info_SetValueForKey1(userinfo, "ip", NET.AdrToString(Netchan.net_from));

		// attractloop servers are ONLY for local clients
		if (sv.attractloop) {
			if (!NET.IsLocalAddress(adr)) {
				Com.Printf("Remote connect in attract loop.  Ignored.\n");
				Netchan.OutOfBandPrint(NS_SERVER, adr, "print\nConnection refused.\n");
				return;
			}
		}

		// see if the challenge is valid
		if (!NET.IsLocalAddress(adr)) {
			for (i = 0; i < MAX_CHALLENGES; i++) {
				if (NET.NET_CompareBaseAdr(Netchan.net_from, svs.challenges[i].adr)) {
					if (challenge == svs.challenges[i].challenge)
						break; // good
					Netchan.OutOfBandPrint(NS_SERVER, adr, "print\nBad challenge.\n");
					return;
				}
			}
			if (i == MAX_CHALLENGES) {
				Netchan.OutOfBandPrint(NS_SERVER, adr, "print\nNo challenge for address.\n");
				return;
			}
		}

		//newcl = temp;
		//memset (newcl, 0, sizeof(client_t));
		//newcl = new client_t();

		// if there is already a slot for this ip, reuse it
		for (i = 0; i < maxclients.value; i++) {
			cl = svs.clients[i];

			if (cl.state == cs_free)
				continue;
			if (NET.NET_CompareBaseAdr(adr, cl.netchan.remote_address)
				&& (cl.netchan.qport == qport || adr.port == cl.netchan.remote_address.port)) {
				if (!NET.IsLocalAddress(adr) && (svs.realtime - cl.lastconnect) < ((int) sv_reconnect_limit.value * 1000)) {
					Com.DPrintf(NET.AdrToString(adr) + ":reconnect rejected : too soon\n");
					return;
				}
				Com.Printf(NET.AdrToString(adr) + ":reconnect\n");

				gotnewcl(i, challenge, userinfo, adr, qport);
				return;
			}
		}

		// find a client slot
		//newcl = null;
		int index = -1;
		for (i = 0; i < maxclients.value; i++) {
			cl = svs.clients[i];
			if (cl.state == cs_free) {
				index = i;
				break;
			}
		}
		if (index == -1) {
			Netchan.OutOfBandPrint(NS_SERVER, adr, "print\nServer is full.\n");
			Com.DPrintf("Rejected a connection.\n");
			return;
		}
		gotnewcl(index, challenge, userinfo, adr, qport);
	}

	public static void gotnewcl(int i, int challenge, String userinfo, netadr_t adr, int qport) {
		// build a new connection
		// accept the new client
		// this is the only place a client_t is ever initialized
		//*newcl = temp;

		sv_client = svs.clients[i];
		//edictnum = (newcl-svs.clients)+1;
		int edictnum = i + 1;
		edict_t ent = GameBase.g_edicts[edictnum];
		svs.clients[i].edict = ent;
		svs.clients[i].challenge = challenge; // save challenge for checksumming

		// get the game a chance to reject this connection or modify the userinfo
		if (!(PlayerClient.ClientConnect(ent, userinfo))) {
			if (Info.Info_ValueForKey(userinfo, "rejmsg") != null)
				Netchan.OutOfBandPrint(
					NS_SERVER,
					adr,
					"print\n" + Info.Info_ValueForKey(userinfo, "rejmsg") + "\nConnection refused.\n");
			else
				Netchan.OutOfBandPrint(NS_SERVER, adr, "print\nConnection refused.\n");
			Com.DPrintf("Game rejected a connection.\n");
			return;
		}

		// parse some info from the info strings
		svs.clients[i].userinfo = userinfo;
		SV_UserinfoChanged(svs.clients[i]);

		// send the connect packet to the client
		Netchan.OutOfBandPrint(NS_SERVER, adr, "client_connect");

		Netchan.Setup(NS_SERVER, svs.clients[i].netchan, adr, qport);

		svs.clients[i].state = cs_connected;

		SZ.Init(svs.clients[i].datagram, svs.clients[i].datagram_buf, svs.clients[i].datagram_buf.length);
		svs.clients[i].datagram.allowoverflow = true;
		svs.clients[i].lastmessage = svs.realtime; // don't timeout
		svs.clients[i].lastconnect = svs.realtime;
		Com.DPrintf("new client added.\n");
	}

	public static int Rcon_Validate() {
		if (0 == rcon_password.string.length())
			return 0;

		if (0 != strcmp(Cmd.Argv(1), rcon_password.string))
			return 0;

		return 1;
	}

	/*
	===============
	SVC_RemoteCommand
	
	A client issued an rcon command.
	Shift down the remaining args
	Redirect all printfs
	===============
	*/
	public static void SVC_RemoteCommand() {
		int i;
		//char		remaining[1024];
		String remaining;

		i = Rcon_Validate();

		String msg = new String(net_message.data, 4, -1);

		if (i == 0)
			Com.Printf("Bad rcon from " + NET.AdrToString(Netchan.net_from) + ":\n" + msg + "\n");
		else
			Com.Printf("Rcon from " + NET.AdrToString(Netchan.net_from) + ":\n" + msg + "\n");

		Com.BeginRedirect(RD_PACKET, SV_SEND.sv_outputbuf, SV_OUTPUTBUF_LENGTH, new Com.RD_Flusher() {
			public void rd_flush(int target, byte[] buffer) {
				SV_SEND.SV_FlushRedirect(target, buffer);
			}
		});

		if (0 == Rcon_Validate()) {
			Com.Printf("Bad rcon_password.\n");
		}
		else {
			remaining = "";

			for (i = 2; i < Cmd.Argc(); i++) {
				remaining += Cmd.Argv(i);
				remaining += " ";
			}

			Cmd.ExecuteString(remaining);
		}

		Com.EndRedirect();
	}

	/*
	=================
	SV_ConnectionlessPacket
	
	A connectionless packet has four leading 0xff
	characters to distinguish it from a game channel.
	Clients that are in the game can still send
	connectionless packets.
	=================
	*/
	public static void SV_ConnectionlessPacket() {
		String s;
		String c;

		MSG.BeginReading(net_message);
		MSG.ReadLong(net_message); // skip the -1 marker

		s = MSG.ReadStringLine(net_message);

		Cmd.TokenizeString(s.toCharArray(), false);

		c = Cmd.Argv(0);
		//Com.Printf("Packet " + NET.AdrToString(Netchan.net_from) + " : " + c + "\n");
		//Com.Printf(Lib.hexDump(net_message.data, 64, false) + "\n");

		if (0 == strcmp(c, "ping"))
			SVC_Ping();
		else if (0 == strcmp(c, "ack"))
			SVC_Ack();
		else if (0 == strcmp(c, "status"))
			SVC_Status();
		else if (0 == strcmp(c, "info"))
			SVC_Info();
		else if (0 == strcmp(c, "getchallenge"))
			SVC_GetChallenge();
		else if (0 == strcmp(c, "connect"))
			SVC_DirectConnect();
		else if (0 == strcmp(c, "rcon"))
			SVC_RemoteCommand();
		else
		{
			Com.Printf("bad connectionless packet from " + NET.AdrToString(Netchan.net_from) + "\n");
			Com.Printf("[" + s + "]\n");
			Com.Printf("" + Lib.hexDump(net_message.data, 128, false));
		}
	}

	//============================================================================

	/*
	===================
	SV_CalcPings
	
	Updates the cl.ping variables
	===================
	*/
	public static void SV_CalcPings() {
		int i, j;
		client_t cl;
		int total, count;

		for (i = 0; i < maxclients.value; i++) {
			cl = svs.clients[i];
			if (cl.state != cs_spawned)
				continue;

			total = 0;
			count = 0;
			for (j = 0; j < LATENCY_COUNTS; j++) {
				if (cl.frame_latency[j] > 0) {
					count++;
					total += cl.frame_latency[j];
				}
			}
			if (0 == count)
				cl.ping = 0;
			else
				cl.ping = total / count;

			// let the game dll know about the ping
			cl.edict.client.ping = cl.ping;
		}
	}

	/*
	===================
	SV_GiveMsec
	
	Every few frames, gives all clients an allotment of milliseconds
	for their command moves.  If they exceed it, assume cheating.
	===================
	*/
	public static void SV_GiveMsec() {
		int i;
		client_t cl;

		if ((sv.framenum & 15) != 0)
			return;

		for (i = 0; i < maxclients.value; i++) {
			cl = svs.clients[i];
			if (cl.state == cs_free)
				continue;

			cl.commandMsec = 1800; // 1600 + some slop
		}
	}

	/*
	=================
	SV_ReadPackets
	=================
	*/
	public static void SV_ReadPackets() {
		int i;
		client_t cl;
		int qport =0;

		while (NET.GetPacket(NS_SERVER, Netchan.net_from, net_message)) {

			// check for connectionless packet (0xffffffff) first
			if ((net_message.data[0] == -1)
				&& (net_message.data[1] == -1)
				&& (net_message.data[2] == -1)
				&& (net_message.data[3] == -1)) {
				SV_ConnectionlessPacket();
				continue;
			}

			// read the qport out of the message so we can fix up
			// stupid address translating routers
			MSG.BeginReading(net_message);
			MSG.ReadLong(net_message); // sequence number
			MSG.ReadLong(net_message); // sequence number
			qport = MSG.ReadShort(net_message) & 0xffff;

			// check for packets from connected clients
			for (i = 0; i < maxclients.value; i++) {
				cl = svs.clients[i];
				if (cl.state == cs_free)
					continue;
				if (!NET.NET_CompareBaseAdr(Netchan.net_from, cl.netchan.remote_address))
					continue;
				if (cl.netchan.qport != qport)
					continue;
				if (cl.netchan.remote_address.port != Netchan.net_from.port) {
					Com.Printf("SV_ReadPackets: fixing up a translated port\n");
					cl.netchan.remote_address.port = Netchan.net_from.port;
				}

				if (Netchan.Process(cl.netchan, net_message)) { // this is a valid, sequenced packet, so process it
					if (cl.state != cs_zombie) {
						cl.lastmessage = svs.realtime; // don't timeout
						SV_USER.SV_ExecuteClientMessage(cl);
					}
				}
				break;
			}

			if (i != maxclients.value)
				continue;
		}
	}

	/*
	==================
	SV_CheckTimeouts
	
	If a packet has not been received from a client for timeout.value
	seconds, drop the conneciton.  Server frames are used instead of
	realtime to avoid dropping the local client while debugging.
	
	When a client is normally dropped, the client_t goes into a zombie state
	for a few seconds to make sure any final reliable message gets resent
	if necessary
	==================
	*/
	public static void SV_CheckTimeouts() {
		int i;
		client_t cl;
		int droppoint;
		int zombiepoint;

		droppoint = (int) (svs.realtime - 1000 * timeout.value);
		zombiepoint = (int) (svs.realtime - 1000 * zombietime.value);

		for (i = 0; i < maxclients.value; i++) {
			cl = svs.clients[i];
			// message times may be wrong across a changelevel
			if (cl.lastmessage > svs.realtime)
				cl.lastmessage = svs.realtime;

			if (cl.state == cs_zombie && cl.lastmessage < zombiepoint) {
				cl.state = cs_free; // can now be reused
				continue;
			}
			if ((cl.state == cs_connected || cl.state == cs_spawned) && cl.lastmessage < droppoint) {
				SV_SEND.SV_BroadcastPrintf(PRINT_HIGH, cl.name + " timed out\n");
				SV_DropClient(cl);
				cl.state = cs_free; // don't bother with zombie state
			}
		}
	}

	/*
	================
	SV_PrepWorldFrame
	
	This has to be done before the world logic, because
	player processing happens outside RunWorldFrame
	================
	*/
	public static void SV_PrepWorldFrame() {
		edict_t ent;
		int i;

		for (i = 0; i < GameBase.num_edicts; i++) {
			ent = GameBase.g_edicts[i];
			// events only last for a single message
			ent.s.event = 0;
		}

	}

	/*
	=================
	SV_RunGameFrame
	=================
	*/
	public static void SV_RunGameFrame() {
		if (host_speeds.value != 0)
			time_before_game = Sys.Milliseconds();

		// we always need to bump framenum, even if we
		// don't run the world, otherwise the delta
		// compression can get confused when a client
		// has the "current" frame
		sv.framenum++;
		sv.time = sv.framenum * 100;

		// don't run if paused
		if (0 == sv_paused.value || maxclients.value > 1) {
			Game.G_RunFrame();

			// never get more than one tic behind
			if (sv.time < svs.realtime) {
				if (sv_showclamp.value != 0)
					Com.Printf("sv highclamp\n");
				svs.realtime = sv.time;
			}
		}

		if (host_speeds.value != 0)
			time_after_game = Sys.Milliseconds();

	}

	/*
	==================
	SV_Frame
	
	==================
	*/
	public static void SV_Frame(long msec) {
		Globals.time_before_game = Globals.time_after_game = 0;

		// if server is not active, do nothing
		if (!svs.initialized)
			return;

		svs.realtime += msec;

		// keep the random time dependent
		Lib.rand();

		// check timeouts
		SV_CheckTimeouts();

		// get packets from clients
		SV_ReadPackets();
		
		//if (Game.g_edicts[1] !=null)
		//	Com.p("player at:" + Lib.vtofsbeaty(Game.g_edicts[1].s.origin  ));

		// move autonomous things around if enough time has passed
		if (0== sv_timedemo.value && svs.realtime < sv.time) {
			// never let the time get too far off
			if (sv.time - svs.realtime > 100) {
				if (sv_showclamp.value != 0)
					Com.Printf("sv lowclamp\n");
				svs.realtime = sv.time - 100;
			}
			NET.NET_Sleep(sv.time - svs.realtime);
			return;
		}

		// update ping based on the last known frame from all clients
		//TODO: dont need yet
		SV_CalcPings();

		// give the clients some timeslices
		//TODO: dont need yet
		SV_GiveMsec();

		// let everything in the world think and move
		SV_RunGameFrame();

		// send messages back to the clients that had packets read this frame
		SV_SEND.SV_SendClientMessages();

		// save the entire world state if recording a serverdemo
		//TODO: dont need yet
		//SV_WORLD.SV_RecordDemoMessage();

		// send a heartbeat to the master if needed
		//TODO: dont need yet
		Master_Heartbeat();

		// clear teleport flags, etc for next frame
		SV_PrepWorldFrame();

	}

	//============================================================================

	/*
	================
	Master_Heartbeat
	
	Send a message to the master every few minutes to
	let it know we are alive, and log information
	================
	*/
	public static final int HEARTBEAT_SECONDS = 300;
	public static void Master_Heartbeat() {
		String string;
		int i;

		// pgm post3.19 change, cvar pointer not validated before dereferencing
		if (dedicated == null || 0 == dedicated.value)
			return; // only dedicated servers send heartbeats

		// pgm post3.19 change, cvar pointer not validated before dereferencing
		if (null == public_server || 0 == public_server.value)
			return; // a private dedicated game

		// check for time wraparound
		if (svs.last_heartbeat > svs.realtime)
			svs.last_heartbeat = svs.realtime;

		if (svs.realtime - svs.last_heartbeat < HEARTBEAT_SECONDS * 1000)
			return; // not time to send yet

		svs.last_heartbeat = svs.realtime;

		// send the same string that we would give for a status OOB command
		string = SV_StatusString();

		// send to group master
		for (i = 0; i < MAX_MASTERS; i++)
			if (master_adr[i].port != 0) {
				Com.Printf("Sending heartbeat to " + NET.AdrToString(master_adr[i]) + "\n");
				Netchan.OutOfBandPrint(NS_SERVER, master_adr[i], "heartbeat\n" + string);
			}
	}

	/*
	=================
	Master_Shutdown
	
	Informs all masters that this server is going down
	=================
	*/
	static void Master_Shutdown() {
		int i;

		// pgm post3.19 change, cvar pointer not validated before dereferencing
		if (null == dedicated || 0 == dedicated.value)
			return; // only dedicated servers send heartbeats

		// pgm post3.19 change, cvar pointer not validated before dereferencing
		if (null == public_server || 0 == public_server.value)
			return; // a private dedicated game

		// send to group master
		for (i = 0; i < MAX_MASTERS; i++)
			if (master_adr[i].port != 0) {
				if (i > 0)
					Com.Printf("Sending heartbeat to " + NET.AdrToString(master_adr[i]) + "\n");
				Netchan.OutOfBandPrint(NS_SERVER, master_adr[i], "shutdown");
			}
	}

	//============================================================================

	/*
	=================
	SV_UserinfoChanged
	
	Pull specific info from a newly changed userinfo string
	into a more C freindly form.
	=================
	*/
	public static void SV_UserinfoChanged(client_t cl) {
		String val;
		int i;

		// call prog code to allow overrides
		PlayerClient.ClientUserinfoChanged(cl.edict, cl.userinfo);

		// name for C code
		cl.name = Info.Info_ValueForKey(cl.userinfo, "name");

		// mask off high bit
		//TODO: masking for german umlaute
		//for (i=0 ; i<sizeof(cl.name) ; i++)
		//	cl.name[i] &= 127;

		// rate command
		val = Info.Info_ValueForKey(cl.userinfo, "rate");
		if (val.length() > 0) {
			i = atoi(val);
			cl.rate = i;
			if (cl.rate < 100)
				cl.rate = 100;
			if (cl.rate > 15000)
				cl.rate = 15000;
		}
		else
			cl.rate = 5000;

		// msg command
		val = Info.Info_ValueForKey(cl.userinfo, "msg");
		if (val.length() > 0) {
			cl.messagelevel = atoi(val);
		}

	}

	//============================================================================

	/*
	===============
	SV_Init
	
	Only called at quake2.exe startup, not for each game
	===============
	*/
	public static void SV_Init() {
		SV_CCMDS.SV_InitOperatorCommands ();		//ok.

		rcon_password = Cvar.Get("rcon_password", "", 0);
		Cvar.Get("skill", "1", 0);
		Cvar.Get("deathmatch", "0", CVAR_LATCH);
		Cvar.Get("coop", "0", CVAR_LATCH);
		Cvar.Get("dmflags", "" + DF_INSTANT_ITEMS, CVAR_SERVERINFO);
		Cvar.Get("fraglimit", "0", CVAR_SERVERINFO);
		Cvar.Get("timelimit", "0", CVAR_SERVERINFO);
		//TODO: set cheats 0
		Cvar.Get("cheats", "1", CVAR_SERVERINFO | CVAR_LATCH);
		Cvar.Get("protocol", "" + PROTOCOL_VERSION, CVAR_SERVERINFO | CVAR_NOSET);
	 
		SV_MAIN.maxclients = Cvar.Get("maxclients", "1", CVAR_SERVERINFO | CVAR_LATCH);
		hostname = Cvar.Get("hostname", "noname", CVAR_SERVERINFO | CVAR_ARCHIVE);
		timeout = Cvar.Get("timeout", "125", 0);
		zombietime = Cvar.Get("zombietime", "2", 0);
		sv_showclamp = Cvar.Get("showclamp", "0", 0);
		sv_paused = Cvar.Get("paused", "0", 0);
		sv_timedemo = Cvar.Get("timedemo", "0", 0);
		sv_enforcetime = Cvar.Get("sv_enforcetime", "0", 0);
		
		// TODO: carsten, re-allow downloads per default
		allow_download = Cvar.Get("allow_download", "0", CVAR_ARCHIVE);
		allow_download_players = Cvar.Get("allow_download_players", "0", CVAR_ARCHIVE);
		allow_download_models = Cvar.Get("allow_download_models", "1", CVAR_ARCHIVE);
		allow_download_sounds = Cvar.Get("allow_download_sounds", "1", CVAR_ARCHIVE);
		allow_download_maps = Cvar.Get("allow_download_maps", "1", CVAR_ARCHIVE);

		sv_noreload = Cvar.Get("sv_noreload", "0", 0);
		sv_airaccelerate = Cvar.Get("sv_airaccelerate", "0", CVAR_LATCH);
		public_server = Cvar.Get("public", "0", 0);
		sv_reconnect_limit = Cvar.Get("sv_reconnect_limit", "3", CVAR_ARCHIVE);

		SZ.Init(net_message, net_message_buffer, net_message_buffer.length);
	}

	/*
	==================
	SV_FinalMessage
	
	Used by SV_Shutdown to send a final message to all
	connected clients before the server goes down.  The messages are sent immediately,
	not just stuck on the outgoing message list, because the server is going
	to totally exit after returning from this function.
	==================
	*/
	public static void SV_FinalMessage(String message, boolean reconnect) {
		int i;
		client_t cl;

		SZ.Clear(net_message);
		MSG.WriteByte(net_message, svc_print);
		MSG.WriteByte(net_message, PRINT_HIGH);
		MSG.WriteString(net_message, message);

		if (reconnect)
			MSG.WriteByte(net_message, svc_reconnect);
		else
			MSG.WriteByte(net_message, svc_disconnect);

		// send it twice
		// stagger the packets to crutch operating system limited buffers

		for (i = 0; i < maxclients.value; i++) {
			cl = svs.clients[i];
			if (cl.state >= cs_connected)
				Netchan.Transmit(cl.netchan, net_message.cursize, net_message.data);
		}
		for (i = 0; i < maxclients.value; i++) {
			cl = svs.clients[i];
			if (cl.state >= cs_connected)
				Netchan.Transmit(cl.netchan, net_message.cursize, net_message.data);
		}
	}

	/*
	================
	SV_Shutdown
	
	Called when each game quits,
	before Sys_Quit or Sys_Error
	================
	*/
	public static void SV_Shutdown(String finalmsg, boolean reconnect) {
		if (svs.clients != null)
			SV_FinalMessage(finalmsg, reconnect);

		Master_Shutdown();
		
		SV_GAME.SV_ShutdownGameProgs ();

		// free current level
		if (sv.demofile != null)
			try {
				sv.demofile.close();
			}
			catch (IOException e) {
				e.printStackTrace();
			}

		//memset (&sv, 0, sizeof(sv));
		sv = new server_t();
		
		Globals.server_state= sv.state;

		// free server static data
		//if (svs.clients!=null)
		//	Z_Free (svs.clients);
		//if (svs.client_entities)
		//	Z_Free (svs.client_entities);

		if (svs.demofile != null)
			try {
				svs.demofile.close();
			}
			catch (IOException e1) {
				e1.printStackTrace();
			}
		//memset (&svs, 0, sizeof(svs));
		svs = new server_static_t();
	}
}
