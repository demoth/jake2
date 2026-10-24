package org.demoth.cake.ui

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import ktx.scene2d.Scene2DSkin
import org.demoth.cake.assets.CakeFileResolver
import org.demoth.cake.stages.MenuStages
import org.demoth.cake.ui.menu.MenuEventBus
import org.demoth.cake.ui.menu.MenuIntent
import org.demoth.cake.ui.menu.MenuScreen
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

@EnabledIfEnvironmentVariable(named = "CAKE_VIDEO_GL_TESTS", matches = "1")
class MenuStagesGlTest {
    @Test
    fun `navigation and rebuild retain active screen and refresh browser presentation`() {
        var failure: Throwable? = null
        Lwjgl3Application(object : ApplicationAdapter() {
            override fun create() {
                try {
                    verifyMenuStages()
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

    private fun verifyMenuStages() {
        jake2.qcommon.MainCommon.registerCvars(false)
        org.demoth.cake.CakeCvars.registerAll()
        val skin = Skin(Gdx.files.internal("../../assets/ui/uiskin.json"))
        Scene2DSkin.defaultSkin = skin
        val style = EngineUiStyle(skin)
        val assets = AssetManager()
        val bus = MenuEventBus()
        val viewport = UiViewport().apply { update(640, 480, true) }
        val menus = MenuStages(viewport, bus, assets, CakeFileResolver(), false) { null }
        try {
            menus.rebuild(style)
            val main = menus.inputProcessor
            assertFalse(menus.select(MenuScreen.MAIN))
            assertSame(main, menus.inputProcessor)
            for (screen in MenuScreen.entries) {
                menus.select(screen)
                menus.render(1f / 60f)
            }

            menus.select(MenuScreen.CONTENT_BROWSER)
            val browser = menus.inputProcessor as Stage
            assertTrue(labels(browser.root).any { it.contains("Select a profile") })
            assertTrue(browser.keyUp(Input.Keys.ESCAPE))
            val intents = mutableListOf<MenuIntent>()
            bus.drainIntents { intents += it }
            assertTrue(intents.contains(MenuIntent.OpenMainMenu))
            // Input posts navigation; selection remains the controller/caller's decision.
            assertEquals(MenuScreen.CONTENT_BROWSER, menus.activeScreen)

            menus.select(MenuScreen.MAIN)
            assertFalse(labels(browser.root).any { it.contains("Select a profile") },
                "Leaving the browser detaches its content")
            menus.select(MenuScreen.CONTENT_BROWSER)
            assertTrue(labels(browser.root).any { it.contains("Select a profile") })

            menus.rebuild(style)
            assertEquals(MenuScreen.CONTENT_BROWSER, menus.activeScreen)
            assertNotSame(browser, menus.inputProcessor)
            assertTrue(labels((menus.inputProcessor as Stage).root).any { it.contains("Select a profile") })
            menus.render(1f / 60f)
            menus.select(MenuScreen.MAIN)
            assertNotSame(main, menus.inputProcessor)
            menus.render(1f / 60f)
        } finally {
            menus.dispose()
            style.dispose()
            assets.dispose()
            skin.dispose()
        }
    }

    private fun labels(actor: Actor): List<String> = when (actor) {
        is Label -> listOf(actor.text.toString())
        is Group -> actor.children.flatMap(::labels)
        else -> emptyList()
    }
}
