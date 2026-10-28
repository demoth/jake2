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
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.exec.CvarTypes;
import jake2.qcommon.exec.cvar_t;
import jake2.qcommon.sys.Timer;

/**
 * Netchan
 */
public final class Netchan {

    /*
     * packet header
     * -------------
     * 1 does this message contains a reliable payload
     * 31 sequence
     * 1 acknowledge receipt of * even/odd message
     * 31 acknowledge sequence
     * 16 qport
     * 
     * The remote connection never knows if it missed a reliable message, the
     * local side detects that it has been dropped by seeing a sequence
     * acknowledge higher thatn the last reliable sequence, but without the
     * correct even/odd bit for the reliable set.
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
     * updated after accepting the sequenced packet and before sending out any replies.
     * 
     * 
     * If there is no information that needs to be transfered on a given frame,
     * such as during the connection stage while waiting for the client to load,
     * then a packet only needs to be delivered if there is something in the
     * unacknowledged reliable
     */

    public static cvar_t<Boolean> showpackets;

    public static cvar_t<Boolean> showdrop;

    public static cvar_t<Integer> qport;

    public static cvar_t<Boolean> printNetwork;

    /**
     * Netchan_Init.
     * 
     */
    public static void Netchan_Init() {

        Cvar cvars = Cvar.getInstance();
        NetworkSettings.registerCvars();
        showpackets = cvars.register("showpackets", CvarTypes.BOOLEAN, false, 0, "Log packet sizes, sequences and acknowledgements");
        showdrop = cvars.register("showdrop", CvarTypes.BOOLEAN, false, 0, "Log dropped or out-of-order packets");
        printNetwork = cvars.register("sv_print_network", CvarTypes.BOOLEAN, false, 0, "Log decoded incoming network messages");
        // Reinitializing channels retains the process's original random qport declaration.
        qport = cvars.isRegistered("qport") ? cvars.find("qport", Integer.class)
                : cvars.register("qport", new CvarTypes.IntegerType(0, 65535),
                        Timer.Milliseconds() & 0xffff, Defines.CVAR_NOSET,
                        "Client connection identifier for NAT port changes; not a UDP socket port");
    }




}
