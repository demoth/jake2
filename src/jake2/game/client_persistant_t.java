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
// $Id: client_persistant_t.java,v 1.11 2004-02-26 22:36:31 rst Exp $

package jake2.game;

import java.io.IOException;
import java.nio.ByteBuffer;

import jake2.*;
import jake2.*;
import jake2.util.Lib;

public class client_persistant_t implements Cloneable {

	public client_persistant_t getClone() {
		try {
			return (client_persistant_t) this.clone();
		}
		catch (CloneNotSupportedException e) {
			return null;
		}
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