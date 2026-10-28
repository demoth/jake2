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
import jake2.qcommon.network.protocol.*;
import jake2.qcommon.network.transport.PacketBytes;
import jake2.qcommon.network.transport.SendResult;
import jake2.qcommon.network.runtime.NetworkTransport;
import jake2.qcommon.sizebuf_t;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.LongSupplier;

/** Legacy engine adapter. Protocol state and owned reliable bytes live in ReliableChannel. */
public class netchan_t {
    public boolean fatal_error;
    public int sock;
    public netadr_t remote_address = new netadr_t();
    public int qport;

    // Compatibility snapshots for existing engine consumers; never used as protocol storage.
    public int dropped;
    public int last_received;
    public int last_sent;
    public int incoming_sequence;
    public int incoming_acknowledged;
    public int incoming_reliable_acknowledged;
    public int reliable_received_flag;
    public int reliable_sent_flag;
    public int outgoing_sequence = 1;
    public int last_reliable_sequence;
    public int last_received_size;
    public int last_sent_size;
    public int smoothed_ping_ms;

    /** Engine message staging remains here until session controllers own message encoding. */
    public final Collection<NetworkMessage> reliablePending = new ArrayList<>();

    private final LongSupplier clockMillis;
    private final NetworkTransport transport;
    private ReliableChannel channel = new ReliableChannel(ChannelRole.CLIENT, 0);

    /**
     * The channel borrows its owner's transport; clearing a connection never closes it.
     * Supply both NetworkTransport and a clock explicitly instead of selecting a global transport.
     */
    public netchan_t(NetworkTransport transport, LongSupplier clockMillis) {
        this.transport = java.util.Objects.requireNonNull(transport);
        this.clockMillis = java.util.Objects.requireNonNull(clockMillis);
    }

    public void setup(int sock, netadr_t adr, int qport) {
        clear();
        this.sock = sock;
        remote_address.set(adr);
        this.qport = qport;
        channel = new ReliableChannel(sock == Defines.NS_CLIENT ? ChannelRole.CLIENT : ChannelRole.SERVER,
                qport & 0xffff);
        last_received = Globals.curtime;
        syncState();
    }

    public boolean accept(NetworkPacket packet) {
        if (packet.isConnectionless()) return false;
        ChannelAcceptResult result = channel.accept(packet.sequencedEnvelope(), clockMillis.getAsLong());
        // Keep the legacy message-processing view free of the reliable flag bits.
        packet.sequence &= Integer.MAX_VALUE;
        packet.sequenceAck &= Integer.MAX_VALUE;
        if (!(result instanceof ChannelAcceptResult.Accepted)) return false;
        last_received = Globals.curtime;
        syncState();
        return true;
    }

    /** Returns true only after the transport accepted the datagram. */
    public boolean transmit(Collection<NetworkMessage> unreliable) {
        if (reliablePending.stream().mapToLong(NetworkMessage::getSize).sum() > Defines.MAX_MSGLEN - 16) {
            fatal_error = true;
            Com.Printf(remote_address + ":Outgoing message overflow\n");
            return false;
        }
        boolean staged = false;
        boolean sent = false;
        try {
            if (channel.getCanSendReliable() && !reliablePending.isEmpty()) {
                PacketBytes reliable = encodeBatch(reliablePending, Defines.MAX_MSGLEN - 16);
                if (channel.enqueueReliable(reliable) != ReliableQueueResult.QUEUED) {
                    fatal_error = true;
                    return false;
                }
                staged = true;
            }
            PrepareSendResult result = channel.prepareSend((sequence, capacity) -> encodeBatch(unreliable, capacity));
            if (result instanceof PrepareSendResult.SequenceExhausted) {
                fatal_error = true;
                return false;
            }
            PrepareSendResult.Prepared transmission = (PrepareSendResult.Prepared) result;
            try {
                sent = send(transmission.getBytes()) instanceof SendResult.Sent;
            } finally {
                channel.completeSend(transmission, sent, clockMillis.getAsLong());
            }
            if (sent) {
                last_sent = Globals.curtime;
                if (transmission.getUnreliableDropped()) Com.Printf("Netchan_Transmit: dumped unreliable\n");
                if (Netchan.showpackets != null && Netchan.showpackets.getTypedValue()) {
                    Com.Printf(String.format("Netchan: %s | out: %6d | in: %6d | outr: %1d | inr: %1d \n",
                            sock == Defines.NS_CLIENT ? "client" : "server", transmission.getSequence(),
                            channel.getIncomingSequence(), transmission.getContainsReliable() ? 1 : 0,
                            channel.getReliableReceivedFlag()));
                }
            }
            return sent;
        } finally {
            if (staged) {
                if (sent) reliablePending.clear();
                else channel.discardPending(); // Leave unsent message staging available to existing cancellation paths.
            }
            syncState();
        }
    }

    private SendResult send(PacketBytes bytes) {
        try {
            return transport.send(remote_address.toNetworkAddress(transport.getLocalPeer()), bytes);
        } catch (java.io.IOException error) {
            return new SendResult.Failed(error);
        }
    }

    private static PacketBytes encodeBatch(Collection<NetworkMessage> messages, int capacity) {
        if (messages == null || messages.isEmpty()) return PacketBytes.copyOf(new byte[0]);
        if (messages.stream().mapToLong(NetworkMessage::getSize).sum() > capacity) return null;
        sizebuf_t buffer = new sizebuf_t(capacity);
        messages.forEach(message -> message.writeTo(buffer));
        return PacketBytes.copyOf(buffer.data, buffer.cursize);
    }

    public boolean canReliable() {
        return channel.getCanSendReliable();
    }

    public boolean needReliable() {
        return channel.getNeedsReliable() || (channel.getCanSendReliable() && !reliablePending.isEmpty());
    }

    public void clear() {
        fatal_error = false;
        sock = qport = last_received = last_sent = 0;
        remote_address = new netadr_t();
        reliablePending.clear();
        channel = new ReliableChannel(ChannelRole.CLIENT, 0);
        syncState();
    }

    private void syncState() {
        dropped = channel.getDropped();
        incoming_sequence = channel.getIncomingSequence();
        incoming_acknowledged = channel.getIncomingAcknowledged();
        incoming_reliable_acknowledged = channel.getIncomingReliableAcknowledged();
        reliable_received_flag = channel.getReliableReceivedFlag();
        reliable_sent_flag = channel.getReliableSentFlag();
        outgoing_sequence = channel.getOutgoingSequence();
        last_reliable_sequence = channel.getLastReliableSequence();
        last_received_size = channel.getLastReceivedSize();
        last_sent_size = channel.getLastSentSize();
        smoothed_ping_ms = channel.getSmoothedPingMillis();
    }
}
