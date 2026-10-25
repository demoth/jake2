package jake2.server

import org.demoth.cake.profile.CakeGameProfile
import org.demoth.cake.profile.CakeGameProfileStore

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import jake2.qcommon.MainCommon
import jake2.qcommon.exec.Cmd
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.find
import org.demoth.cake.Cake
import org.demoth.cake.CakeCvars
import org.demoth.cake.CakeStartupBootstrap
import org.demoth.cake.ui.menu.MenuEventBus
import org.demoth.cake.ui.menu.MenuIntent
import org.demoth.cake.ui.menu.ProfileFormState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@EnabledIfEnvironmentVariable(named = "CAKE_HOST_GL_TESTS", matches = "1")
class CakeProfileActivationGlTest {
    @TempDir lateinit var temp: Path

    @Test
    fun `console and menu activation preserve outgoing config and reject invalid profiles before applying`() {
        val previousHome = System.getProperty("user.home")
        System.setProperty("user.home", temp.toString())
        var failure: Throwable? = null
        try {
            CakeStartupBootstrap.ensureInitialized()
            Cvar.getInstance().clear()
            MainCommon.registerCvars(false)
            CakeCvars.registerAll()
            val content = Files.createDirectories(temp.resolve("content"))
            val store = CakeGameProfileStore()
            store.upsertProfile(CakeGameProfile("first", content.toString()))
            for (id in listOf("first", "second", "third")) {
                val directory = Files.createDirectories(temp.resolve(".cake/$id"))
                Files.writeString(directory.resolve("config.cfg"), "set name $id\n")
            }
            val startup = CakeStartupBootstrap.bootstrap()
            Lwjgl3Application(object : ApplicationAdapter() {
                private var cake: Cake? = null
                override fun create() {
                    try {
                        val app = Cake(startup).also { cake = it; it.create() }
                        val name = Cvar.getInstance().find<String>("name")
                        name.set("outgoing-first")
                        Cmd.ExecuteString("cake_profile_set second \"$content\"")
                        assertEquals("second", activeProfile(app).id)
                        assertEquals("second", store.readSelectedProfileId())
                        assertEquals("second", name.typedValue)
                        assertTrue(Files.readString(temp.resolve(".cake/first/config.cfg")).contains("outgoing-first"))

                        val bus = Cake::class.java.getDeclaredField("menuEventBus").apply {
                            isAccessible = true
                        }.get(app) as MenuEventBus
                        name.set("outgoing-second")
                        bus.postIntent(MenuIntent.SelectProfile("first"))
                        bus.postIntent(MenuIntent.ApplySelectedProfile)
                        app.render()
                        assertEquals("first", activeProfile(app).id)
                        assertEquals("outgoing-first", name.typedValue)
                        assertTrue(Files.readString(temp.resolve(".cake/second/config.cfg")).contains("outgoing-second"))

                        bus.postIntent(MenuIntent.CreateProfileDraft())
                        bus.postIntent(MenuIntent.SaveProfile(ProfileFormState("third", content.toString(), "")))
                        app.render()
                        assertEquals("third", activeProfile(app).id)
                        assertEquals("third", name.typedValue)

                        val before = activeProfile(app)
                        Cmd.ExecuteString("cake_profile_set invalid \"${temp.resolve("missing")}\"")
                        assertEquals(before, activeProfile(app))
                        assertEquals("third", store.readSelectedProfileId())
                        assertEquals("third", name.typedValue)
                    } catch (error: Throwable) {
                        failure = error
                    } finally {
                        Gdx.app.exit()
                    }
                }

                override fun dispose() {
                    try { cake?.dispose() } catch (error: Throwable) { failure = failure ?: error }
                }
            }, Lwjgl3ApplicationConfiguration().apply {
                setInitialVisible(false)
                disableAudio(true)
                setWindowedMode(640, 480)
                setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL32, 3, 2)
            })
            failure?.let { throw it }
        } finally {
            System.setProperty("user.home", previousHome)
        }
    }

    private fun activeProfile(cake: Cake): CakeGameProfile =
        Cake::class.java.getDeclaredField("activeGameProfile").apply {
            isAccessible = true
        }.get(cake) as CakeGameProfile
}
