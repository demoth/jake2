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
// $Id: SV_ENTS.java,v 1.4 2004-08-29 21:39:25 hzi Exp $

package jake2.server;

import jake2.game.*;
import jake2.qcommon.*;

import java.io.IOException;

public class SV_ENTS extends SV_USER {

	/*
	=============================================================================
	
	Encode a client frame onto the network channel
	
	=============================================================================
	*/

	/*
	=============
	SV_EmitPacketEntities
	
	Writes a delta update of an entity_state_t list to the message.
	=============
	*/
	static void SV_EmitPacketEntities(client_frame_t from, client_frame_t to, sizebuf_t msg) {
		entity_state_t oldent = null, newent = null;
		int oldindex, newindex;
		int oldnum, newnum;
		int from_num_entities;
		int bits;

		MSG.WriteByte(msg, svc_packetentities);

		if (from == null)
			from_num_entities = 0;
		else
			from_num_entities = from.num_entities;

		newindex = 0;
		oldindex = 0;
		while (newindex < to.num_entities || oldindex < from_num_entities) {
			if (newindex >= to.num_entities)
				newnum = 9999;
			else {
				newent = svs.client_entities[(to.first_entity + newindex) % svs.num_client_entities];
				newnum = newent.number;
			}

			if (oldindex >= from_num_entities)
				oldnum = 9999;
			else {
				oldent = svs.client_entities[(from.first_entity + oldindex) % svs.num_client_entities];
				oldnum = oldent.number;
			}

			if (newnum == oldnum) { // delta update from old position
				// because the force parm is false, this will not result
				// in any bytes being emited if the entity has not changed at all
				// note that players are always 'newentities', this updates their oldorigin always
				// and prevents warping
				MSG.WriteDeltaEntity(oldent, newent, msg, false, newent.number <= maxclients.value);
				oldindex++;
				newindex++;
				continue;
			}

			if (newnum < oldnum) { // this is a new entity, send it from the baseline
				MSG.WriteDeltaEntity(sv.baselines[newnum], newent, msg, true, true);
				newindex++;
				continue;
			}

			if (newnum > oldnum) { // the old entity isn't present in the new message
				bits = U_REMOVE;
				if (oldnum >= 256)
					bits |= U_NUMBER16 | U_MOREBITS1;

				MSG.WriteByte(msg, bits & 255);
				if ((bits & 0x0000ff00) != 0)
					MSG.WriteByte(msg, (bits >> 8) & 255);

				if ((bits & U_NUMBER16) != 0)
					MSG.WriteShort(msg, oldnum);
				else
					MSG.WriteByte(msg, oldnum);

				oldindex++;
				continue;
			}
		}

		MSG.WriteShort(msg, 0); // end of packetentities

	}

	/*
	=============
	SV_WritePlayerstateToClient
	
	=============
	*/
	static void SV_WritePlayerstateToClient(client_frame_t from, client_frame_t to, sizebuf_t msg) {
		int i;
		int pflags;
		// ptr
		player_state_t ps, ops;
		// mem
		player_state_t dummy;
		int statbits;

		ps = to.ps;
		if (from == null) {
			//memset (dummy, 0, sizeof(dummy));
			dummy = new player_state_t();
			ops = dummy;
		}
		else
			ops = from.ps;

		//
		// determine what needs to be sent
		//
		pflags = 0;

		if (ps.pmove.pm_type != ops.pmove.pm_type)
			pflags |= PS_M_TYPE;

		if (ps.pmove.origin[0] != ops.pmove.origin[0]
			|| ps.pmove.origin[1] != ops.pmove.origin[1]
			|| ps.pmove.origin[2] != ops.pmove.origin[2])
			pflags |= PS_M_ORIGIN;

		if (ps.pmove.velocity[0] != ops.pmove.velocity[0]
			|| ps.pmove.velocity[1] != ops.pmove.velocity[1]
			|| ps.pmove.velocity[2] != ops.pmove.velocity[2])
			pflags |= PS_M_VELOCITY;

		if (ps.pmove.pm_time != ops.pmove.pm_time)
			pflags |= PS_M_TIME;

		if (ps.pmove.pm_flags != ops.pmove.pm_flags)
			pflags |= PS_M_FLAGS;

		if (ps.pmove.gravity != ops.pmove.gravity)
			pflags |= PS_M_GRAVITY;

		if (ps.pmove.delta_angles[0] != ops.pmove.delta_angles[0]
			|| ps.pmove.delta_angles[1] != ops.pmove.delta_angles[1]
			|| ps.pmove.delta_angles[2] != ops.pmove.delta_angles[2])
			pflags |= PS_M_DELTA_ANGLES;

		if (ps.viewoffset[0] != ops.viewoffset[0] || ps.viewoffset[1] != ops.viewoffset[1] || ps.viewoffset[2] != ops.viewoffset[2])
			pflags |= PS_VIEWOFFSET;

		if (ps.viewangles[0] != ops.viewangles[0] || ps.viewangles[1] != ops.viewangles[1] || ps.viewangles[2] != ops.viewangles[2])
			pflags |= PS_VIEWANGLES;

		if (ps.kick_angles[0] != ops.kick_angles[0]
			|| ps.kick_angles[1] != ops.kick_angles[1]
			|| ps.kick_angles[2] != ops.kick_angles[2])
			pflags |= PS_KICKANGLES;

		if (ps.blend[0] != ops.blend[0] || ps.blend[1] != ops.blend[1] || ps.blend[2] != ops.blend[2] || ps.blend[3] != ops.blend[3])
			pflags |= PS_BLEND;

		if (ps.fov != ops.fov)
			pflags |= PS_FOV;

		if (ps.rdflags != ops.rdflags)
			pflags |= PS_RDFLAGS;

		if (ps.gunframe != ops.gunframe)
			pflags |= PS_WEAPONFRAME;

		pflags |= PS_WEAPONINDEX;

		//
		// write it
		//
		MSG.WriteByte(msg, svc_playerinfo);
		MSG.WriteShort(msg, pflags);

		//
		// write the pmove_state_t
		//
		if ((pflags & PS_M_TYPE) != 0)
			MSG.WriteByte(msg, ps.pmove.pm_type);

		if ((pflags & PS_M_ORIGIN) != 0) {
			MSG.WriteShort(msg, ps.pmove.origin[0]);
			MSG.WriteShort(msg, ps.pmove.origin[1]);
			MSG.WriteShort(msg, ps.pmove.origin[2]);
		}

		if ((pflags & PS_M_VELOCITY) != 0) {
			MSG.WriteShort(msg, ps.pmove.velocity[0]);
			MSG.WriteShort(msg, ps.pmove.velocity[1]);
			MSG.WriteShort(msg, ps.pmove.velocity[2]);
		}

		if ((pflags & PS_M_TIME) != 0)
			MSG.WriteByte(msg, ps.pmove.pm_time);

		if ((pflags & PS_M_FLAGS) != 0)
			MSG.WriteByte(msg, ps.pmove.pm_flags);

		if ((pflags & PS_M_GRAVITY) != 0)
			MSG.WriteShort(msg, ps.pmove.gravity);

		if ((pflags & PS_M_DELTA_ANGLES) != 0) {
			MSG.WriteShort(msg, ps.pmove.delta_angles[0]);
			MSG.WriteShort(msg, ps.pmove.delta_angles[1]);
			MSG.WriteShort(msg, ps.pmove.delta_angles[2]);
		}

		//
		// write the rest of the player_state_t
		//
		if ((pflags & PS_VIEWOFFSET) != 0) {
			MSG.WriteChar(msg, ps.viewoffset[0] * 4);
			MSG.WriteChar(msg, ps.viewoffset[1] * 4);
			MSG.WriteChar(msg, ps.viewoffset[2] * 4);
		}

		if ((pflags & PS_VIEWANGLES) != 0) {
			MSG.WriteAngle16(msg, ps.viewangles[0]);
			MSG.WriteAngle16(msg, ps.viewangles[1]);
			MSG.WriteAngle16(msg, ps.viewangles[2]);
		}

		if ((pflags & PS_KICKANGLES) != 0) {
			MSG.WriteChar(msg, ps.kick_angles[0] * 4);
			MSG.WriteChar(msg, ps.kick_angles[1] * 4);
			MSG.WriteChar(msg, ps.kick_angles[2] * 4);
		}

		if ((pflags & PS_WEAPONINDEX) != 0) {
			MSG.WriteByte(msg, ps.gunindex);
		}

		if ((pflags & PS_WEAPONFRAME) != 0) {
			MSG.WriteByte(msg, ps.gunframe);
			MSG.WriteChar(msg, ps.gunoffset[0] * 4);
			MSG.WriteChar(msg, ps.gunoffset[1] * 4);
			MSG.WriteChar(msg, ps.gunoffset[2] * 4);
			MSG.WriteChar(msg, ps.gunangles[0] * 4);
			MSG.WriteChar(msg, ps.gunangles[1] * 4);
			MSG.WriteChar(msg, ps.gunangles[2] * 4);
		}

		if ((pflags & PS_BLEND) != 0) {
			MSG.WriteByte(msg, ps.blend[0] * 255);
			MSG.WriteByte(msg, ps.blend[1] * 255);
			MSG.WriteByte(msg, ps.blend[2] * 255);
			MSG.WriteByte(msg, ps.blend[3] * 255);
		}
		if ((pflags & PS_FOV) != 0)
			MSG.WriteByte(msg, ps.fov);
		if ((pflags & PS_RDFLAGS) != 0)
			MSG.WriteByte(msg, ps.rdflags);

		// send stats
		statbits = 0;
		for (i = 0; i < MAX_STATS; i++)
			if (ps.stats[i] != ops.stats[i])
				statbits |= 1 << i;
		MSG.WriteLong(msg, statbits);
		for (i = 0; i < MAX_STATS; i++)
			if ((statbits & (1 << i)) != 0)
				MSG.WriteShort(msg, ps.stats[i]);
	}

	/*
	==================
	SV_WriteFrameToClient
	==================
	*/
	public static void SV_WriteFrameToClient(client_t client, sizebuf_t msg) {
		//ptr
		client_frame_t frame, oldframe;
		int lastframe;

		//Com.Printf ("%i . %i\n", new Vargs().add(client.lastframe).add(sv.framenum));
		// this is the frame we are creating
		frame = client.frames[sv.framenum & UPDATE_MASK];
		if (client.lastframe <= 0) { // client is asking for a retransmit
			oldframe = null;
			lastframe = -1;
		}
		else if (
			sv.framenum - client.lastframe >= (UPDATE_BACKUP - 3)) { // client hasn't gotten a good message through in a long time
			// Com_Printf ("%s: Delta request from out-of-date packet.\n", client.name);
			oldframe = null;
			lastframe = -1;
		}
		else { // we have a valid message to delta from
			oldframe = client.frames[client.lastframe & UPDATE_MASK];
			lastframe = client.lastframe;
		}

		MSG.WriteByte(msg, svc_frame);
		MSG.WriteLong(msg, sv.framenum);
		MSG.WriteLong(msg, lastframe); // what we are delta'ing from
		MSG.WriteByte(msg, client.surpressCount); // rate dropped packets
		client.surpressCount = 0;

		// send over the areabits
		MSG.WriteByte(msg, frame.areabytes);
		SZ.Write(msg, frame.areabits, frame.areabytes);

		// delta encode the playerstate
		SV_WritePlayerstateToClient(oldframe, frame, msg);

		// delta encode the entities
		SV_EmitPacketEntities(oldframe, frame, msg);
	}

	/*
	=============================================================================
	
	Build a client frame structure
	
	=============================================================================
	*/

	static byte fatpvs[] = new byte[65536 / 8]; // 32767 is MAX_MAP_LEAFS

	/*
	============
	SV_FatPVS
	
	The client will interpolate the view position,
	so we can't use a single PVS point
	===========
	*/
	public static void SV_FatPVS(float[] org) {
		int leafs[] = new int[64];
		int i, j, count;
		int longs;
		byte src[];
		float[] mins = { 0, 0, 0 }, maxs = { 0, 0, 0 };

		for (i = 0; i < 3; i++) {
			mins[i] = org[i] - 8;
			maxs[i] = org[i] + 8;
		}

		count = CM.CM_BoxLeafnums(mins, maxs, leafs, 64, null);

		if (count < 1)
			Com.Error(ERR_FATAL, "SV_FatPVS: count < 1");

		longs = (CM.CM_NumClusters() + 31) >> 5;

		// convert leafs to clusters
		for (i = 0; i < count; i++)
			leafs[i] = CM.CM_LeafCluster(leafs[i]);

		System.arraycopy(CM.CM_ClusterPVS(leafs[0]), 0, fatpvs, 0, longs << 2);
		// or in all the other leaf bits
		for (i = 1; i < count; i++) {
			for (j = 0; j < i; j++)
				if (leafs[i] == leafs[j])
					break;
			if (j != i)
				continue; // already have the cluster we want

			src = CM.CM_ClusterPVS(leafs[i]);

			//for (j=0 ; j<longs ; j++)
			//	((long *)fatpvs)[j] |= ((long *)src)[j];
			int k=0;
			for (j = 0; j < longs; j++) {
				fatpvs[k] |= src[k++];
				fatpvs[k] |= src[k++];
				fatpvs[k] |= src[k++];
				fatpvs[k] |= src[k++];
			}
		}
	}

	/*
	=============
	SV_BuildClientFrame
	
	Decides which entities are going to be visible to the client, and
	copies off the playerstat and areabits.
	=============
	*/
	public static void SV_BuildClientFrame(client_t client) {
		int e, i;
		float[] org = { 0, 0, 0 };
		edict_t ent;
		edict_t clent;
		client_frame_t frame;
		entity_state_t state;
		int l;
		int clientarea, clientcluster;
		int leafnum;
		int c_fullsend;
		byte clientphs[];
		byte bitvector[];

		clent = client.edict;
		if (clent.client == null)
			return; // not in game yet

		// this is the frame we are creating
		frame = client.frames[sv.framenum & UPDATE_MASK];

		frame.senttime = svs.realtime; // save it for ping calc later

		// find the client's PVS
		for (i = 0; i < 3; i++)
			org[i] = clent.client.ps.pmove.origin[i] * 0.125f + clent.client.ps.viewoffset[i];

		leafnum = CM.CM_PointLeafnum(org);
		clientarea = CM.CM_LeafArea(leafnum);
		clientcluster = CM.CM_LeafCluster(leafnum);

		// calculate the visible areas
		frame.areabytes = CM.CM_WriteAreaBits(frame.areabits, clientarea);

		// grab the current player_state_t
		frame.ps.set(clent.client.ps);

		SV_FatPVS(org);
		clientphs = CM.CM_ClusterPHS(clientcluster);

		// build up the list of visible entities
		frame.num_entities = 0;
		frame.first_entity = svs.next_client_entities;

		c_fullsend = 0;

		for (e = 1; e < GameBase.num_edicts; e++) {
			ent = GameBase.g_edicts[e];

			// ignore ents without visible models
			if ((ent.svflags & SVF_NOCLIENT) != 0)
				continue;

			// ignore ents without visible models unless they have an effect
			if (0 == ent.s.modelindex && 0 == ent.s.effects && 0 == ent.s.sound && 0 == ent.s.event)
				continue;

			// ignore if not touching a PV leaf
			if (ent != clent) {
				// check area
				if (!CM.CM_AreasConnected(clientarea, ent.areanum)) { // doors can legally straddle two areas, so
					// we may need to check another one
					if (0 == ent.areanum2 || !CM.CM_AreasConnected(clientarea, ent.areanum2))
						continue; // blocked by a door
				}

				// beams just check one point for PHS
				if ((ent.s.renderfx & RF_BEAM) != 0) {
					l = ent.clusternums[0];
					if (0 == (clientphs[l >> 3] & (1 << (l & 7))))
						continue;
				}
				else {
					// FIXME: if an ent has a model and a sound, but isn't
					// in the PVS, only the PHS, clear the model
					if (ent.s.sound == 0) {
						bitvector = fatpvs; //clientphs;
					}
					else
						bitvector = fatpvs;

					if (ent.num_clusters == -1) { // too many leafs for individual check, go by headnode
						if (!CM.CM_HeadnodeVisible(ent.headnode, bitvector))
							continue;
						c_fullsend++;
					}
					else { // check individual leafs
						for (i = 0; i < ent.num_clusters; i++) {
							l = ent.clusternums[i];
							if ((bitvector[l >> 3] & (1 << (l & 7))) != 0)
								break;
						}
						if (i == ent.num_clusters)
							continue; // not visible
					}

					if (ent.s.modelindex == 0) { // don't send sounds if they will be attenuated away
						float[] delta = { 0, 0, 0 };
						float len;

						VectorSubtract(org, ent.s.origin, delta);
						len = VectorLength(delta);
						if (len > 400)
							continue;
					}
				}
			}

			// add it to the circular client_entities array
			int ix = svs.next_client_entities % svs.num_client_entities;
			state = svs.client_entities[ix];
			if (ent.s.number != e) {
				Com.DPrintf("FIXING ENT.S.NUMBER!!!\n");
				ent.s.number = e;
			}

			//*state = ent.s;
			svs.client_entities[ix].set(ent.s);

			// don't mark players missiles as solid
			if (ent.owner == client.edict)
				state.solid = 0;

			svs.next_client_entities++;
			frame.num_entities++;
		}
	}

	/*
	==================
	SV_RecordDemoMessage
	
	Save everything in the world out without deltas.
	Used for recording footage for merged or assembled demos
	==================
	*/
	public static void SV_RecordDemoMessage() {
		int e;
		edict_t ent;
		entity_state_t nostate = new entity_state_t(null);
		sizebuf_t buf = new sizebuf_t();
		byte buf_data[] = new byte[32768];
		int len;

		if (svs.demofile == null)
			return;

		//memset (nostate, 0, sizeof(nostate));
		SZ.Init(buf, buf_data, buf_data.length);

		// write a frame message that doesn't contain a player_state_t
		MSG.WriteByte(buf, svc_frame);
		MSG.WriteLong(buf, sv.framenum);

		MSG.WriteByte(buf, svc_packetentities);

		e = 1;
		ent = GameBase.g_edicts[e];

		while (e < GameBase.num_edicts) {
			// ignore ents without visible models unless they have an effect
			if (ent.inuse
				&& ent.s.number != 0
				&& (ent.s.modelindex != 0 || ent.s.effects != 0 || ent.s.sound != 0 || ent.s.event != 0)
				&& 0 == (ent.svflags & SVF_NOCLIENT))
				MSG.WriteDeltaEntity(nostate, ent.s, buf, false, true);

			e++;
			ent = GameBase.g_edicts[e];
		}

		MSG.WriteShort(buf, 0); // end of packetentities

		// now add the accumulated multicast information
		SZ.Write(buf, svs.demo_multicast.data, svs.demo_multicast.cursize);
		SZ.Clear(svs.demo_multicast);

		// now write the entire message to the file, prefixed by the length
		len = EndianHandler.swapInt(buf.cursize);
		
		try {
			//fwrite (len, 4, 1, svs.demofile);
			svs.demofile.writeInt(len);
			//fwrite (buf.data, buf.cursize, 1, svs.demofile);
			svs.demofile.write(buf.data, 0, buf.cursize);
		}
		catch (IOException e1) {
			Com.Printf("Error writing demo file:" + e);
		}
	}
}
