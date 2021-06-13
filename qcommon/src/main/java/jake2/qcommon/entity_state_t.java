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

package jake2.qcommon;

import jake2.qcommon.filesystem.QuakeFile;
import jake2.qcommon.util.Math3D;

import java.io.IOException;

/**
 * entity_state_t is the information conveyed from the server
 * in an update message about entities that the client will
 * need to render in some way.
 */
public class entity_state_t implements Cloneable
{
	public entity_state_t(edict_t ent)
	{
		this.surrounding_ent = ent;
		if (ent != null)
		    number = ent.index;
	}

	/** edict index. TODO: this is critical. The index has to be proper managed. */
	public int number = 0; 
	// TODO: why was this introduced?
	public edict_t surrounding_ent = null;
	public float[] origin = { 0, 0, 0 };
	public float[] angles = { 0, 0, 0 };
	
	/** for lerping. */
	public float[] old_origin = { 0, 0, 0 }; 
	public int modelindex;
	/** weapons, CTF flags, etc. */
	public int modelindex2, modelindex3, modelindex4; 
	public int frame;
	public int skinnum;
	/** PGM - we're filling it, so it needs to be unsigned. */
	public int effects; 
	public int renderfx;
	public int solid;
	// for client side prediction, 8*(bits 0-4) is x/y radius
	// 8*(bits 5-9) is z down distance, 8(bits10-15) is z up
	// gi.linkentity sets this properly
	public int sound; // for looping sounds, to guarantee shutoff
	public int event; // impulse events -- muzzle flashes, footsteps, etc
	// events only go out for a single frame, they
	// are automatically cleared each frame

	/** Writes the entity state to the file. */
	public void write(QuakeFile f) throws IOException
	{
		f.writeEdictRef(surrounding_ent);
		f.writeVector(origin);
		f.writeVector(angles);
		f.writeVector(old_origin);
	
		f.writeInt(modelindex); 
		
		f.writeInt(modelindex2);
		f.writeInt(modelindex3);
		f.writeInt(modelindex4);
	
		f.writeInt(frame);	
		f.writeInt(skinnum);
		
		f.writeInt(effects);
		f.writeInt(renderfx);
		f.writeInt(solid);
		
		f.writeInt(sound);
		f.writeInt(event);
		
	}

	/** Reads the entity state from the file. */
	public void read(QuakeFile f, edict_t[] g_edicts) throws IOException
	{
		surrounding_ent = f.readEdictRef(g_edicts);
		origin = f.readVector();
		angles = f.readVector();
		old_origin = f.readVector();
	
		modelindex = f.readInt(); 
		
		modelindex2= f.readInt();
		modelindex3= f.readInt();
		modelindex4= f.readInt();
	
		frame = f.readInt();	
		skinnum = f.readInt();
		
		effects = f.readInt();
		renderfx = f.readInt();
		solid = f.readInt();
		
		sound = f.readInt();
		event = f.readInt();
		

	}


	public entity_state_t getClone()
	{
		entity_state_t out = new entity_state_t(this.surrounding_ent);
		out.set(this);
		return out;
	}

	public void set(entity_state_t from)
	{
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

	/**
	 * Copies the fields from another entity state, controlled by the flags
	 * in the same way it's encoded during serialization.
	 */
	public void setByFlags(entity_state_t from, int flags) {
		number = from.number;

		if ((flags & Defines.U_ORIGIN1) != 0)
			origin[0] = from.origin[0];
		if ((flags & Defines.U_ORIGIN2) != 0)
			origin[1] = from.origin[1];
		if ((flags & Defines.U_ORIGIN3) != 0)
			origin[2] = from.origin[2];

		if ((flags & Defines.U_ANGLE1) != 0)
			angles[0] = from.angles[0];
		if ((flags & Defines.U_ANGLE2) != 0)
			angles[1] = from.angles[1];
		if ((flags & Defines.U_ANGLE3) != 0)
			angles[2] = from.angles[2];

		if ((flags & Defines.U_OLDORIGIN) != 0)
			Math3D.VectorCopy(from.old_origin, old_origin);

		if ((flags & Defines.U_MODEL) != 0)
			modelindex = from.modelindex;
		if ((flags & Defines.U_MODEL2) != 0)
			modelindex2 = from.modelindex2;
		if ((flags & Defines.U_MODEL3) != 0)
			modelindex3 = from.modelindex3;
		if ((flags & Defines.U_MODEL4) != 0)
			modelindex4 = from.modelindex4;

		if ((flags & Defines.U_FRAME8) != 0 || (flags & Defines.U_FRAME16) != 0)
			frame = from.frame;

		if ((flags & Defines.U_SKIN8) != 0 || (flags & Defines.U_SKIN16) != 0)
			skinnum = from.skinnum;

		if ((flags & Defines.U_EFFECTS8) != 0 || (flags & Defines.U_EFFECTS16) != 0)
			effects = from.effects;

		if ((flags & Defines.U_RENDERFX8) != 0 || (flags & Defines.U_RENDERFX16) != 0)
			renderfx = from.renderfx;

		if ((flags & Defines.U_SOLID) != 0)
			solid = from.solid;

		if ((flags & Defines.U_SOUND) != 0)
			sound = from.sound;

		if ((flags & Defines.U_EVENT) != 0)
			event = from.event;
	}

	public void clear()
	{
	    //TODO: this is critical. The index has to be proper managed.
		number = 0;
		surrounding_ent = null;
		Math3D.VectorClear(origin);
		Math3D.VectorClear(angles);
		Math3D.VectorClear(old_origin);
		modelindex = 0;
		modelindex2 = modelindex3 = modelindex4 = 0; 
		frame = 0;
		skinnum = 0;
		effects = 0; 
		renderfx = 0;
		solid = 0;
		sound = 0;
		event = 0;
	}
}