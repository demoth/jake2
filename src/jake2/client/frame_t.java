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
// $Id: frame_t.java,v 1.3 2004-01-30 13:05:46 cwei Exp $

package jake2.client;

import jake2.game.*;

public class frame_t {
		
	public static final int MAX_MAP_AREAS = 256; 
		
	boolean		valid;			// cleared if delta parsing was invalid
	int				serverframe;
	int				servertime;		// server time the message is valid for (in msec)
	int				deltaframe;
	byte			areabits[] = new byte [MAX_MAP_AREAS/8];		// portalarea visibility bits
	player_state_t playerstate = new player_state_t();
	int				num_entities;
	int				parse_entities;	// non-masked index into cl_parse_entities array
}
