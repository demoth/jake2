package jake2.server

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import jake2.game.GameEntity
import jake2.game.GameExportsImpl
import jake2.qcommon.exec.Cbuf
import jake2.qcommon.exec.Cmd
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.find
import jake2.qcommon.vfs.EngineVfs
import jake2.qcommon.vfs.EngineWriteRoot
import org.demoth.cake.Cake
import org.demoth.cake.CakeStartupBootstrap
import org.demoth.cake.assets.CakeFileResolver
import org.demoth.cake.fullgame.EmbeddedCakeHost
import org.demoth.cake.profile.CakeGameProfile
import org.demoth.cake.profile.CakeGameProfileStore
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** Real client/save round trip, with read-only installed assets and temporary profiles/content. */
@EnabledIfEnvironmentVariable(named = "CAKE_HOST_GAME_TESTS", matches = "1")
class CakeProfileSaveGlTest {
    @TempDir lateinit var temp: Path

    @Test
    fun `mod resolution and console saves remain isolated across profile switches`() {
        val installation = Path.of(requireNotNull(System.getenv("CAKE_TEST_BASEDIR")))
        val content = Files.createDirectories(temp.resolve("content"))
        Files.createSymbolicLink(content.resolve("baseq2"), installation.resolve("baseq2"))
        val mod = Files.createDirectories(content.resolve("custom/maps"))
        val previousHome = System.getProperty("user.home")
        System.setProperty("user.home", temp.toString())
        val host = EmbeddedCakeHost(temp.resolve(".cake"))
        var failure: Throwable? = null
        try {
            CakeStartupBootstrap.ensureInitialized()
            // A mod-only map proves that the attached client and server both select the mod.
            val source = CakeFileResolver(basedir = installation.toString())
            Files.write(mod.resolve("profilemap.bsp"), source.resolve("maps/base1.bsp")!!.readBytes())
            CakeGameProfileStore().upsertProfile(CakeGameProfile("first", content.toString(), "custom"))
            val startup = CakeStartupBootstrap.bootstrap(arguments = listOf("+map", "profilemap"))
            Lwjgl3Application(object : ApplicationAdapter() {
                private lateinit var cake: Cake
                private var step = 0
                private var ticks = 0
                private var stageStart = System.nanoTime()
                private lateinit var firstSave: String
                private val firstSlot = temp.resolve(".cake/first/save/shared/game.ssv.json")
                private val secondSlot = temp.resolve(".cake/second/save/shared/game.ssv.json")

                override fun create() {
                    try {
                        cake = Cake(startup, host = host)
                        cake.create()
                    } catch (error: Throwable) { fail(error) }
                }

                private fun fail(error: Throwable) { failure = error; Gdx.app.exit() }
                private fun next(command: String) {
                    step++
                    ticks = 0
                    stageStart = System.nanoTime()
                    Cbuf.AddText(command)
                }
                private fun active(map: String) = ticks > 2 && state() == "ACTIVE" &&
                    Cvar.getInstance().find<String>("mapname").typedValue == map
                private fun state() = Cake::class.java.getDeclaredField("networkState").apply {
                    isAccessible = true
                }.get(cake).toString()
                private fun player() = (host.server!!.games.getValue("default").gameExports as GameExportsImpl).getEdict(1) as GameEntity

                override fun render() {
                    if (failure != null) return
                    try {
                        cake.render()
                        ticks++
                        assertTrue(System.nanoTime() - stageStart < 30_000_000_000L,
                            "Timed out at step $step, client=${state()}")
                        when (step) {
                            0 -> if (active("profilemap")) {
                                val resolver = Cake::class.java.getDeclaredField("fileResolver").apply {
                                    isAccessible = true
                                }.get(cake) as CakeFileResolver
                                assertEquals("custom", resolver.gamemod)
                                assertArrayEquals(EngineVfs.loadBytes("maps/profilemap.bsp"),
                                    resolver.resolve("maps/profilemap.bsp")!!.readBytes())
                                // Client-side remote mod selection must not mutate the hosted filesystem.
                                resolver.gamemod = null
                                assertTrue(EngineVfs.exists("maps/profilemap.bsp"))
                                resolver.gamemod = "custom"
                                Cmd.ExecuteString("cake_profile_set forbidden \"$content\"")
                                assertEquals(temp.resolve(".cake/first"), EngineWriteRoot.path())
                                player().health = 73
                                Cmd.ExecuteString("save shared")
                                firstSave = Files.readString(firstSlot)
                                next("gamemap base2")
                            }
                            1 -> if (active("base2")) next("load shared")
                            2 -> if (active("profilemap")) {
                                assertEquals(73, player().health)
                                next("disconnect")
                            }
                            3 -> {
                                assertEquals("DISCONNECTED", state())
                                Cmd.ExecuteString("cake_profile_set second \"$content\"")
                                next("gamemap *base2")
                            }
                            4 -> if (active("base2")) {
                                player().health = 41
                                Cmd.ExecuteString("save shared")
                                assertTrue(Files.exists(secondSlot))
                                assertEquals(firstSave, Files.readString(firstSlot))
                                next("gamemap base1")
                            }
                            5 -> if (active("base1")) next("load shared")
                            6 -> if (active("base2")) {
                                assertEquals(41, player().health)
                                next("disconnect")
                            }
                            7 -> {
                                Cmd.ExecuteString("cake_profile_set first \"$content\" custom")
                                // Idle load must attach directly, without starting a disposable map.
                                next("load shared")
                            }
                            8 -> if (active("profilemap")) {
                                assertEquals(73, player().health)
                                assertEquals(temp.resolve(".cake/first"), EngineWriteRoot.path())
                                Gdx.app.exit()
                            }
                        }
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
        } finally {
            host.dispose()
            System.setProperty("user.home", previousHome)
        }
    }
}
