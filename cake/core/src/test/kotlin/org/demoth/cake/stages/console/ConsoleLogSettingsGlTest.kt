package org.demoth.cake.stages.console

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.utils.viewport.ScreenViewport
import jake2.qcommon.Com.ConsoleLevel
import jake2.qcommon.Com.LogLevelOverride
import jake2.qcommon.Com.LogSubsystem
import jake2.qcommon.Globals
import jake2.qcommon.MainCommon
import ktx.scene2d.Scene2DSkin
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

@EnabledIfEnvironmentVariable(named = "CAKE_VIDEO_GL_TESTS", matches = "1")
class ConsoleLogSettingsGlTest {
    @Test
    fun `gear and keyboard open live log controls and closing restores the command draft`() {
        var failure: Throwable? = null
        Lwjgl3Application(object : ApplicationAdapter() {
            override fun create() {
                try { verifyControls() } catch (error: Throwable) { failure = error }
                finally { Gdx.app.exit() }
            }
        }, Lwjgl3ApplicationConfiguration().apply {
            setInitialVisible(false)
            disableAudio(true)
            setWindowedMode(640, 360)
        })
        failure?.let { throw it }
    }

    private fun verifyControls() {
        MainCommon.registerCvars(false)
        val global = Globals.logLevel
        val overrides = Globals.logLevelOverrides
        val network = overrides.getValue(LogSubsystem.NETWORK)
        val previousGlobal = global.typedValue
        val previousOverrides = overrides.mapValues { (_, cvar) -> cvar.typedValue }
        val skin = Skin(Gdx.files.internal("../../assets/ui/uiskin.json"))
        Scene2DSkin.defaultSkin = skin
        val stage = ConsoleStage(ScreenViewport())
        try {
            global.set(ConsoleLevel.ERROR)
            overrides.values.forEach { it.set(LogLevelOverride.TRACE) }
            stage.focus()
            stage.consoleInput.text = "echo unfinished"
            stage.draw()

            val gear = stage.root.findActor<Actor>("console.logSettings")
            val click = stage.stageToScreenCoordinates(gear.localToStageCoordinates(Vector2(gear.width / 2, gear.height / 2)))
            stage.touchDown(click.x.toInt(), click.y.toInt(), 0, Input.Buttons.LEFT)
            stage.touchUp(click.x.toInt(), click.y.toInt(), 0, Input.Buttons.LEFT)
            val globalBox = stage.root.findActor<SelectBox<ConsoleLevel>>("console.logLevel.global")
            val networkBox = stage.root.findActor<SelectBox<LogLevelOverride>>("console.logLevel.network")
            val globalLabel = stage.root.findActor<Label>("console.logLevel.global.label")
            val networkLabel = stage.root.findActor<Label>("console.logLevel.network.label")
            assertEquals(LogLevelOverride.TRACE, networkBox.selected)
            assertEquals(ConsoleLevel.ERROR, globalBox.selected)
            assertEquals(skin.getColor("error"), globalLabel.color)
            assertEquals(skin.getColor("message"), networkLabel.color)
            assertSame(globalBox, stage.keyboardFocus)

            globalBox.selection.choose(ConsoleLevel.INFO)
            assertTrue(overrides.values.all { it.typedValue == LogLevelOverride.GLOBAL })
            assertEquals(ConsoleLevel.INFO, global.typedValue)
            assertEquals(LogLevelOverride.GLOBAL, networkBox.selected)
            assertEquals(skin.getColor("font"), networkLabel.color)
            network.set(LogLevelOverride.DEBUG)
            global.set(ConsoleLevel.WARN)
            stage.act(0f)
            assertEquals(LogLevelOverride.DEBUG, networkBox.selected)
            assertEquals(skin.getColor("fontSecondary"), networkLabel.color)
            assertEquals(skin.getColor("warning"), globalLabel.color)
            assertEquals(skin.getColor("warning"), stage.root.findActor<Label>("console.logLevel.client.label").color)

            assertTrue(stage.keyDown(Input.Keys.ESCAPE))
            assertNull(stage.root.findActor<Actor>("console.logLevels"))
            assertSame(stage.consoleInput, stage.keyboardFocus)
            assertEquals("echo unfinished", stage.consoleInput.text)
            assertTrue(stage.keyDown(Input.Keys.F4))
            repeat(LogSubsystem.entries.size) { assertTrue(stage.keyDown(Input.Keys.TAB)) }
            val serverBox = stage.root.findActor<SelectBox<LogLevelOverride>>("console.logLevel.server")
            assertSame(serverBox, stage.keyboardFocus)
            assertTrue((stage.scrollFocus as ScrollPane).scrollY > 0f)
            assertTrue(stage.keyDown(Input.Keys.ENTER))
            assertTrue(serverBox.list.isTouchable)
            assertTrue(stage.keyDown(Input.Keys.ESCAPE))
            assertFalse(serverBox.list.isTouchable)
        } finally {
            stage.dispose()
            skin.dispose()
            global.set(previousGlobal)
            previousOverrides.forEach { (subsystem, value) -> overrides.getValue(subsystem).set(value) }
        }
    }
}
