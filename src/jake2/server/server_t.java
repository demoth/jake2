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

// Created on 14.01.2004 by RST.
// $Id: server_t.java,v 1.1 2004-01-14 21:23:57 rst Exp $

package jake2.server;


import java.io.File;

import jake2.*;
import jake2.client.*;
import jake2.game.*;
import jake2.qcommon.*;
import jake2.render.*;

public class server_t {
	//server_state_t	state;			// precache commands are only valid during load
	int	state;			// precache commands are only valid during load
	
	boolean	attractloop;		// running cinematics and demos for the local system only
	boolean	loadgame;			// client begins should reuse existing entity

	int		time;				// always sv.framenum * 100 msec
	int		framenum;

	//char 		name[MAX_QPATH];			// map name, or cinematic name
	String 		name;			// map name, or cinematic name
	
	//struct cmodel_s		*models[MAX_MODELS];
	cmodel_t		models[] = new cmodel_t[Defines.MAX_MODELS];

	//char		configstrings[MAX_CONFIGSTRINGS][MAX_QPATH];
	String 		configstrings[] = new String[Defines.MAX_CONFIGSTRINGS];
	entity_state_t	baselines[] = new entity_state_t[Defines.MAX_EDICTS];

	// the multicast buffer is used to send a message to a set of clients
	// it is only used to marshall data until SV_Multicast is called
	sizebuf_t	multicast;
	byte		multicast_buf[] = new byte[Defines.MAX_MSGLEN];

	// demo server information
	File		demofile;
	boolean	timedemo;		// don't time sync
}
