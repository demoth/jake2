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

// Created on 17.01.2004 by RST.
// $Id: SV_USER.java,v 1.9 2004-02-15 18:01:27 rst Exp $

package jake2.server;

import java.io.IOException;

import jake2.*;
import jake2.client.*;
import jake2.game.*;
import jake2.qcommon.*;
import jake2.render.*;
import jake2.util.Lib;

public class SV_USER extends SV_SEND {

	static edict_t sv_player;

	/*
	============================================================
	
	USER STRINGCMD EXECUTION
	
	sv_client and sv_player will be valid.
	============================================================
	*/

	/*
	==================
	SV_BeginDemoServer
	==================
	*/
	public static void SV_BeginDemoserver() {
		String name;

		name = "demos/" + sv.name;
		try {
			sv.demofile = FS.FOpenFile(name);
		}
		catch (IOException e) {
			Com.Error(ERR_DROP, "Couldn't open " + name + "\n");
		}
		if (sv.demofile == null)
			Com.Error(ERR_DROP, "Couldn't open " + name + "\n");
	}

	/*
	================
	SV_New_f
	
	Sends the first message from the server to a connected client.
	This will be sent on the initial connection and upon each server load.
	================
	*/
	public static void SV_New_f() {
		String gamedir;
		int playernum;
		edict_t ent;

		Com.DPrintf("New() from " + sv_client.name + "\n");

		if (sv_client.state != cs_connected) {
			Com.Printf("New not valid -- already spawned\n");
			return;
		}

		// demo servers just dump the file message
		if (sv.state == ss_demo) {
			SV_BeginDemoserver();
			return;
		}

		//
		// serverdata needs to go over for all types of servers
		// to make sure the protocol is right, and to set the gamedir
		//
		gamedir = Cvar.VariableString("gamedir");

		// send the serverdata
		MSG.WriteByte(sv_client.netchan.message, svc_serverdata);
		MSG.WriteInt(sv_client.netchan.message, PROTOCOL_VERSION);
		MSG.WriteLong(sv_client.netchan.message, svs.spawncount);
		MSG.WriteByte(sv_client.netchan.message, sv.attractloop ? 1 : 0);
		MSG.WriteString(sv_client.netchan.message, gamedir);

		if (sv.state == ss_cinematic || sv.state == ss_pic)
			playernum = -1;
		else
			//playernum = sv_client - svs.clients;
			playernum = sv_client.serverindex;

		MSG.WriteShort(sv_client.netchan.message, playernum);

		// send full levelname
		MSG.WriteString(sv_client.netchan.message, sv.configstrings[CS_NAME]);

		//
		// game server
		// 
		if (sv.state == ss_game) {
			// set up the entity for the client
			ent = SV_GAME.ge.edicts[playernum + 1];
			ent.s.number = playernum + 1;
			sv_client.edict = ent;
			sv_client.lastcmd = new usercmd_t();

			// begin fetching configstrings
			MSG.WriteByte(sv_client.netchan.message, svc_stufftext);
			MSG.WriteString(sv_client.netchan.message, "cmd configstrings " + svs.spawncount + " 0\n");
		}

	}

	/*
	==================
	SV_Configstrings_f
	==================
	*/
	public static void SV_Configstrings_f() {
		int start;

		Com.DPrintf("Configstrings() from " + sv_client.name + "\n");

		if (sv_client.state != cs_connected) {
			Com.Printf("configstrings not valid -- already spawned\n");
			return;
		}

		// handle the case of a level changing while a client was connecting
		if (atoi(Cmd.Argv(1)) != svs.spawncount) {
			Com.Printf("SV_Configstrings_f from different level\n");
			SV_New_f();
			return;
		}

		start = atoi(Cmd.Argv(2));

		// write a packet full of data

		while (sv_client.netchan.message.cursize < MAX_MSGLEN / 2 && start < MAX_CONFIGSTRINGS) {
			if (sv.configstrings[start] != null && sv.configstrings[start].length() != 0) {
				MSG.WriteByte(sv_client.netchan.message, svc_configstring);
				MSG.WriteShort(sv_client.netchan.message, start);
				MSG.WriteString(sv_client.netchan.message, sv.configstrings[start]);
			}
			start++;
		}

		// send next command

		if (start == MAX_CONFIGSTRINGS) {
			MSG.WriteByte(sv_client.netchan.message, svc_stufftext);
			MSG.WriteString(sv_client.netchan.message, "cmd baselines " + svs.spawncount + " 0\n");
		}
		else {
			MSG.WriteByte(sv_client.netchan.message, svc_stufftext);
			MSG.WriteString(sv_client.netchan.message, "cmd configstrings " + svs.spawncount + " " + start + "\n");
		}
	}

	/*
	==================
	SV_Baselines_f
	==================
	*/
	public static void SV_Baselines_f() {
		int start;
		entity_state_t nullstate;
		entity_state_t base;

		Com.DPrintf("Baselines() from " + sv_client.name + "\n");

		if (sv_client.state != cs_connected) {
			Com.Printf("baselines not valid -- already spawned\n");
			return;
		}

		// handle the case of a level changing while a client was connecting
		if (atoi(Cmd.Argv(1)) != svs.spawncount) {
			Com.Printf("SV_Baselines_f from different level\n");
			SV_New_f();
			return;
		}

		start = atoi(Cmd.Argv(2));

		//memset (&nullstate, 0, sizeof(nullstate));
		nullstate = new entity_state_t(null);

		// write a packet full of data

		while (sv_client.netchan.message.cursize < MAX_MSGLEN / 2 && start < MAX_EDICTS) {
			base = sv.baselines[start];
			if (base.modelindex != 0 || base.sound != 0 || base.effects != 0) {
				MSG.WriteByte(sv_client.netchan.message, svc_spawnbaseline);
				MSG.WriteDeltaEntity(nullstate, base, sv_client.netchan.message, true, true);
			}
			start++;
		}

		// send next command

		if (start == MAX_EDICTS) {
			MSG.WriteByte(sv_client.netchan.message, svc_stufftext);
			MSG.WriteString(sv_client.netchan.message, "precache " + svs.spawncount + "\n");
		}
		else {
			MSG.WriteByte(sv_client.netchan.message, svc_stufftext);
			MSG.WriteString(sv_client.netchan.message, "cmd baselines " + svs.spawncount + " " + start + "\n");
		}
	}

	/*
	==================
	SV_Begin_f
	==================
	*/
	public static void SV_Begin_f() {
		Com.DPrintf("Begin() from " + sv_client.name + "\n");

		// handle the case of a level changing while a client was connecting
		if (atoi(Cmd.Argv(1)) != svs.spawncount) {
			Com.Printf("SV_Begin_f from different level\n");
			SV_New_f();
			return;
		}

		sv_client.state = cs_spawned;

		// call the game begin function
		SV_GAME.ge.ClientBegin(sv_player);

		Cbuf.InsertFromDefer();
	}

	//=============================================================================

	/*
	==================
	SV_NextDownload_f
	==================
	*/
	public static void SV_NextDownload_f() {
		int r;
		int percent;
		int size;

		if (sv_client.download == null)
			return;

		r = sv_client.downloadsize - sv_client.downloadcount;
		if (r > 1024)
			r = 1024;

		MSG.WriteByte(sv_client.netchan.message, svc_download);
		MSG.WriteShort(sv_client.netchan.message, r);

		sv_client.downloadcount += r;
		size = sv_client.downloadsize;
		if (size == 0)
			size = 1;
		percent = sv_client.downloadcount * 100 / size;
		MSG.WriteByte(sv_client.netchan.message, percent);
		SZ.Write(sv_client.netchan.message, sv_client.download, sv_client.downloadcount - r, r);

		if (sv_client.downloadcount != sv_client.downloadsize)
			return;

		FS.FreeFile(sv_client.download);
		sv_client.download = null;
	}

	/*
	==================
	SV_BeginDownload_f
	==================
	*/
	public static void SV_BeginDownload_f() {
		String name;
		int offset = 0;

		name = Cmd.Argv(1);

		if (Cmd.Argc() > 2)
			offset = atoi(Cmd.Argv(2)); // downloaded offset

		// hacked by zoid to allow more conrol over download
		// first off, no .. or global allow check

		if (name.indexOf("..") != -1
			|| allow_download.value == 0 // leading dot is no good
			|| name.charAt(0) == '.' // leading slash bad as well, must be in subdir
			|| name.charAt(0) == '/' // next up, skin check
			|| (strncmp(name, "players/", 6) == 0 && 0 == allow_download_players.value) // now models
			|| (strncmp(name, "models/", 6) == 0 && 0 == allow_download_models.value) // now sounds
			|| (strncmp(name, "sound/", 6) == 0
				&& 0 == allow_download_sounds.value) // now maps (note special case for maps, must not be in pak)
			|| (strncmp(name, "maps/", 6) == 0 && 0 == allow_download_maps.value) // MUST be in a subdirectory	
			|| name.indexOf('/') == -1) { // don't allow anything with .. path
			MSG.WriteByte(sv_client.netchan.message, svc_download);
			MSG.WriteShort(sv_client.netchan.message, -1);
			MSG.WriteByte(sv_client.netchan.message, 0);
			return;
		}

		if (sv_client.download != null)
			FS.FreeFile(sv_client.download);

		sv_client.download = FS.LoadFile(name);
		sv_client.downloadsize = sv_client.download.length;
		sv_client.downloadcount = offset;

		if (offset > sv_client.downloadsize)
			sv_client.downloadcount = sv_client.downloadsize;

		if (sv_client.download == null // special check for maps, if it came from a pak file, don't allow
		// download  ZOID
			|| (strncmp(name, "maps/", 5) == 0 && FS.file_from_pak != 0)) {
			Com.DPrintf("Couldn't download " + name + " to " + sv_client.name + "\n");
			if (sv_client.download != null) {
				FS.FreeFile(sv_client.download);
				sv_client.download = null;
			}

			MSG.WriteByte(sv_client.netchan.message, svc_download);
			MSG.WriteShort(sv_client.netchan.message, -1);
			MSG.WriteByte(sv_client.netchan.message, 0);
			return;
		}

		SV_NextDownload_f();
		Com.DPrintf("Downloading " + name + " to " + sv_client.name + "\n");
	}

	//============================================================================

	/*
	=================
	SV_Disconnect_f
	
	The client is going to disconnect, so remove the connection immediately
	=================
	*/
	public static void SV_Disconnect_f() {
		//	SV_EndRedirect ();
		SV_DropClient(sv_client);
	}

	/*
	==================
	SV_ShowServerinfo_f
	
	Dumps the serverinfo info string
	==================
	*/
	public static void SV_ShowServerinfo_f() {
		Info.Print(Cvar.Serverinfo());
	}

	public static void SV_Nextserver() {
		String v;

		//ZOID, ss_pic can be nextserver'd in coop mode
		if (sv.state == ss_game || (sv.state == ss_pic && 0 == Cvar.VariableValue("coop")))
			return; // can't nextserver while playing a normal game

		svs.spawncount++; // make sure another doesn't sneak in
		v = Cvar.VariableString("nextserver");
		//if (!v[0])
		if (v.length() == 0)
			Cbuf.AddText("killserver\n");
		else {
			Cbuf.AddText(v);
			Cbuf.AddText("\n");
		}
		Cvar.Set("nextserver", "");
	}

	/*
	==================
	SV_Nextserver_f
	
	A cinematic has completed or been aborted by a client, so move
	to the next server,
	==================
	*/
	public static void SV_Nextserver_f() {
		if (Lib.atoi(Cmd.Argv(1)) != svs.spawncount) {
			Com.DPrintf("Nextserver() from wrong level, from " + sv_client.name + "\n");
			return; // leftover from last server
		}

		Com.DPrintf("Nextserver() from " + sv_client.name + "\n");

		SV_Nextserver();
	}

	public static class ucmd_t {
		public ucmd_t(String n, Runnable r) {
			name = n;
			this.r = r;
		}
		String name;
		Runnable r;
	}

	static ucmd_t u1 = new ucmd_t("new", new Runnable() {
		public void run() {
			SV_New_f();
		}
	});

	static ucmd_t ucmds[] = {
		// auto issued
		new ucmd_t("new", new Runnable() { public void run() { SV_New_f();
			}
		}), new ucmd_t("configstrings", new Runnable() {
			public void run() {
				SV_Configstrings_f();
			}
		}), new ucmd_t("baselines", new Runnable() {
			public void run() {
				SV_Baselines_f();
			}
		}), new ucmd_t("begin", new Runnable() {
			public void run() {
				SV_Begin_f();
			}
		}), new ucmd_t("nextserver", new Runnable() {
			public void run() {
				SV_Nextserver_f();
			}
		}), new ucmd_t("disconnect", new Runnable() {
			public void run() {
				SV_Disconnect_f();
			}
		}),

		// issued by hand at client consoles	
		new ucmd_t("info", new Runnable() {
			public void run() {
				SV_ShowServerinfo_f();
			}
		}), new ucmd_t("download", new Runnable() {
			public void run() {
				SV_BeginDownload_f();
			}
		}), new ucmd_t("nextdl", new Runnable() {
			public void run() {
				SV_NextDownload_f();
			}
		})
		};

	/*
	==================
	SV_ExecuteUserCommand
	==================
	*/
	public static void SV_ExecuteUserCommand(String s) {
		ucmd_t u = null;

		Cmd.TokenizeString(s.toCharArray(), true);
		sv_player = sv_client.edict;

		//	SV_BeginRedirect (RD_CLIENT);

		for (int i = 0; i < ucmds.length; i++) {
			u = ucmds[i];
			if (0 == strcmp(Cmd.Argv(0), u.name)) {
				u.r.run();
				break;
			}
		}
		if (u.name == null && sv.state == ss_game)
			SV_GAME.ge.ClientCommand(sv_player);

		//	SV_EndRedirect ();
	}

	/*
	===========================================================================
	
	USER CMD EXECUTION
	
	===========================================================================
	*/

	public static void SV_ClientThink(client_t cl, usercmd_t cmd) {
		cl.commandMsec -= cmd.msec & 0xFF;

		if (cl.commandMsec < 0 && sv_enforcetime.value != 0) {
			Com.DPrintf("commandMsec underflow from " + cl.name + "\n");
			return;
		}

		SV_GAME.ge.ClientThink(cl.edict, cmd);
	}

	public static final int MAX_STRINGCMDS = 8;
	/*
	===================
	SV_ExecuteClientMessage
	
	The current net_message is parsed for the given client
	===================
	*/
	public static void SV_ExecuteClientMessage(client_t cl) {
		int c;
		String s;

		usercmd_t nullcmd=new usercmd_t();
		usercmd_t oldest=new usercmd_t(), oldcmd=new usercmd_t(), newcmd=new usercmd_t();
		int net_drop;
		int stringCmdCount;
		int checksum, calculatedChecksum;
		int checksumIndex;
		boolean move_issued;
		int lastframe;

		sv_client = cl;
		sv_player = sv_client.edict;

		// only allow one move command
		move_issued = false;
		stringCmdCount = 0;

		while (true) {
			if (net_message.readcount > net_message.cursize) {
				Com.Printf("SV_ReadClientMessage: bad read:\n");
				Com.Printf(Lib.hexDump(net_message.data, 32, false));
				SV_DropClient(cl);
				return;
			}

			c = MSG.ReadByte(net_message);
			if (c == -1)
				break;

			switch (c) {
				default :
					Com.Printf("SV_ReadClientMessage: unknown command char\n");
					SV_DropClient(cl);
					return;

				case clc_nop :
					break;

				case clc_userinfo :
					cl.userinfo = MSG.ReadString(net_message);
					SV_MAIN.SV_UserinfoChanged(cl);
					break;

				case clc_move :
					if (move_issued)
						return; // someone is trying to cheat...

					move_issued = true;
					checksumIndex = net_message.readcount;
					checksum = MSG.ReadByte(net_message);
					lastframe = MSG.ReadLong(net_message);
		
					if (lastframe != cl.lastframe) {
						cl.lastframe = lastframe;
						if (cl.lastframe > 0) {
							cl.frame_latency[cl.lastframe & (LATENCY_COUNTS - 1)] =
								svs.realtime - cl.frames[cl.lastframe & UPDATE_MASK].senttime;
						}
					}

					//memset (nullcmd, 0, sizeof(nullcmd));
					nullcmd = new usercmd_t();
					MSG.ReadDeltaUsercmd(net_message, nullcmd, oldest);
					MSG.ReadDeltaUsercmd(net_message, oldest, oldcmd);
					MSG.ReadDeltaUsercmd(net_message, oldcmd, newcmd);

					if (cl.state != cs_spawned) {
						cl.lastframe = -1;
						break;
					}

					// if the checksum fails, ignore the rest of the packet
					
					calculatedChecksum = 0;
					/*
						 = Com.BlockSequenceCRCByte(
							net_message.data + checksumIndex + 1,
							net_message.readcount - checksumIndex - 1,
							cl.netchan.incoming_sequence);
							*/

					if (calculatedChecksum != checksum) {
						Com.DPrintf(
							"Failed command checksum for "
								+ cl.name
								+ " ("
								+ calculatedChecksum
								+ " != "
								+ checksum
								+ ")/"
								+ cl.netchan.incoming_sequence
								+ "\n");
						return;
					}

					if (0 == sv_paused.value) {
						net_drop = cl.netchan.dropped;
						if (net_drop < 20) {

							//if (net_drop > 2)

							//	Com.Printf ("drop %i\n", net_drop);
							while (net_drop > 2) {
								SV_ClientThink(cl, cl.lastcmd);

								net_drop--;
							}
							if (net_drop > 1)
								SV_ClientThink(cl, oldest);

							if (net_drop > 0)
								SV_ClientThink(cl, oldcmd);

						}
						SV_ClientThink(cl, newcmd);
					}

					// copy.
					cl.lastcmd = newcmd.getClone();
					break;

				case clc_stringcmd :
					s = MSG.ReadString(net_message);

					// malicious users may try using too many string commands
					if (++stringCmdCount < MAX_STRINGCMDS)
						SV_ExecuteUserCommand(s);

					if (cl.state == cs_zombie)
						return; // disconnect command
					break;
			}
		}
	}
}
