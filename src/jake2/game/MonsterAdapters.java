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
// $Id: MonsterAdapters.java,v 1.1 2004-02-26 22:36:31 rst Exp $

package jake2.game;

import jake2.Defines;
import jake2.client.M;

public class MonsterAdapters {

	public static EntThinkAdapter monster_think = new EntThinkAdapter() {
		public boolean think(edict_t self) {
	
			M.M_MoveFrame(self);
			if (self.linkcount != self.monsterinfo.linkcount) {
				self.monsterinfo.linkcount = self.linkcount;
				M.M_CheckGround(self);
			}
			M.M_CatagorizePosition(self);
			M.M_WorldEffects(self);
			M.M_SetEffects(self);
			return true;
		}
	};
	public static EntThinkAdapter monster_triggered_spawn = new EntThinkAdapter() {
		public boolean think(edict_t self) {
	
			self.s.origin[2] += 1;
			GameUtil.KillBox(self);
	
			self.solid = Defines.SOLID_BBOX;
			self.movetype = Defines.MOVETYPE_STEP;
			self.svflags &= ~Defines.SVF_NOCLIENT;
			self.air_finished = GameBase.level.time + 12;
			GameBase.gi.linkentity(self);
	
			Monster.monster_start_go(self);
	
			if (self.enemy != null && 0 == (self.spawnflags & 1) && 0 == (self.enemy.flags & Defines.FL_NOTARGET)) {
				GameUtil.FoundTarget(self);
			}
			else {
				self.enemy = null;
			}
			return true;
		}
	};
	//	we have a one frame delay here so we don't telefrag the guy who activated us
	public static EntUseAdapter monster_triggered_spawn_use = new EntUseAdapter() {
	
		public void use(edict_t self, edict_t other, edict_t activator) {
			self.think = monster_triggered_spawn;
			self.nextthink = GameBase.level.time + Defines.FRAMETIME;
			if (activator.client != null)
				self.enemy = activator;
			self.use = GameUtilAdapters.monster_use;
		}
	};
	public static EntThinkAdapter monster_triggered_start = new EntThinkAdapter() {
		public boolean think(edict_t self) {
			self.solid = Defines.SOLID_NOT;
			self.movetype = Defines.MOVETYPE_NONE;
			self.svflags |= Defines.SVF_NOCLIENT;
			self.nextthink = 0;
			self.use = monster_triggered_spawn_use;
			return true;
		}
	};
}
