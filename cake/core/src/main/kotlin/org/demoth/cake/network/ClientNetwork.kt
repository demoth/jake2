package org.demoth.cake.network

import jake2.qcommon.network.*
import jake2.qcommon.network.messages.ConnectionlessCommand
import jake2.qcommon.network.messages.NetworkPacket
import jake2.qcommon.network.runtime.NetworkTransport
import jake2.qcommon.network.runtime.PacketNetwork
import jake2.qcommon.network.runtime.NetworkRole

/** Client transport boundary. A stopped game retires UDP and incoming local traffic, not its peer. */
interface ClientNetwork : AutoCloseable {
    fun configure(multiplayer: Boolean): Boolean
    fun newChannel(): netchan_t
    fun receive(): NetworkPacket?
    fun sendConnectionless(to: netadr_t, command: ConnectionlessCommand, payload: String)
    fun stop()
    override fun close() = stop()
}

/** Owns the supplied transport; connections borrow it through the shared packet adapter. */
class TransportClientNetwork(transport: NetworkTransport) : ClientNetwork {
    private val packets = PacketNetwork(transport, NetworkRole.CLIENT)
    override fun newChannel() = packets.newChannel()
    override fun configure(multiplayer: Boolean) = packets.configure(multiplayer)
    override fun receive() = packets.receive()
    override fun sendConnectionless(to: netadr_t, command: ConnectionlessCommand, payload: String) =
        packets.sendConnectionless(to, command, payload)
    override fun stop() = packets.stop()
    override fun close() = packets.close()
}
