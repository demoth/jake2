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

package jake2.client;

import jake2.qcommon.netchan_t;
import java.io.File;

public class client_static_t {

	// was enum connstate_t 
	int state;

	// was enum keydest_t 
	int key_dest;

	int framecount;
	int realtime; // always increasing, no clamping, etc
	float frametime; // seconds since last frame

	//	   screen rendering information
	float disable_screen; // showing loading plaque between levels
	// or changing rendering dlls
	// if time gets > 30 seconds ahead, break it
	int disable_servercount; // when we receive a frame and cl.servercount
	// > cls.disable_servercount, clear disable_screen

	//	   connection information
	String servername; // name of server from original connect
	float connect_time; // for connection retransmits

	int quakePort; // a 16 bit value that allows quake servers
	// to work around address translating routers
	netchan_t netchan;
	int serverProtocol; // in case we are doing some kind of version hack

	int challenge; // from the server to use for connecting

	File download; // file transfer from server
	String downloadtempname;
	String downloadname;
	int downloadnumber;
	// was enum dltype_t 
	int downloadtype;
	int downloadpercent;

	//	   demo recording info must be here, so it isn't cleared on level change
	boolean demorecording;
	boolean demowaiting; // don't record until a non-delta message is received
	File demofile;
}
