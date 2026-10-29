package jake2.qcommon.network.runtime

import jake2.qcommon.Defines
import jake2.qcommon.exec.Cvar
import jake2.qcommon.network.CvarNetworkSettings
import jake2.qcommon.network.NetworkSettings
import jake2.qcommon.network.transport.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NetworkSettingsTest {
    @Test
    fun `registration consumes early values protects declarations and retains original qport seed`() {
        val cvars = Cvar()
        val settings = CvarNetworkSettings(cvars)
        assertFalse(settings.logPackets)
        assertFalse(settings.logDrops)
        assertFalse(settings.logMessages)
        assertFalse(cvars.isRegistered("port"), "Constructing settings must leave startup registration to its owner")
        cvars.setResult("ip", "127.0.0.1")
        cvars.setResult("port", "28000")
        cvars.setResult("clientport", "-1")
        cvars.setResult("qport", "65535")
        NetworkSettings.registerCvars(cvars) { 1234 }
        assertEquals("127.0.0.1", settings.bindAddress)
        assertEquals(28000, settings.serverPort)
        assertEquals(-1, settings.clientPort)
        assertEquals(65535, settings.qport)
        val declaredQport = cvars.find("qport", Int::class.javaObjectType)
        assertEquals(1234, declaredQport.defaultValue)
        assertEquals(Defines.CVAR_NOSET, declaredQport.flags)
        NetworkSettings.registerCvars(cvars) { error("Repeated registration must retain the original seed") }
        assertSame(declaredQport, cvars.find("qport", Int::class.javaObjectType))
        cvars.setResult("port", "29000")
        cvars.setResult("qport", "12")
        assertEquals(28000, settings.serverPort)
        assertEquals(65535, settings.qport)
        cvars.forceSetResult("qport", "65536")
        cvars.forceSetResult("port", "-2")
        assertEquals(65535, settings.qport)
        assertEquals(28000, settings.serverPort)
    }

    @Test
    fun `live provider follows edits and registry reset without retaining stale handles`() {
        val cvars = Cvar()
        val settings = NetworkSettings.registerCvars(cvars) { -1 }
        assertEquals(65535, settings.qport)
        cvars.setResult("showpackets", "true")
        cvars.setResult("showdrop", "true")
        cvars.setResult("sv_print_network", "true")
        assertTrue(settings.logPackets && settings.logDrops && settings.logMessages)
        cvars.clear()
        assertFalse(settings.logPackets || settings.logDrops || settings.logMessages)
        NetworkSettings.registerCvars(cvars) { 7 }
        assertEquals(7, settings.qport)
        assertEquals(Defines.PORT_SERVER, settings.serverPort)
        assertFalse(settings.logPackets || settings.logDrops || settings.logMessages)
    }

    @Test
    fun `owners bind and log through independent injected registries including live channel diagnostics`() {
        val a = Cvar()
        val b = Cvar()
        val settingsA = NetworkSettings.registerCvars(a) { 11 }
        val settingsB = NetworkSettings.registerCvars(b) { 22 }
        a.forceSetResult("ip", "127.0.0.1")
        b.forceSetResult("ip", "127.0.0.1")
        a.forceSetResult("port", "0")
        b.forceSetResult("port", "0")
        val logsA = mutableListOf<String>()
        val logsB = mutableListOf<String>()
        LoopbackLink().use { linkA -> LoopbackLink().use { linkB ->
            val transportA = NetworkTransport(linkA.second, linkA.first.localAddress)
            val transportB = NetworkTransport(linkB.second, linkB.first.localAddress)
            PacketNetwork(transportA, NetworkRole.SERVER, settingsA, logsA::add).use { networkA ->
                PacketNetwork(transportB, NetworkRole.SERVER, settingsB, logsB::add).use { networkB ->
                    assertTrue(networkA.configure(true))
                    assertTrue(networkB.configure(true))
                    assertNotEquals(transportA.udpEndpoint!!.localAddress, transportB.udpEndpoint!!.localAddress)
                    val channelA = networkA.newChannel().apply { setup(networkA.localPeer, settingsA.qport) }
                    val channelB = networkB.newChannel().apply { setup(networkB.localPeer, settingsB.qport) }
                    channelA.transmit(null); channelB.transmit(null)
                    assertTrue(logsA.isEmpty() && logsB.isEmpty())
                    a.setResult("showpackets", "true")
                    channelA.transmit(null); channelB.transmit(null)
                    assertTrue(logsA.single().contains("Netchan:"))
                    assertTrue(logsB.isEmpty())
                    logsA.clear()
                    a.setResult("showdrop", "true")
                    val malformed = PacketBytes.copyOf(byteArrayOf(1))
                    linkA.first.send(linkA.second.localAddress, malformed)
                    linkB.first.send(linkB.second.localAddress, malformed)
                    networkA.poll {}; networkB.poll {}
                    assertTrue(logsA.single().contains("dropped packet"))
                    assertTrue(logsB.isEmpty())
                    logsA.clear()
                    a.setResult("sv_print_network", "true")
                    val ping = PacketBytes.copyOf(byteArrayOf(-1, -1, -1, -1) + "ping".toByteArray())
                    linkA.first.send(linkA.second.localAddress, ping)
                    linkB.first.send(linkB.second.localAddress, ping)
                    networkA.poll {}; networkB.poll {}
                    assertTrue(logsA.single().contains("connectionless: ping"))
                    assertTrue(logsB.isEmpty())
                    networkA.configure(false)
                    assertTrue(transportB.udpEndpoint!!.isOpen)
                    assertEquals(11, settingsA.qport)
                    assertEquals(22, settingsB.qport)
                }
            }
        } }
    }
}
