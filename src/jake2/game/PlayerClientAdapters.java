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

// Created on 26.02.2004 by RST.
// $Id: PlayerClientAdapters.java,v 1.1 2004-02-26 22:36:31 rst Exp $

package jake2.game;

import jake2.game.pmove_t.TraceAdapter;

import jake2.Defines;
import jake2.util.Lib;
import jake2.util.Math3D;

public class PlayerClientAdapters extends PlayerClient {

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
				es = GameBase.G_Find(es, GameBase.findByClass, "info_player_start");
				spot = es.o;
				if (spot == null)
					return true;
				if (spot.targetname == null)
					continue;
				Math3D.VectorSubtract(self.s.origin, spot.s.origin, d);
				if (Math3D.VectorLength(d) < 384) {
					if ((self.targetname == null) || Lib.Q_stricmp(self.targetname, spot.targetname) != 0) {
						//				gi.dprintf("FixCoopSpots changed %s at %s targetname from %s to %s\n", self.classname, vtos(self.s.origin), self.targetname, spot.targetname);
						self.targetname = spot.targetname;
					}
					return true;
				}
			}
		}
	};
	// now if that one wasn't ugly enough for you then try this one on for size
	// some maps don't have any coop spots at all, so we need to create them
	// where they should have been
	
	static EntThinkAdapter SP_CreateCoopSpots = new EntThinkAdapter() {
		public boolean think(edict_t self) {
	
			edict_t spot;
	
			if (Lib.Q_stricmp(GameBase.level.mapname, "security") == 0) {
				spot = GameUtil.G_Spawn();
				spot.classname = "info_player_coop";
				spot.s.origin[0] = 188 - 64;
				spot.s.origin[1] = -164;
				spot.s.origin[2] = 80;
				spot.targetname = "jail3";
				spot.s.angles[1] = 90;
	
				spot = GameUtil.G_Spawn();
				spot.classname = "info_player_coop";
				spot.s.origin[0] = 188 + 64;
				spot.s.origin[1] = -164;
				spot.s.origin[2] = 80;
				spot.targetname = "jail3";
				spot.s.angles[1] = 90;
	
				spot = GameUtil.G_Spawn();
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
	//=======================================================================
	
	static EntPainAdapter player_pain = new EntPainAdapter() {
		public void pain(edict_t self, edict_t other, float kick, int damage) {
				// player pain is handled at the end of the frame in P_DamageFeedback
	}
	};
	static EntDieAdapter body_die = new EntDieAdapter() {
		public void die(edict_t self, edict_t inflictor, edict_t attacker, int damage, float[] point) {
	
			int n;
	
			if (self.health < -40) {
				GameBase.gi.sound(self, Defines.CHAN_BODY, GameBase.gi.soundindex("misc/udeath.wav"), 1, Defines.ATTN_NORM, 0);
				for (n = 0; n < 4; n++)
					GameAI.ThrowGib(self, "models/objects/gibs/sm_meat/tris.md2", damage, Defines.GIB_ORGANIC);
				self.s.origin[2] -= 48;
				GameAI.ThrowClientHead(self, damage);
				self.takedamage = Defines.DAMAGE_NO;
			}
		}
	};
	//==============================================================
	
	static edict_t pm_passent;
	// pmove doesn't need to know about passent and contentmask
	public static pmove_t.TraceAdapter PM_trace = new pmove_t.TraceAdapter() {
	
		public trace_t trace(float[] start, float[] mins, float[] maxs, float[] end) {
			if (pm_passent.health > 0)
				return GameBase.gi.trace(start, mins, maxs, end, pm_passent, Defines.MASK_PLAYERSOLID);
			else
				return GameBase.gi.trace(start, mins, maxs, end, pm_passent, Defines.MASK_DEADSOLID);
		}
	
	};
}
