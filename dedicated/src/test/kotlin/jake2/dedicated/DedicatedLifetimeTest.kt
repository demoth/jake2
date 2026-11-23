package jake2.dedicated

import jake2.qcommon.MainCommon
import jake2.qcommon.exec.Cbuf
import jake2.qcommon.exec.Cmd
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.find
import jake2.qcommon.network.runtime.NetworkTransport
import jake2.qcommon.network.transport.LocalSide
import jake2.server.TransportServerNetwork
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** Dedicated owns its Host through startup, loop recovery, and shutdown. */
@Timeout(10)
class DedicatedLifetimeTest {
    private lateinit var transport: NetworkTransport
    private lateinit var network: TransportServerNetwork

    private fun runHost(args: List<String>) {
        transport = NetworkTransport.standalone(LocalSide.SECOND)
        network = TransportServerNetwork(transport)
        Jake2Dedicated.runHost(args, network)
    }
    @BeforeEach
    fun setup() {
        Cvar.getInstance().clear()
        MainCommon.registerCvars(true)
        Cmd.Init()
        jake2.qcommon.network.NetworkSettings.registerCvars()
        Cvar.getInstance().find<Int>("port").forceSet(0)
    }

    @AfterEach
    fun cleanup() {
        Thread.interrupted()
        Cmd.RemoveCommand("test_start")
        Cmd.RemoveCommand("test_end")
        Cbuf.Execute()
        if (::network.isInitialized) network.close()
        Cvar.getInstance().clear()
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `startup and loop failures close the Host and preserve the exception`(duringStartup: Boolean) {
        val failure = IllegalStateException("dedicated command failed")
        Cmd.AddCommand("test_start") {
            assertNotNull(Cmd.FindCommand("map"))
            assertTrue(network.configure(true))
            if (duringStartup) throw failure
        }
        Cmd.AddCommand("test_end") { throw failure }
        val arguments = if (duringStartup) listOf("Jake2", "+test_start")
            else listOf("Jake2", "+test_start", "+wait", "+test_end")
        assertSame(failure, assertThrows(IllegalStateException::class.java) { runHost(arguments) })
        assertReleased()
    }

    @Test
    fun `loop interruption closes Host and permits another dedicated lifetime`() {
        var starts = 0
        Cmd.AddCommand("test_start") {
            starts++
            assertNotNull(Cmd.FindCommand("map"))
            assertTrue(network.configure(true))
        }
        Cmd.AddCommand("test_end") { Thread.currentThread().interrupt() }
        repeat(2) {
            runHost(listOf("Jake2", "+test_start", "+wait", "+test_end"))
            assertTrue(Thread.interrupted(), "Loop must preserve interrupt status for its caller")
            assertReleased()
        }
        assertEquals(2, starts)
    }

    @Test
    fun `map rejection leaves the dedicated loop and transport available`() {
        Cmd.AddCommand("test_start") { assertTrue(network.configure(true)) }
        var continued = false
        Cmd.AddCommand("test_end") {
            continued = true
            assertNotNull(Cmd.FindCommand("map"))
            assertNotNull(transport.udpEndpoint)
            Thread.currentThread().interrupt()
        }
        runHost(listOf("Jake2", "+test_start", "+wait", "+gamemap", "__missing_dedicated_test_map__", "+test_end"))
        assertTrue(continued)
        assertReleased()
    }

    @Test
    fun `default dedicated composition exits and releases commands`() {
        Cmd.AddCommand("test_end") { Thread.currentThread().interrupt() }
        Jake2Dedicated.runHost(listOf("Jake2", "+test_end"))
        assertTrue(Thread.interrupted())
        assertNull(Cmd.FindCommand("map"))
    }

    private fun assertReleased() {
        assertNull(Cmd.FindCommand("map"))
        assertNull(Cmd.FindCommand("gamemap"))
        assertNull(transport.udpEndpoint)
    }
}
