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
// $Id: client_persistant_t.java,v 1.3 2004-07-08 20:24:29 hzi Exp $

package jake2.game;

import jake2.Defines;
import jake2.util.Lib;

import java.io.IOException;
import java.nio.ByteBuffer;

public class client_persistant_t implements Cloneable {

//	public client_persistant_t getClone() {
//		try {
//			return (client_persistant_t) this.clone();
//		}
//		catch (CloneNotSupportedException e) {
//			return null;
//		}
//	}
	
	public void set(client_persistant_t from)
	{
		userinfo = from.userinfo;
		netname = from.netname;
		hand = from.hand;
		connected = from.connected;
		health = from.health;
		max_health = from.max_health;
		savedFlags = from.savedFlags;
		selected_item = from.selected_item;
		System.arraycopy(from.inventory, 0, inventory, 0, inventory.length);
		//inventory = Lib.clone(from.inventory);
		max_bullets = from.max_bullets;
		max_shells = from.max_shells;
		max_rockets = from.max_rockets;
		max_grenades = from.max_grenades;
		max_cells = from.max_cells;
		max_slugs = from.max_slugs;
		weapon = from.weapon;
		lastweapon = from.lastweapon;
		power_cubes = from.power_cubes;
		score = from.score;
		game_helpchanged = from.game_helpchanged;
		helpchanged = from.helpchanged;
		spectator = from.spectator;
	}

	//	client data that stays across multiple level loads
	String userinfo = "";
	String netname = "";
	int hand;

	boolean connected; // a loadgame will leave valid entities that
	// just don't have a connection yet

	// values saved and restored from edicts when changing levels
	int health;
	int max_health;
	int savedFlags;

	int selected_item;
	int inventory[] = new int[Defines.MAX_ITEMS];

	// ammo capacities
	int max_bullets;
	int max_shells;
	int max_rockets;
	int max_grenades;
	int max_cells;
	int max_slugs;
	//pointer
	gitem_t weapon;
	//pointer
	gitem_t lastweapon;
	int power_cubes; // used for tracking the cubes in coop games
	int score; // for calculating total unit score in coop games
	int game_helpchanged;
	int helpchanged;
	boolean spectator; // client is a spectator

	public void load(ByteBuffer bb) throws IOException {
		// client persistant_t

		userinfo = Lib.readString(bb, Defines.MAX_INFO_STRING);
		netname = Lib.readString(bb, 16);

		hand = bb.getInt();

		connected = bb.getInt() != 0;
		health = bb.getInt();

		max_health = bb.getInt();
		savedFlags = bb.getInt();
		selected_item = bb.getInt();

		for (int n = 0; n < Defines.MAX_ITEMS; n++)
			inventory[n] = bb.getInt();

		max_bullets = bb.getInt();
		max_shells = bb.getInt();
		max_rockets = bb.getInt();
		max_grenades = bb.getInt();
		max_cells = bb.getInt();
		max_slugs = bb.getInt();

		weapon = GameAI.itemlist[bb.getInt()];
		lastweapon = GameAI.itemlist[bb.getInt()];
		power_cubes = bb.getInt();
		score = bb.getInt();

		game_helpchanged = bb.getInt();
		helpchanged = bb.getInt();
		spectator = bb.getInt() != 0;

	}

	public void dump() {
		// client persistant_t

		System.out.println("userinfo: " + userinfo);
		System.out.println("netname: " + netname);

		System.out.println("hand: " + hand);

		System.out.println("connected: " + connected);
		System.out.println("health: " + health);

		System.out.println("max_health: " + max_health);
		System.out.println("savedFlags: " + savedFlags);
		System.out.println("selected_item: " + selected_item);

		for (int n = 0; n < Defines.MAX_ITEMS; n++)
			System.out.println("inventory[" + n + "]: " + inventory[n]);

		System.out.println("max_bullets: " + max_bullets);
		System.out.println("max_shells: " + max_shells);
		System.out.println("max_rockets: " + max_rockets);
		System.out.println("max_grenades: " + max_grenades);
		System.out.println("max_cells: " + max_cells);
		System.out.println("max_slugs: " + max_slugs);
		System.out.println("weapon: " + weapon);
		System.out.println("lastweapon: " + lastweapon);
		System.out.println("powercubes: " + power_cubes);
		System.out.println("score: " + score);

		System.out.println("gamehelpchanged: " + game_helpchanged);
		System.out.println("helpchanged: " + helpchanged);
		System.out.println("spectator: " + spectator);
	}
}