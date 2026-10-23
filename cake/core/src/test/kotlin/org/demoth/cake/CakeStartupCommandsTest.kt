package org.demoth.cake

import jake2.qcommon.exec.Cbuf
import jake2.qcommon.exec.Cmd
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.find
import jake2.qcommon.vfs.DefaultWritableFileSystem
import org.demoth.cake.profile.CakeGameProfile
import org.demoth.cake.profile.CakeGameProfileStore
import org.demoth.cake.profile.CakeProfileConfigStore
import org.demoth.cake.profile.CakeProfilesConfig
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** Startup precedence and deferred commands, with profile files as the persistence boundary. */
class CakeStartupCommandsTest {
    @TempDir lateinit var temp: Path
    private val cvars = Cvar.getInstance()
    private fun profiles() = CakeGameProfileStore(writableFactory = { DefaultWritableFileSystem(temp) })
    private fun configs() = CakeProfileConfigStore(writableFactory = { DefaultWritableFileSystem(temp.resolve(it)) })

    @BeforeEach @AfterEach
    fun resetSettings() {
        CakeStartupBootstrap.ensureInitialized()
        Cbuf.Execute()
        cvars.find<Boolean>("cfg_autosave").forceSet(true)
        cvars.find<Boolean>("vid_hidden").forceSet(false)
        cvars.find<Int>("vid_windowed_width").forceSet(1024)
    }

    @Test fun commandLineOverridesProfileAndDefersCommandsUntilClientInitialization() {
        profiles().writeConfig(CakeProfilesConfig(profiles = listOf(CakeGameProfile("default", temp.toString()))))
        Files.createDirectories(temp.resolve("default"))
        val config = temp.resolve("default/config.cfg")
        Files.writeString(config, "set vid_windowed_width 800\n")
        val before = Files.readAllBytes(temp.resolve("profiles.json"))
        val context = CakeStartupBootstrap.bootstrap(profiles(), configs(), listOf(
            "+set", "cfg_autosave", "0", "+set", "vid_windowed_width", "1440",
            "+set", "vid_hidden", "1", "+startup_probe", "path with spaces/a-b+c",
        ))
        assertEquals(1440, context.videoMode.width)
        assertTrue(context.videoMode.hidden)
        assertFalse(cvars.find<Boolean>("cfg_autosave").typedValue)
        assertEquals(0, cvars.find<Boolean>("cfg_autosave").flags)
        assertEquals("", Cbuf.contents())
        var observed: List<String>? = null
        Cmd.AddCommand("startup_probe") { observed = it }
        try {
            Cbuf.AddLateCommands(context.startupCommands)
            Cbuf.Execute()
            assertEquals(listOf("startup_probe", "path with spaces/a-b+c"), observed)
        } finally { Cmd.RemoveCommand("startup_probe") }
        assertArrayEquals(before, Files.readAllBytes(temp.resolve("profiles.json")))
        assertEquals("set vid_windowed_width 800\n", Files.readString(config))
    }

    @Test fun autosaveControlPreventsFirstRunProfileCreationButKeepsDetectedContent() {
        val context = CakeStartupBootstrap.bootstrap(profiles(), configs(),
            listOf("+set", "cfg_autosave", "0"), autodetectBasedir = { temp.toString() })
        assertEquals(temp.toString(), context.activeProfile?.basedir)
        assertFalse(Files.exists(temp.resolve("profiles.json")))
    }

    @Test fun ordinaryStartupStillCreatesDefaultProfile() {
        CakeStartupBootstrap.bootstrap(profiles(), configs(), autodetectBasedir = { temp.toString() })
        assertEquals(temp.toString(), profiles().readSelected()?.basedir)
        assertTrue(cvars.find<Boolean>("cfg_autosave").typedValue)
    }
}
