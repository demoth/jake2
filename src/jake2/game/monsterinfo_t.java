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

// Created on 31.10.2003 by RST.

package jake2.game;

import jake2.util.QuakeFile;

import java.io.IOException;

public class monsterinfo_t {

	public mmove_t currentmove;
	public int aiflags;
	public int nextframe;
	public float scale;

	public EntThinkAdapter stand;
	public EntThinkAdapter idle;
	public EntThinkAdapter search;
	public EntThinkAdapter walk;
	public EntThinkAdapter run;

	public EntDodgeAdapter dodge;

	public EntThinkAdapter attack;
	public EntThinkAdapter melee;

	public EntInteractAdapter sight;

	public EntThinkAdapter checkattack;

	public float pausetime;
	public float attack_finished;

	public float[] saved_goal= { 0, 0, 0 };
	public float search_time;
	public float trail_time;
	public float[] last_sighting= { 0, 0, 0 };
	public int attack_state;
	public int lefty;
	public float idle_time;
	public int linkcount;

	public int power_armor_type;
	public int power_armor_power;

	/** Writes the monsterinfo to the file.*/
	public void write(QuakeFile f) throws IOException
	{
		f.writeBoolean(currentmove != null);
		if (currentmove != null)
			currentmove.write(f);
		f.writeInt(aiflags);
		f.writeInt(nextframe);
		f.writeFloat(scale);
		f.writeAdapter(stand);
		f.writeAdapter(idle);
		f.writeAdapter(search);
		f.writeAdapter(walk);
		f.writeAdapter(run);
		
		f.writeAdapter(dodge);
		
		f.writeAdapter(attack);
		f.writeAdapter(melee);
		
		f.writeAdapter(sight);
		
		f.writeAdapter(checkattack);
		
 		f.writeFloat(pausetime);
 		f.writeFloat(attack_finished);
 	
		f.writeVector(saved_goal);
		
		f.writeFloat(search_time);
		f.writeFloat(trail_time);
		
		f.writeVector(last_sighting);
 
		f.writeInt(attack_state);
		f.writeInt(lefty);
	
		f.writeFloat(idle_time);
		f.writeInt(linkcount);
		
		f.writeInt(power_armor_power);
		f.writeInt(power_armor_type);
	}

	/** Writes the monsterinfo to the file.*/
	public void read(QuakeFile f) throws IOException
	{
		if (f.readBoolean())
		{
			currentmove= new mmove_t();
			currentmove.read(f);
		}
		else
			currentmove= null; 
		aiflags = f.readInt();
		nextframe = f.readInt();
		scale = f.readFloat();
		stand = (EntThinkAdapter) f.readAdapter();
		idle = (EntThinkAdapter) f.readAdapter();
		search = (EntThinkAdapter) f.readAdapter();
		walk = (EntThinkAdapter) f.readAdapter();
		run = (EntThinkAdapter) f.readAdapter();
		
		dodge = (EntDodgeAdapter) f.readAdapter();
		
		attack = (EntThinkAdapter) f.readAdapter();
		melee = (EntThinkAdapter) f.readAdapter();
		
		sight = (EntInteractAdapter) f.readAdapter();
		
		checkattack = (EntThinkAdapter) f.readAdapter();
		
 		pausetime = f.readFloat();
 		attack_finished = f.readFloat();
 	
		saved_goal = f.readVector();
		
		search_time = f.readFloat();
		trail_time = f.readFloat();
		
		last_sighting = f.readVector();
 
		attack_state = f.readInt();
		lefty = f.readInt();
	
		idle_time = f.readFloat();
		linkcount = f.readInt();
		
		power_armor_power = f.readInt();
		power_armor_type = f.readInt();

	}


}
