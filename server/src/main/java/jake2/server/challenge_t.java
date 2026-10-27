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

package jake2.server;

import jake2.qcommon.network.netadr_t;

/** One entry in the Host's bounded connection-challenge cache; see ServerHost.getChallenge. */
public class challenge_t {
	netadr_t		adr = new netadr_t();
	int			challenge;
	// Host-local eviction order, not a timestamp or wire value; zero denotes an unused entry.
	long		issuedOrder;
}
