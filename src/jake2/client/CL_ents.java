/*
 * CL_ents.java
 * Copyright (C) 2004
 * 
 * $Id: CL_ents.java,v 1.14 2004-02-25 13:20:29 hoz Exp $
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

import jake2.game.entity_state_t;
import jake2.game.player_state_t;
import jake2.qcommon.*;
import jake2.render.model_t;

/**
 * CL_ents
 */
//	   cl_ents.c -- entity parsing and management
public class CL_ents extends CL_inv {


	/*
	=========================================================================
	
	FRAME PARSING
	
	=========================================================================
	*/

	/*
	=================
	CL_ParseEntityBits
	
	Returns the entity number and the header bits
	=================
	*/
	static int bitcounts[] = new int[32]; /// just for protocol profiling
	public static int ParseEntityBits(CM.intwrap bits) {
		int b, total;
		int i;
		int number;

		total = MSG.ReadByte(net_message);
		if ((total & U_MOREBITS1) != 0) {
			b = MSG.ReadByte(net_message);
			total |= b << 8;
		}
		if ((total & U_MOREBITS2) != 0) {
			b = MSG.ReadByte(net_message);
			total |= b << 16;
		}
		if ((total & U_MOREBITS3) != 0) {
			b = MSG.ReadByte(net_message);
			total |= b << 24;
		}

		// count the bits for net profiling
		for (i = 0; i < 32; i++)
			if ((total & (1 << i)) != 0)
				bitcounts[i]++;

		if ((total & U_NUMBER16) != 0)
			number = MSG.ReadShort(net_message);
		else
			number = MSG.ReadByte(net_message);

		bits.i = total;

		return number;
	}

	/*
	==================
	CL_ParseDelta
	
	Can go from either a baseline or a previous packet_entity
	==================
	*/
	public static void ParseDelta(entity_state_t from, entity_state_t to, int number, int bits) {
		// set everything to the state we are delta'ing from
		to.set(from);

		VectorCopy(from.origin, to.old_origin);
		to.number = number;

		if ((bits & U_MODEL) != 0)
			to.modelindex = MSG.ReadByte(net_message);
		if ((bits & U_MODEL2) != 0)
			to.modelindex2 = MSG.ReadByte(net_message);
		if ((bits & U_MODEL3) != 0)
			to.modelindex3 = MSG.ReadByte(net_message);
		if ((bits & U_MODEL4) != 0)
			to.modelindex4 = MSG.ReadByte(net_message);

		if ((bits & U_FRAME8) != 0)
			to.frame = MSG.ReadByte(net_message);
		if ((bits & U_FRAME16) != 0)
			to.frame = MSG.ReadShort(net_message);

		if ((bits & U_SKIN8) != 0 && (bits & U_SKIN16) != 0) //used for laser colors
			to.skinnum = MSG.ReadLong(net_message);
		else if ((bits & U_SKIN8) != 0)
			to.skinnum = MSG.ReadByte(net_message);
		else if ((bits & U_SKIN16) != 0)
			to.skinnum = MSG.ReadShort(net_message);

		if ((bits & (U_EFFECTS8 | U_EFFECTS16)) == (U_EFFECTS8 | U_EFFECTS16))
			to.effects = MSG.ReadLong(net_message);
		else if ((bits & U_EFFECTS8) != 0)
			to.effects = MSG.ReadByte(net_message);
		else if ((bits & U_EFFECTS16) != 0)
			to.effects = MSG.ReadShort(net_message);

		if ((bits & (U_RENDERFX8 | U_RENDERFX16)) == (U_RENDERFX8 | U_RENDERFX16))
			to.renderfx = MSG.ReadLong(net_message);
		else if ((bits & U_RENDERFX8) != 0)
			to.renderfx = MSG.ReadByte(net_message);
		else if ((bits & U_RENDERFX16) != 0)
			to.renderfx = MSG.ReadShort(net_message);

		if ((bits & U_ORIGIN1) != 0)
			to.origin[0] = MSG.ReadCoord(net_message);
		if ((bits & U_ORIGIN2) != 0)
			to.origin[1] = MSG.ReadCoord(net_message);
		if ((bits & U_ORIGIN3) != 0)
			to.origin[2] = MSG.ReadCoord(net_message);

		if ((bits & U_ANGLE1) != 0)
			to.angles[0] = MSG.ReadAngle(net_message);
		if ((bits & U_ANGLE2) != 0)
			to.angles[1] = MSG.ReadAngle(net_message);
		if ((bits & U_ANGLE3) != 0)
			to.angles[2] = MSG.ReadAngle(net_message);

		if ((bits & U_OLDORIGIN) != 0)
			MSG.ReadPos(net_message, to.old_origin);

		if ((bits & U_SOUND) != 0)
			to.sound = MSG.ReadByte(net_message);

		if ((bits & U_EVENT) != 0)
			to.event = MSG.ReadByte(net_message);
		else
			to.event = 0;

		if ((bits & U_SOLID) != 0)
			to.solid = MSG.ReadShort(net_message);
	}

	/*
	==================
	CL_DeltaEntity
	
	Parses deltas from the given base and adds the resulting entity
	to the current frame
	==================
	*/
	public static void DeltaEntity(frame_t frame, int newnum, entity_state_t old, int bits) {
		centity_t ent;
		entity_state_t state;

		ent = cl_entities[newnum];

		state = cl_parse_entities[cl.parse_entities & (MAX_PARSE_ENTITIES - 1)];
		cl.parse_entities++;
		frame.num_entities++;

		ParseDelta(old, state, newnum, bits);

		// some data changes will force no lerping
		if (state.modelindex != ent.current.modelindex
			|| state.modelindex2 != ent.current.modelindex2
			|| state.modelindex3 != ent.current.modelindex3
			|| state.modelindex4 != ent.current.modelindex4
			|| Math.abs(state.origin[0] - ent.current.origin[0]) > 512
			|| Math.abs(state.origin[1] - ent.current.origin[1]) > 512
			|| Math.abs(state.origin[2] - ent.current.origin[2]) > 512
			|| state.event == EV_PLAYER_TELEPORT
			|| state.event == EV_OTHER_TELEPORT) {
			ent.serverframe = -99;
		}

		if (ent.serverframe != cl.frame.serverframe - 1) { // wasn't in last update, so initialize some things
			ent.trailcount = 1024; // for diminishing rocket / grenade trails
			// duplicate the current state so lerping doesn't hurt anything
			ent.prev.set(state);
			if (state.event == EV_OTHER_TELEPORT) {
				VectorCopy(state.origin, ent.prev.origin);
				VectorCopy(state.origin, ent.lerp_origin);
			}
			else {
				VectorCopy(state.old_origin, ent.prev.origin);
				VectorCopy(state.old_origin, ent.lerp_origin);
			}
		}
		else { // shuffle the last state to previous
			// Copy !
			ent.prev.set(ent.current);
		}

		ent.serverframe = cl.frame.serverframe;
		// Copy !
		ent.current.set(state);
	}

	/*
	==================
	CL_ParsePacketEntities
	
	An svc_packetentities has just been parsed, deal with the
	rest of the data stream.
	==================
	*/
	public static void ParsePacketEntities(frame_t oldframe, frame_t newframe) {
		int newnum;
		int bits=0;

		entity_state_t oldstate=null;
		int oldindex, oldnum;

		newframe.parse_entities = cl.parse_entities;
		newframe.num_entities = 0;

		// delta from the entities present in oldframe
		oldindex = 0;
		if (oldframe == null)
			oldnum = 99999;
		else {
			if (oldindex >= oldframe.num_entities)
				oldnum = 99999;
			else {
				oldstate = cl_parse_entities[(oldframe.parse_entities + oldindex) & (MAX_PARSE_ENTITIES - 1)];
				oldnum = oldstate.number;
			}
		}

		while (true) {
			CM.intwrap iw = new CM.intwrap(bits);
			newnum = ParseEntityBits(iw);
			bits = iw.i;

			if (newnum >= MAX_EDICTS)
				Com.Error(ERR_DROP, "CL_ParsePacketEntities: bad number:" + newnum);

			if (net_message.readcount > net_message.cursize)
				Com.Error(ERR_DROP, "CL_ParsePacketEntities: end of message");

			if (0 == newnum)
				break;

			while (oldnum < newnum) { // one or more entities from the old packet are unchanged
				if (cl_shownet.value == 3)
					Com.Printf("   unchanged: " + oldnum + "\n");
				DeltaEntity(newframe, oldnum, oldstate, 0);

				oldindex++;

				if (oldindex >= oldframe.num_entities)
					oldnum = 99999;
				else {
					oldstate = cl_parse_entities[(oldframe.parse_entities + oldindex) & (MAX_PARSE_ENTITIES - 1)];
					oldnum = oldstate.number;
				}
			}

			if ((bits & U_REMOVE) != 0) { // the entity present in oldframe is not in the current frame
				if (cl_shownet.value == 3)
					Com.Printf("   remove: " + newnum + "\n");
				if (oldnum != newnum)
					Com.Printf("U_REMOVE: oldnum != newnum\n");

				oldindex++;

				if (oldindex >= oldframe.num_entities)
					oldnum = 99999;
				else {
					oldstate = cl_parse_entities[(oldframe.parse_entities + oldindex) & (MAX_PARSE_ENTITIES - 1)];
					oldnum = oldstate.number;
				}
				continue;
			}

			if (oldnum == newnum) { // delta from previous state
				if (cl_shownet.value == 3)
					Com.Printf("   delta: " + newnum + "\n");
				DeltaEntity(newframe, newnum, oldstate, bits);

				oldindex++;

				if (oldindex >= oldframe.num_entities)
					oldnum = 99999;
				else {
					oldstate = cl_parse_entities[(oldframe.parse_entities + oldindex) & (MAX_PARSE_ENTITIES - 1)];
					oldnum = oldstate.number;
				}
				continue;
			}

			if (oldnum > newnum) { // delta from baseline
				if (cl_shownet.value == 3)
					Com.Printf("   baseline: " + newnum + "\n");
				DeltaEntity(newframe, newnum, cl_entities[newnum].baseline, bits);
				continue;
			}

		}

		// any remaining entities in the old frame are copied over
		while (oldnum != 99999) { // one or more entities from the old packet are unchanged
			if (cl_shownet.value == 3)
				Com.Printf("   unchanged: " + oldnum + "\n");
			DeltaEntity(newframe, oldnum, oldstate, 0);

			oldindex++;

			if (oldindex >= oldframe.num_entities)
				oldnum = 99999;
			else {
				oldstate = cl_parse_entities[(oldframe.parse_entities + oldindex) & (MAX_PARSE_ENTITIES - 1)];
				oldnum = oldstate.number;
			}
		}
	}

	/*
	===================
	CL_ParsePlayerstate
	===================
	*/
	public static void ParsePlayerstate(frame_t oldframe, frame_t newframe) {
		int flags;
		player_state_t state;
		int i;
		int statbits;

		state = newframe.playerstate;

		// clear to old value before delta parsing
		if (oldframe != null)
			state.set(oldframe.playerstate);
		else
			//memset (state, 0, sizeof(*state));
			state.clear();

		flags = MSG.ReadShort(net_message);

		//
		// parse the pmove_state_t
		//
		if ((flags & PS_M_TYPE) != 0)
			state.pmove.pm_type = MSG.ReadByte(net_message);

		if ((flags & PS_M_ORIGIN) != 0) {
			state.pmove.origin[0] = MSG.ReadShort(net_message);
			state.pmove.origin[1] = MSG.ReadShort(net_message);
			state.pmove.origin[2] = MSG.ReadShort(net_message);
		}

		if ((flags & PS_M_VELOCITY) != 0) {
			state.pmove.velocity[0] = MSG.ReadShort(net_message);
			state.pmove.velocity[1] = MSG.ReadShort(net_message);
			state.pmove.velocity[2] = MSG.ReadShort(net_message);
		}

		if ((flags & PS_M_TIME) != 0)
		{
			state.pmove.pm_time = (byte) MSG.ReadByte(net_message);
		}

		if ((flags & PS_M_FLAGS) != 0)
			state.pmove.pm_flags = (byte) MSG.ReadByte(net_message);

		if ((flags & PS_M_GRAVITY) != 0)
			state.pmove.gravity = MSG.ReadShort(net_message);

		if ((flags & PS_M_DELTA_ANGLES) != 0) {
			state.pmove.delta_angles[0] = MSG.ReadShort(net_message);
			state.pmove.delta_angles[1] = MSG.ReadShort(net_message);
			state.pmove.delta_angles[2] = MSG.ReadShort(net_message);
		}

		if (cl.attractloop)
			state.pmove.pm_type = PM_FREEZE; // demo playback

		//
		// parse the rest of the player_state_t
		//
		if ((flags & PS_VIEWOFFSET) != 0) {
			state.viewoffset[0] = MSG.ReadChar(net_message) * 0.25f;
			state.viewoffset[1] = MSG.ReadChar(net_message) * 0.25f;
			state.viewoffset[2] = MSG.ReadChar(net_message) * 0.25f;
		}

		if ((flags & PS_VIEWANGLES) != 0) {
			state.viewangles[0] = MSG.ReadAngle16(net_message);
			state.viewangles[1] = MSG.ReadAngle16(net_message);
			state.viewangles[2] = MSG.ReadAngle16(net_message);
		}

		if ((flags & PS_KICKANGLES) != 0) {
			
			state.kick_angles[0] = MSG.ReadChar(net_message) * 0.25f;
			state.kick_angles[1] = MSG.ReadChar(net_message) * 0.25f;
			state.kick_angles[2] = MSG.ReadChar(net_message) * 0.25f;
			
		}

		if ((flags & PS_WEAPONINDEX) != 0) {
			state.gunindex = MSG.ReadByte(net_message);
		}

		if ((flags & PS_WEAPONFRAME) != 0) {
			state.gunframe = MSG.ReadByte(net_message);
			state.gunoffset[0] = MSG.ReadChar(net_message) * 0.25f;
			state.gunoffset[1] = MSG.ReadChar(net_message) * 0.25f;
			state.gunoffset[2] = MSG.ReadChar(net_message) * 0.25f;
			state.gunangles[0] = MSG.ReadChar(net_message) * 0.25f;
			state.gunangles[1] = MSG.ReadChar(net_message) * 0.25f;
			state.gunangles[2] = MSG.ReadChar(net_message) * 0.25f;
		}

		if ((flags & PS_BLEND) != 0) {
			state.blend[0] = MSG.ReadByte(net_message) / 255.0f;
			state.blend[1] = MSG.ReadByte(net_message) / 255.0f;
			state.blend[2] = MSG.ReadByte(net_message) / 255.0f;
			state.blend[3] = MSG.ReadByte(net_message) / 255.0f;
		}

		if ((flags & PS_FOV) != 0)
			state.fov = MSG.ReadByte(net_message);

		if ((flags & PS_RDFLAGS) != 0)
			state.rdflags = MSG.ReadByte(net_message);

		// parse stats
		statbits = MSG.ReadLong(net_message);
		for (i = 0; i < MAX_STATS; i++)
			if ((statbits & (1 << i))!=0)
				state.stats[i] = MSG.ReadShort(net_message);
	}

	/*
	==================
	CL_FireEntityEvents
	
	==================
	*/
	public static void FireEntityEvents(frame_t frame) {
		entity_state_t s1;
		int pnum, num;

		for (pnum = 0; pnum < frame.num_entities; pnum++) {
			num = (frame.parse_entities + pnum) & (MAX_PARSE_ENTITIES - 1);
			s1 = cl_parse_entities[num];
			if (s1.event!=0)
				EntityEvent(s1);

			// EF_TELEPORTER acts like an event, but is not cleared each frame
			if ((s1.effects & EF_TELEPORTER)!=0)
				CL_fx.TeleporterParticles(s1);
		}
	}

	/*
	================
	CL_ParseFrame
	================
	*/
	public static void ParseFrame() {
		int cmd;
		int len;
		frame_t old;

		//memset( cl.frame, 0, sizeof(cl.frame));
		cl.frame.reset();

		cl.frame.serverframe = MSG.ReadLong(net_message);
		cl.frame.deltaframe = MSG.ReadLong(net_message);
		cl.frame.servertime = cl.frame.serverframe * 100;

		// BIG HACK to let old demos continue to work
		if (cls.serverProtocol != 26)
			cl.surpressCount = MSG.ReadByte(net_message);

		if (cl_shownet.value == 3)
			Com.Printf("   frame:" +  cl.frame.serverframe + "  delta:" + cl.frame.deltaframe + "\n");

		// If the frame is delta compressed from data that we
		// no longer have available, we must suck up the rest of
		// the frame, but not use it, then ask for a non-compressed
		// message 
		if (cl.frame.deltaframe <= 0) {
			cl.frame.valid = true; // uncompressed frame
			old = null;
			cls.demowaiting = false; // we can start recording now
		}
		else {
			old = cl.frames[cl.frame.deltaframe & UPDATE_MASK];
			if (!old.valid) { // should never happen
				Com.Printf("Delta from invalid frame (not supposed to happen!).\n");
			}
			if (old.serverframe != cl.frame.deltaframe) { // The frame that the server did the delta from
				// is too old, so we can't reconstruct it properly.
				Com.Printf("Delta frame too old.\n");
			}
			else if (cl.parse_entities - old.parse_entities > MAX_PARSE_ENTITIES - 128) {
				Com.Printf("Delta parse_entities too old.\n");
			}
			else
				cl.frame.valid = true; // valid delta parse
		}

		// clamp time 
		if (cl.time > cl.frame.servertime)
			cl.time = cl.frame.servertime;
		else if (cl.time < cl.frame.servertime - 100)
			cl.time = cl.frame.servertime - 100;

		// read areabits
		len = MSG.ReadByte(net_message);
		MSG.ReadData(net_message, cl.frame.areabits, len);

		// read playerinfo
		cmd = MSG.ReadByte(net_message);
		CL_parse.SHOWNET(CL_parse.svc_strings[cmd]);
		if (cmd != svc_playerinfo)
			Com.Error(ERR_DROP, "CL_ParseFrame: not playerinfo");
		ParsePlayerstate(old, cl.frame);

		// read packet entities
		cmd = MSG.ReadByte(net_message);
		CL_parse.SHOWNET(CL_parse.svc_strings[cmd]);
		if (cmd != svc_packetentities)
			Com.Error(ERR_DROP, "CL_ParseFrame: not packetentities");
			
		ParsePacketEntities(old, cl.frame);

		// save the frame off in the backup array for later delta comparisons
		cl.frames[cl.frame.serverframe & UPDATE_MASK].set(cl.frame);

		if (cl.frame.valid) {
			// getting a valid frame message ends the connection process
			if (cls.state != ca_active) {
				cls.state = ca_active;
				cl.force_refdef = true;
				
				cl.predicted_origin[0] = cl.frame.playerstate.pmove.origin[0] * 0.125f;
				cl.predicted_origin[1] = cl.frame.playerstate.pmove.origin[1] * 0.125f;
				cl.predicted_origin[2] = cl.frame.playerstate.pmove.origin[2] * 0.125f;

				VectorCopy(cl.frame.playerstate.viewangles, cl.predicted_angles);
				if (cls.disable_servercount != cl.servercount && cl.refresh_prepped)
					SCR.EndLoadingPlaque(); // get rid of loading plaque
			}
			cl.sound_prepped = true; // can start mixing ambient sounds

			// fire entity events
			FireEntityEvents(cl.frame);
			CL_pred.CheckPredictionError();
		}
	}

	/*
	==========================================================================
	
	INTERPOLATE BETWEEN FRAMES TO GET RENDERING PARMS
	
	==========================================================================
	*/

	public static model_t S_RegisterSexedModel(entity_state_t  ent, String base) {
		int n;
		model_t mdl;
		String model;
		String buffer;

		// determine what model the client is using
		model = "";
		
		n = CS_PLAYERSKINS + ent.number - 1;
		
		if (cl.configstrings[n].length() >0) {
			
			int pos = cl.configstrings[n].indexOf('\\');
			if (pos!=-1) {
				pos++;
				model = cl.configstrings[n].substring(pos);
				pos = model.indexOf('/');
				if (pos !=-1)
					model = model.substring(0,pos);
			}
		}
		// if we can't figure it out, they're male
		if (model.length()==0)
			model = "male";

		buffer= "players/" + model + "/" + base + 1;
		mdl = re.RegisterModel(buffer);
		if (mdl==null) {
			// not found, try default weapon model
			buffer =  "players/" + model + "/weapon.md2";
			mdl = re.RegisterModel(buffer);
			if (mdl==null) {
				// no, revert to the male model
				 buffer="players/male/" + base + 1;
				mdl = re.RegisterModel(buffer);
				if (mdl==null) {
					// last try, default male weapon.md2
					buffer = "players/male/weapon.md2";
					mdl = re.RegisterModel(buffer);
				}
			}
		}

		return mdl;
	}

	//	   PMM - used in shell code 

	/*
	===============
	CL_AddPacketEntities
	
	===============
	*/
	static int bfg_lightramp[] = { 300, 400, 600, 300, 150, 75 };
	
	static void AddPacketEntities(frame_t  frame) {
		entity_t ent;
		entity_state_t s1;
		float autorotate;
		int i;
		int pnum;
		centity_t  cent;
		int autoanim;
		clientinfo_t  ci;
		int effects, renderfx;

		// bonus items rotate at a fixed rate
		autorotate = anglemod(cl.time / 10);

		// brush models can auto animate their frames
		autoanim = 2 * cl.time / 1000;

		//memset( ent, 0, sizeof(ent));
		ent = new entity_t();

		for (pnum = 0; pnum < frame.num_entities; pnum++) {
			s1 =   cl_parse_entities[(frame.parse_entities + pnum) & (MAX_PARSE_ENTITIES - 1)];

			cent =   cl_entities[s1.number];

			effects = s1.effects;
			renderfx = s1.renderfx;

			// set frame
			if ((effects & EF_ANIM01)!=0)
				ent.frame = autoanim & 1;
			else if ((effects & EF_ANIM23)!=0)
				ent.frame = 2 + (autoanim & 1);
			else if ((effects & EF_ANIM_ALL)!=0)
				ent.frame = autoanim;
			else if ((effects & EF_ANIM_ALLFAST)!=0)
				ent.frame = cl.time / 100;
			else
				ent.frame = s1.frame;

			// quad and pent can do different things on client
			if ((effects & EF_PENT)!=0) {
				effects &= ~EF_PENT;
				effects |= EF_COLOR_SHELL;
				renderfx |= RF_SHELL_RED;
			}

			if ((effects & EF_QUAD)!=0) {
				effects &= ~EF_QUAD;
				effects |= EF_COLOR_SHELL;
				renderfx |= RF_SHELL_BLUE;
			}
			//	  ======
			//	   PMM
			if ((effects & EF_DOUBLE)!=0) {
				effects &= ~EF_DOUBLE;
				effects |= EF_COLOR_SHELL;
				renderfx |= RF_SHELL_DOUBLE;
			}

			if ((effects & EF_HALF_DAMAGE) !=0){
				effects &= ~EF_HALF_DAMAGE;
				effects |= EF_COLOR_SHELL;
				renderfx |= RF_SHELL_HALF_DAM;
			}
			//	   pmm
			//	  ======
			ent.oldframe = cent.prev.frame;
			ent.backlerp = 1.0f - cl.lerpfrac;

			if ((renderfx & (RF_FRAMELERP | RF_BEAM))!=0) { // step origin discretely, because the frames
				// do the animation properly
				VectorCopy(cent.current.origin, ent.origin);
				VectorCopy(cent.current.old_origin, ent.oldorigin);
			}
			else { // interpolate origin
				for (i = 0; i < 3; i++) {
					ent.origin[i] =
						ent.oldorigin[i] = cent.prev.origin[i] + cl.lerpfrac * (cent.current.origin[i] - cent.prev.origin[i]);
				}
			}

			// create a new entity

			// tweak the color of beams
			if ((renderfx & RF_BEAM)!=0) { // the four beam colors are encoded in 32 bits of skinnum (hack)
				ent.alpha = 0.30f;
				ent.skinnum = (s1.skinnum >> ((rnd.nextInt(4)) * 8)) & 0xff;
				Math.random();
				ent.model = null;
			}
			else {
				// set skin
				if (s1.modelindex == 255) { // use custom player skin
					ent.skinnum = 0;
					ci =   cl.clientinfo[s1.skinnum & 0xff];
					ent.skin = ci.skin;
					ent.model = ci.model;
					if (null==ent.skin || null==ent.model) {
						ent.skin = cl.baseclientinfo.skin;
						ent.model = cl.baseclientinfo.model;
					}

					//	  ============
					//	  PGM
					if ((renderfx & RF_USE_DISGUISE)!=0) {
						if (ent.skin.name.startsWith("players/male")) {
							ent.skin = re.RegisterSkin("players/male/disguise.pcx");
							ent.model = re.RegisterModel("players/male/tris.md2");
						}
						else if (ent.skin.name.startsWith( "players/female")) {
							ent.skin = re.RegisterSkin("players/female/disguise.pcx");
							ent.model = re.RegisterModel("players/female/tris.md2");
						}
						else if (ent.skin.name.startsWith("players/cyborg")) {
							ent.skin = re.RegisterSkin("players/cyborg/disguise.pcx");
							ent.model = re.RegisterModel("players/cyborg/tris.md2");
						}
					}
					//	  PGM
					//	  ============
				}
				else {
					ent.skinnum = s1.skinnum;
					ent.skin = null;
					ent.model = cl.model_draw[s1.modelindex];
				}
			}

			// only used for black hole model right now, FIXME: do better
			if (renderfx == RF_TRANSLUCENT)
				ent.alpha = 0.70f;

			// render effects (fullbright, translucent, etc)
			if ((effects & EF_COLOR_SHELL)!=0)
				ent.flags = 0; // renderfx go on color shell entity
			else
				ent.flags = renderfx;

			// calculate angles
			if ((effects & EF_ROTATE)!=0) { // some bonus items auto-rotate
				ent.angles[0] = 0;
				ent.angles[1] = autorotate;
				ent.angles[2] = 0;
			}
			// RAFAEL
			else if ((effects & EF_SPINNINGLIGHTS)!=0) {
				ent.angles[0] = 0;
				ent.angles[1] = anglemod(cl.time / 2) + s1.angles[1];
				ent.angles[2] = 180;
				{
					float[] forward={0,0,0};
					float[] start={0,0,0};

					AngleVectors(ent.angles, forward, null, null);
					VectorMA(ent.origin, 64, forward, start);
					V.AddLight(start, 100, 1, 0, 0);
				}
			}
			else { // interpolate angles
				float a1, a2;

				for (i = 0; i < 3; i++) {
					a1 = cent.current.angles[i];
					a2 = cent.prev.angles[i];
					ent.angles[i] = LerpAngle(a2, a1, cl.lerpfrac);
				}
			}

			if (s1.number == cl.playernum + 1) {
				ent.flags |= RF_VIEWERMODEL; // only draw from mirrors
				// FIXME: still pass to refresh

				if ((effects & EF_FLAG1)!=0)
					V.AddLight(ent.origin, 225, 1.0f, 0.1f, 0.1f);
				else if ((effects & EF_FLAG2)!=0)
					V.AddLight(ent.origin, 225, 0.1f, 0.1f, 1.0f);
				else if ((effects & EF_TAGTRAIL)!=0) //PGM
					V.AddLight(ent.origin, 225, 1.0f, 1.0f, 0.0f); //PGM
				else if ((effects & EF_TRACKERTRAIL)!=0) //PGM
					V.AddLight(ent.origin, 225, -1.0f, -1.0f, -1.0f); //PGM

				continue;
			}

			// if set to invisible, skip
			if (s1.modelindex==0)
				continue;

			if ((effects & EF_BFG)!=0) {
				ent.flags |= RF_TRANSLUCENT;
				ent.alpha = 0.30f;
			}

			// RAFAEL
			if ((effects & EF_PLASMA)!=0) {
				ent.flags |= RF_TRANSLUCENT;
				ent.alpha = 0.6f;
			}

			if ((effects & EF_SPHERETRANS)!=0) {
				ent.flags |= RF_TRANSLUCENT;
				// PMM - *sigh*  yet more EF overloading
				if ((effects & EF_TRACKERTRAIL)!=0)
					ent.alpha = 0.6f;
				else
					ent.alpha = 0.3f;
			}
			//	  pmm

			// add to refresh list
			V.AddEntity( ent);

			// color shells generate a seperate entity for the main model
			if ((effects & EF_COLOR_SHELL)!=0) {
				/*
				 PMM - at this point, all of the shells have been handled
				 if we're in the rogue pack, set up the custom mixing, otherwise just
				 keep going
								if(Developer_searchpath(2) == 2)
								{
				 all of the solo colors are fine.  we need to catch any of the combinations that look bad
				 (double & half) and turn them into the appropriate color, and make double/quad something special
		
				 */
				if ((renderfx & RF_SHELL_HALF_DAM)!=0) {
					if (FS.Developer_searchpath(2) == 2) {
						// ditch the half damage shell if any of red, blue, or double are on
						if ((renderfx & (RF_SHELL_RED | RF_SHELL_BLUE | RF_SHELL_DOUBLE))!=0)
							renderfx &= ~RF_SHELL_HALF_DAM;
					}
				}

				if ((renderfx & RF_SHELL_DOUBLE)!=0) {
					if (FS.Developer_searchpath(2) == 2) {
						// lose the yellow shell if we have a red, blue, or green shell
						if ((renderfx & (RF_SHELL_RED | RF_SHELL_BLUE | RF_SHELL_GREEN))!=0)
							renderfx &= ~RF_SHELL_DOUBLE;
						// if we have a red shell, turn it to purple by adding blue
						if ((renderfx & RF_SHELL_RED)!=0)
							renderfx |= RF_SHELL_BLUE;
						// if we have a blue shell (and not a red shell), turn it to cyan by adding green
						else if ((renderfx & RF_SHELL_BLUE)!=0)
							// go to green if it's on already, otherwise do cyan (flash green)
							if ((renderfx & RF_SHELL_GREEN)!=0)
								renderfx &= ~RF_SHELL_BLUE;
							else
								renderfx |= RF_SHELL_GREEN;
					}
				}
				//				}
				// pmm
				ent.flags = renderfx | RF_TRANSLUCENT;
				ent.alpha = 0.30f;
				V.AddEntity( ent);
			}

			ent.skin = null; // never use a custom skin on others
			ent.skinnum = 0;
			ent.flags = 0;
			ent.alpha = 0;

			// duplicate for linked models
			if (s1.modelindex2!=0) {
				if (s1.modelindex2 == 255) { // custom weapon
					ci = cl.clientinfo[s1.skinnum & 0xff];
					i = (s1.skinnum >> 8); // 0 is default weapon model
					if (0==cl_vwep.value || i > MAX_CLIENTWEAPONMODELS - 1)
						i = 0;
					ent.model = ci.weaponmodel[i];
					if (null==ent.model) {
						if (i != 0)
							ent.model = ci.weaponmodel[0];
						if (null==ent.model)
							ent.model = cl.baseclientinfo.weaponmodel[0];
					}
				}
				else
					ent.model = cl.model_draw[s1.modelindex2];

				// PMM - check for the defender sphere shell .. make it translucent
				// replaces the previous version which used the high bit on modelindex2 to determine transparency
				if (cl.configstrings[CS_MODELS + (s1.modelindex2)].equalsIgnoreCase( "models/items/shell/tris.md2")) {
					ent.alpha = 0.32f;
					ent.flags = RF_TRANSLUCENT;
				}
				// pmm

				V.AddEntity( ent);

				//PGM - make sure these get reset.
				ent.flags = 0;
				ent.alpha = 0;
				//PGM
			}
			if (s1.modelindex3!=0) {
				ent.model = cl.model_draw[s1.modelindex3];
				V.AddEntity( ent);
			}
			if (s1.modelindex4!=0) {
				ent.model = cl.model_draw[s1.modelindex4];
				V.AddEntity( ent);
			}

			if ((effects & EF_POWERSCREEN)!=0) {
				ent.model = CL_tent.cl_mod_powerscreen;
				ent.oldframe = 0;
				ent.frame = 0;
				ent.flags |= (RF_TRANSLUCENT | RF_SHELL_GREEN);
				ent.alpha = 0.30f;
				V.AddEntity( ent);
			}

			// add automatic particle trails
			if ((effects & ~EF_ROTATE)!=0) {
				if ((effects & EF_ROCKET)!=0) {
					RocketTrail(cent.lerp_origin, ent.origin, cent);
					V.AddLight(ent.origin, 200, 1, 1, 0);
				}
				// PGM - Do not reorder EF_BLASTER and EF_HYPERBLASTER. 
				// EF_BLASTER | EF_TRACKER is a special case for EF_BLASTER2... Cheese!
				else if ((effects & EF_BLASTER)!=0) {
					//					CL_BlasterTrail (cent.lerp_origin, ent.origin);
					//	  PGM
					if ((effects & EF_TRACKER)!=0) // lame... problematic?
						{
						CL_newfx.BlasterTrail2(cent.lerp_origin, ent.origin);
						V.AddLight(ent.origin, 200, 0, 1, 0);
					}
					else {
						BlasterTrail(cent.lerp_origin, ent.origin);
						V.AddLight(ent.origin, 200, 1, 1, 0);
					}
					//	  PGM
				}
				else if ((effects & EF_HYPERBLASTER)!=0) {
					if ((effects & EF_TRACKER)!=0) // PGM	overloaded for blaster2.
						V.AddLight(ent.origin, 200, 0, 1, 0); // PGM
					else // PGM
						V.AddLight(ent.origin, 200, 1, 1, 0);
				}
				else if ((effects & EF_GIB)!=0) {
					DiminishingTrail(cent.lerp_origin, ent.origin, cent, effects);
				}
				else if ((effects & EF_GRENADE)!=0) {
					DiminishingTrail(cent.lerp_origin, ent.origin, cent, effects);
				}
				else if ((effects & EF_FLIES)!=0) {
					FlyEffect(cent, ent.origin);
				}
				else if ((effects & EF_BFG)!=0) {
					

					if ((effects & EF_ANIM_ALLFAST)!=0) {
						BfgParticles( ent);
						i = 200;
					}
					else {
						i = bfg_lightramp[s1.frame];
					}
					V.AddLight(ent.origin, i, 0, 1, 0);
				}
				// RAFAEL
				else if ((effects & EF_TRAP)!=0) {
					ent.origin[2] += 32;
					TrapParticles( ent);
					i = (rnd.nextInt(100)) + 100;
					V.AddLight(ent.origin, i, 1, 0.8f, 0.1f);
				}
				else if ((effects & EF_FLAG1)!=0) {
					FlagTrail(cent.lerp_origin, ent.origin, 242);
					V.AddLight(ent.origin, 225, 1, 0.1f, 0.1f);
				}
				else if ((effects & EF_FLAG2)!=0) {
					FlagTrail(cent.lerp_origin, ent.origin, 115);
					V.AddLight(ent.origin, 225, 0.1f, 0.1f, 1);
				}
				//	  ======
				//	  ROGUE
				else if ((effects & EF_TAGTRAIL)!=0) {
					CL_newfx.TagTrail(cent.lerp_origin, ent.origin, 220);
					V.AddLight(ent.origin, 225, 1.0f, 1.0f, 0.0f);
				}
				else if ((effects & EF_TRACKERTRAIL)!=0) {
					if ((effects & EF_TRACKER)!=0) {
						float intensity;

						intensity = (float) (50 + (500 * (Math.sin(cl.time / 500.0) + 1.0)));
						// FIXME - check out this effect in rendition
						if (vidref_val == VIDREF_GL)
							V.AddLight(ent.origin, intensity, -1.0f, -1.0f, -1.0f);
						else
							V.AddLight(ent.origin, -1.0f * intensity, 1.0f, 1.0f, 1.0f);
					}
					else {
						CL_newfx.Tracker_Shell(cent.lerp_origin);
						V.AddLight(ent.origin, 155, -1.0f, -1.0f, -1.0f);
					}
				}
				else if ((effects & EF_TRACKER)!=0) {
					CL_newfx.TrackerTrail(cent.lerp_origin, ent.origin, 0);
					// FIXME - check out this effect in rendition
					if (vidref_val == VIDREF_GL)
						V.AddLight(ent.origin, 200, -1, -1, -1);
					else
						V.AddLight(ent.origin, -200, 1, 1, 1);
				}
				//	  ROGUE
				//	  ======
				// RAFAEL
				else if ((effects & EF_GREENGIB)!=0) {
					DiminishingTrail(cent.lerp_origin, ent.origin, cent, effects);
				}
				// RAFAEL
				else if ((effects & EF_IONRIPPER)!=0) {
					IonripperTrail(cent.lerp_origin, ent.origin);
					V.AddLight(ent.origin, 100, 1, 0.5f, 0.5f);
				}
				// RAFAEL
				else if ((effects & EF_BLUEHYPERBLASTER)!=0) {
					V.AddLight(ent.origin, 200, 0, 0, 1);
				}
				// RAFAEL
				else if ((effects & EF_PLASMA)!=0) {
					if ((effects & EF_ANIM_ALLFAST)!=0) {
						BlasterTrail(cent.lerp_origin, ent.origin);
					}
					V.AddLight(ent.origin, 130, 1, 0.5f, 0.5f);
				}
			}

			VectorCopy(ent.origin, cent.lerp_origin);
		}
	}

	/*
	==============
	CL_AddViewWeapon
	==============
	*/
	static void AddViewWeapon(player_state_t   ps, player_state_t  ops) {
		entity_t gun; // view model
		int i;

		// allow the gun to be completely removed
		if (0==cl_gun.value)
			return;

		// don't draw gun if in wide angle view
		if (ps.fov > 90)
			return;

		//memset( gun, 0, sizeof(gun));
		gun = new entity_t();

		if (gun_model!=null)
			gun.model = gun_model; // development tool
		else
			gun.model = cl.model_draw[ps.gunindex];
			
		if (gun.model==null)
			return;

		// set up gun position
		for (i = 0; i < 3; i++) {
			gun.origin[i] = cl.refdef.vieworg[i] + ops.gunoffset[i] + cl.lerpfrac * (ps.gunoffset[i] - ops.gunoffset[i]);
			gun.angles[i] = cl.refdef.viewangles[i] + LerpAngle(ops.gunangles[i], ps.gunangles[i], cl.lerpfrac);
		}

		if (gun_frame!=0) {
			gun.frame = gun_frame; // development tool
			gun.oldframe = gun_frame; // development tool
		}
		else {
			gun.frame = ps.gunframe;
			if (gun.frame == 0)
				gun.oldframe = 0; // just changed weapons, don't lerp from old
			else
				gun.oldframe = ops.gunframe;
		}

		gun.flags = RF_MINLIGHT | RF_DEPTHHACK | RF_WEAPONMODEL;
		gun.backlerp = 1.0f - cl.lerpfrac;
		VectorCopy(gun.origin, gun.oldorigin); // don't lerp at all
		V.AddEntity( gun);
	}

	/*
	===============
	CL_CalcViewValues
	
	Sets cl.refdef view values
	===============
	*/
	static void CalcViewValues() {
		int i;
		float lerp, backlerp;
		frame_t   oldframe;
		player_state_t   ps,   ops;

		// find the previous frame to interpolate from
		ps = cl.frame.playerstate;
		
		i = (cl.frame.serverframe - 1) & UPDATE_MASK;
		oldframe =  cl.frames[i];
		
		if (oldframe.serverframe != cl.frame.serverframe - 1 || !oldframe.valid)
			oldframe =  cl.frame; // previous frame was dropped or involid
		ops =  oldframe.playerstate;

		// see if the player entity was teleported this frame
		if (Math.abs(ops.pmove.origin[0] - ps.pmove.origin[0]) > 256 * 8
			|| Math.abs(ops.pmove.origin[1] - ps.pmove.origin[1]) > 256 * 8
			|| Math.abs(ops.pmove.origin[2] - ps.pmove.origin[2]) > 256 * 8)
			ops = ps; // don't interpolate

		lerp = cl.lerpfrac;

		// calculate the origin
		if ((cl_predict.value!=0) && 0==(cl.frame.playerstate.pmove.pm_flags & PMF_NO_PREDICTION)) { // use predicted values
			int delta;

			backlerp = 1.0f - lerp;
			for (i = 0; i < 3; i++) {
				cl.refdef.vieworg[i] =
					cl.predicted_origin[i]
						+ ops.viewoffset[i]
						+ cl.lerpfrac * (ps.viewoffset[i] - ops.viewoffset[i])
						- backlerp * cl.prediction_error[i];
			}

			// smooth out stair climbing
			delta = (int) (cls.realtime - cl.predicted_step_time);
			if (delta < 100)
				cl.refdef.vieworg[2] -= cl.predicted_step * (100 - delta) * 0.01;
		}
		else { // just use interpolated values
			for (i = 0; i < 3; i++)
				cl.refdef.vieworg[i] =
					ops.pmove.origin[i] * 0.125f
						+ ops.viewoffset[i]
						+ lerp * (ps.pmove.origin[i] * 0.125f + ps.viewoffset[i] - (ops.pmove.origin[i] * 0.125f + ops.viewoffset[i]));
		}

		// if not running a demo or on a locked frame, add the local angle movement
		if (cl.frame.playerstate.pmove.pm_type < PM_DEAD) { // use predicted values
			for (i = 0; i < 3; i++)
				cl.refdef.viewangles[i] = cl.predicted_angles[i];
		}
		else { // just use interpolated values
			for (i = 0; i < 3; i++)
				cl.refdef.viewangles[i] = LerpAngle(ops.viewangles[i], ps.viewangles[i], lerp);
		}

		for (i = 0; i < 3; i++)
			cl.refdef.viewangles[i] += LerpAngle(ops.kick_angles[i], ps.kick_angles[i], lerp);

		AngleVectors(cl.refdef.viewangles, cl.v_forward, cl.v_right, cl.v_up);

		// interpolate field of view
		cl.refdef.fov_x = ops.fov + lerp * (ps.fov - ops.fov);

		// don't interpolate blend color
		for (i = 0; i < 4; i++)
			cl.refdef.blend[i] = ps.blend[i];

		// add the weapon
		AddViewWeapon(ps, ops);
	}

	/*
	===============
	CL_AddEntities
	
	Emits all entities, particles, and lights to the refresh
	===============
	*/
	static void AddEntities() {
		if (cls.state != ca_active)
			return;

		if (cl.time > cl.frame.servertime) {
			if (cl_showclamp.value!=0)
				Com.Printf("high clamp " +  (cl.time - cl.frame.servertime) + "\n");
			cl.time = cl.frame.servertime;
			cl.lerpfrac = 1.0f;
		}
		else if (cl.time < cl.frame.servertime - 100) {
			if (cl_showclamp.value!=0)
				Com.Printf("low clamp " + (cl.frame.servertime - 100 - cl.time)+"\n");
			cl.time = cl.frame.servertime - 100;
			cl.lerpfrac = 0;
		}
		else
			cl.lerpfrac = 1.0f - (cl.frame.servertime - cl.time) * 0.01f;

		if (cl_timedemo.value!=0)
			cl.lerpfrac = 1.0f;

		 
		/* is ok..
				CL_AddPacketEntities (cl.frame);
				CL_AddTEnts ();
				CL_AddParticles ();
				CL_AddDLights ();
				CL_AddLightStyles ();
		*/
		
		CalcViewValues();
		// PMM - moved this here so the heat beam has the right values for the vieworg, and can lock the beam to the gun
		AddPacketEntities( cl.frame);

		CL_tent.AddTEnts();
		AddParticles();
		CL_fx.AddDLights();
		AddLightStyles();
	}

	/*
	===============
	CL_GetEntitySoundOrigin
	
	Called to get the sound spatialization origin
	===============
	*/
	void GetEntitySoundOrigin(int ent, float[] org) {
		centity_t old;

		if (ent < 0 || ent >= MAX_EDICTS)
			Com.Error(ERR_DROP, "CL_GetEntitySoundOrigin: bad ent");
		old = cl_entities[ent];
		VectorCopy(old.lerp_origin, org);

		// FIXME: bmodel issues...
	}

}
