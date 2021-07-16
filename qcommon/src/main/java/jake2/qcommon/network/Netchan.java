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
package jake2.qcommon.network;

import jake2.qcommon.Defines;
import jake2.qcommon.Globals;
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.exec.cvar_t;
import jake2.qcommon.network.messages.ConnectionlessCommand;
import jake2.qcommon.sizebuf_t;
import jake2.qcommon.sys.Timer;
import jake2.qcommon.util.Lib;

/**
 * Netchan
 */
public final class Netchan {

    /*
     * packet header
     * -------------
     * 31 sequence
     * 1 does this message contains a reliable payload
     * 31 acknowledge sequence
     * 1 acknowledge receipt of * even/odd message
     * 16 qport
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
     * netcon (Connectionless/out-of-band packet)
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

    /**
     * Netchan_Init.
     * 
     */
    public static void Netchan_Init() {

        // pick a port value that should be nice and random
        long port = Timer.Milliseconds() & 0xffff;

        showpackets = Cvar.getInstance().Get("showpackets", "0", 0);
        showdrop = Cvar.getInstance().Get("showdrop", "0", 0);
        qport = Cvar.getInstance().Get("qport", "" + port, Defines.CVAR_NOSET);
    }

    /**
     * Out Of Band
     */
    public static void sendConnectionlessPacket(int net_socket, netadr_t adr, ConnectionlessCommand cmd, String payload) {
        String msg = cmd.name() + payload;

        sizebuf_t packet = new sizebuf_t();
        byte[] send_buf = new byte[Defines.MAX_MSGLEN];
        packet.init(send_buf, Defines.MAX_MSGLEN);

        // write the packet header
        packet.writeInt(-1); // -1 sequence means connectionless (out of band)
        packet.writeBytes(Lib.stringToBytes(msg), msg.length());

        // send the datagram
        NET.SendPacket(net_socket, packet.cursize, packet.data, adr);
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
    }


}
