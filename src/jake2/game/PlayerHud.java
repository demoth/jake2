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
// $Id: PlayerHud.java,v 1.5 2004-02-14 13:24:02 rst Exp $

package jake2.game;

import jake2.*;
import jake2.client.*;
import jake2.qcommon.*;
import jake2.render.*;
import jake2.server.*;
import jake2.util.Vargs;

public class PlayerHud extends GameTarget {

	/*
	======================================================================
	
	INTERMISSION
	
	======================================================================
	*/

	public static void MoveClientToIntermission(edict_t ent) {
		if (deathmatch.value != 0 || coop.value != 0)
			ent.client.showscores = true;
		VectorCopy(level.intermission_origin, ent.s.origin);
		ent.client.ps.pmove.origin[0] = (short) (level.intermission_origin[0] * 8);
		ent.client.ps.pmove.origin[1] = (short) (level.intermission_origin[1] * 8);
		ent.client.ps.pmove.origin[2] = (short) (level.intermission_origin[2] * 8);
		VectorCopy(level.intermission_angle, ent.client.ps.viewangles);
		ent.client.ps.pmove.pm_type = PM_FREEZE;
		ent.client.ps.gunindex = 0;
		ent.client.ps.blend[3] = 0;
		ent.client.ps.rdflags &= ~RDF_UNDERWATER;

		// clean up powerup info
		ent.client.quad_framenum = 0;
		ent.client.invincible_framenum = 0;
		ent.client.breather_framenum = 0;
		ent.client.enviro_framenum = 0;
		ent.client.grenade_blew_up = false;
		ent.client.grenade_time = 0;

		ent.viewheight = 0;
		ent.s.modelindex = 0;
		ent.s.modelindex2 = 0;
		ent.s.modelindex3 = 0;
		ent.s.modelindex = 0;
		ent.s.effects = 0;
		ent.s.sound = 0;
		ent.solid = SOLID_NOT;

		// add the layout

		if (deathmatch.value != 0 || coop.value != 0) {
			DeathmatchScoreboardMessage(ent, null);
			gi.unicast(ent, true);
		}

	}

	public static void BeginIntermission(edict_t targ) {
		int i, n;
		edict_t ent, client;

		if (level.intermissiontime != 0)
			return; // already activated

		game.autosaved = false;

		// respawn any dead clients
		for (i = 0; i < maxclients.value; i++) {
			client = g_edicts[1 + i];
			if (!client.inuse)
				continue;
			if (client.health <= 0)
				PlayerClient.respawn(client);
		}

		level.intermissiontime = level.time;
		level.changemap = targ.map;

		if (level.changemap.indexOf('*') > -1) {
			if (coop.value != 0) {
				for (i = 0; i < maxclients.value; i++) {
					client = g_edicts[1 + i];
					if (!client.inuse)
						continue;
					// strip players of all keys between units
					for (n = 0; n < MAX_ITEMS; n++) {
						if ((itemlist[n].flags & IT_KEY) != 0)
							client.client.pers.inventory[n] = 0;
					}
				}
			}
		}
		else {
			if (0 == deathmatch.value) {
				level.exitintermission = true; // go immediately to the next level
				return;
			}
		}

		level.exitintermission = false;

		// find an intermission spot
		ent = G_Find(null, findByClass, "info_player_intermission").o;
		if (ent == null) { // the map creator forgot to put in an intermission point...
			ent = G_Find(null, findByClass, "info_player_start").o;
			if (ent == null)
				ent = G_Find(null, findByClass, "info_player_deathmatch").o;
		}
		else { // chose one of four spots
			i = rand() & 3;
			EdictIterator es = null;

			while (i-- > 0) {
				es = G_Find(es, findByClass, "info_player_intermission");
				ent = es.o;
				if (ent == null) { // wrap around the list
					es = G_Find(es, findByClass, "info_player_intermission");
					ent = es.o;
				}
			}
		}

		VectorCopy(ent.s.origin, level.intermission_origin);
		VectorCopy(ent.s.angles, level.intermission_angle);

		// move all clients to the intermission point
		for (i = 0; i < maxclients.value; i++) {
			client = g_edicts[1 + i];
			if (!client.inuse)
				continue;
			MoveClientToIntermission(client);
		}
	}

	/*
	==================
	DeathmatchScoreboardMessage
	
	==================
	*/
	public static void DeathmatchScoreboardMessage(edict_t ent, edict_t killer) {
		StringBuffer string = new StringBuffer(1400);
		//String string;
		//char entry[1024];
		//char string[1400];
		int stringlength;
		int i, j, k;
		int sorted[] = new int[MAX_CLIENTS];
		int sortedscores[] = new int[MAX_CLIENTS];
		int score, total;
		int picnum;
		int x, y;
		gclient_t cl;
		edict_t cl_ent;
		String tag;

		// sort the clients by score
		total = 0;
		for (i = 0; i < game.maxclients; i++) {
			cl_ent = g_edicts[1 + i];
			if (!cl_ent.inuse || game.clients[i].resp.spectator)
				continue;
			score = game.clients[i].resp.score;
			for (j = 0; j < total; j++) {
				if (score > sortedscores[j])
					break;
			}
			for (k = total; k > j; k--) {
				sorted[k] = sorted[k - 1];
				sortedscores[k] = sortedscores[k - 1];
			}
			sorted[j] = i;
			sortedscores[j] = score;
			total++;
		}

		// print level name and exit rules
		//string[0] = 0;
		//stringlength = strlen(string);

		// add the clients in sorted order
		if (total > 12)
			total = 12;

		for (i = 0; i < total; i++) {
			cl = game.clients[sorted[i]];
			cl_ent = g_edicts[1 + sorted[i]];

			picnum = gi.imageindex("i_fixme");
			x = (i >= 6) ? 160 : 0;
			y = 32 + 32 * (i % 6);

			// add a dogtag
			if (cl_ent == ent)
				tag = "tag1";
			else if (cl_ent == killer)
				tag = "tag2";
			else
				tag = null;

			if (tag != null) {
				string.append("xv ").append(x + 32).append(" yv ").append(y).append(" picn ").append(tag);
				/*
				//Com_sprintf(entry, sizeof(entry), "xv %i yv %i picn %s ", x + 32, y, tag);
				j = strlen(entry);
				if (stringlength + j > 1024)
					break;
				strcpy(string + stringlength, entry);
				stringlength += j;
				*/
			}

			// send the layout
			string
				.append("client ")
				.append(x)
				.append(" ")
				.append(y)
				.append(" ")
				.append(sorted[i])
				.append(" ")
				.append(cl.resp.score)
				.append(" ")
				.append(cl.ping)
				.append(" ")
				.append((level.framenum - cl.resp.enterframe) / 600);

			/*
			Com_sprintf(
				entry,
				sizeof(entry),
				"client %i %i %i %i %i %i ",
				x,
				y,
				sorted[i],
				cl.resp.score,
				cl.ping,
				(level.framenum - cl.resp.enterframe) / 600);
			j = strlen(entry);
			if (stringlength + j > 1024)
				break;
			strcpy(string + stringlength, entry);
			stringlength += j;
			*/

		}

		gi.WriteByte(svc_layout);
		gi.WriteString(string.toString());
	}

	/*
	==================
	DeathmatchScoreboard
	
	Draw instead of help message.
	Note that it isn't that hard to overflow the 1400 byte message limit!
	==================
	*/
	public static void DeathmatchScoreboard(edict_t ent) {
		DeathmatchScoreboardMessage(ent, ent.enemy);
		gi.unicast(ent, true);
	}

	/*
	==================
	Cmd_Score_f
	
	Display the scoreboard
	==================
	*/
	public static void Cmd_Score_f(edict_t ent) {
		ent.client.showinventory = false;
		ent.client.showhelp = false;

		if (0 == deathmatch.value && 0 == coop.value)
			return;

		if (ent.client.showscores) {
			ent.client.showscores = false;
			return;
		}

		ent.client.showscores = true;
		DeathmatchScoreboard(ent);
	}

	/*
	==================
	HelpComputer
	
	Draw help computer.
	==================
	*/
	public static void HelpComputer(edict_t ent) {
		//char string[1024];
		String string;

		String sk;

		if (skill.value == 0)
			sk = "easy";
		else if (skill.value == 1)
			sk = "medium";
		else if (skill.value == 2)
			sk = "hard";
		else
			sk = "hard+";

		// send the layout

			string = Com.sprintf("xv 32 yv 8 picn help " + // background
		"xv 202 yv 12 string2 \"%s\" " + // skill
		"xv 0 yv 24 cstring2 \"%s\" " + // level name
		"xv 0 yv 54 cstring2 \"%s\" " + // help 1
		"xv 0 yv 110 cstring2 \"%s\" " + // help 2
	"xv 50 yv 164 string2 \" kills     goals    secrets\" " + "xv 50 yv 172 string2 \"%3i/%3i     %i/%i       %i/%i\" ",
		new Vargs()
			.add(sk)
			.add(level.level_name)
			.add(game.helpmessage1)
			.add(game.helpmessage2)
			.add(level.killed_monsters)
			.add(level.total_monsters)
			.add(level.found_goals)
			.add(level.total_goals)
			.add(level.found_secrets)
			.add(level.total_secrets));

		gi.WriteByte(svc_layout);
		gi.WriteString(string);
		gi.unicast(ent, true);
	}

	/*
	==================
	Cmd_Help_f
	
	Display the current help message
	==================
	*/
	public static void Cmd_Help_f(edict_t ent) {
		// this is for backwards compatability
		if (deathmatch.value != 0) {
			Cmd_Score_f(ent);
			return;
		}

		ent.client.showinventory = false;
		ent.client.showscores = false;

		if (ent.client.showhelp && (ent.client.pers.game_helpchanged == game.helpchanged)) {
			ent.client.showhelp = false;
			return;
		}

		ent.client.showhelp = true;
		ent.client.pers.helpchanged = 0;
		HelpComputer(ent);
	}

	//=======================================================================

	/*
	===============
	G_SetStats
	===============
	*/
	public static void G_SetStats(edict_t ent) {
		gitem_t item;
		int index, cells = 0;
		int power_armor_type;

		//
		// health
		//
		ent.client.ps.stats[STAT_HEALTH_ICON] = (short) level.pic_health;
		ent.client.ps.stats[STAT_HEALTH] = (short) ent.health;

		//
		// ammo
		//
		if (0 == ent.client.ammo_index /* || !ent.client.pers.inventory[ent.client.ammo_index] */
			) {
			ent.client.ps.stats[STAT_AMMO_ICON] = 0;
			ent.client.ps.stats[STAT_AMMO] = 0;
		}
		else {
			item = itemlist[ent.client.ammo_index];
			ent.client.ps.stats[STAT_AMMO_ICON] = (short) gi.imageindex(item.icon);
			ent.client.ps.stats[STAT_AMMO] = (short) ent.client.pers.inventory[ent.client.ammo_index];
		}

		//
		// armor
		//
		power_armor_type = PowerArmorType(ent);
		if (power_armor_type != 0) {
			cells = ent.client.pers.inventory[ITEM_INDEX(FindItem("cells"))];
			if (cells == 0) { // ran out of cells for power armor
				ent.flags &= ~FL_POWER_ARMOR;
				gi.sound(ent, CHAN_ITEM, gi.soundindex("misc/power2.wav"), 1, ATTN_NORM, 0);
				power_armor_type = 0;
				;
			}
		}

		index = ArmorIndex(ent);
		if (power_armor_type != 0 && (0 == index || 0 != (level.framenum & 8))) { // flash between power armor and other armor icon
			ent.client.ps.stats[STAT_ARMOR_ICON] = (short) gi.imageindex("i_powershield");
			ent.client.ps.stats[STAT_ARMOR] = (short) cells;
		}
		else if (index != 0) {
			item = GetItemByIndex(index);
			ent.client.ps.stats[STAT_ARMOR_ICON] = (short) gi.imageindex(item.icon);
			ent.client.ps.stats[STAT_ARMOR] = (short) ent.client.pers.inventory[index];
		}
		else {
			ent.client.ps.stats[STAT_ARMOR_ICON] = 0;
			ent.client.ps.stats[STAT_ARMOR] = 0;
		}

		//
		// pickup message
		//
		if (level.time > ent.client.pickup_msg_time) {
			ent.client.ps.stats[STAT_PICKUP_ICON] = 0;
			ent.client.ps.stats[STAT_PICKUP_STRING] = 0;
		}

		//
		// timers
		//
		if (ent.client.quad_framenum > level.framenum) {
			ent.client.ps.stats[STAT_TIMER_ICON] = (short) gi.imageindex("p_quad");
			ent.client.ps.stats[STAT_TIMER] = (short) ((ent.client.quad_framenum - level.framenum) / 10);
		}
		else if (ent.client.invincible_framenum > level.framenum) {
			ent.client.ps.stats[STAT_TIMER_ICON] = (short) gi.imageindex("p_invulnerability");
			ent.client.ps.stats[STAT_TIMER] = (short) ((ent.client.invincible_framenum - level.framenum) / 10);
		}
		else if (ent.client.enviro_framenum > level.framenum) {
			ent.client.ps.stats[STAT_TIMER_ICON] = (short) gi.imageindex("p_envirosuit");
			ent.client.ps.stats[STAT_TIMER] = (short) ((ent.client.enviro_framenum - level.framenum) / 10);
		}
		else if (ent.client.breather_framenum > level.framenum) {
			ent.client.ps.stats[STAT_TIMER_ICON] = (short) gi.imageindex("p_rebreather");
			ent.client.ps.stats[STAT_TIMER] = (short) ((ent.client.breather_framenum - level.framenum) / 10);
		}
		else {
			ent.client.ps.stats[STAT_TIMER_ICON] = 0;
			ent.client.ps.stats[STAT_TIMER] = 0;
		}

		//
		// selected item
		//
		// bugfix rst
		if (ent.client.pers.selected_item <=0)
			ent.client.ps.stats[STAT_SELECTED_ICON] = 0;
		else
			ent.client.ps.stats[STAT_SELECTED_ICON] = 
				(short) gi.imageindex(itemlist[ent.client.pers.selected_item].icon);

		ent.client.ps.stats[STAT_SELECTED_ITEM] = (short) ent.client.pers.selected_item;

		//
		// layouts
		//
		ent.client.ps.stats[STAT_LAYOUTS] = 0;

		if (deathmatch.value != 0) {
			if (ent.client.pers.health <= 0 || level.intermissiontime != 0 || ent.client.showscores)
				ent.client.ps.stats[STAT_LAYOUTS] |= 1;
			if (ent.client.showinventory && ent.client.pers.health > 0)
				ent.client.ps.stats[STAT_LAYOUTS] |= 2;
		}
		else {
			if (ent.client.showscores || ent.client.showhelp)
				ent.client.ps.stats[STAT_LAYOUTS] |= 1;
			if (ent.client.showinventory && ent.client.pers.health > 0)
				ent.client.ps.stats[STAT_LAYOUTS] |= 2;
		}

		//
		// frags
		//
		ent.client.ps.stats[STAT_FRAGS] = (short) ent.client.resp.score;

		//
		// help icon / current weapon if not shown
		//
		if (ent.client.pers.helpchanged != 0 && (level.framenum & 8) != 0)
			ent.client.ps.stats[STAT_HELPICON] = (short) gi.imageindex("i_help");
		else if ((ent.client.pers.hand == CENTER_HANDED || ent.client.ps.fov > 91) && ent.client.pers.weapon != null)
			ent.client.ps.stats[STAT_HELPICON] = (short) gi.imageindex(ent.client.pers.weapon.icon);
		else
			ent.client.ps.stats[STAT_HELPICON] = 0;

		ent.client.ps.stats[STAT_SPECTATOR] = 0;
	}

	/*
	===============
	G_CheckChaseStats
	===============
	*/
	public static void G_CheckChaseStats(edict_t ent) {
		int i;
		gclient_t cl;

		for (i = 1; i <= maxclients.value; i++) {
			cl = g_edicts[i].client;
			if (!g_edicts[i].inuse || cl.chase_target != ent)
				continue;
			//memcpy(cl.ps.stats, ent.client.ps.stats, sizeof(cl.ps.stats));
			System.arraycopy(ent.client.ps.stats, 0, cl.ps.stats, 0, Defines.MAX_STATS);

			G_SetSpectatorStats(g_edicts[i]);
		}
	}

	/*
	===============
	G_SetSpectatorStats
	===============
	*/
	public static void G_SetSpectatorStats(edict_t ent) {
		gclient_t cl = ent.client;

		if (null == cl.chase_target)
			G_SetStats(ent);

		cl.ps.stats[STAT_SPECTATOR] = 1;

		// layouts are independant in spectator
		cl.ps.stats[STAT_LAYOUTS] = 0;
		if (cl.pers.health <= 0 || level.intermissiontime != 0 || cl.showscores)
			cl.ps.stats[STAT_LAYOUTS] |= 1;
		if (cl.showinventory && cl.pers.health > 0)
			cl.ps.stats[STAT_LAYOUTS] |= 2;

		if (cl.chase_target != null && cl.chase_target.inuse)
			//cl.ps.stats[STAT_CHASE] = (short) (CS_PLAYERSKINS + (cl.chase_target - g_edicts) - 1);
			cl.ps.stats[STAT_CHASE] = (short) (CS_PLAYERSKINS + cl.chase_target.index - 1);
		else
			cl.ps.stats[STAT_CHASE] = 0;
	}

}
