package org.demoth.cake.automation

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.viewport.ScreenViewport
import jake2.qcommon.Globals
import jake2.qcommon.MainCommon
import org.demoth.cake.ui.menu.MenuIntent
import org.demoth.cake.ui.menu.MenuScreen
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

@EnabledIfEnvironmentVariable(named = "CAKE_VIDEO_GL_TESTS", matches = "1")
class AutomationUiGlTest {
    @Test
    fun `ID clicks use current stage input and reject unavailable targets`() {
        var failure: Throwable? = null
        Lwjgl3Application(object : ApplicationAdapter() {
            override fun create() {
                try { verifyDriver() } catch (error: Throwable) { failure = error }
                finally { Gdx.app.exit() }
            }
        }, Lwjgl3ApplicationConfiguration().apply {
            setInitialVisible(false)
            disableAudio(true)
            setWindowedMode(640, 480)
        })
        failure?.let { throw it }
    }

    private fun verifyDriver() {
        MainCommon.registerCvars(false)
        val developer = Globals.developer.typedValue
        fun stage() = Stage(ScreenViewport().apply { update(Gdx.graphics.width, Gdx.graphics.height, true) })
        val first = stage()
        val second = stage()
        var active: Stage? = first
        var navigation: MenuIntent? = null
        var changes = 0
        fun button() = Button(Button.ButtonStyle()).apply {
            name = "action"
            setBounds(40f, 40f, 120f, 40f)
            addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent, actor: Actor) { changes++ }
            })
        }
        val oldButton = button()
        first.addActor(oldButton)
        val ui = AutomationUi({ active }, { navigation = it })
        try {
            Globals.developer.forceSet(false)
            assertThrows(IllegalStateException::class.java) { ui.click("action") }
            assertThrows(IllegalStateException::class.java) { ui.open(MenuScreen.SAVE_GAME) }
            Globals.developer.forceSet(true)
            ui.open(MenuScreen.SAVE_GAME)
            assertEquals(MenuIntent.OpenSaveGame, navigation)
            ui.click("action")
            assertTrue(oldButton.isChecked, "A real down/up must toggle the button")
            assertEquals(1, changes)

            assertNull(ui.find("missing"))
            assertThrows(IllegalStateException::class.java) { ui.click("missing") }
            val duplicate = Actor().apply { name = "action"; isVisible = false }
            first.addActor(duplicate)
            assertThrows(IllegalStateException::class.java) { ui.element("action") }
            duplicate.remove()

            val parent = Group()
            oldButton.remove()
            parent.addActor(oldButton)
            first.addActor(parent)
            parent.isVisible = false
            assertThrows(IllegalStateException::class.java) { ui.click("action") }
            parent.isVisible = true
            oldButton.isDisabled = true
            assertThrows(IllegalStateException::class.java) { ui.click("action") }
            oldButton.isDisabled = false
            parent.touchable = Touchable.disabled
            assertThrows(IllegalStateException::class.java) { ui.click("action") }
            parent.touchable = Touchable.childrenOnly

            val cover = Actor().apply { setBounds(40f, 40f, 120f, 40f) }
            first.addActor(cover)
            assertThrows(IllegalStateException::class.java) { ui.click("action") }
            cover.remove()
            oldButton.setPosition(-1000f, 40f)
            assertThrows(IllegalStateException::class.java) { ui.click("action") }
            oldButton.setPosition(40f, 40f)
            val passive = Actor().apply { name = "passive"; setBounds(240f, 40f, 80f, 40f) }
            first.addActor(passive)
            assertThrows(IllegalStateException::class.java) { ui.click("passive") }
            assertEquals(1, changes, "Rejected clicks must not activate the button")

            val rebuiltButton = button()
            second.addActor(rebuiltButton)
            active = second
            assertSame(rebuiltButton, ui.element("action"))
            ui.click("action")
            assertTrue(rebuiltButton.isChecked)
            assertTrue(oldButton.isChecked, "Switching stages must not click a cached actor")
            assertEquals(2, changes)
            active = null
            assertThrows(IllegalStateException::class.java) { ui.find("action") }
            Globals.developer.forceSet(false)
            active = second
            assertThrows(IllegalStateException::class.java) { ui.click("action") }
        } finally {
            Globals.developer.forceSet(developer)
            first.dispose()
            second.dispose()
        }
    }
}
