package jake2.server

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import jake2.qcommon.MainCommon
import jake2.qcommon.exec.Cbuf
import jake2.qcommon.exec.Cmd
import jake2.qcommon.exec.Cvar
import org.demoth.cake.Cake
import org.demoth.cake.CakeCvars
import org.demoth.cake.CakeStartupBootstrap
import org.demoth.cake.profile.CakeGameProfile
import org.demoth.cake.profile.CakeGameProfileStore
import org.demoth.cake.stages.console.ConsoleStage
import org.demoth.cake.ui.sample.UiSkinSample
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@EnabledIfEnvironmentVariable(named = "CAKE_HOST_GL_TESTS", matches = "1")
class CakeUiSampleGlTest {
    @TempDir lateinit var temp: Path

    @Test
    fun `sample uses deferred menu transitions while style cleanup remains immediate`() {
        val previousHome = System.getProperty("user.home")
        System.setProperty("user.home", temp.toString())
        var failure: Throwable? = null
        try {
            CakeStartupBootstrap.ensureInitialized()
            Cvar.getInstance().clear()
            MainCommon.registerCvars(false)
            CakeCvars.registerAll()
            CakeGameProfileStore().upsertProfile(CakeGameProfile("first", temp.toString()))
            val startup = CakeStartupBootstrap.bootstrap()
            Lwjgl3Application(object : ApplicationAdapter() {
                private var cake: Cake? = null
                override fun create() {
                    try {
                        val app = Cake(startup).also { cake = it; it.create() }
                        val menu = activeInput()
                        Cbuf.AddText("ui_sample engine\n")
                        app.render()
                        assertSame(menu, activeInput(), "Command requests open after this frame's menu pump")
                        app.render()
                        val sample = activeInput()
                        assertInstanceOf(UiSkinSample::class.java, sample)
                        app.keyUp(Input.Keys.GRAVE)
                        assertInstanceOf(ConsoleStage::class.java, activeInput())
                        app.keyUp(Input.Keys.GRAVE)
                        assertSame(sample, activeInput())
                        app.keyUp(Input.Keys.ESCAPE)
                        assertSame(sample, activeInput(), "Escape requests close through the bus")
                        app.render()
                        assertSame(menu, activeInput())

                        Cmd.ExecuteString("ui_sample game")
                        app.render()
                        assertInstanceOf(UiSkinSample::class.java, activeInput())
                        Cmd.ExecuteString("cake_profile_set second \"$temp\"")
                        assertFalse(activeInput() is UiSkinSample, "Profile/style replacement closes synchronously")
                        app.render()
                        assertFalse(activeInput() is UiSkinSample)
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

    private fun activeInput() = (Gdx.input.inputProcessor as InputMultiplexer).processors[1]
}
