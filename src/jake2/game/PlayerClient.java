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

// Created on 28.12.2003 by RST.
// $Id: PlayerClient.java,v 1.9 2004-02-12 14:25:38 cwei Exp $

package jake2.game;

import jake2.*;
import jake2.client.*;
import jake2.game.*;
import jake2.qcommon.*;
import jake2.render.*;
import jake2.server.*;

public class PlayerClient extends PlayerHud {

	//
	// Gross, ugly, disgustuing hack section
	//

	// this function is an ugly as hell hack to fix some map flaws
	//
	// the coop spawn spots on some maps are SNAFU.  There are coop spots
	// with the wrong targetname as well as spots with no name at all
	//
	// we use carnal knowledge of the maps to fix the coop spot targetnames to match
	// that of the nearest named single player spot

	static EntThinkAdapter SP_FixCoopSpots = new EntThinkAdapter() {
		public boolean think(edict_t self) {

			edict_t spot;
			float[] d = { 0, 0, 0 };

			spot = null;
			EdictIterator es = null;

			while (true) {
				es = G_Find(es, findByClass, "info_player_start");
				spot = es.o;
				if (spot == null)
					return true;
				if (spot.targetname == null)
					continue;
				VectorSubtract(self.s.origin, spot.s.origin, d);
				if (VectorLength(d) < 384) {
					if ((self.targetname == null) || Q_stricmp(self.targetname, spot.targetname) != 0) {
						//				gi.dprintf("FixCoopSpots changed %s at %s targetname from %s to %s\n", self.classname, vtos(self.s.origin), self.targetname, spot.targetname);
						self.targetname = spot.targetname;
					}
					return true;
				}
			}
			return true;
		}
	};

	// now if that one wasn't ugly enough for you then try this one on for size
	// some maps don't have any coop spots at all, so we need to create them
	// where they should have been

	static EntThinkAdapter SP_CreateCoopSpots = new EntThinkAdapter() {
		public boolean think(edict_t self) {

			edict_t spot;

			if (Q_stricmp(level.mapname, "security") == 0) {
				spot = G_Spawn();
				spot.classname = "info_player_coop";
				spot.s.origin[0] = 188 - 64;
				spot.s.origin[1] = -164;
				spot.s.origin[2] = 80;
				spot.targetname = "jail3";
				spot.s.angles[1] = 90;

				spot = G_Spawn();
				spot.classname = "info_player_coop";
				spot.s.origin[0] = 188 + 64;
				spot.s.origin[1] = -164;
				spot.s.origin[2] = 80;
				spot.targetname = "jail3";
				spot.s.angles[1] = 90;

				spot = G_Spawn();
				spot.classname = "info_player_coop";
				spot.s.origin[0] = 188 + 128;
				spot.s.origin[1] = -164;
				spot.s.origin[2] = 80;
				spot.targetname = "jail3";
				spot.s.angles[1] = 90;
			}
			return true;
		}
	};

	/*QUAKED info_player_start (1 0 0) (-16 -16 -24) (16 16 32)
	The normal starting point for a level.
	*/
	public static void SP_info_player_start(edict_t self) {
		if (coop.value == 0)
			return;
		if (Q_stricmp(level.mapname, "security") == 0) {
			// invoke one of our gross, ugly, disgusting hacks
			self.think = SP_CreateCoopSpots;
			self.nextthink = level.time + FRAMETIME;
		}
	}

	/*QUAKED info_player_deathmatch (1 0 1) (-16 -16 -24) (16 16 32)
	potential spawning position for deathmatch games
	*/
	public static void SP_info_player_deathmatch(edict_t self) {
		if (0 == deathmatch.value) {
			G_FreeEdict(self);
			return;
		}
		SP_misc_teleporter_dest.think(self);
	}

	/*QUAKED info_player_coop (1 0 1) (-16 -16 -24) (16 16 32)
	potential spawning position for coop games
	*/

	public static void SP_info_player_coop(edict_t self) {
		if (0 == coop.value) {
			G_FreeEdict(self);
			return;
		}

		if ((Q_stricmp(level.mapname, "jail2") == 0)
			|| (Q_stricmp(level.mapname, "jail4") == 0)
			|| (Q_stricmp(level.mapname, "mine1") == 0)
			|| (Q_stricmp(level.mapname, "mine2") == 0)
			|| (Q_stricmp(level.mapname, "mine3") == 0)
			|| (Q_stricmp(level.mapname, "mine4") == 0)
			|| (Q_stricmp(level.mapname, "lab") == 0)
			|| (Q_stricmp(level.mapname, "boss1") == 0)
			|| (Q_stricmp(level.mapname, "fact3") == 0)
			|| (Q_stricmp(level.mapname, "biggun") == 0)
			|| (Q_stricmp(level.mapname, "space") == 0)
			|| (Q_stricmp(level.mapname, "command") == 0)
			|| (Q_stricmp(level.mapname, "power2") == 0)
			|| (Q_stricmp(level.mapname, "strike") == 0)) {
			// invoke one of our gross, ugly, disgusting hacks
			self.think = SP_FixCoopSpots;
			self.nextthink = level.time + FRAMETIME;
		}
	}

	/*QUAKED info_player_intermission (1 0 1) (-16 -16 -24) (16 16 32)
	The deathmatch intermission point will be at one of these
	Use 'angles' instead of 'angle', so you can set pitch or roll as well as yaw.  'pitch yaw roll'
	*/
	public static void SP_info_player_intermission() {
	}

	//=======================================================================

	static EntPainAdapter player_pain = new EntPainAdapter() {
		public void pain(edict_t self, edict_t other, float kick, int damage) {
				// player pain is handled at the end of the frame in P_DamageFeedback
	}
	};

	public static void ClientObituary(edict_t self, edict_t inflictor, edict_t attacker) {
		int mod;
		String message;
		String message2;
		boolean ff;

		if (coop.value != 0 && attacker.client != null)
			meansOfDeath |= MOD_FRIENDLY_FIRE;

		if (deathmatch.value != 0 || coop.value != 0) {
			ff = (meansOfDeath & MOD_FRIENDLY_FIRE) != 0;
			mod = meansOfDeath & ~MOD_FRIENDLY_FIRE;
			message = null;
			message2 = "";

			switch (mod) {
				case MOD_SUICIDE :
					message = "suicides";
					break;
				case MOD_FALLING :
					message = "cratered";
					break;
				case MOD_CRUSH :
					message = "was squished";
					break;
				case MOD_WATER :
					message = "sank like a rock";
					break;
				case MOD_SLIME :
					message = "melted";
					break;
				case MOD_LAVA :
					message = "does a back flip into the lava";
					break;
				case MOD_EXPLOSIVE :
				case MOD_BARREL :
					message = "blew up";
					break;
				case MOD_EXIT :
					message = "found a way out";
					break;
				case MOD_TARGET_LASER :
					message = "saw the light";
					break;
				case MOD_TARGET_BLASTER :
					message = "got blasted";
					break;
				case MOD_BOMB :
				case MOD_SPLASH :
				case MOD_TRIGGER_HURT :
					message = "was in the wrong place";
					break;
			}
			if (attacker == self) {
				switch (mod) {
					case MOD_HELD_GRENADE :
						message = "tried to put the pin back in";
						break;
					case MOD_HG_SPLASH :
					case MOD_G_SPLASH :
						if (IsNeutral(self))
							message = "tripped on its own grenade";
						else if (IsFemale(self))
							message = "tripped on her own grenade";
						else
							message = "tripped on his own grenade";
						break;
					case MOD_R_SPLASH :
						if (IsNeutral(self))
							message = "blew itself up";
						else if (IsFemale(self))
							message = "blew herself up";
						else
							message = "blew himself up";
						break;
					case MOD_BFG_BLAST :
						message = "should have used a smaller gun";
						break;
					default :
						if (IsNeutral(self))
							message = "killed itself";
						else if (IsFemale(self))
							message = "killed herself";
						else
							message = "killed himself";
						break;
				}
			}
			if (message != null) {
				gi.bprintf(PRINT_MEDIUM, self.client.pers.netname + " " + message + ".\n");
				if (deathmatch.value != 0)
					self.client.resp.score--;
				self.enemy = null;
				return;
			}

			self.enemy = attacker;
			if (attacker != null && attacker.client != null) {
				switch (mod) {
					case MOD_BLASTER :
						message = "was blasted by";
						break;
					case MOD_SHOTGUN :
						message = "was gunned down by";
						break;
					case MOD_SSHOTGUN :
						message = "was blown away by";
						message2 = "'s super shotgun";
						break;
					case MOD_MACHINEGUN :
						message = "was machinegunned by";
						break;
					case MOD_CHAINGUN :
						message = "was cut in half by";
						message2 = "'s chaingun";
						break;
					case MOD_GRENADE :
						message = "was popped by";
						message2 = "'s grenade";
						break;
					case MOD_G_SPLASH :
						message = "was shredded by";
						message2 = "'s shrapnel";
						break;
					case MOD_ROCKET :
						message = "ate";
						message2 = "'s rocket";
						break;
					case MOD_R_SPLASH :
						message = "almost dodged";
						message2 = "'s rocket";
						break;
					case MOD_HYPERBLASTER :
						message = "was melted by";
						message2 = "'s hyperblaster";
						break;
					case MOD_RAILGUN :
						message = "was railed by";
						break;
					case MOD_BFG_LASER :
						message = "saw the pretty lights from";
						message2 = "'s BFG";
						break;
					case MOD_BFG_BLAST :
						message = "was disintegrated by";
						message2 = "'s BFG blast";
						break;
					case MOD_BFG_EFFECT :
						message = "couldn't hide from";
						message2 = "'s BFG";
						break;
					case MOD_HANDGRENADE :
						message = "caught";
						message2 = "'s handgrenade";
						break;
					case MOD_HG_SPLASH :
						message = "didn't see";
						message2 = "'s handgrenade";
						break;
					case MOD_HELD_GRENADE :
						message = "feels";
						message2 = "'s pain";
						break;
					case MOD_TELEFRAG :
						message = "tried to invade";
						message2 = "'s personal space";
						break;
				}
				if (message != null) {
					gi.bprintf(
						PRINT_MEDIUM,
						self.client.pers.netname
							+ " "
							+ message
							+ " "
							+ attacker.client.pers.netname
							+ " "
							+ message2
							+ "\n");
					if (deathmatch.value != 0) {
						if (ff)
							attacker.client.resp.score--;
						else
							attacker.client.resp.score++;
					}
					return;
				}
			}
		}

		gi.bprintf(PRINT_MEDIUM, self.client.pers.netname + " died.\n");
		if (deathmatch.value != 0)
			self.client.resp.score--;
	}

	/*
	==================
	player_die
	==================
	*/

	//=======================================================================

	/*
	==============
	InitClientPersistant
	
	This is only called when the game first initializes in single player,
	but is called after each death and level change in deathmatch
	==============
	*/
	public static void InitClientPersistant(gclient_t client) {
		gitem_t item;

		//memset(& client.pers, 0, sizeof(client.pers));
		client.pers.clear();

		item = FindItem("Blaster");
		client.pers.selected_item = ITEM_INDEX(item);
		client.pers.inventory[client.pers.selected_item] = 1;

		client.pers.weapon = item;

		client.pers.health = 100;
		client.pers.max_health = 100;

		client.pers.max_bullets = 200;
		client.pers.max_shells = 100;
		client.pers.max_rockets = 50;
		client.pers.max_grenades = 50;
		client.pers.max_cells = 200;
		client.pers.max_slugs = 50;

		client.pers.connected = true;
	}

	public static void InitClientResp(gclient_t client) {
		//memset(& client.resp, 0, sizeof(client.resp));
		client.resp.clear();
		client.resp.enterframe = level.framenum;
		client.resp.coop_respawn = client.pers;
	}

	/*
	==================
	SaveClientData
	
	Some information that should be persistant, like health, 
	is still stored in the edict structure, so it needs to
	be mirrored out to the client structure before all the
	edicts are wiped.
	==================
	*/
	public static void SaveClientData() {
		int i;
		edict_t ent;

		for (i = 0; i < game.maxclients; i++) {
			ent = g_edicts[1 + i];
			if (!ent.inuse)
				continue;
			game.clients[i].pers.health = ent.health;
			game.clients[i].pers.max_health = ent.max_health;
			game.clients[i].pers.savedFlags = (ent.flags & (FL_GODMODE | FL_NOTARGET | FL_POWER_ARMOR));
			if (coop.value != 0)
				game.clients[i].pers.score = ent.client.resp.score;
		}
	}

	public static void FetchClientEntData(edict_t ent) {
		ent.health = ent.client.pers.health;
		ent.max_health = ent.client.pers.max_health;
		ent.flags |= ent.client.pers.savedFlags;
		if (coop.value != 0)
			ent.client.resp.score = ent.client.pers.score;
	}

	/*
	=======================================================================
	
	  SelectSpawnPoint
	
	=======================================================================
	*/

	/*
	================
	PlayersRangeFromSpot
	
	Returns the distance to the nearest player from the given spot
	================
	*/
	static float PlayersRangeFromSpot(edict_t spot) {
		edict_t player;
		float bestplayerdistance;
		float[] v = { 0, 0, 0 };
		int n;
		float playerdistance;

		bestplayerdistance = 9999999;

		for (n = 1; n <= maxclients.value; n++) {
			player = g_edicts[n];

			if (!player.inuse)
				continue;

			if (player.health <= 0)
				continue;

			VectorSubtract(spot.s.origin, player.s.origin, v);
			playerdistance = VectorLength(v);

			if (playerdistance < bestplayerdistance)
				bestplayerdistance = playerdistance;
		}

		return bestplayerdistance;
	}

	/*
	================
	SelectRandomDeathmatchSpawnPoint
	
	go to a random point, but NOT the two points closest
	to other players
	================
	*/
	public static edict_t SelectRandomDeathmatchSpawnPoint() {
		edict_t spot, spot1, spot2;
		int count = 0;
		int selection;
		float range, range1, range2;

		spot = null;
		range1 = range2 = 99999;
		spot1 = spot2 = null;

		EdictIterator es = null;

		while ((es = G_Find(es, findByClass, "info_player_deathmatch")).o != null) {
			spot = es.o;
			count++;
			range = PlayersRangeFromSpot(spot);
			if (range < range1) {
				range1 = range;
				spot1 = spot;
			}
			else if (range < range2) {
				range2 = range;
				spot2 = spot;
			}
		}

		if (count == 0)
			return null;

		if (count <= 2) {
			spot1 = spot2 = null;
		}
		else
			count -= 2;

		selection = rand() % count;

		spot = null;
		es = null;
		do {
			es = G_Find(es, findByClass, "info_player_deathmatch");
			spot = es.o;
			if (spot == spot1 || spot == spot2)
				selection++;
		}
		while (selection-- > 0);

		return spot;
	}

	/*
	================
	SelectFarthestDeathmatchSpawnPoint
	
	================
	*/
	static edict_t SelectFarthestDeathmatchSpawnPoint() {
		edict_t bestspot;
		float bestdistance, bestplayerdistance;
		edict_t spot;

		spot = null;
		bestspot = null;
		bestdistance = 0;

		EdictIterator es = null;
		while ((es = G_Find(es, findByClass, "info_player_deathmatch")).o != null) {
			spot = es.o;
			bestplayerdistance = PlayersRangeFromSpot(spot);

			if (bestplayerdistance > bestdistance) {
				bestspot = spot;
				bestdistance = bestplayerdistance;
			}
		}

		if (bestspot != null) {
			return bestspot;
		}

		// if there is a player just spawned on each and every start spot
		// we have no choice to turn one into a telefrag meltdown
		spot = G_Find(null, findByClass, "info_player_deathmatch").o;

		return spot;
	}

	public static edict_t SelectDeathmatchSpawnPoint() {
		if (0 != ((int) (dmflags.value) & DF_SPAWN_FARTHEST))
			return SelectFarthestDeathmatchSpawnPoint();
		else
			return SelectRandomDeathmatchSpawnPoint();
	}

	public static edict_t SelectCoopSpawnPoint(edict_t ent) {
		int index;
		edict_t spot = null;
		String target;

		//index = ent.client - game.clients;
		index = ent.client.index;

		// player 0 starts in normal player spawn point
		if (index == 0)
			return null;

		spot = null;
		EdictIterator es = null;

		// assume there are four coop spots at each spawnpoint
		while (true) {

			spot = (es = G_Find(es, findByClass, "info_player_coop")).o;
			if (spot == null)
				return null; // we didn't have enough...

			target = spot.targetname;
			if (target == null)
				target = "";
			if (Q_stricmp(game.spawnpoint, target) == 0) { // this is a coop spawn point for one of the clients here
				index--;
				if (0 == index)
					return spot; // this is it
			}
		}

		return spot;
	}

	/*
	===========
	SelectSpawnPoint
	
	Chooses a player start, deathmatch start, coop start, etc
	============
	*/
	public static void SelectSpawnPoint(edict_t ent, float[] origin, float[] angles) {
		edict_t spot = null;

		if (deathmatch.value != 0)
			spot = SelectDeathmatchSpawnPoint();
		else if (coop.value != 0)
			spot = SelectCoopSpawnPoint(ent);

		EdictIterator es = null;
		// find a single player start spot
		if (null == spot) {
			while ((spot = (es = G_Find(es, findByClass, "info_player_start")).o) != null) {
				if (game.spawnpoint.length() == 0 && spot.targetname == null)
					break;

				if (game.spawnpoint.length() == 0 || spot.targetname == null)
					continue;

				if (Q_stricmp(game.spawnpoint, spot.targetname) == 0)
					break;
			}

			if (null == spot) {
				if (game.spawnpoint.length() == 0) { // there wasn't a spawnpoint without a target, so use any
					spot = (es = G_Find(es, findByClass, "info_player_start")).o;
				}
				if (null == spot)
					gi.error("Couldn't find spawn point " + game.spawnpoint + "\n");
			}
		}

		VectorCopy(spot.s.origin, origin);
		origin[2] += 9;
		VectorCopy(spot.s.angles, angles);
	}

	//======================================================================

	public static void InitBodyQue() {
		int i;
		edict_t ent;

		level.body_que = 0;
		for (i = 0; i < BODY_QUEUE_SIZE; i++) {
			ent = G_Spawn();
			ent.classname = "bodyque";
		}
	}

	static EntDieAdapter body_die = new EntDieAdapter() {
		public void die(edict_t self, edict_t inflictor, edict_t attacker, int damage, float[] point) {

			int n;

			if (self.health < -40) {
				gi.sound(self, CHAN_BODY, gi.soundindex("misc/udeath.wav"), 1, ATTN_NORM, 0);
				for (n = 0; n < 4; n++)
					ThrowGib(self, "models/objects/gibs/sm_meat/tris.md2", damage, GIB_ORGANIC);
				self.s.origin[2] -= 48;
				ThrowClientHead(self, damage);
				self.takedamage = DAMAGE_NO;
			}
		}
	};

	public static void CopyToBodyQue(edict_t ent) {
		edict_t body;

		// grab a body que and cycle to the next one
		int i = (int) maxclients.value + level.body_que + 1;
		body = g_edicts[i];
		level.body_que = (level.body_que + 1) % BODY_QUEUE_SIZE;

		// FIXME: send an effect on the removed body

		gi.unlinkentity(ent);

		gi.unlinkentity(body);
		body.s = ent.s;

		//TODO: ok ?
		//body.s.number = body - g_edicts;
		body.s.number = i;

		body.svflags = ent.svflags;
		VectorCopy(ent.mins, body.mins);
		VectorCopy(ent.maxs, body.maxs);
		VectorCopy(ent.absmin, body.absmin);
		VectorCopy(ent.absmax, body.absmax);
		VectorCopy(ent.size, body.size);
		body.solid = ent.solid;
		body.clipmask = ent.clipmask;
		body.owner = ent.owner;
		body.movetype = ent.movetype;

		body.die = body_die;
		body.takedamage = DAMAGE_YES;

		gi.linkentity(body);
	}

	public static void respawn(edict_t self) {
		if (deathmatch.value != 0 || coop.value != 0) {
			// spectator's don't leave bodies
			if (self.movetype != MOVETYPE_NOCLIP)
				CopyToBodyQue(self);
			self.svflags &= ~SVF_NOCLIENT;
			PutClientInServer(self);

			// add a teleportation effect
			self.s.event = EV_PLAYER_TELEPORT;

			// hold in place briefly
			self.client.ps.pmove.pm_flags = PMF_TIME_TELEPORT;
			self.client.ps.pmove.pm_time = 14;

			self.client.respawn_time = level.time;

			return;
		}

		// restart the entire server
		gi.AddCommandString("menu_loadgame\n");
	}

	private static boolean passwdOK(String i1, String i2) {
		if (i1.length() != 0 && !i1.equals("none") && !i1.equals(i2))
			return false;
		return true;
	}

	/* 
	 * only called when pers.spectator changes
	 * note that resp.spectator should be the opposite of pers.spectator here
	 */
	public static void spectator_respawn(edict_t ent) {
		int i, numspec;

		// if the user wants to become a spectator, make sure he doesn't
		// exceed max_spectators

		if (ent.client.pers.spectator) {
			String value = Info.Info_ValueForKey(ent.client.pers.userinfo, "spectator");

			if (!passwdOK(spectator_password.string, value)) {
				gi.cprintf(ent, PRINT_HIGH, "Spectator password incorrect.\n");
				ent.client.pers.spectator = false;
				gi.WriteByte(svc_stufftext);
				gi.WriteString("spectator 0\n");
				gi.unicast(ent, true);
				return;
			}

			// count spectators
			for (i = 1, numspec = 0; i <= maxclients.value; i++)
				if (g_edicts[i].inuse && g_edicts[i].client.pers.spectator)
					numspec++;

			if (numspec >= maxspectators.value) {
				gi.cprintf(ent, PRINT_HIGH, "Server spectator limit is full.");
				ent.client.pers.spectator = false;
				// reset his spectator var
				gi.WriteByte(svc_stufftext);
				gi.WriteString("spectator 0\n");
				gi.unicast(ent, true);
				return;
			}
		}
		else {
			// he was a spectator and wants to join the game
			// he must have the right password
			String value = Info.Info_ValueForKey(ent.client.pers.userinfo, "password");
			if (!passwdOK(spectator_password.string, value)) {
				gi.cprintf(ent, PRINT_HIGH, "Password incorrect.\n");
				ent.client.pers.spectator = true;
				gi.WriteByte(svc_stufftext);
				gi.WriteString("spectator 1\n");
				gi.unicast(ent, true);
				return;
			}
		}

		// clear client on respawn
		ent.client.resp.score = ent.client.pers.score = 0;

		ent.svflags &= ~SVF_NOCLIENT;
		PutClientInServer(ent);

		// add a teleportation effect
		if (!ent.client.pers.spectator) {
			// send effect
			gi.WriteByte(svc_muzzleflash);
			//gi.WriteShort(ent - g_edicts);
			gi.WriteShort(ent.s.number);

			gi.WriteByte(MZ_LOGIN);
			gi.multicast(ent.s.origin, MULTICAST_PVS);

			// hold in place briefly
			ent.client.ps.pmove.pm_flags = PMF_TIME_TELEPORT;
			ent.client.ps.pmove.pm_time = 14;
		}

		ent.client.respawn_time = level.time;

		if (ent.client.pers.spectator)
			gi.bprintf(PRINT_HIGH, ent.client.pers.netname + " has moved to the sidelines\n");
		else
			gi.bprintf(PRINT_HIGH, ent.client.pers.netname + " joined the game\n");
	}

	//==============================================================

	/*
	===========
	PutClientInServer
	
	Called when a player connects to a server or respawns in
	a deathmatch.
	============
	*/
	public static void PutClientInServer(edict_t ent) {
		float[] mins = { -16, -16, -24 };
		float[] maxs = { 16, 16, 32 };
		int index;
		float[] spawn_origin = { 0, 0, 0 }, spawn_angles = { 0, 0, 0 };
		gclient_t client;
		int i;
		client_persistant_t saved;
		client_respawn_t resp = new client_respawn_t();

		// find a spawn point
		// do it before setting health back up, so farthest
		// ranging doesn't count this client
		SelectSpawnPoint(ent, spawn_origin, spawn_angles);

		index = ent.s.number - 1;
		client = ent.client;

		// deathmatch wipes most client data every spawn
		if (deathmatch.value != 0) {
			String userinfo;
			//char userinfo[MAX_INFO_STRING];

			resp = client.resp;
			userinfo = client.pers.userinfo;

			//memcpy(userinfo, client.pers.userinfo, sizeof(userinfo));
			InitClientPersistant(client);
			userinfo = ClientUserinfoChanged(ent, userinfo);
		}
		else if (coop.value != 0) {
			//		int			n;
			//char userinfo[MAX_INFO_STRING];
			String userinfo;

			resp = client.resp;
			//memcpy(userinfo, client.pers.userinfo, sizeof(userinfo));
			userinfo = client.pers.userinfo;
			// this is kind of ugly, but it's how we want to handle keys in coop
			//		for (n = 0; n < game.num_items; n++)
			//		{
			//			if (itemlist[n].flags & IT_KEY)
			//				resp.coop_respawn.inventory[n] = client.pers.inventory[n];
			//		}
			resp.coop_respawn.game_helpchanged = client.pers.game_helpchanged;
			resp.coop_respawn.helpchanged = client.pers.helpchanged;
			client.pers = resp.coop_respawn;
			userinfo = ClientUserinfoChanged(ent, userinfo);
			if (resp.score > client.pers.score)
				client.pers.score = resp.score;
		}
		else {
			//memset(& resp, 0, sizeof(resp));
			resp.clear();
		}

		// clear everything but the persistant data
		saved = client.pers;
		//memset(client, 0, sizeof(* client));
		client.clear();
		client.pers = saved;
		if (client.pers.health <= 0)
			InitClientPersistant(client);
		client.resp = resp;

		// copy some data from the client to the entity
		FetchClientEntData(ent);

		// clear entity values
		ent.groundentity = null;
		ent.client = game.clients[index];
		ent.takedamage = DAMAGE_AIM;
		ent.movetype = MOVETYPE_WALK;
		ent.viewheight = 22;
		ent.inuse = true;
		ent.classname = "player";
		ent.mass = 200;
		ent.solid = SOLID_BBOX;
		ent.deadflag = DEAD_NO;
		ent.air_finished = level.time + 12;
		ent.clipmask = MASK_PLAYERSOLID;
		ent.model = "players/male/tris.md2";
		ent.pain = player_pain;
		ent.die = player_die;
		ent.waterlevel = 0;
		ent.watertype = 0;
		ent.flags &= ~FL_NO_KNOCKBACK;
		ent.svflags &= ~SVF_DEADMONSTER;

		VectorCopy(mins, ent.mins);
		VectorCopy(maxs, ent.maxs);
		VectorClear(ent.velocity);

		// clear playerstate values
		ent.client.ps.clear();
		//memset(& ent.client.ps, 0, sizeof(client.ps));

		client.ps.pmove.origin[0] = (short) (spawn_origin[0] * 8);
		client.ps.pmove.origin[1] = (short) (spawn_origin[1] * 8);
		client.ps.pmove.origin[2] = (short) (spawn_origin[2] * 8);

		if (deathmatch.value != 0 && 0 != ((int) dmflags.value & DF_FIXED_FOV)) {
			client.ps.fov = 90;
		}
		else {
			client.ps.fov = atoi(Info.Info_ValueForKey(client.pers.userinfo, "fov"));
			if (client.ps.fov < 1)
				client.ps.fov = 90;
			else if (client.ps.fov > 160)
				client.ps.fov = 160;
		}

		client.ps.gunindex = gi.modelindex(client.pers.weapon.view_model);

		// clear entity state values
		ent.s.effects = 0;
		ent.s.modelindex = 255; // will use the skin specified model
		ent.s.modelindex2 = 255; // custom gun model
		// sknum is player num and weapon number
		// weapon number will be added in changeweapon
		ent.s.skinnum = ent.s.number - 1;

		ent.s.frame = 0;
		VectorCopy(spawn_origin, ent.s.origin);
		ent.s.origin[2] += 1; // make sure off ground
		VectorCopy(ent.s.origin, ent.s.old_origin);

		// set the delta angle
		for (i = 0; i < 3; i++) {
			client.ps.pmove.delta_angles[i] = (short) ANGLE2SHORT(spawn_angles[i] - client.resp.cmd_angles[i]);
		}

		ent.s.angles[PITCH] = 0;
		ent.s.angles[YAW] = spawn_angles[YAW];
		ent.s.angles[ROLL] = 0;
		VectorCopy(ent.s.angles, client.ps.viewangles);
		VectorCopy(ent.s.angles, client.v_angle);

		// spawn a spectator
		if (client.pers.spectator) {
			client.chase_target = null;

			client.resp.spectator = true;

			ent.movetype = MOVETYPE_NOCLIP;
			ent.solid = SOLID_NOT;
			ent.svflags |= SVF_NOCLIENT;
			ent.client.ps.gunindex = 0;
			gi.linkentity(ent);
			return;
		}
		else
			client.resp.spectator = false;

		if (!KillBox(ent)) { // could't spawn in?
		}

		gi.linkentity(ent);

		// force the current weapon up
		client.newweapon = client.pers.weapon;
		ChangeWeapon(ent);
	}

	/*
	=====================
	ClientBeginDeathmatch
	
	A client has just connected to the server in 
	deathmatch mode, so clear everything out before starting them.
	=====================
	*/
	public static void ClientBeginDeathmatch(edict_t ent) {
		G_InitEdict(ent, ent.s.number);

		InitClientResp(ent.client);

		// locate ent at a spawn point
		PutClientInServer(ent);

		if (level.intermissiontime != 0) {
			MoveClientToIntermission(ent);
		}
		else {
			// send effect
			gi.WriteByte(svc_muzzleflash);
			//gi.WriteShort(ent - g_edicts);
			gi.WriteShort(ent.s.number);
			gi.WriteByte(MZ_LOGIN);
			gi.multicast(ent.s.origin, MULTICAST_PVS);
		}

		gi.bprintf(PRINT_HIGH, ent.client.pers.netname + " entered the game\n");

		// make sure all view stuff is valid
		PlayerView.ClientEndServerFrame(ent);
	}

	/*
	===========
	ClientBegin
	
	called when a client has finished connecting, and is ready
	to be placed into the game.  This will happen every level load.
	============
	*/
	public static void ClientBegin(edict_t ent) {
		int i;

		//ent.client = game.clients + (ent - g_edicts - 1);
		ent.client = game.clients[ent.s.number - 1];

		if (deathmatch.value != 0) {
			ClientBeginDeathmatch(ent);
			return;
		}

		// if there is already a body waiting for us (a loadgame), just
		// take it, otherwise spawn one from scratch
		if (ent.inuse == true) {
			// the client has cleared the client side viewangles upon
			// connecting to the server, which is different than the
			// state when the game is saved, so we need to compensate
			// with deltaangles
			for (i = 0; i < 3; i++)
				ent.client.ps.pmove.delta_angles[i] = (short) ANGLE2SHORT(ent.client.ps.viewangles[i]);
		}
		else {
			// a spawn point will completely reinitialize the entity
			// except for the persistant data that was initialized at
			// ClientConnect() time
			G_InitEdict(ent, ent.s.number);
			ent.classname = "player";
			InitClientResp(ent.client);
			PutClientInServer(ent);
		}

		if (level.intermissiontime != 0) {
			MoveClientToIntermission(ent);
		}
		else {
			// send effect if in a multiplayer game
			if (game.maxclients > 1) {
				gi.WriteByte(svc_muzzleflash);
				//gi.WriteShort(ent - g_edicts);
				gi.WriteShort(ent.s.number);
				gi.WriteByte(MZ_LOGIN);
				gi.multicast(ent.s.origin, MULTICAST_PVS);

				gi.bprintf(PRINT_HIGH, ent.client.pers.netname + " entered the game\n");
			}
		}

		// make sure all view stuff is valid
		PlayerView.ClientEndServerFrame(ent);
	}

	/*
	===========
	ClientUserInfoChanged
	
	called whenever the player updates a userinfo variable.
	
	The game can override any of the settings in place
	(forcing skins or names, etc) before copying it off.
	============
	*/
	public static String ClientUserinfoChanged(edict_t ent, String userinfo) {
		String s;
		int playernum;

		// check for malformed or illegal info strings
		if (!Info.Info_Validate(userinfo)) {
			//strcpy(userinfo, "\\name\\badinfo\\skin\\male/grunt");
			return "\\name\\badinfo\\skin\\male/grunt";
		}

		// set name
		s = Info.Info_ValueForKey(userinfo, "name");

		//strncpy(ent.client.pers.netname, s, sizeof(ent.client.pers.netname) - 1);
		ent.client.pers.netname = s;

		// set spectator
		s = Info.Info_ValueForKey(userinfo, "spectator");
		// spectators are only supported in deathmatch
		if (deathmatch.value != 0 && !s.equals("0"))
			ent.client.pers.spectator = true;
		else
			ent.client.pers.spectator = false;

		// set skin
		s = Info.Info_ValueForKey(userinfo, "skin");

		playernum = ent.s.number - 1;

		// combine name and skin into a configstring
		gi.configstring(CS_PLAYERSKINS + playernum, ent.client.pers.netname + "\\" + s);

		// fov
		if (deathmatch.value != 0 && 0 != ((int) dmflags.value & DF_FIXED_FOV)) {
			ent.client.ps.fov = 90;
		}
		else {
			ent.client.ps.fov = atoi(Info.Info_ValueForKey(userinfo, "fov"));
			if (ent.client.ps.fov < 1)
				ent.client.ps.fov = 90;
			else if (ent.client.ps.fov > 160)
				ent.client.ps.fov = 160;
		}

		// handedness
		s = Info.Info_ValueForKey(userinfo, "hand");
		if (strlen(s) > 0) {
			ent.client.pers.hand = atoi(s);
		}

		// save off the userinfo in case we want to check something later
		//strncpy(ent.client.pers.userinfo, userinfo, sizeof(ent.client.pers.userinfo) - 1);
		ent.client.pers.userinfo = userinfo;

		return userinfo;
	}

	/*
	===========
	ClientConnect
	
	Called when a player begins connecting to the server.
	The game can refuse entrance to a client by returning false.
	If the client is allowed, the connection process will continue
	and eventually get to ClientBegin()
	Changing levels will NOT cause this to be called again, but
	loadgames will.
	============
	*/
	public static boolean ClientConnect(edict_t ent, String userinfo) {
		String value;

		// check to see if they are on the banned IP list
		value = Info.Info_ValueForKey(userinfo, "ip");
		if (GameSVCmds.SV_FilterPacket(value)) {
			userinfo = Info.Info_SetValueForKey1(userinfo, "rejmsg", "Banned.");
			return false;
		}

		// check for a spectator
		value = Info.Info_ValueForKey(userinfo, "spectator");
		if (deathmatch.value != 0 && value.length() != 0 && 0 != strcmp(value, "0")) {
			int i, numspec;

			if (!passwdOK(spectator_password.string, value)) {
				userinfo = Info.Info_SetValueForKey1(userinfo, "rejmsg", "Spectator password required or incorrect.");
				return false;
			}

			// count spectators
			for (i = numspec = 0; i < maxclients.value; i++)
				if (g_edicts[i + 1].inuse && g_edicts[i + 1].client.pers.spectator)
					numspec++;

			if (numspec >= maxspectators.value) {
				userinfo = Info.Info_SetValueForKey1(userinfo, "rejmsg", "Server spectator limit is full.");
				return false;
			}
		}
		else {
			// check for a password
			value = Info.Info_ValueForKey(userinfo, "password");
			if (!passwdOK(spectator_password.string, value)) {
				userinfo = Info.Info_SetValueForKey1(userinfo, "rejmsg", "Password required or incorrect.");
				return false;
			}
		}

		// they can connect
		ent.client = game.clients[ent.s.number - 1];

		// if there is already a body waiting for us (a loadgame), just
		// take it, otherwise spawn one from scratch
		if (ent.inuse == false) {
			// clear the respawning variables
			InitClientResp(ent.client);
			if (!game.autosaved || null == ent.client.pers.weapon)
				InitClientPersistant(ent.client);
		}

		userinfo = ClientUserinfoChanged(ent, userinfo);

		if (game.maxclients > 1)
			gi.dprintf(ent.client.pers.netname + " connected\n");

		ent.svflags = 0; // make sure we start with known default
		ent.client.pers.connected = true;
		return true;
	}

	/*
	===========
	ClientDisconnect
	
	Called when a player drops from the server.
	Will not be called between levels.
	============
	*/
	public static void ClientDisconnect(edict_t ent) {
		int playernum;

		if (ent.client == null)
			return;

		gi.bprintf(PRINT_HIGH, ent.client.pers.netname + " disconnected\n");

		// send effect
		gi.WriteByte(svc_muzzleflash);
		gi.WriteShort(ent.s.number);
		gi.WriteByte(MZ_LOGOUT);
		gi.multicast(ent.s.origin, MULTICAST_PVS);

		gi.unlinkentity(ent);
		ent.s.modelindex = 0;
		ent.solid = SOLID_NOT;
		ent.inuse = false;
		ent.classname = "disconnected";
		ent.client.pers.connected = false;

		playernum = ent.s.number - 1;
		gi.configstring(CS_PLAYERSKINS + playernum, "");
	}

	//==============================================================

	private static edict_t pm_passent;

	// pmove doesn't need to know about passent and contentmask
	public static pmove_t.TraceAdapter PM_trace = new pmove_t.TraceAdapter() {

		public trace_t trace(float[] start, float[] mins, float[] maxs, float[] end) {
			if (pm_passent.health > 0)
				return gi.trace(start, mins, maxs, end, pm_passent, MASK_PLAYERSOLID);
			else
				return gi.trace(start, mins, maxs, end, pm_passent, MASK_DEADSOLID);
		}

	};

	/*
	static int CheckBlock(, int c) {
		int v, i;
		v = 0;
		for (i = 0; i < c; i++)
			v += ((byte *) b)[i];
		return v;
	}
	
	public static void PrintPmove(pmove_t * pm) {
		unsigned c1, c2;
	
		c1 = CheckBlock(& pm.s, sizeof(pm.s));
		c2 = CheckBlock(& pm.cmd, sizeof(pm.cmd));
		Com_Printf("sv %3i:%i %i\n", pm.cmd.impulse, c1, c2);
	}
	*/

	/*
	==============
	ClientThink
	
	This will be called once for each client frame, which will
	usually be a couple times for each server frame.
	==============
	*/
	public static void ClientThink(edict_t ent, usercmd_t ucmd) {
		gclient_t client;
		edict_t other;
		int i, j;
		pmove_t pm = null;

		level.current_entity = ent;
		client = ent.client;

		if (level.intermissiontime != 0) {
			client.ps.pmove.pm_type = PM_FREEZE;
			// can exit intermission after five seconds
			if (level.time > level.intermissiontime + 5.0f && 0 != (ucmd.buttons & BUTTON_ANY))
				level.exitintermission = true;
			return;
		}

		pm_passent = ent;

		if (ent.client.chase_target != null) {

			client.resp.cmd_angles[0] = SHORT2ANGLE(ucmd.angles[0]);
			client.resp.cmd_angles[1] = SHORT2ANGLE(ucmd.angles[1]);
			client.resp.cmd_angles[2] = SHORT2ANGLE(ucmd.angles[2]);

		}
		else {

			// set up for pmove
			//memset(& pm, 0, sizeof(pm));
			pm.clear();

			if (ent.movetype == MOVETYPE_NOCLIP)
				client.ps.pmove.pm_type = PM_SPECTATOR;
			else if (ent.s.modelindex != 255)
				client.ps.pmove.pm_type = PM_GIB;
			else if (ent.deadflag != 0)
				client.ps.pmove.pm_type = PM_DEAD;
			else
				client.ps.pmove.pm_type = PM_NORMAL;

			client.ps.pmove.gravity = (short) sv_gravity.value;
			pm.s = client.ps.pmove;

			for (i = 0; i < 3; i++) {
				pm.s.origin[i] = (short) (ent.s.origin[i] * 8);
				pm.s.velocity[i] = (short) (ent.velocity[i] * 8);
			}

			if (client.old_pmove.equals(pm.s)) {
				pm.snapinitial = true;
				//		gi.dprintf ("pmove changed!\n");
			}

			// TODO bugfix cwei
			// this should be a copy
			pm.cmd = new usercmd_t(ucmd);

			pm.trace = PM_trace; // adds default parms
			pm.pointcontents = gi.pointcontents;

			// perform a pmove
			gi.Pmove(pm);

			// save results of pmove
			client.ps.pmove = pm.s;
			client.old_pmove = pm.s;

			for (i = 0; i < 3; i++) {
				ent.s.origin[i] = pm.s.origin[i] * 0.125f;
				ent.velocity[i] = pm.s.velocity[i] * 0.125f;
			}

			VectorCopy(pm.mins, ent.mins);
			VectorCopy(pm.maxs, ent.maxs);

			client.resp.cmd_angles[0] = SHORT2ANGLE(ucmd.angles[0]);
			client.resp.cmd_angles[1] = SHORT2ANGLE(ucmd.angles[1]);
			client.resp.cmd_angles[2] = SHORT2ANGLE(ucmd.angles[2]);

			if (ent.groundentity != null && null == pm.groundentity && (pm.cmd.upmove >= 10) && (pm.waterlevel == 0)) {
				gi.sound(ent, CHAN_VOICE, gi.soundindex("*jump1.wav"), 1, ATTN_NORM, 0);
				PlayerNoise(ent, ent.s.origin, PNOISE_SELF);
			}

			ent.viewheight = (int) pm.viewheight;
			ent.waterlevel = (int) pm.waterlevel;
			ent.watertype = pm.watertype;
			ent.groundentity = pm.groundentity;
			if (pm.groundentity != null)
				ent.groundentity_linkcount = pm.groundentity.linkcount;

			if (ent.deadflag != 0) {
				client.ps.viewangles[ROLL] = 40;
				client.ps.viewangles[PITCH] = -15;
				client.ps.viewangles[YAW] = client.killer_yaw;
			}
			else {
				VectorCopy(pm.viewangles, client.v_angle);
				VectorCopy(pm.viewangles, client.ps.viewangles);
			}

			gi.linkentity(ent);

			if (ent.movetype != MOVETYPE_NOCLIP)
				G_TouchTriggers(ent);

			// touch other objects
			for (i = 0; i < pm.numtouch; i++) {
				other = pm.touchents[i];
				for (j = 0; j < i; j++)
					if (pm.touchents[j] == other)
						break;
				if (j != i)
					continue; // duplicated
				if (other.touch != null)
					continue;
				other.touch.touch(other, ent, null, null);
			}

		}

		client.oldbuttons = client.buttons;
		client.buttons = ucmd.buttons;
		client.latched_buttons |= client.buttons & ~client.oldbuttons;

		// save light level the player is standing on for
		// monster sighting AI
		ent.light_level = ucmd.lightlevel;

		// fire weapon from final position if needed
		if ((client.latched_buttons & BUTTON_ATTACK) != 0) {
			if (client.resp.spectator) {

				client.latched_buttons = 0;

				if (client.chase_target != null) {
					client.chase_target = null;
					client.ps.pmove.pm_flags &= ~PMF_NO_PREDICTION;
				}
				else
					GetChaseTarget(ent);

			}
			else if (!client.weapon_thunk) {
				client.weapon_thunk = true;
				Think_Weapon(ent);
			}
		}

		if (client.resp.spectator) {
			if (ucmd.upmove >= 10) {
				if (0 == (client.ps.pmove.pm_flags & PMF_JUMP_HELD)) {
					client.ps.pmove.pm_flags |= PMF_JUMP_HELD;
					if (client.chase_target != null)
						ChaseNext(ent);
					else
						GetChaseTarget(ent);
				}
			}
			else
				client.ps.pmove.pm_flags &= ~PMF_JUMP_HELD;
		}

		// update chase cam if being followed
		for (i = 1; i <= maxclients.value; i++) {
			other = g_edicts[i];
			if (other.inuse && other.client.chase_target == ent)
				UpdateChaseCam(other);
		}
	}

	/*
	==============
	ClientBeginServerFrame
	
	This will be called once for each server frame, before running
	any other entities in the world.
	==============
	*/
	public static void ClientBeginServerFrame(edict_t ent) {
		gclient_t client;
		int buttonMask;

		if (level.intermissiontime != 0)
			return;

		client = ent.client;

		if (deathmatch.value != 0
			&& client.pers.spectator != client.resp.spectator
			&& (level.time - client.respawn_time) >= 5) {
			spectator_respawn(ent);
			return;
		}

		// run weapon animations if it hasn't been done by a ucmd_t
		if (!client.weapon_thunk && !client.resp.spectator)
			Think_Weapon(ent);
		else
			client.weapon_thunk = false;

		if (ent.deadflag != 0) {
			// wait for any button just going down
			if (level.time > client.respawn_time) {
				// in deathmatch, only wait for attack button
				if (deathmatch.value != 0)
					buttonMask = BUTTON_ATTACK;
				else
					buttonMask = -1;

				if ((client.latched_buttons & buttonMask) != 0
					|| (deathmatch.value != 0 && 0 != ((int) dmflags.value & DF_FORCE_RESPAWN))) {
					respawn(ent);
					client.latched_buttons = 0;
				}
			}
			return;
		}

		// add player trail so monsters can follow
		if (deathmatch.value != 0)
			if (!visible(ent, PlayerTrail.LastSpot()))
				PlayerTrail.Add(ent.s.old_origin);

		client.latched_buttons = 0;
	}
}
