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

// Created on 27.11.2003 by RST.
// $Id: centity_t.java,v 1.2 2004-07-08 20:56:50 hzi Exp $

package org.demoth.cake;

import com.badlogic.gdx.graphics.g3d.ModelInstance;
import jake2.qcommon.entity_state_t;
import org.demoth.cake.assets.Sp2Asset;

/**
 * centity_t
 */
public class ClientEntity {
	public entity_state_t baseline= new entity_state_t(); // delta from this if not from a previous frame
	public entity_state_t current= new entity_state_t();
	public entity_state_t prev= new entity_state_t(); // will always be valid, but might just be a copy of current

	public int serverframe; // if not current, this ent isn't in the frame

	public int trailcount; // for diminishing grenade trails
	public float[] lerp_origin = { 0, 0, 0 }; // for trails (variable hz)

	int fly_stoptime;

	public ModelInstance modelInstance; // md2/brush runtime instance; null for pure sprite entities
	public Sp2Asset spriteAsset; // loaded .sp2 asset when entity is sprite-backed
	// Resolved per-frame values after legacy compatibility rules in ClientEntityManager:
	// frame (EF_ANIM*), translucency upgrades (EF_BFG/EF_PLASMA/EF_SPHERETRANS), and alpha.
	public int resolvedFrame;
	public int resolvedRenderFx;
	public float alpha = 1.0f;

	public String name;

    public ClientEntity(String name) {
        this.name = name;
    }
}
