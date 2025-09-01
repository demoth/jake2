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

// Created on 20.11.2003 by RST

package jake2.game;

import jake2.qcommon.ServerEntity;
import jake2.qcommon.filesystem.QuakeFile;

import java.io.IOException;

/**
 * this structure is cleared as each map is entered
 * it is read/written to the level.sav file for savegames
 */
public class level_locals_t {
    public int framenum;
    public float time;

    public String level_name = ""; // the descriptive name (Outer Base, etc)
    public String mapname = ""; // the server name (base1, etc)
    public String nextmap = ""; // go here when fraglimit is hit

    // intermission state
    public float intermissiontime; // time the intermission was started
    public String changemap;
    public boolean exitintermission;
    public float[] intermission_origin = {0, 0, 0};
    public float[] intermission_angle = {0, 0, 0};

    public GameEntity sight_client; // changed once each frame for coop games

    public GameEntity sight_entity;
    public int sight_entity_framenum;

    public GameEntity sound_entity;
    public int sound_entity_framenum;
	
	public GameEntity sound2_entity;
	public int sound2_entity_framenum;

	public int pic_health;

	public int total_secrets;
	public int found_secrets;

	public int total_goals;
	public int found_goals;

	public int total_monsters;
	public int killed_monsters;

	public ServerEntity current_entity; // entity running from G_RunFrame
	public int body_que; // dead bodies

	public int power_cubes; // ugly necessity for coop

	/** Writes the levellocales to the file.*/	
	public void write(QuakeFile f) throws IOException  
	{
		f.writeInt(framenum);
		f.writeFloat(time);
		f.writeString(level_name);
		f.writeString(mapname);
		f.writeString(nextmap);
		f.writeFloat(intermissiontime);	
		f.writeString(changemap);
		f.writeBoolean(exitintermission);
		f.writeVector(intermission_origin);
		f.writeVector(intermission_angle);
 		f.writeEdictRef(sight_client);
 		
		f.writeEdictRef(sight_entity);
		f.writeInt(sight_entity_framenum);
		
		f.writeEdictRef(sound_entity);
		f.writeInt(sound_entity_framenum);
		f.writeEdictRef(sound2_entity);
		f.writeInt(sound2_entity_framenum);

		f.writeInt(pic_health);

		f.writeInt(total_secrets);
		f.writeInt(found_secrets);
		
		f.writeInt(total_goals);
		f.writeInt(found_goals);
		f.writeInt(total_monsters);
		f.writeInt(killed_monsters);
		
		f.writeEdictRef(current_entity); 
		f.writeInt(body_que); // dead bodies
		f.writeInt(power_cubes); // ugly necessity for coop
		
		// rst's checker :-)		
		f.writeInt(4711);
	}
	
	/** Reads the level locals from the file. */
	public void read(QuakeFile f, ServerEntity[] g_edicts) throws IOException
	{
		framenum = f.readInt();
		time = f.readFloat();
		level_name = f.readString();
		mapname = f.readString();
		nextmap = f.readString();
		intermissiontime = f.readFloat();	
		changemap = f.readString();
		exitintermission = f.readBoolean();
		intermission_origin = f.readVector();
		intermission_angle = f.readVector();
 		sight_client = (GameEntity) f.readEdictRef(g_edicts);
 		
		sight_entity = (GameEntity) f.readEdictRef(g_edicts);
		sight_entity_framenum = f.readInt();
		
		sound_entity = (GameEntity) f.readEdictRef(g_edicts);
		sound_entity_framenum = f.readInt();
		sound2_entity = (GameEntity) f.readEdictRef(g_edicts);
		sound2_entity_framenum = f.readInt();

		pic_health = f.readInt();

		total_secrets = f.readInt();
		found_secrets = f.readInt();
		
		total_goals = f.readInt();
		found_goals = f.readInt();
		total_monsters = f.readInt();
		killed_monsters = f.readInt();
		
		current_entity = f.readEdictRef(g_edicts);
		body_que = f.readInt(); // dead bodies
		power_cubes = f.readInt(); // ugly necessity for coop		
		
		// rst's checker :-)
		if (f.readInt()!= 4711)
			System.out.println("error in reading level_locals.");
	}
}
