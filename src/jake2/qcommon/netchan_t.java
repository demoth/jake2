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
// $Id: netchan_t.java,v 1.3 2003-11-29 19:26:33 rst Exp $

package jake2.qcommon;

import jake2.*;

public class netchan_t {


	public boolean fatal_error;

	// was enum {NS_CLIENT, NS_SERVER}
	public int sock;

	public int dropped; // between last packet and previous

	public int last_received; // for timeouts
	public int last_sent; // for retransmits

	public netadr_t remote_address;
	public int qport; // qport value to write when transmitting

	// sequencing variables
	public int incoming_sequence;
	public int incoming_acknowledged;
	public int incoming_reliable_acknowledged; // single bit

	public int incoming_reliable_sequence; // single bit, maintained local

	public int outgoing_sequence;
	public int reliable_sequence; // single bit
	public int last_reliable_sequence; // sequence number of last send

	//	   reliable staging and holding areas
	public sizebuf_t message; // writing buffer to send to server
	public byte message_buf[] = new byte[Defines.MAX_MSGLEN - 16]; // leave space for header

	//	   message is copied to this buffer when it is first transfered
	int reliable_length;
	byte reliable_buf[] = new byte[Defines.MAX_MSGLEN - 16]; // unacked reliable message

}
