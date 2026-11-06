package jake2.server

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputMultiplexer
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
import org.demoth.cake.testing.HiddenGlTest
import org.demoth.cake.ui.sample.UiSkinSample
import org.demoth.cake.ui.menu.MenuScreen
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
        HiddenGlTest.run(home = temp) {
            CakeStartupBootstrap.ensureInitialized()
            Cvar.getInstance().clear()
            MainCommon.registerCvars(false)
            CakeCvars.registerAll()
            CakeGameProfileStore().upsertProfile(CakeGameProfile("first", temp.toString()))
            val startup = CakeStartupBootstrap.bootstrap(arguments = listOf("+set", "developer", "1"))
            val app = Cake(startup)
            onCleanup(app::dispose)
            app.create()
            val menu = activeInput()
            Cbuf.AddText("ui_sample engine\n")
            app.render()
            assertSame(menu, activeInput(), "Command requests open after this frame's menu pump")
            app.render()
            val sample = activeInput()
            assertInstanceOf(UiSkinSample::class.java, sample)
            assertSame(sample, app.automationUi.stage)
            assertNull(app.presentedMenuScreen)
            val oldAction = app.automationUi.element("sample-action")
            app.automationUi.click("sample-game")
            assertNotSame(oldAction, app.automationUi.element("sample-action"),
                "Theme rebuilds must resolve new controls with the same IDs")
            app.automationUi.click("sample-action")
            app.keyUp(Input.Keys.GRAVE)
            assertInstanceOf(ConsoleStage::class.java, activeInput())
            assertSame(activeInput(), app.automationUi.stage)
            app.keyUp(Input.Keys.GRAVE)
            assertSame(sample, activeInput())
            app.keyUp(Input.Keys.ESCAPE)
            assertSame(sample, activeInput(), "Escape requests close through the bus")
            app.render()
            assertSame(menu, activeInput())

            app.automationUi.open(MenuScreen.OPTIONS)
            app.render()
            assertEquals(MenuScreen.OPTIONS, app.presentedMenuScreen)

            Cmd.ExecuteString("ui_sample game")
            app.render()
            assertInstanceOf(UiSkinSample::class.java, activeInput())
            Cmd.ExecuteString("cake_profile_set second \"$temp\"")
            assertFalse(activeInput() is UiSkinSample, "Profile/style replacement closes synchronously")
            app.render()
            assertFalse(activeInput() is UiSkinSample)
        }
    }

    private fun activeInput() = (Gdx.input.inputProcessor as InputMultiplexer).processors[1]
}
