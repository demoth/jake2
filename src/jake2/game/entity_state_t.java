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

// Created on 08.11.2003 by RST.
// $Id: entity_state_t.java,v 1.7 2004-02-13 22:04:00 rst Exp $

package jake2.game;

import jake2.util.Math3D;

public class entity_state_t implements Cloneable {
	//	entity_state_t is the information conveyed from the server
	//	in an update message about entities that the client will
	//	need to render in some way


	public entity_state_t(edict_t ent)
	{
		this.surrounding_ent =  ent;
	}

	public int number = -99999; // edict index
	public edict_t surrounding_ent = null;
	public float[] origin = { 0, 0, 0 };
	public float[] angles = { 0, 0, 0 };
	public float[] old_origin = { 0, 0, 0 }; // for lerping
	public int modelindex;
	public int modelindex2, modelindex3, modelindex4; // weapons, CTF flags, etc.
	public int frame;
	public int skinnum;
	public int effects; // PGM - we're filling it, so it needs to be unsigned
	public int renderfx;
	public int solid;
	// for client side prediction, 8*(bits 0-4) is x/y radius
	// 8*(bits 5-9) is z down distance, 8(bits10-15) is z up
	// gi.linkentity sets this properly
	public int sound; // for looping sounds, to guarantee shutoff
	public int event; // impulse events -- muzzle flashes, footsteps, etc
	// events only go out for a single frame, they
	// are automatically cleared each frame

	public entity_state_t getClone()
	{
		entity_state_t out = new entity_state_t(this.surrounding_ent);
		out.set(this);
		return out;
	}

	public void set(entity_state_t from) {
		number = from.number;
		Math3D.VectorCopy(from.origin, origin);
		Math3D.VectorCopy(from.angles, angles);
		Math3D.VectorCopy(from.old_origin, old_origin);

		modelindex = from.modelindex;
		modelindex2 = from.modelindex2;
		modelindex3 = from.modelindex3;
		modelindex4 = from.modelindex4;

		frame = from.frame;
		skinnum = from.skinnum;
		effects = from.effects;
		renderfx = from.renderfx;
		solid = from.solid;
		sound = from.sound;
		event = from.event;
	}
}