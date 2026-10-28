package jake2.server

import jake2.qcommon.Defines
import jake2.qcommon.network.*
import jake2.qcommon.network.messages.ConnectionlessCommand
import jake2.qcommon.network.messages.NetworkPacket
import jake2.qcommon.network.runtime.NetworkTransport
import jake2.qcommon.network.runtime.PacketNetwork
import jake2.qcommon.network.runtime.NetworkRole

/** Server transport boundary. A stopped game retires UDP and incoming local traffic, not its peer. */
interface ServerNetwork : AutoCloseable {
    fun configure(multiplayer: Boolean): Boolean
    fun newChannel(): netchan_t
    fun receive(): NetworkPacket?
    fun sendConnectionless(to: netadr_t, command: ConnectionlessCommand, payload: String)
    fun stop()
    fun sleep(millis: Int)
    override fun close() = stop()
}

/** Compatibility composition until application roots supply their own local link. */
@Deprecated("Compatibility adapter scheduled for removal; inject TransportServerNetwork with an owned NetworkTransport")
internal object LegacyServerNetwork : ServerNetwork {
    override fun configure(multiplayer: Boolean) = NET.configureServer(multiplayer)
    override fun newChannel() = netchan_t()
    override fun receive() = NET.receiveNetworkPacket(Defines.NS_SERVER)
    override fun sendConnectionless(to: netadr_t, command: ConnectionlessCommand, payload: String) =
        Netchan.sendConnectionlessPacket(Defines.NS_SERVER, to, command, payload)
    override fun stop() {
        NET.configureServer(false)
        NET.clearLoopback(Defines.NS_SERVER)
    }
    override fun sleep(millis: Int) = NET.Sleep(millis)
}

/** Owns the supplied transport; connections borrow it through the shared packet adapter. */
class TransportServerNetwork(private val transport: NetworkTransport) : ServerNetwork {
    private val packets = PacketNetwork(transport, NetworkRole.SERVER)
    override fun newChannel() = packets.newChannel()
    override fun configure(multiplayer: Boolean) = packets.configure(multiplayer)
    override fun receive() = packets.receive()
    override fun sendConnectionless(to: netadr_t, command: ConnectionlessCommand, payload: String) =
        packets.sendConnectionless(to, command, payload)
    override fun stop() = packets.stop()
    override fun close() = packets.close()

    override fun sleep(millis: Int) {
        if (transport.udpEndpoint == null) return
        try {
            Thread.sleep(millis.toLong())
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
