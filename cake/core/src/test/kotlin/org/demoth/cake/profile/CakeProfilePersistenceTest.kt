package org.demoth.cake.profile

import jake2.qcommon.MainCommon
import jake2.qcommon.exec.Cbuf
import jake2.qcommon.exec.Cmd
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.find
import jake2.qcommon.vfs.DefaultWritableFileSystem
import org.demoth.cake.CakeCvars
import org.demoth.cake.input.ClientBindings
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class CakeProfilePersistenceTest {
    @TempDir lateinit var temp: Path
    private val cvars = Cvar.getInstance()
    private lateinit var bindings: ClientBindings
    private lateinit var store: CakeGameProfileStore
    private lateinit var persistence: CakeProfilePersistence
    private var active: CakeGameProfile? = null

    @BeforeEach
    fun setUp() {
        Cmd.Init()
        Cvar.Init()
        cvars.clear()
        MainCommon.registerCvars(false)
        CakeCvars.registerAll()
        Cbuf.Execute()
        bindings = ClientBindings()
        store = CakeGameProfileStore(writableFactory = { DefaultWritableFileSystem(temp) })
        persistence = CakeProfilePersistence(store, bindings, { active },
            writableFactory = { DefaultWritableFileSystem(temp.resolve(it)) })
    }

    @Test
    fun `selected profile takes precedence and config history and paths stay isolated`() {
        active = CakeGameProfile("runtime", temp.toString(), "rogue")
        store.upsertProfile(CakeGameProfile("selected", temp.toString()))
        bindings.setBindingByName("F1", "say hello")
        persistence.saveConfig()
        persistence.saveHistory(listOf("connect localhost"))

        assertEquals("selected", persistence.activeProfileId())
        assertTrue(Files.readString(temp.resolve("selected/config.cfg")).contains("bind F1 \"say hello\""))
        assertEquals(listOf("connect localhost"), persistence.loadHistory())
        assertEquals(temp.resolve("selected/scrnshot/test.png").toString(),
            persistence.writable().resolveWritePath("scrnshot/test.png"))
        assertFalse(Files.exists(temp.resolve("runtime")))
        assertFalse(Files.exists(temp.resolve("rogue")))

        store.upsertProfile(CakeGameProfile("second", temp.toString()))
        assertTrue(persistence.loadHistory().isEmpty())
        persistence.saveHistory(listOf("status"))
        assertEquals(listOf("connect localhost"), Files.readAllLines(temp.resolve("selected/console-history.txt")))
        assertEquals(listOf("status"), persistence.loadHistory())
    }

    @Test
    fun `autosave suppresses config and history but explicit writeconfig still writes`() {
        active = CakeGameProfile("manual", temp.toString())
        cvars.find<Boolean>("cfg_autosave").set(false)
        persistence.saveConfig()
        persistence.saveHistory(listOf("status"))
        assertFalse(Files.exists(temp.resolve("manual")))

        persistence.saveConfig(explicit = true)
        assertTrue(Files.exists(temp.resolve("manual/config.cfg")))
        assertFalse(Files.exists(temp.resolve("manual/console-history.txt")))
    }

    @Test
    fun `missing selection falls back to runtime and default without creating default config`() {
        assertNull(persistence.selectedProfileId())
        assertEquals("default", persistence.activeProfileId())
        persistence.saveConfig(explicit = true)
        assertFalse(Files.exists(temp.resolve("default/config.cfg")))
        persistence.saveHistory(listOf("help"))
        assertEquals(listOf("help"), persistence.loadHistory())

        active = CakeGameProfile("runtime", temp.toString())
        Files.writeString(temp.resolve("profiles.json"), "invalid json")
        assertNull(persistence.selectedProfileId())
        assertEquals("runtime", persistence.activeProfileId())
        persistence.saveConfig()
        assertTrue(Files.exists(temp.resolve("runtime/config.cfg")))
    }

    @Test
    fun `loading config executes bindings and applies latched settings`() {
        active = CakeGameProfile("incoming", temp.toString())
        Files.createDirectories(temp.resolve("incoming"))
        Files.writeString(temp.resolve("incoming/config.cfg"),
            "unbindall\nbind F2 \"say loaded\"\nset name incoming\nset vid_windowed_width 1280\n")
        bindings.setBindingByName("F1", "old")
        persistence.loadConfig()

        assertNull(bindings.getBindingByName("F1"))
        assertEquals("say loaded", bindings.getBindingByName("F2"))
        assertEquals("incoming", cvars.find<String>("name").typedValue)
        assertEquals(1280, cvars.find<Int>("vid_windowed_width").typedValue)
        assertNull(cvars.find<Int>("vid_windowed_width").rawPendingString)
    }

    @Test
    fun `storage failures are contained and unreadable history returns empty`() {
        active = CakeGameProfile("broken", temp.toString())
        val failing = CakeProfilePersistence(store, bindings, { active },
            writableFactory = { error("Storage unavailable") })
        assertDoesNotThrow { failing.saveConfig() }
        assertDoesNotThrow { failing.loadConfig() }
        assertDoesNotThrow { failing.saveHistory(listOf("status")) }
        assertTrue(failing.loadHistory().isEmpty())
    }
}
