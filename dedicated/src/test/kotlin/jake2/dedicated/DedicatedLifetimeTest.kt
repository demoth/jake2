package jake2.dedicated

import jake2.qcommon.MainCommon
import jake2.qcommon.exec.Cbuf
import jake2.qcommon.exec.Cmd
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.find
import jake2.qcommon.network.NET
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

@Timeout(10)
class DedicatedLifetimeTest {
    @BeforeEach
    fun setup() {
        Cvar.getInstance().clear()
        MainCommon.registerCvars(true)
        Cmd.Init()
        jake2.qcommon.network.Netchan.Netchan_Init()
        Cvar.getInstance().find<Int>("port").forceSet(0)
    }

    @AfterEach
    fun cleanup() {
        Thread.interrupted()
        Cmd.RemoveCommand("test_start")
        Cmd.RemoveCommand("test_end")
        Cmd.RemoveCommand("test_drop")
        Cbuf.Execute()
        NET.configureServer(false)
        Cvar.getInstance().clear()
    }

    @Test
    fun `late startup failure closes Host and preserves original exception`() {
        val failure = IllegalStateException("startup failed")
        Cmd.AddCommand("test_start") {
            assertNotNull(Cmd.FindCommand("map"))
            assertTrue(NET.configureServer(true))
            throw failure
        }
        assertSame(failure, assertThrows(IllegalStateException::class.java) {
            Jake2Dedicated.runHost(listOf("Jake2", "+test_start"))
        })
        assertReleased()
    }

    @Test
    fun `loop command failure closes Host without swallowing programming errors`() {
        val failure = IllegalStateException("frame command failed")
        Cmd.AddCommand("test_start") { assertTrue(NET.configureServer(true)) }
        Cmd.AddCommand("test_end") { throw failure }
        assertSame(failure, assertThrows(IllegalStateException::class.java) {
            Jake2Dedicated.runHost(listOf("Jake2", "+test_start", "+wait", "+test_end"))
        })
        assertReleased()
    }

    @Test
    fun `output buffer failure unwinds the loop and closes Host`() {
        Cmd.AddCommand("test_start") { assertTrue(NET.configureServer(true)) }
        Cmd.AddCommand("test_end") { jake2.qcommon.sizebuf_t(1).writeInt(123) }
        val failure = assertThrows(IllegalStateException::class.java) {
            Jake2Dedicated.runHost(listOf("Jake2", "+test_start", "+wait", "+test_end"))
        }
        assertTrue(failure.message!!.contains("SZ_GetSpace"))
        assertReleased()
    }

    @Test
    fun `loop interruption closes Host and permits another dedicated lifetime`() {
        var starts = 0
        Cmd.AddCommand("test_start") {
            starts++
            assertNotNull(Cmd.FindCommand("map"))
            assertTrue(NET.configureServer(true))
        }
        Cmd.AddCommand("test_end") { Thread.currentThread().interrupt() }
        repeat(2) {
            Jake2Dedicated.runHost(listOf("Jake2", "+test_start", "+wait", "+test_end"))
            assertTrue(Thread.interrupted(), "Loop must preserve interrupt status for its caller")
            assertReleased()
        }
        assertEquals(2, starts)
    }

    @Test
    fun `legacy recoverable drop retains Host commands and continues loop`() {
        Cmd.AddCommand("test_start") { assertTrue(NET.configureServer(true)) }
        Cmd.AddCommand("test_drop") {
            jake2.qcommon.Com.Error(jake2.qcommon.Defines.ERR_DROP, "recoverable test failure")
        }
        var continued = false
        Cmd.AddCommand("test_end") {
            continued = true
            assertNotNull(Cmd.FindCommand("map"))
            assertNull(NET.udpEndpoint(jake2.qcommon.Defines.NS_SERVER))
            Thread.currentThread().interrupt()
        }
        Jake2Dedicated.runHost(listOf("Jake2", "+test_start", "+wait", "+test_drop", "+test_end"))
        assertTrue(continued)
        assertReleased()
    }

    private fun assertReleased() {
        assertNull(Cmd.FindCommand("map"))
        assertNull(Cmd.FindCommand("gamemap"))
        assertNull(NET.udpEndpoint(jake2.qcommon.Defines.NS_SERVER))
    }
}
