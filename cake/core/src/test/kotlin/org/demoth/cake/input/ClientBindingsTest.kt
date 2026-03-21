package org.demoth.cake.input

import com.badlogic.gdx.Input
import jake2.qcommon.exec.Cmd
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClientBindingsTest {

    @Test
    fun bindAndUnbindCommandsUpdateRuntimeBindings() {
        val bindings = ClientBindings(executeImmediate = {}, queueCommand = {})

        Cmd.ExecuteString("bind x use shotgun")
        assertEquals("use shotgun", bindings.getBindingByName("x"))

        Cmd.ExecuteString("unbind x")
        assertNull(bindings.getBindingByName("x"))
    }

    @Test
    fun keyAliasesAndListOutputAreSupported() {
        val bindings = ClientBindings(executeImmediate = {}, queueCommand = {})
        bindings.clearBindings()

        assertTrue(bindings.setBindingByName("uparrow", "+lookup"))
        assertTrue(bindings.setBindingByName("ctrl", "+attack"))
        assertTrue(bindings.setBindingByName("mwheelup", "weapnext"))

        val listed = bindings.listBindings().toMap()
        assertEquals("+lookup", listed["UPARROW"])
        assertEquals("+attack", listed["CTRL"])
        assertEquals("weapnext", listed["MWHEELUP"])
    }

    @Test
    fun configStyleBindCommandsCanReplaceDefaults() {
        val bindings = ClientBindings(executeImmediate = {}, queueCommand = {})

        assertEquals("+forward", bindings.getBindingByName("w"))

        Cmd.ExecuteString("unbindall")
        Cmd.ExecuteString("bind x use shotgun")

        assertNull(bindings.getBindingByName("w"))
        assertEquals("use shotgun", bindings.getBindingByName("x"))
    }

    @Test
    fun plusCommandsFireOnPressAndReleaseWithoutAutoRepeat() {
        val immediate = mutableListOf<String>()
        val queued = mutableListOf<String>()
        val bindings = ClientBindings(executeImmediate = immediate::add, queueCommand = queued::add)
        bindings.clearBindings()
        bindings.setBindingByName("x", "+forward")

        bindings.handleKeyDown(Input.Keys.X)
        bindings.handleKeyDown(Input.Keys.X) // auto-repeat should not trigger another +forward
        bindings.handleKeyUp(Input.Keys.X)

        assertEquals(listOf("+forward", "-forward"), immediate)
        assertTrue(queued.isEmpty())
    }

    @Test
    fun commandStyleBindingsExecuteOnKeyDownOnly() {
        val immediate = mutableListOf<String>()
        val queued = mutableListOf<String>()
        val bindings = ClientBindings(executeImmediate = immediate::add, queueCommand = queued::add)
        bindings.clearBindings()
        bindings.setBindingByName("x", "use shotgun")

        bindings.handleKeyDown(Input.Keys.X)
        bindings.handleKeyUp(Input.Keys.X)

        assertTrue(immediate.isEmpty())
        assertEquals(listOf("use shotgun"), queued)
    }

    @Test
    fun wheelExecutesTransientButtonCommands() {
        val immediate = mutableListOf<String>()
        val bindings = ClientBindings(executeImmediate = immediate::add, queueCommand = {})
        bindings.clearBindings()
        bindings.setBindingByName("mwheelup", "+attack")

        bindings.handleScroll(0f, -1f)

        assertEquals(listOf("+attack", "-attack"), immediate)
    }
}
