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

import jake2.qcommon.Com;
import jake2.qcommon.Defines;
import jake2.qcommon.edict_t;
import jake2.qcommon.network.MulticastTypes;
import jake2.qcommon.network.messages.NetworkMessage;
import jake2.qcommon.network.messages.server.PrintMessage;
import jake2.qcommon.network.messages.server.SoundMessage;
import jake2.qcommon.util.Math3D;

import java.util.ArrayList;
import java.util.Collection;

public class SV_SEND {
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
			cl.netchan.reliablePending.add(new PrintMessage(level, s));
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

		// if the sound doesn't attenuate,send it to everyone
		// (global radio chatter, voiceovers, etc)
		if (attenuation == Defines.ATTN_NONE)
			use_phs = false;

		final MulticastTypes multicastType;

		if ((channel & Defines.CHAN_RELIABLE) != 0) {
			if (use_phs)
				multicastType = MulticastTypes.MULTICAST_PHS_R;
			else
				multicastType = MulticastTypes.MULTICAST_ALL_R;
		} else {
			if (use_phs)
				multicastType = MulticastTypes.MULTICAST_PHS;
			else
				multicastType = MulticastTypes.MULTICAST_ALL;
		}
		gameImports.multicastMessage(origin, new SoundMessage(flags, soundindex, volume, attenuation, timeofs, sendchan, origin), multicastType);
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
	public static void SV_SendClientDatagram(client_t client, GameImportsImpl gameImports) {
		gameImports.sv_ents.SV_BuildClientFrame(client);

		// send over all the relevant entity_state_t
		// and the player_state_t
		Collection<NetworkMessage> unreliable = new ArrayList<>();
		unreliable.addAll(gameImports.sv_ents.SV_WriteFrameToClient(client));

		// copy the accumulated multicast datagram
		// for this client out to the message
		// it is necessary for this to be after the SV_WriteFrameToClient
		// so that entity references will be current
		unreliable.addAll(client.unreliable);
		client.unreliable.clear();
		// send the datagram
		client.netchan.transmit(unreliable);

		// record the size for rate estimation
		client.message_size[gameImports.sv.framenum % Defines.RATE_MESSAGES] = unreliable.stream().mapToInt(NetworkMessage::getSize).sum();
	}

}

