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

// Created on 13.01.2004 by RST.

package jake2.server;

import jake2.qcommon.Defines;
import jake2.qcommon.edict_t;
import jake2.qcommon.network.messages.NetworkMessage;
import jake2.qcommon.network.netchan_t;
import jake2.qcommon.usercmd_t;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Server side representation of client.
 */
public class client_t {

	public client_t() {
		for (int n = 0; n < Defines.UPDATE_BACKUP; n++) {
			frames[n] = new client_frame_t();
		}
	}

	public static final int LATENCY_COUNTS = 16;
	public static final int RATE_MESSAGES = 10;

	ClientStates state = ClientStates.CS_FREE;

	String userinfo = "";

	String gameName;

	// for delta compression
	int lastReceivedFrame;
	usercmd_t lastcmd = new usercmd_t(); // for filling in big drops

	int commandMsec; // every seconds this is reset, if user
	// commands exhaust it, assume time cheating

	int frame_latency[] = new int[LATENCY_COUNTS];
	int ping;

	int message_size[] = new int[RATE_MESSAGES]; // used to rate drop packets
	int rate;
	int surpressCount; // number of messages rate supressed

	// pointer
	edict_t edict; // EDICT_NUM(clientnum+1)

	//char				name[32];			// extracted from userinfo, high bits masked
	String name = ""; // extracted from userinfo, high bits masked

	int messagelevel; // for filtering printed messages

	// This collection (the datagram) is written to by sound calls, prints, temp ents, etc.
	// It can be harmlessly overflowed.
	Collection<NetworkMessage> unreliable = new ArrayList<>();

	client_frame_t frames[] = new client_frame_t[Defines.UPDATE_BACKUP]; // updates can be delta'd from here

	byte download[]; // file being downloaded
	int downloadsize; // total bytes (can't use EOF because of paks)
	int downloadcount; // bytes sent

	int lastmessage; // sv.framenum when packet was last received
	int lastconnect;

	int challenge; // challenge of this user, randomly generated

	netchan_t netchan = new netchan_t();
}
