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

//Created on 31.10.2003 by RST.

package jake2.game;



public class client_persistant_t
{
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
	int inventory[]= new int[gamedefs.MAX_ITEMS];

	// ammo capacities
	int max_bullets;
	int max_shells;
	int max_rockets;
	int max_grenades;
	int max_cells;
	int max_slugs;

	gitem_t weapon;
	gitem_t lastweapon;

	int power_cubes; // used for tracking the cubes in coop games
	int score; // for calculating total unit score in coop games

	int game_helpchanged;
	int helpchanged;

	boolean spectator; // client is a spectator
}
