package jake2.server

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import jake2.game.GameEntity
import jake2.game.GameExportsImpl
import jake2.qcommon.exec.Cmd
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.find
import jake2.qcommon.vfs.EngineWriteRoot
import org.demoth.cake.Cake
import org.demoth.cake.CakeStartupBootstrap
import org.demoth.cake.fullgame.EmbeddedCakeHost
import org.demoth.cake.hosting.HostState
import org.demoth.cake.profile.CakeGameProfile
import org.demoth.cake.profile.CakeGameProfileStore
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Save and load run in separate JVMs; no engine state can survive between launches. */
@EnabledIfEnvironmentVariable(named = "CAKE_HOST_GAME_TESTS", matches = "1")
class CakeFreshLoadGlTest {
    @TempDir lateinit var temp: Path

    @Test
    fun `fresh launch loads selected profile directly and preserves source slot`() {
        val basedir = requireNotNull(System.getenv("CAKE_TEST_BASEDIR"))
        runApplication("save", basedir)
        val slot = temp.resolve(".cake/fresh/save/checkpoint")
        val savedFiles = Files.walk(slot).use { paths ->
            paths.filter(Files::isRegularFile).toList().associate { slot.relativize(it) to Files.readAllBytes(it) }
        }
        assertTrue(savedFiles.isNotEmpty())
        runApplication("load", basedir)
        for ((relative, bytes) in savedFiles) assertArrayEquals(bytes, Files.readAllBytes(slot.resolve(relative)),
            "Loading must preserve checkpoint/$relative")
    }

    private fun runApplication(mode: String, basedir: String) {
        val urls = generateSequence(javaClass.classLoader) { it.parent }
            .filterIsInstance<URLClassLoader>().flatMap { it.urLs.asSequence() }
            .map { Path.of(it.toURI()).toString() }.toList()
        val classpath = (urls + System.getProperty("java.class.path").split(java.io.File.pathSeparator))
            .distinct().joinToString(java.io.File.pathSeparator)
        val log = temp.resolve("$mode.log")
        val process = ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "--enable-native-access=ALL-UNNAMED", "-Duser.home=$temp", "-cp", classpath,
            CakeFreshLoadPeer::class.java.name, mode, basedir,
        ).redirectErrorStream(true).redirectOutput(log.toFile()).start()
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), "Fresh $mode timed out; see $log")
            assertEquals(0, process.exitValue(), "Fresh $mode failed:\n${Files.readString(log)}")
        } finally {
            if (process.isAlive) {
                process.destroy()
                if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly()
            }
        }
    }
}

/** Uses the fullgame composition and real bootstrap +commands, with hidden rendering for CI. */
object CakeFreshLoadPeer {
    @JvmStatic
    fun main(args: Array<String>) {
        val saving = args[0] == "save"
        if (saving) CakeGameProfileStore().upsertProfile(CakeGameProfile("fresh", args[1]))
        // The load process discovers the persisted selection, without recreating the profile.
        val startup = CakeStartupBootstrap.bootstrap(arguments = if (saving)
            listOf("+set", "skill", "3", "+map", "base1") else listOf("+load", "checkpoint"))
        val host = EmbeddedCakeHost()
        var failure: Throwable? = null
        var completed = false
        val deadline = System.nanoTime() + 45_000_000_000L
        try {
            Lwjgl3Application(object : ApplicationAdapter() {
                private lateinit var cake: Cake
                override fun create() {
                    try {
                        cake = Cake(startup, host = host)
                        cake.create()
                        check(host.state == HostState.STOPPED) // Startup command has not executed yet.
                        check(host.server!!.games.isEmpty())
                    } catch (error: Throwable) { fail(error) }
                }
                private fun fail(error: Throwable) { failure = error; Gdx.app.exit() }
                override fun render() {
                    if (failure != null || completed) return
                    try {
                        check(System.nanoTime() < deadline) { "Timed out waiting for fresh ${args[0]} attachment" }
                        cake.render()
                        val state = Cake::class.java.getDeclaredField("networkState").apply {
                            isAccessible = true
                        }.get(cake).toString()
                        if (state != "ACTIVE") return
                        check(Cvar.getInstance().find<String>("mapname").typedValue == "base1")
                        check(EngineWriteRoot.path() == Path.of(System.getProperty("user.home"), ".cake/fresh"))
                        val game = host.server!!.games.getValue("default").gameExports as GameExportsImpl
                        check(game.gameCvars.skill.typedValue == 3)
                        val player = game.getEdict(1) as GameEntity
                        if (saving) {
                            player.health = 73
                            Cmd.ExecuteString("save checkpoint")
                            check(Files.exists(EngineWriteRoot.path().resolve("save/checkpoint/game.ssv.json")))
                        } else {
                            check(player.health == 73) { "Loaded player health ${player.health}, expected 73" }
                        }
                        completed = true
                        Gdx.app.exit()
                    } catch (error: Throwable) { fail(error) }
                }
                override fun dispose() {
                    try { if (::cake.isInitialized) cake.dispose() }
                    catch (error: Throwable) { failure = failure ?: error }
                }
            }, Lwjgl3ApplicationConfiguration().apply {
                setInitialVisible(false)
                disableAudio(true)
                setWindowedMode(640, 480)
                setForegroundFPS(60)
                setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL32, 3, 2)
                setPauseWhenLostFocus(false)
                setPauseWhenMinimized(false)
            })
            failure?.let { throw it }
            check(completed) { "Application exited before verifying ${args[0]}" }
        } finally { host.dispose() }
    }
}
