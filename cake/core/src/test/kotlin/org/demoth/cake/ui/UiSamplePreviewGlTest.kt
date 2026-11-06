package org.demoth.cake.ui

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import jake2.qcommon.exec.Cmd
import org.demoth.cake.ui.sample.UiSamplePreview
import org.demoth.cake.ui.menu.MenuEventBus
import org.demoth.cake.ui.menu.MenuIntent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

@EnabledIfEnvironmentVariable(named = "CAKE_VIDEO_GL_TESTS", matches = "1")
class UiSamplePreviewGlTest {
    @Test
    fun `sample commands preserve modes and defer close until handled`() {
        var failure: Throwable? = null
        Lwjgl3Application(object : ApplicationAdapter() {
            override fun create() {
                try {
                    verifyPreview()
                } catch (error: Throwable) {
                    failure = error
                } finally {
                    Gdx.app.exit()
                }
            }
        }, Lwjgl3ApplicationConfiguration().apply {
            setInitialVisible(false)
            disableAudio(true)
            setWindowedMode(640, 480)
        })
        failure?.let { throw it }
    }

    private fun verifyPreview() {
        val skin = Skin(Gdx.files.internal("../../assets/ui/uiskin.json"))
        val style = EngineUiStyle(skin)
        val bus = MenuEventBus()
        val preview = UiSamplePreview(bus)
        // MenuController's intent-to-signal forwarding is covered by its own test.
        fun handleRequests() {
            bus.drainIntents { intent ->
                when (intent) {
                    is MenuIntent.OpenUiSample -> preview.open(intent.game, 720f, skin, style)
                    MenuIntent.CloseUiSample -> preview.close()
                    else -> error("Unexpected intent: $intent")
                }
            }
        }
        fun heading() = descendants((preview.inputProcessor as Stage).root)
            .filterIsInstance<Label>().first().text.toString()
        fun click(text: String) {
            descendants((preview.inputProcessor as Stage).root).filterIsInstance<TextButton>()
                .first { it.text.toString() == text }.fire(ChangeListener.ChangeEvent())
        }
        try {
            preview.registerCommands()
            Cmd.ExecuteString("ui_sample engine")
            assertFalse(preview.isOpen)
            handleRequests()
            assertTrue(preview.isOpen)
            val first = preview.inputProcessor as Stage
            click("Game")
            assertTrue(heading().startsWith("Game:"))
            bus.drainIntents { error("In-gallery mode switching must stay local: $it") }
            assertSame(first, preview.inputProcessor)
            preview.resize(800, 600)
            assertEquals(800, first.viewport.screenWidth)
            preview.setReferenceHeight(960f)
            assertEquals(960f, first.viewport.worldHeight)
            click("Close")
            assertTrue(preview.isOpen, "Close is deferred until the request is handled")
            handleRequests()
            assertFalse(preview.isOpen)
            assertNull(preview.inputProcessor)
            assertFalse(preview.close())
            Cmd.ExecuteString("ui_sample")
            handleRequests()
            assertTrue(heading().startsWith("Game:"), "Reopening remembers game mode")
            assertNotSame(first, preview.inputProcessor)
        } finally {
            preview.dispose()
            style.dispose()
            skin.dispose()
        }
    }

    private fun descendants(actor: Actor): List<Actor> =
        listOf(actor) + if (actor is Group) actor.children.flatMap(::descendants) else emptyList()
}
