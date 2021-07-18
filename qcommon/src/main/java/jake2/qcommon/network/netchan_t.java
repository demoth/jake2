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

import jake2.qcommon.Com;
import jake2.qcommon.Defines;
import jake2.qcommon.Globals;
import jake2.qcommon.network.messages.NetworkMessage;
import jake2.qcommon.network.messages.NetworkPacket;
import jake2.qcommon.sizebuf_t;

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

    // single bit, maintained locally, switched every time a reliable message is received.
    public int reliable_received_flag;
    // single bit, maintained locally, switched every time a reliable message is sent
    public int reliable_sent_flag;

    public int outgoing_sequence;

    public int last_reliable_sequence; // sequence number of last send

    /**
     * Reliable staging areas, appended during the frame update.
     * Overall size of the messages in bytes should not exceed Defines.MAX_MSGLEN - 16 (some space for header is reserved), otherwise connection will be closed.
     * See @{link jake2.qcommon.network.Netchan#Transmit}
     */
    public Collection<NetworkMessage> reliablePending = new ArrayList<>();

    //	   message is copied to this buffer when it is first transfered
    public sizebuf_t reliableUnacknowledged = new sizebuf_t(Defines.MAX_MSGLEN - 16);

    /**
     * Netchan_Setup is called to open a channel to a remote system.
     */
    public void setup(int sock, netadr_t adr, int qport) {
        clear();
        this.sock = sock;
        remote_address.set(adr);
        this.qport = qport;
        last_received = Globals.curtime;
        incoming_sequence = 0;
        outgoing_sequence = 1;
    }

    // Netchan.Process
    public boolean accept(NetworkPacket packet) {
        // unpack sequences and flags
        // achtung unsigned int
        boolean containsReliable = (packet.sequence >>> 31) != 0;
        int reliable_ack = packet.sequenceAck >>> 31; // single bit
        packet.sequence &= ~(1 << 31);
        packet.sequenceAck &= ~(1 << 31);

        //
        // discard stale or duplicated packets
        //
        if (packet.sequence <= incoming_sequence) {
            return false;
        }

        //
        // dropped packets don't keep the message from being used
        //
        dropped = packet.sequence - (incoming_sequence + 1);

        //
        // if the current outgoing reliable message has been acknowledged
        // clear the buffer to make way for the next
        //
        if (reliable_ack == reliable_sent_flag)
            reliableUnacknowledged.clear(); // it has been received

        //
        // if this message contains a reliable message, bump
        // incoming_reliable_sequence
        //
        incoming_sequence = packet.sequence;
        incoming_acknowledged = packet.sequenceAck;
        incoming_reliable_acknowledged = reliable_ack;
        if (containsReliable) {
            reliable_received_flag ^= 1;
        }

        //
        // the message can now be read from the current message pointer
        //
        last_received = (int) Globals.curtime;
        return true;
    }


    /**
     * `Netchan_Transmit` tries to send an unreliable message to a connection,
     * and handles the transmition / retransmition of the reliable messages.
     *
     * Reliable messages are kept in two separate areas - pending and unacknowledged.
     * Pending reliable messages are accumulated during the frame update.
     * Unacknowledged reliable messages - the messages that were already sent but not yet confirmed by the other side.
     * Once the messages become acknowledged, all pending messages are copied to the unacknowledged buffer and the process repeats.
     *
     * @param unreliable An empty or null collection will still generate a packet and deal with the reliable
     * messages.
     */
    public void transmit(Collection<NetworkMessage> unreliable) {

        // check for message overflow
        int pendingReliableSize = reliablePending.stream().mapToInt(NetworkMessage::getSize).sum();
        if (pendingReliableSize > Defines.MAX_MSGLEN - 16) {
            fatal_error = true;
            Com.Printf(remote_address.toString() + ":Outgoing message overflow\n");
            return;
        }

        // single bit
        int send_reliable = needReliable() ? 1 : 0;

        // If the last reliable message was acknowledged by the client (jake2.qcommon.network.netchan_t.accept),
        // send next portion of pending reliable messages (if there is something to send).
        // Otherwise, the contents of the reliableUnacknowledged will be retransmitted
        if (reliableUnacknowledged.cursize == 0 && reliablePending.size() != 0) {
            reliablePending.forEach(networkMessage -> networkMessage.writeTo(reliableUnacknowledged));
            reliablePending.clear();
            reliable_sent_flag ^= 1;
        }

        sizebuf_t packet = new sizebuf_t(Defines.MAX_MSGLEN);

        // write the packet header
        int sequence = (outgoing_sequence & ~(1 << 31)) | (send_reliable << 31);
        int sequenceAck = (incoming_sequence & ~(1 << 31)) | (reliable_received_flag << 31);

        outgoing_sequence++;
        last_sent = (int) Globals.curtime;

        // write header
        packet.writeInt(sequence);
        packet.writeInt(sequenceAck);

        // send the qport if we are a client
        if (sock == Defines.NS_CLIENT)
            packet.writeShort((int) Netchan.qport.value);

        // copy the reliable message to the packet first
        if (send_reliable != 0) {
            packet.writeBytes(reliableUnacknowledged.data, reliableUnacknowledged.cursize);
            last_reliable_sequence = outgoing_sequence;
        }

        if (unreliable != null) {
            // add the unreliable part if space is available
            int length = unreliable.stream().mapToInt(NetworkMessage::getSize).sum();
            if (packet.maxsize - packet.cursize >= length) {
                unreliable.forEach(msg -> msg.writeTo(packet));
            } else {
                Com.Printf("Netchan_Transmit: dumped unreliable\n");
            }
        }

        // send the datagram
        NET.SendPacket(sock, packet.cursize, packet.data, remote_address);

        if (Netchan.showpackets.value != 0) {
            Com.Printf(String.format("Netchan: %s | out: %6d | in: %6d | outr: %1d | inr: %1d \n", sock == 0 ? "client" : "server", outgoing_sequence - 1, incoming_sequence, send_reliable, reliable_received_flag));
        }

    }

    /**
     * Netchan_CanReliable. Returns true if the last reliable message has acked.
     */
    public boolean canReliable() {
        return reliableUnacknowledged.cursize == 0; // waiting for ack
    }

    /**
     * Netchan_NeedReliable
     */
    public boolean needReliable() {
        // if the remote side dropped the last reliable message, resend it
        boolean send_reliable = incoming_acknowledged > last_reliable_sequence && incoming_reliable_acknowledged != reliable_sent_flag;

        // if the reliable transmit buffer is empty, copy the current message out
        if (reliableUnacknowledged.cursize == 0 && reliablePending.size() != 0) {
            send_reliable = true;
        }

        return send_reliable;
    }

    //ok.
    public void clear() {
        sock = dropped = last_received = last_sent = 0;
        remote_address = new netadr_t();
        qport = incoming_sequence = incoming_acknowledged = incoming_reliable_acknowledged = reliable_received_flag = outgoing_sequence = reliable_sent_flag = last_reliable_sequence = 0;
        reliablePending.clear();
        reliableUnacknowledged.clear();
    }

}