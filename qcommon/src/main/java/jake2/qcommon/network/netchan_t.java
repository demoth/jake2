/*
 * Copyright (C) 1997-2001 Id Software, Inc.
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.
 * 
 * See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place - Suite 330, Boston, MA 02111-1307, USA.
 *  
 */

// Created on 27.11.2003 by RST.
// $Id: netchan_t.java,v 1.2 2004-09-22 19:22:09 salomo Exp $
package jake2.qcommon.network;

import jake2.qcommon.Defines;
import jake2.qcommon.network.messages.NetworkMessage;

import java.util.ArrayList;
import java.util.Collection;

public class netchan_t {

    public boolean fatal_error;

    // was enum {NS_CLIENT, NS_SERVER}
    public int sock;

    public int dropped; // between last packet and previous

    public int last_received; // for timeouts

    public int last_sent; // for retransmits

    public netadr_t remote_address = new netadr_t();

    public int qport; // qport value to write when transmitting

    // sequencing variables
    public int incoming_sequence;

    public int incoming_acknowledged;

    public int incoming_reliable_acknowledged; // single bit

    public int incoming_reliable_sequence; // single bit, maintained local

    public int outgoing_sequence;

    public int reliable_sequence; // single bit

    public int last_reliable_sequence; // sequence number of last send

    /**
     * Reliable staging and holding areas, writing buffer to send to server.
     * Overall size of the messages in bytes should not exceed Defines.MAX_MSGLEN - 16 (some space for header is reserved), otherwise connection will be closed.
     * See @{link jake2.qcommon.network.Netchan#Transmit}
     */
    public Collection<NetworkMessage> reliable = new ArrayList<>();

    //	   message is copied to this buffer when it is first transfered
    public int reliable_length;

    public byte reliable_buf[] = new byte[Defines.MAX_MSGLEN - 16]; // unpcked

    /**
     * Netchan_CanReliable. Returns true if the last reliable message has acked.
     */
    public boolean canReliable() {
        return reliable_length == 0; // waiting for ack
    }

    /**
     * Netchan_NeedReliable
     */
    public boolean needReliable() {
        // if the remote side dropped the last reliable message, resend it
        boolean send_reliable = incoming_acknowledged > last_reliable_sequence && incoming_reliable_acknowledged != reliable_sequence;

        // if the reliable transmit buffer is empty, copy the current message out
        if (reliable_length == 0 && reliable.size() != 0) {
            send_reliable = true;
        }

        return send_reliable;
    }

    //ok.
    public void clear() {
        sock = dropped = last_received = last_sent = 0;
        remote_address = new netadr_t();
        qport = incoming_sequence = incoming_acknowledged = incoming_reliable_acknowledged = incoming_reliable_sequence = outgoing_sequence = reliable_sequence = last_reliable_sequence = 0;
        reliable.clear();

        reliable_length = 0;
        reliable_buf = new byte[Defines.MAX_MSGLEN - 16];
    }

}