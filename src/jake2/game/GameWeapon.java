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

// Created on 12.11.2003 by RST.
// $Id: GameWeapon.java,v 1.5 2004-02-26 22:36:31 rst Exp $

package jake2.game;

import jake2.Defines;
import jake2.Globals;
import jake2.util.*;

public class GameWeapon extends GameAI {

	/*
	===============
	PlayerNoise
	
	Each player can have two noise objects associated with it:
	a personal noise (jumping, pain, weapon firing), and a weapon
	target noise (bullet wall impacts)
	
	Monsters that don't directly see the player can move
	to a noise in hopes of seeing the player from there.
	===============
	*/
	static void PlayerNoise(edict_t who, float[] where, int type) {
		edict_t noise;

		if (type == PNOISE_WEAPON) {
			if (who.client.silencer_shots == 0) {
				who.client.silencer_shots--;
				return;
			}
		}

		if (deathmatch.value != 0)
			return;

		if ((who.flags & FL_NOTARGET) != 0)
			return;

		if (who.mynoise == null) {
			noise= G_Spawn();
			noise.classname= "player_noise";
			Math3D.VectorSet(noise.mins, -8, -8, -8);
			Math3D.VectorSet(noise.maxs, 8, 8, 8);
			noise.owner= who;
			noise.svflags= SVF_NOCLIENT;
			who.mynoise= noise;

			noise= G_Spawn();
			noise.classname= "player_noise";
			Math3D.VectorSet(noise.mins, -8, -8, -8);
			Math3D.VectorSet(noise.maxs, 8, 8, 8);
			noise.owner= who;
			noise.svflags= SVF_NOCLIENT;
			who.mynoise2= noise;
		}

		if (type == PNOISE_SELF || type == PNOISE_WEAPON) {
			noise= who.mynoise;
			level.sound_entity= noise;
			level.sound_entity_framenum= level.framenum;
		} else // type == PNOISE_IMPACT
			{
			noise= who.mynoise2;
			level.sound2_entity= noise;
			level.sound2_entity_framenum= level.framenum;
		}

		Math3D.VectorCopy(where, noise.s.origin);
		Math3D.VectorSubtract(where, noise.maxs, noise.absmin);
		Math3D.VectorAdd(where, noise.maxs, noise.absmax);
		noise.teleport_time= level.time;
		gi.linkentity(noise);
	}

	/*
	=================
	check_dodge
	
	This is a support routine used when a client is firing
	a non-instant attack weapon.  It checks to see if a
	monster's dodge function should be called.
	=================
	*/
	static void check_dodge(edict_t self, float[] start, float[] dir, int speed) {
		float[] end= { 0, 0, 0 };
		float[] v= { 0, 0, 0 };
		trace_t tr;
		float eta;

		// easy mode only ducks one quarter the time
		if (skill.value == 0) {
			if (Lib.random() > 0.25)
				return;
		}
		Math3D.VectorMA(start, 8192, dir, end);
		tr= gi.trace(start, null, null, end, self, MASK_SHOT);
		if ((tr.ent != null)
			&& (tr.ent.svflags & SVF_MONSTER) != 0
			&& (tr.ent.health > 0)
			&& (null != tr.ent.monsterinfo.dodge)
			&& infront(tr.ent, self)) {
			Math3D.VectorSubtract(tr.endpos, start, v);
			eta= (Math3D.VectorLength(v) - tr.ent.maxs[0]) / speed;
			tr.ent.monsterinfo.dodge.dodge(tr.ent, self, eta);
		}
	}

}
