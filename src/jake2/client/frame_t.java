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
// $Id: frame_t.java,v 1.7 2004-02-13 21:04:38 rst Exp $

package jake2.client;

import jake2.game.*;

public class frame_t implements Cloneable {
		
	public static final int MAX_MAP_AREAS = 256; 
		
	boolean		valid;			// cleared if delta parsing was invalid
	int				serverframe;
	int				servertime;		// server time the message is valid for (in msec)
	int				deltaframe;
	byte			areabits[] = new byte [MAX_MAP_AREAS/8];		// portalarea visibility bits
	public 		player_state_t playerstate = new player_state_t();
	int				num_entities;
	int				parse_entities;	// non-masked index into cl_parse_entities array
	
	public frame_t getClone()
	{
		frame_t out = null;
		
		try {
			out = (frame_t) this.clone();
			out.playerstate = playerstate.getClone();
		}
		catch (CloneNotSupportedException e) {
		}
		
		return out;
	}
	
	public void reset()
	{
		valid = false;
		serverframe = servertime = deltaframe = 0;
		areabits = new byte [MAX_MAP_AREAS/8];
		playerstate = new player_state_t();
		num_entities = parse_entities = 0;
	}	
}
