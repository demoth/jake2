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
// $Id: SV_SEND.java,v 1.7 2005-01-17 22:00:09 cawe Exp $

package jake2.server;

import java.io.IOException;

import jake2.*;
import jake2.client.*;
import jake2.game.*;
import jake2.qcommon.*;
import jake2.render.*;
import jake2.util.Math3D;

public class SV_SEND {
	/*
	=============================================================================
	
	Com_Printf redirection
	
	=============================================================================
	*/

	public static byte sv_outputbuf[] = new byte[Defines.SV_OUTPUTBUF_LENGTH];

	public static void SV_FlushRedirect(int sv_redirected, byte outputbuf[]) {
		if (sv_redirected == Defines.RD_PACKET) {
			String s = ("print\n" + outputbuf);
			Netchan.Netchan_OutOfBand(Defines.NS_SERVER, Globals.net_from, s.length(), s.getBytes());
		}
		else if (sv_redirected == Defines.RD_CLIENT) {
			MSG.WriteByte(SV_MAIN.sv_client.netchan.message, Defines.svc_print);
			MSG.WriteByte(SV_MAIN.sv_client.netchan.message, Defines.PRINT_HIGH);
			MSG.WriteString(SV_MAIN.sv_client.netchan.message, outputbuf);
        }
	}
	/*
	=============================================================================
	
	EVENT MESSAGES
	
	=============================================================================
	*/

	/*
	=================
	SV_ClientPrintf
	
	Sends text across to be displayed if the level passes
	=================
	*/
	public static void SV_ClientPrintf(client_t cl, int level, String s) {

		if (level < cl.messagelevel)
			return;

		MSG.WriteByte(cl.netchan.message, Defines.svc_print);
		MSG.WriteByte(cl.netchan.message, level);
		MSG.WriteString(cl.netchan.message, s);
	}
	/*
	=================
	SV_BroadcastPrintf
	
	Sends text to all active clients
	=================
	*/
	public static void SV_BroadcastPrintf(int level, String s) {

		client_t cl;

		// echo to console
		if (Globals.dedicated.value != 0) {

			Com.Printf(s);
		}

		for (int i = 0; i < SV_MAIN.maxclients.value; i++) {
			cl = SV_INIT.svs.clients[i];
			if (level < cl.messagelevel)
				continue;
			if (cl.state != Defines.cs_spawned)
				continue;
			MSG.WriteByte(cl.netchan.message, Defines.svc_print);
			MSG.WriteByte(cl.netchan.message, level);
			MSG.WriteString(cl.netchan.message, s);
		}
	}
	/*
	=================
	SV_BroadcastCommand
	
	Sends text to all active clients
	=================
	*/
	public static void SV_BroadcastCommand(String s) {

		if (SV_INIT.sv.state == 0)
			return;

		MSG.WriteByte(SV_INIT.sv.multicast, Defines.svc_stufftext);
		MSG.WriteString(SV_INIT.sv.multicast, s);
		SV_Multicast(null, Defines.MULTICAST_ALL_R);
	}
	/*
	=================
	SV_Multicast
	
	Sends the contents of sv.multicast to a subset of the clients,
	then clears sv.multicast.
	
	MULTICAST_ALL	same as broadcast (origin can be null)
	MULTICAST_PVS	send to clients potentially visible from org
	MULTICAST_PHS	send to clients potentially hearable from org
	=================
	*/
	public static void SV_Multicast(float[] origin, int to) {
		client_t client;
		byte mask[];
		int leafnum, cluster;
		int j;
		boolean reliable;
		int area1, area2;

		reliable = false;

		if (to != Defines.MULTICAST_ALL_R && to != Defines.MULTICAST_ALL) {
			leafnum = CM.CM_PointLeafnum(origin);
			area1 = CM.CM_LeafArea(leafnum);
		}
		else {
			leafnum = 0; // just to avoid compiler warnings
			area1 = 0;
		}

		// if doing a serverrecord, store everything
		if (SV_INIT.svs.demofile != null)
			SZ.Write(SV_INIT.svs.demo_multicast, SV_INIT.sv.multicast.data, SV_INIT.sv.multicast.cursize);

		switch (to) {
			case Defines.MULTICAST_ALL_R :
				reliable = true; // intentional fallthrough, no break here
			case Defines.MULTICAST_ALL :
				leafnum = 0;
				mask = null;
				break;

			case Defines.MULTICAST_PHS_R :
				reliable = true; // intentional fallthrough
			case Defines.MULTICAST_PHS :
				leafnum = CM.CM_PointLeafnum(origin);
				cluster = CM.CM_LeafCluster(leafnum);
				mask = CM.CM_ClusterPHS(cluster);
				break;

			case Defines.MULTICAST_PVS_R :
				reliable = true; // intentional fallthrough
			case Defines.MULTICAST_PVS :
				leafnum = CM.CM_PointLeafnum(origin);
				cluster = CM.CM_LeafCluster(leafnum);
				mask = CM.CM_ClusterPVS(cluster);
				break;

			default :
				mask = null;
				Com.Error(Defines.ERR_FATAL, "SV_Multicast: bad to:" + to + "\n");
		}

		// send the data to all relevent clients
		for (j = 0; j < SV_MAIN.maxclients.value; j++) {
			client = SV_INIT.svs.clients[j];

			if (client.state == Defines.cs_free || client.state == Defines.cs_zombie)
				continue;
			if (client.state != Defines.cs_spawned && !reliable)
				continue;

			if (mask != null) {
				leafnum = CM.CM_PointLeafnum(client.edict.s.origin);
				cluster = CM.CM_LeafCluster(leafnum);
				area2 = CM.CM_LeafArea(leafnum);
				if (!CM.CM_AreasConnected(area1, area2))
					continue;

				// quake2 bugfix
				if (cluster == -1)
					continue;
				if (mask != null && (0 == (mask[cluster >> 3] & (1 << (cluster & 7)))))
					continue;
			}

			if (reliable)
				SZ.Write(client.netchan.message, SV_INIT.sv.multicast.data, SV_INIT.sv.multicast.cursize);
			else
				SZ.Write(client.datagram, SV_INIT.sv.multicast.data, SV_INIT.sv.multicast.cursize);
		}

		SZ.Clear(SV_INIT.sv.multicast);
	}

	private static final float[] origin_v = { 0, 0, 0 };
	/*  
	==================
	SV_StartSound
	
	Each entity can have eight independant sound sources, like voice,
	weapon, feet, etc.
	
	If cahnnel & 8, the sound will be sent to everyone, not just
	things in the PHS.
	
	FIXME: if entity isn't in PHS, they must be forced to be sent or
	have the origin explicitly sent.
	
	Channel 0 is an auto-allocate channel, the others override anything
	already running on that entity/channel pair.
	
	An attenuation of 0 will play full volume everywhere in the level.
	Larger attenuations will drop off.  (max 4 attenuation)
	
	Timeofs can range from 0.0 to 0.1 to cause sounds to be started
	later in the frame than they normally would.
	
	If origin is null, the origin is determined from the entity origin
	or the midpoint of the entity box for bmodels.
	==================
	*/
	public static void SV_StartSound(
		float[] origin,
		edict_t entity,
		int channel,
		int soundindex,
		float volume,
		float attenuation,
		float timeofs) {
		int sendchan;
		int flags;
		int i;
		int ent;
		boolean use_phs;

		if (volume < 0 || volume > 1.0)
			Com.Error(Defines.ERR_FATAL, "SV_StartSound: volume = " + volume);

		if (attenuation < 0 || attenuation > 4)
			Com.Error(Defines.ERR_FATAL, "SV_StartSound: attenuation = " + attenuation);

		//	if (channel < 0 || channel > 15)
		//		Com_Error (ERR_FATAL, "SV_StartSound: channel = %i", channel);

		if (timeofs < 0 || timeofs > 0.255)
			Com.Error(Defines.ERR_FATAL, "SV_StartSound: timeofs = " + timeofs);

		ent = entity.index;

		// no PHS flag
		if ((channel & 8) != 0) {
			use_phs = false;
			channel &= 7;
		}
		else
			use_phs = true;

		sendchan = (ent << 3) | (channel & 7);

		flags = 0;
		if (volume != Defines.DEFAULT_SOUND_PACKET_VOLUME)
			flags |= Defines.SND_VOLUME;
		if (attenuation != Defines.DEFAULT_SOUND_PACKET_ATTENUATION)
			flags |= Defines.SND_ATTENUATION;

		// the client doesn't know that bmodels have weird origins
		// the origin can also be explicitly set
		if ((entity.svflags & Defines.SVF_NOCLIENT) != 0 || (entity.solid == Defines.SOLID_BSP) || origin != null)
			flags |= Defines.SND_POS;

		// always send the entity number for channel overrides
		flags |= Defines.SND_ENT;

		if (timeofs != 0)
			flags |= Defines.SND_OFFSET;

		// use the entity origin unless it is a bmodel or explicitly specified
		if (origin == null) {
			origin = origin_v;
			if (entity.solid == Defines.SOLID_BSP) {
				for (i = 0; i < 3; i++)
					origin_v[i] = entity.s.origin[i] + 0.5f * (entity.mins[i] + entity.maxs[i]);
			}
			else {
				Math3D.VectorCopy(entity.s.origin, origin_v);
			}
		}

		MSG.WriteByte(SV_INIT.sv.multicast, Defines.svc_sound);
		MSG.WriteByte(SV_INIT.sv.multicast, flags);
		MSG.WriteByte(SV_INIT.sv.multicast, soundindex);

		if ((flags & Defines.SND_VOLUME) != 0)
			MSG.WriteByte(SV_INIT.sv.multicast, volume * 255);
		if ((flags & Defines.SND_ATTENUATION) != 0)
			MSG.WriteByte(SV_INIT.sv.multicast, attenuation * 64);
		if ((flags & Defines.SND_OFFSET) != 0)
			MSG.WriteByte(SV_INIT.sv.multicast, timeofs * 1000);

		if ((flags & Defines.SND_ENT) != 0)
			MSG.WriteShort(SV_INIT.sv.multicast, sendchan);

		if ((flags & Defines.SND_POS) != 0)
			MSG.WritePos(SV_INIT.sv.multicast, origin);

		// if the sound doesn't attenuate,send it to everyone
		// (global radio chatter, voiceovers, etc)
		if (attenuation == Defines.ATTN_NONE)
			use_phs = false;

		if ((channel & Defines.CHAN_RELIABLE) != 0) {
			if (use_phs)
				SV_Multicast(origin, Defines.MULTICAST_PHS_R);
			else
				SV_Multicast(origin, Defines.MULTICAST_ALL_R);
		}
		else {
			if (use_phs)
				SV_Multicast(origin, Defines.MULTICAST_PHS);
			else
				SV_Multicast(origin, Defines.MULTICAST_ALL);
		}
	}
	/*
	===============================================================================
	
	FRAME UPDATES
	
	===============================================================================
	*/

	/*
	=======================
	SV_SendClientDatagram
	=======================
	*/
	public static boolean SV_SendClientDatagram(client_t client) {
		//byte msg_buf[] = new byte[Defines.MAX_MSGLEN];
		sizebuf_t msg = new sizebuf_t();

		SV_ENTS.SV_BuildClientFrame(client);

		SZ.Init(msg, msgbuf, msgbuf.length);
		msg.allowoverflow = true;

		// send over all the relevant entity_state_t
		// and the player_state_t
		SV_ENTS.SV_WriteFrameToClient(client, msg);

		// copy the accumulated multicast datagram
		// for this client out to the message
		// it is necessary for this to be after the WriteEntities
		// so that entity references will be current
		if (client.datagram.overflowed)
			Com.Printf("WARNING: datagram overflowed for " + client.name + "\n");
		else
			SZ.Write(msg, client.datagram.data, client.datagram.cursize);
		SZ.Clear(client.datagram);

		if (msg.overflowed) { // must have room left for the packet header
			Com.Printf("WARNING: msg overflowed for " + client.name + "\n");
			SZ.Clear(msg);
		}

		// send the datagram
		Netchan.Transmit(client.netchan, msg.cursize, msg.data);

		// record the size for rate estimation
		client.message_size[SV_INIT.sv.framenum % Defines.RATE_MESSAGES] = msg.cursize;

		return true;
	}
	/*
	==================
	SV_DemoCompleted
	==================
	*/
	public static void SV_DemoCompleted() {
		if (SV_INIT.sv.demofile != null) {
			try {
				SV_INIT.sv.demofile.close();
			}
			catch (IOException e) {
				Com.Printf("IOError closing d9emo fiele:" + e);
			}
			SV_INIT.sv.demofile = null;
		}
		SV_USER.SV_Nextserver();
	}
	/*
	=======================
	SV_RateDrop
	
	Returns true if the client is over its current
	bandwidth estimation and should not be sent another packet
	=======================
	*/
	public static boolean SV_RateDrop(client_t c) {
		int total;
		int i;

		// never drop over the loopback
		if (c.netchan.remote_address.type == Defines.NA_LOOPBACK)
			return false;

		total = 0;

		for (i = 0; i < Defines.RATE_MESSAGES; i++) {
			total += c.message_size[i];
		}

		if (total > c.rate) {
			c.surpressCount++;
			c.message_size[SV_INIT.sv.framenum % Defines.RATE_MESSAGES] = 0;
			return true;
		}

		return false;
	}

	private static final byte msgbuf[] = new byte[Defines.MAX_MSGLEN];
	/*
	=======================
	SV_SendClientMessages
	=======================
	*/
	public static void SV_SendClientMessages() {
		int i;
		client_t c;
		int msglen;
		int r;

		msglen = 0;

		// read the next demo message if needed
		if (SV_INIT.sv.state == Defines.ss_demo && SV_INIT.sv.demofile != null) {
			if (SV_MAIN.sv_paused.value != 0)
				msglen = 0;
			else {
				// get the next message
				//r = fread (&msglen, 4, 1, sv.demofile);
				try {
					msglen = EndianHandler.swapInt(SV_INIT.sv.demofile.readInt());
				}
				catch (Exception e) {
					SV_DemoCompleted();
					return;
				}

				//msglen = LittleLong (msglen);
				if (msglen == -1) {
					SV_DemoCompleted();
					return;
				}
				if (msglen > Defines.MAX_MSGLEN)
					Com.Error(Defines.ERR_DROP, "SV_SendClientMessages: msglen > MAX_MSGLEN");

				//r = fread (msgbuf, msglen, 1, sv.demofile);
				r = 0;
				try {
					r = SV_INIT.sv.demofile.read(msgbuf, 0, msglen);
				}
				catch (IOException e1) {
					Com.Printf("IOError: reading demo file, " + e1);
				}
				if (r != msglen) {
					SV_DemoCompleted();
					return;
				}
			}
		}

		// send a message to each connected client
		for (i = 0; i < SV_MAIN.maxclients.value; i++) {
			c = SV_INIT.svs.clients[i];

			if (c.state == 0)
				continue;
			// if the reliable message overflowed,
			// drop the client
			if (c.netchan.message.overflowed) {
				SZ.Clear(c.netchan.message);
				SZ.Clear(c.datagram);
				SV_BroadcastPrintf(Defines.PRINT_HIGH, c.name + " overflowed\n");
				SV_MAIN.SV_DropClient(c);
			}

			if (SV_INIT.sv.state == Defines.ss_cinematic
				|| SV_INIT.sv.state == Defines.ss_demo
				|| SV_INIT.sv.state == Defines.ss_pic)
				Netchan.Transmit(c.netchan, msglen, msgbuf);
			else if (c.state == Defines.cs_spawned) {
				// don't overrun bandwidth
				if (SV_RateDrop(c))
					continue;

				SV_SendClientDatagram(c);
			}
			else {
				// just update reliable	if needed
				if (c.netchan.message.cursize != 0 || Globals.curtime - c.netchan.last_sent > 1000)
					Netchan.Transmit(c.netchan, 0, new byte[0]);
			}
		}
	}
}
