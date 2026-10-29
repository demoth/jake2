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
package jake2.qcommon.network

import jake2.qcommon.Defines
import jake2.qcommon.network.messages.NetworkMessage
import jake2.qcommon.network.messages.NetworkPacket
import jake2.qcommon.network.protocol.*
import jake2.qcommon.network.transport.*
import jake2.qcommon.sizebuf_t
import java.io.IOException
import java.util.Collections
import java.util.function.LongSupplier

/**
 * Engine connection adapter, confined to its owner's game-loop thread. Protocol state and reliable
 * bytes belong to [ReliableChannel]. The sender borrows its owner's transport; clearing a connection
 * never closes it. Transport, monotonic clock, and diagnostics are supplied explicitly.
 */
class ConnectionChannel @JvmOverloads constructor(
    private val role: ChannelRole,
    private val localPeer: NetworkAddress.Local,
    private val sendPacket: (NetworkAddress, PacketBytes) -> SendResult,
    private val logPackets: () -> Boolean = { false },
    private val log: (String) -> Unit = {},
    private val clockMillis: LongSupplier,
) {
    private var channel = ReliableChannel(role, 0)
    private var destination: NetworkAddress = localPeer
    private val pending = ArrayList<NetworkMessage>()
    private var idleSinceMillis = clockMillis.asLong

    var fatalError = false
        private set
    var qport = 0
        private set

    // Read protocol state directly from the owned reliable channel.
    val incomingSequence: Int get() = channel.incomingSequence
    val incomingAcknowledged: Int get() = channel.incomingAcknowledged
    val outgoingSequence: Int get() = channel.outgoingSequence
    val reliableSentFlag: Int get() = channel.reliableSentFlag
    val dropped: Int get() = channel.dropped
    val lastReceivedSize: Int get() = channel.lastReceivedSize
    val lastSentSize: Int get() = channel.lastSentSize
    val lastSentMillis: Long get() = channel.lastSentMillis
    val smoothedPingMillis: Int get() = channel.smoothedPingMillis
    val canSendReliable: Boolean get() = channel.canSendReliable
    val needsReliable: Boolean get() = channel.needsReliable || (channel.canSendReliable && pending.isNotEmpty())

    /** Immutable peer address, including private local-link identity. */
    val remoteAddress: NetworkAddress get() = destination

    /** Engine staging remains here until session controllers own message encoding. No mutable list escapes. */
    fun queueReliable(message: NetworkMessage) { pending.add(message) }
    fun clearQueuedReliable() { pending.clear() }
    val hasQueuedReliable: Boolean get() = pending.isNotEmpty()
    val queuedReliableBytes: Long get() = pending.sumOf { it.size.toLong() }
    val queuedReliable: List<NetworkMessage> get() = Collections.unmodifiableList(ArrayList(pending))

    fun setup(address: NetworkAddress, qport: Int) {
        clear()
        destination = address
        this.qport = qport
        channel = ReliableChannel(role, qport and 0xffff)
    }

    /** Commit NAT source-port changes only after the router accepts the sequenced packet. */
    fun updateRemotePort(port: Int) {
        require(port in 0..65535)
        val remote = destination as? NetworkAddress.Udp ?: error("Local peers have no UDP port")
        destination = remote.copy(port = port)
    }

    /** Keepalive timing shares the injected monotonic clock with acknowledgements and RTT tracking. */
    fun hasBeenIdleFor(millis: Long): Boolean {
        require(millis >= 0)
        return clockMillis.asLong - idleSinceMillis > millis
    }

    fun accept(packet: NetworkPacket): Boolean {
        if (packet.isConnectionless) return false
        val result = channel.accept(packet.sequencedEnvelope(), clockMillis.asLong)
        // Keep the engine message-processing view free of reliable flag bits.
        packet.sequence = packet.sequence and Int.MAX_VALUE
        packet.sequenceAck = packet.sequenceAck and Int.MAX_VALUE
        return result is ChannelAcceptResult.Accepted
    }

    /** Returns true only after the transport accepted the datagram. */
    fun transmit(unreliable: Collection<NetworkMessage>?): Boolean {
        if (queuedReliableBytes > Defines.MAX_MSGLEN - 16) {
            fatalError = true
            log("$destination:Outgoing message overflow\n")
            return false
        }
        var staged = false
        var sent = false
        try {
            if (channel.canSendReliable && pending.isNotEmpty()) {
                val reliable = requireNotNull(encodeBatch(pending, Defines.MAX_MSGLEN - 16, null))
                if (channel.enqueueReliable(reliable) != ReliableQueueResult.QUEUED) {
                    fatalError = true
                    return false
                }
                staged = true
            }
            val result = channel.prepareSend { sequence, capacity -> encodeBatch(unreliable, capacity, sequence) }
            if (result is PrepareSendResult.SequenceExhausted) {
                fatalError = true
                return false
            }
            val transmission = result as PrepareSendResult.Prepared
            try {
                sent = send(transmission.bytes) is SendResult.Sent
            } finally {
                channel.completeSend(transmission, sent, clockMillis.asLong)
            }
            if (sent) {
                idleSinceMillis = channel.lastSentMillis
                if (transmission.unreliableDropped) log("Netchan_Transmit: dumped unreliable\n")
                if (logPackets()) log(String.format(
                    "Netchan: %s | out: %6d | in: %6d | outr: %1d | inr: %1d \n",
                    if (role == ChannelRole.CLIENT) "client" else "server", transmission.sequence,
                    channel.incomingSequence, if (transmission.containsReliable) 1 else 0, channel.reliableReceivedFlag))
            }
            return sent
        } finally {
            if (staged) {
                if (sent) pending.clear()
                else channel.discardPending() // Leave unsent staging available to cancellation paths.
            }
        }
    }

    private fun send(bytes: PacketBytes): SendResult = try {
        sendPacket(destination, bytes)
    } catch (error: IOException) {
        SendResult.Failed(error)
    }

    private fun encodeBatch(messages: Collection<NetworkMessage>?, capacity: Int, sequence: Int?): PacketBytes? {
        if (messages.isNullOrEmpty()) return PacketBytes.copyOf(byteArrayOf())
        if (messages.sumOf { it.size.toLong() } > capacity) return null
        val buffer = sizebuf_t(capacity)
        for (message in messages) {
            if (sequence == null) message.writeTo(buffer) else message.writeTo(buffer, sequence)
        }
        return PacketBytes.copyOf(buffer.data, buffer.cursize)
    }

    fun clear() {
        fatalError = false
        qport = 0
        destination = localPeer
        pending.clear()
        channel = ReliableChannel(role, 0)
        idleSinceMillis = clockMillis.asLong
    }
}
