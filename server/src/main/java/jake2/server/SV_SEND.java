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

package jake2.server;

import jake2.qcommon.*;
import jake2.qcommon.network.MulticastTypes;
import jake2.qcommon.network.Netchan;
import jake2.qcommon.network.commands.PrintMessage;
import jake2.qcommon.network.commands.SoundMessage;
import jake2.qcommon.util.Lib;
import jake2.qcommon.util.Math3D;

public class SV_SEND {
	/*
	=============================================================================
	
	Com_Printf redirection
	
	=============================================================================
	*/

	public static void SV_FlushRedirect(int sv_redirected, byte[] outputbuf, GameImportsImpl gameImports) {
		if (sv_redirected == Defines.RD_PACKET) {
			String s = ("print\n" + Lib.CtoJava(outputbuf));
			Netchan.Netchan_OutOfBand(Defines.NS_SERVER, Globals.net_from, s.length(), Lib.stringToBytes(s));
		}
		else if (sv_redirected == Defines.RD_CLIENT) {
			new PrintMessage(Defines.PRINT_HIGH, new String(outputbuf).trim()).writeTo(gameImports.sv_client.netchan.message);
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

		if (level >= cl.messagelevel) {
			new PrintMessage(level, s).writeTo(cl.netchan.message);
		}
	}

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
		float timeofs,
		GameImportsImpl gameImports) {

		if (volume < 0 || volume > 1.0)
			Com.Error(Defines.ERR_FATAL, "SV_StartSound: volume = " + volume);

		if (attenuation < 0 || attenuation > 4)
			Com.Error(Defines.ERR_FATAL, "SV_StartSound: attenuation = " + attenuation);

		//	if (channel < 0 || channel > 15)
		//		Com_Error (ERR_FATAL, "SV_StartSound: channel = %i", channel);

		if (timeofs < 0 || timeofs > 0.255)
			Com.Error(Defines.ERR_FATAL, "SV_StartSound: timeofs = " + timeofs);

		int ent = entity.index;

		// no PHS flag
		boolean use_phs;
		if ((channel & 8) != 0) {
			use_phs = false;
			channel &= 7;
		}
		else
			use_phs = true;

		int sendchan = (ent << 3) | (channel & 7);

		int flags = 0;
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
			origin = gameImports.origin_v;
			if (entity.solid == Defines.SOLID_BSP) {
				for (int i = 0; i < 3; i++)
					gameImports.origin_v[i] = entity.s.origin[i] + 0.5f * (entity.mins[i] + entity.maxs[i]);
			}
			else {
				Math3D.VectorCopy(entity.s.origin, gameImports.origin_v);
			}
		}

		new SoundMessage(flags, soundindex, volume, attenuation, timeofs, sendchan, origin).writeTo(gameImports.sv.multicast);

		// if the sound doesn't attenuate,send it to everyone
		// (global radio chatter, voiceovers, etc)
		if (attenuation == Defines.ATTN_NONE)
			use_phs = false;

		if ((channel & Defines.CHAN_RELIABLE) != 0) {
			if (use_phs)
				gameImports.SV_Multicast(origin, MulticastTypes.MULTICAST_PHS_R);
			else
				gameImports.SV_Multicast(origin, MulticastTypes.MULTICAST_ALL_R);
		}
		else {
			if (use_phs)
				gameImports.SV_Multicast(origin, MulticastTypes.MULTICAST_PHS);
			else
				gameImports.SV_Multicast(origin, MulticastTypes.MULTICAST_ALL);
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
	// todo move to gameImports
	public static boolean SV_SendClientDatagram(client_t client, GameImportsImpl gameImports) {
		//byte msg_buf[] = new byte[Defines.MAX_MSGLEN];

		gameImports.sv_ents.SV_BuildClientFrame(client);

		SZ.Init(gameImports.msg, gameImports.msgbuf, gameImports.msgbuf.length);
		gameImports.msg.allowoverflow = true;

		// send over all the relevant entity_state_t
		// and the player_state_t
		gameImports.sv_ents.SV_WriteFrameToClient(client);

		// copy the accumulated multicast datagram
		// for this client out to the message
		// it is necessary for this to be after the WriteEntities
		// so that entity references will be current
		if (client.datagram.overflowed)
			Com.Printf("WARNING: datagram overflowed for " + client.name + "\n");
		else
			SZ.Write(gameImports.msg, client.datagram.data, client.datagram.cursize);
        client.datagram.clear();

        if (gameImports.msg.overflowed) { // must have room left for the packet header
			Com.Printf("WARNING: msg overflowed for " + client.name + "\n");
			gameImports.msg.clear();
        }

		// send the datagram
		Netchan.Transmit(client.netchan, gameImports.msg.cursize, gameImports.msg.data);

		// record the size for rate estimation
		client.message_size[gameImports.sv.framenum % Defines.RATE_MESSAGES] = gameImports.msg.cursize;

		return true;
	}

}

