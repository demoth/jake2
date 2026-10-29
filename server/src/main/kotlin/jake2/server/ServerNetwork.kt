package jake2.server

import jake2.qcommon.network.*
import jake2.qcommon.exec.Cvar
import jake2.qcommon.network.transport.NetworkAddress
import jake2.qcommon.network.transport.AddressResolver
import jake2.qcommon.network.messages.ConnectionlessCommand
import jake2.qcommon.network.messages.NetworkPacket
import jake2.qcommon.network.runtime.NetworkTransport
import jake2.qcommon.network.runtime.PacketNetwork
import jake2.qcommon.network.runtime.NetworkRole
import jake2.qcommon.network.transport.LocalSide

/** Server transport boundary. A stopped game retires UDP and incoming local traffic, not its peer. */
interface ServerNetwork : AutoCloseable {
    val logMessages: Boolean
    val localPeer: NetworkAddress.Local
    fun resolveAddress(text: String, defaultPort: Int): NetworkAddress? = AddressResolver.resolve(text, defaultPort, localPeer)
    fun configure(multiplayer: Boolean): Boolean
    fun newChannel(): ConnectionChannel
    fun poll(budget: Int = PacketNetwork.DEFAULT_POLL_BUDGET, consume: (NetworkPacket) -> Unit): Int
    fun sendConnectionless(to: NetworkAddress, command: ConnectionlessCommand, payload: String)
    fun stop()
    fun sleep(millis: Int)
    override fun close() = stop()
}

/** Owns the supplied transport; connections borrow it through the shared packet adapter. */
class TransportServerNetwork @JvmOverloads constructor(
    private val transport: NetworkTransport,
    private val settings: NetworkSettings = CvarNetworkSettings(Cvar.getInstance()),
) : ServerNetwork {
    companion object {
        @JvmStatic fun standalone() = TransportServerNetwork(
            NetworkTransport.standalone(LocalSide.SECOND))
    }
    private val packets = PacketNetwork(transport, NetworkRole.SERVER, settings)
    override val logMessages: Boolean get() = settings.logMessages
    override val localPeer: NetworkAddress.Local get() = packets.localPeer
    override fun newChannel() = packets.newChannel()
    override fun configure(multiplayer: Boolean) = packets.configure(multiplayer)
    override fun poll(budget: Int, consume: (NetworkPacket) -> Unit) = packets.poll(budget, consume)
    override fun sendConnectionless(to: NetworkAddress, command: ConnectionlessCommand, payload: String) =
        packets.sendConnectionless(to, command, payload)
    override fun stop() = packets.stop()
    override fun close() = packets.close()

    /** Dedicated tick wait: currently a timed sleep, not socket-readiness waiting. */
    override fun sleep(millis: Int) {
        if (transport.udpEndpoint == null) return
        try {
            Thread.sleep(millis.toLong())
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
