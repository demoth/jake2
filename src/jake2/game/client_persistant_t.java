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

import jake2.Defines;
import jake2.util.QuakeFile;

import java.io.IOException;

public class client_persistant_t {

	public void set(client_persistant_t from) {
		
		userinfo= from.userinfo;
		netname= from.netname;
		hand= from.hand;
		connected= from.connected;
		health= from.health;
		max_health= from.max_health;
		savedFlags= from.savedFlags;
		selected_item= from.selected_item;
		System.arraycopy(from.inventory, 0, inventory, 0, inventory.length);
		max_bullets= from.max_bullets;
		max_shells= from.max_shells;
		max_rockets= from.max_rockets;
		max_grenades= from.max_grenades;
		max_cells= from.max_cells;
		max_slugs= from.max_slugs;
		weapon= from.weapon;
		lastweapon= from.lastweapon;
		power_cubes= from.power_cubes;
		score= from.score;
		game_helpchanged= from.game_helpchanged;
		helpchanged= from.helpchanged;
		spectator= from.spectator;
	}

	//	client data that stays across multiple level loads
	String userinfo= "";
	String netname= "";
	int hand;

	boolean connected; // a loadgame will leave valid entities that
	// just don't have a connection yet

	// values saved and restored from edicts when changing levels
	int health;
	int max_health;
	int savedFlags;

	int selected_item;
	int inventory[]= new int[Defines.MAX_ITEMS];

	// ammo capacities
	public int max_bullets;
	public int max_shells;
	public int max_rockets;
	public int max_grenades;
	public int max_cells;
	public int max_slugs;
	//pointer
	gitem_t weapon;
	//pointer
	gitem_t lastweapon;
	int power_cubes; // used for tracking the cubes in coop games
	int score; // for calculating total unit score in coop games
	int game_helpchanged;
	int helpchanged;
	boolean spectator; // client is a spectator

	/** Reads a client_persistant structure from a file. */
	public void read(QuakeFile f) throws IOException {

		userinfo= f.readString();
		netname= f.readString();

		hand= f.readInt();

		connected= f.readInt() != 0;
		health= f.readInt();

		max_health= f.readInt();
		savedFlags= f.readInt();
		selected_item= f.readInt();

		for (int n= 0; n < Defines.MAX_ITEMS; n++)
			inventory[n]= f.readInt();

		max_bullets= f.readInt();
		max_shells= f.readInt();
		max_rockets= f.readInt();
		max_grenades= f.readInt();
		max_cells= f.readInt();
		max_slugs= f.readInt();

		weapon= f.readItem();
		lastweapon= f.readItem();
		power_cubes= f.readInt();
		score= f.readInt();

		game_helpchanged= f.readInt();
		helpchanged= f.readInt();
		spectator= f.readInt() != 0;
	}

	/** Writes a client_persistant structure to a file. */
	public void write(QuakeFile f) throws IOException {
		// client persistant_t
		f.writeString(userinfo);
		f.writeString(netname);

		f.writeInt(hand);

		f.writeInt(connected ? 1 : 0);
		f.writeInt(health);

		f.writeInt(max_health);
		f.writeInt(savedFlags);
		f.writeInt(selected_item);

		for (int n= 0; n < Defines.MAX_ITEMS; n++)
			f.writeInt(inventory[n]);

		f.writeInt(max_bullets);
		f.writeInt(max_shells);
		f.writeInt(max_rockets);
		f.writeInt(max_grenades);
		f.writeInt(max_cells);
		f.writeInt(max_slugs);

		f.writeItem(weapon);
		f.writeItem(lastweapon);
		f.writeInt(power_cubes);
		f.writeInt(score);

		f.writeInt(game_helpchanged);
		f.writeInt(helpchanged);
		f.writeInt(spectator ? 1 : 0);
	}
}