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

// Created on 02.01.2004 by RST.
// $Id: dnode_t.java,v 1.1 2004-01-02 17:40:54 rst Exp $

package jake2.qcommon;


// import jake2.*;
// import jake2.client.*;
// import jake2.game.*;
// import jake2.qcommon.*;
// import jake2.render.*;
// import jake2.server.*;

public class dnode_t {
	int		planenum;
	int		children[]= {0,0};	// negative numbers are -(leafs+1), not nodes
	short		mins[]= {0,0,0};		// for frustom culling
	short		maxs[]= {0,0,0};
	
	/*
	unsigned short	firstface;
	unsigned short	numfaces;	// counting both sides
	*/

	int		firstface;
	int		numfaces;	// counting both sides
}
