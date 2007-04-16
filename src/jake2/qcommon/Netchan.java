/*
 * NetChannel.java
 * Copyright (C) 2003
 */
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
package jake2.qcommon;

import jake2.Defines;
import jake2.Globals;
import jake2.game.cvar_t;
import jake2.server.SV_MAIN;
import jake2.sys.NET;
import jake2.sys.Timer;
import jake2.util.Lib;

/**
 * Netchan
 */
public final class Netchan extends SV_MAIN {

    /*
     * 
     * packet header ------------- 31 sequence 1 does this message contains a
     * reliable payload 31 acknowledge sequence 1 acknowledge receipt of
     * even/odd message 16 qport
     * 
     * The remote connection never knows if it missed a reliable message, the
     * local side detects that it has been dropped by seeing a sequence
     * acknowledge higher thatn the last reliable sequence, but without the
     * correct evon/odd bit for the reliable set.
     * 
     * If the sender notices that a reliable message has been dropped, it will
     * be retransmitted. It will not be retransmitted again until a message
     * after the retransmit has been acknowledged and the reliable still failed
     * to get there.
     * 
     * if the sequence number is -1, the packet should be handled without a
     * netcon
     * 
     * The reliable message can be added to at any time by doing MSG_Write*
     * (&netchan.message, <data>).
     * 
     * If the message buffer is overflowed, either by a single message, or by
     * multiple frames worth piling up while the last reliable transmit goes
     * unacknowledged, the netchan signals a fatal error.
     * 
     * Reliable messages are always placed first in a packet, then the
     * unreliable message is included if there is sufficient room.
     * 
     * To the receiver, there is no distinction between the reliable and
     * unreliable parts of the message, they are just processed out as a single
     * larger message.
     * 
     * Illogical packet sequence numbers cause the packet to be dropped, but do
     * not kill the connection. This, combined with the tight window of valid
     * reliable acknowledgement numbers provides protection against malicious
     * address spoofing.
     * 
     * 
     * The qport field is a workaround for bad address translating routers that
     * sometimes remap the client's source port on a packet during gameplay.
     * 
     * If the base part of the net address matches and the qport matches, then
     * the channel matches even if the IP port differs. The IP port should be
     * updated to the new value before sending out any replies.
     * 
     * 
     * If there is no information that needs to be transfered on a given frame,
     * such as during the connection stage while waiting for the client to load,
     * then a packet only needs to be delivered if there is something in the
     * unacknowledged reliable
     */

    public static cvar_t showpackets;

    public static cvar_t showdrop;

    public static cvar_t qport;

    //public static netadr_t net_from = new netadr_t();
    public static sizebuf_t net_message = new sizebuf_t();

    public static byte net_message_buffer[] = new byte[Defines.MAX_MSGLEN];

    /**
     * Netchan_Init.
     * 
     */
    public static void Netchan_Init() {
        long port;

        // pick a port value that should be nice and random
        port = Timer.Milliseconds() & 0xffff;

        showpackets = Cvar.Get("showpackets", "0", 0);
        showdrop = Cvar.Get("showdrop", "0", 0);
        qport = Cvar.Get("qport", "" + port, Defines.CVAR_NOSET);
    }

    private static final byte send_buf[] = new byte[Defines.MAX_MSGLEN];
    private static final sizebuf_t send = new sizebuf_t();
    
    /**
     * Netchan_OutOfBand. Sends an out-of-band datagram.
     */
    public static void Netchan_OutOfBand(int net_socket, netadr_t adr,
            int length, byte data[]) {

        // write the packet header
        SZ.Init(send, send_buf, Defines.MAX_MSGLEN);

        MSG.WriteInt(send, -1); // -1 sequence means out of band
        SZ.Write(send, data, length);

        // send the datagram
        NET.SendPacket(net_socket, send.cursize, send.data, adr);
    }

    public static void OutOfBandPrint(int net_socket, netadr_t adr, String s) {
        Netchan_OutOfBand(net_socket, adr, s.length(), Lib.stringToBytes(s));
    }

    /**
     * Netchan_Setup is alled to open a channel to a remote system.
     */
    public static void Setup(int sock, netchan_t chan, netadr_t adr, int qport) {
        chan.clear();
        chan.sock = sock;
        chan.remote_address.set(adr);
        chan.qport = qport;
        chan.last_received = Globals.curtime;
        chan.incoming_sequence = 0;
        chan.outgoing_sequence = 1;

        SZ.Init(chan.message, chan.message_buf, chan.message_buf.length);
        chan.message.allowoverflow = true;
    }

    /**
     * Netchan_CanReliable. Returns true if the last reliable message has acked.
     */
    public static boolean Netchan_CanReliable(netchan_t chan) {
        if (chan.reliable_length != 0)
            return false; // waiting for ack
        return true;
    }

    
    public static boolean Netchan_NeedReliable(netchan_t chan) {
        boolean send_reliable;

        // if the remote side dropped the last reliable message, resend it
        send_reliable = false;

        if (chan.incoming_acknowledged > chan.last_reliable_sequence
                && chan.incoming_reliable_acknowledged != chan.reliable_sequence)
            send_reliable = true;

        // if the reliable transmit buffer is empty, copy the current message
        // out
        if (0 == chan.reliable_length && chan.message.cursize != 0) {
            send_reliable = true;
        }

        return send_reliable;
    }

    /**
     * Netchan_Transmit tries to send an unreliable message to a connection, 
     * and handles the transmition / retransmition of the reliable messages.
     * 
     * A 0 length will still generate a packet and deal with the reliable
     * messages.
     */
    public static void Transmit(netchan_t chan, int length, byte data[]) {
        int send_reliable;
        int w1, w2;

        // check for message overflow
        if (chan.message.overflowed) {
            chan.fatal_error = true;
            Com.Printf(NET.AdrToString(chan.remote_address)
                    + ":Outgoing message overflow\n");
            return;
        }

        send_reliable = Netchan_NeedReliable(chan) ? 1 : 0;

        if (chan.reliable_length == 0 && chan.message.cursize != 0) {
            System.arraycopy(chan.message_buf, 0, chan.reliable_buf, 0,
                    chan.message.cursize);
            chan.reliable_length = chan.message.cursize;
            chan.message.cursize = 0;
            chan.reliable_sequence ^= 1;
        }

        // write the packet header
        SZ.Init(send, send_buf, send_buf.length);

        w1 = (chan.outgoing_sequence & ~(1 << 31)) | (send_reliable << 31);
        w2 = (chan.incoming_sequence & ~(1 << 31))
                | (chan.incoming_reliable_sequence << 31);

        chan.outgoing_sequence++;
        chan.last_sent = (int) Globals.curtime;

        MSG.WriteInt(send, w1);
        MSG.WriteInt(send, w2);

        // send the qport if we are a client
        if (chan.sock == Defines.NS_CLIENT)
            MSG.WriteShort(send, (int) qport.value);

        // copy the reliable message to the packet first
        if (send_reliable != 0) {
            SZ.Write(send, chan.reliable_buf, chan.reliable_length);
            chan.last_reliable_sequence = chan.outgoing_sequence;
        }

        // add the unreliable part if space is available
        if (send.maxsize - send.cursize >= length)
            SZ.Write(send, data, length);
        else
            Com.Printf("Netchan_Transmit: dumped unreliable\n");

        // send the datagram
        NET.SendPacket(chan.sock, send.cursize, send.data, chan.remote_address);

        if (showpackets.value != 0) {
            if (send_reliable != 0)
                Com.Printf(
                        "send " + send.cursize + " : s="
                                + (chan.outgoing_sequence - 1) + " reliable="
                                + chan.reliable_sequence + " ack="
                                + chan.incoming_sequence + " rack="
                                + chan.incoming_reliable_sequence + "\n");
            else
                Com.Printf(
                        "send " + send.cursize + " : s="
                                + (chan.outgoing_sequence - 1) + " ack="
                                + chan.incoming_sequence + " rack="
                                + chan.incoming_reliable_sequence + "\n");
        }
    }

    /**
     * Netchan_Process is called when the current net_message is from remote_address modifies
     * net_message so that it points to the packet payload.
     */
    public static boolean Process(netchan_t chan, sizebuf_t msg) {
        // get sequence numbers
        MSG.BeginReading(msg);
        int sequence = MSG.ReadLong(msg);
        int sequence_ack = MSG.ReadLong(msg);

        // read the qport if we are a server
        if (chan.sock == Defines.NS_SERVER)
            MSG.ReadShort(msg);

        // achtung unsigned int
        int reliable_message = sequence >>> 31;
        int reliable_ack = sequence_ack >>> 31;

        sequence &= ~(1 << 31);
        sequence_ack &= ~(1 << 31);

        if (showpackets.value != 0) {
            if (reliable_message != 0)
                Com.Printf(
                        "recv " + msg.cursize + " : s=" + sequence
                                + " reliable="
                                + (chan.incoming_reliable_sequence ^ 1)
                                + " ack=" + sequence_ack + " rack="
                                + reliable_ack + "\n");
            else
                Com.Printf(
                        "recv " + msg.cursize + " : s=" + sequence + " ack="
                                + sequence_ack + " rack=" + reliable_ack + "\n");
        }

        //
        // discard stale or duplicated packets
        //
        if (sequence <= chan.incoming_sequence) {
            if (showdrop.value != 0)
                Com.Printf(NET.AdrToString(chan.remote_address)
                        + ":Out of order packet " + sequence + " at "
                        + chan.incoming_sequence + "\n");
            return false;
        }

        //
        // dropped packets don't keep the message from being used
        //
        chan.dropped = sequence - (chan.incoming_sequence + 1);
        if (chan.dropped > 0) {
            if (showdrop.value != 0)
                Com.Printf(NET.AdrToString(chan.remote_address) + ":Dropped "
                        + chan.dropped + " packets at " + sequence + "\n");
        }

        //
        // if the current outgoing reliable message has been acknowledged
        // clear the buffer to make way for the next
        //
        if (reliable_ack == chan.reliable_sequence)
            chan.reliable_length = 0; // it has been received

        //
        // if this message contains a reliable message, bump
        // incoming_reliable_sequence
        //
        chan.incoming_sequence = sequence;
        chan.incoming_acknowledged = sequence_ack;
        chan.incoming_reliable_acknowledged = reliable_ack;
        if (reliable_message != 0) {
            chan.incoming_reliable_sequence ^= 1;
        }

        //
        // the message can now be read from the current message pointer
        //
        chan.last_received = (int) Globals.curtime;

        return true;
    }
}