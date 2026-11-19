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
    fun `live provider follows registry reset without retaining stale handles`() {
        val cvars = Cvar()
        val settings = NetworkSettings.registerCvars(cvars) { -1 }
        assertEquals(65535, settings.qport)
        cvars.clear()
        NetworkSettings.registerCvars(cvars) { 7 }
        assertEquals(7, settings.qport)
        assertEquals(Defines.PORT_SERVER, settings.serverPort)
    }

    @Test
    fun `owners bind through independent injected registries`() {
        val a = Cvar()
        val b = Cvar()
        val settingsA = NetworkSettings.registerCvars(a) { 11 }
        val settingsB = NetworkSettings.registerCvars(b) { 22 }
        a.forceSetResult("ip", "127.0.0.1")
        b.forceSetResult("ip", "127.0.0.1")
        a.forceSetResult("port", "0")
        b.forceSetResult("port", "0")
        LoopbackLink().use { linkA -> LoopbackLink().use { linkB ->
            val transportA = NetworkTransport(linkA.second, linkA.first.localAddress)
            val transportB = NetworkTransport(linkB.second, linkB.first.localAddress)
            PacketNetwork(transportA, NetworkRole.SERVER, settingsA).use { networkA ->
                PacketNetwork(transportB, NetworkRole.SERVER, settingsB).use { networkB ->
                    assertTrue(networkA.configure(true))
                    assertTrue(networkB.configure(true))
                    assertNotEquals(transportA.udpEndpoint!!.localAddress, transportB.udpEndpoint!!.localAddress)
                    networkA.configure(false)
                    assertTrue(transportB.udpEndpoint!!.isOpen)
                    assertEquals(11, settingsA.qport)
                    assertEquals(22, settingsB.qport)
                }
            }
        } }
    }
}
