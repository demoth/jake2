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
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import org.demoth.cake.stages.HostingSetupStage
import org.demoth.cake.stages.SavedGamesStage
import org.demoth.cake.stages.SingleplayerMenuStage
import org.demoth.cake.stages.MainMenuStage
import org.demoth.cake.hosting.HostSaveAccess
import org.demoth.cake.hosting.HostSaveEntry
import org.demoth.cake.hosting.HostMode
import org.demoth.cake.ui.menu.SavedGamesState
import org.demoth.cake.ui.menu.HostMap
import org.demoth.cake.ui.menu.HostGameFormState
import org.demoth.cake.ui.menu.HostingSetupState
import org.demoth.cake.ui.menu.MenuStateSnapshot
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
            verifyHostMapSelection(viewport, style)
            verifySavedGames(viewport, style)
            verifySingleplayerHub(viewport, style)
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

    private fun verifyHostMapSelection(viewport: UiViewport, style: GameUiStyle) {
        val maps = listOf(HostMap("arena", "Arena title"), HostMap("custom"))
        val bus = MenuEventBus(MenuStateSnapshot(canHostGame = true,
            hostGame = HostingSetupState(form = HostGameFormState(map = "custom"), maps = maps)))
        val stage = HostingSetupStage(viewport, bus, style, false)
        try {
            stage.act(0f)
            val actors = descendants(stage.root)
            @Suppress("UNCHECKED_CAST")
            val dropdown = actors.filterIsInstance<SelectBox<*>>().first() as SelectBox<HostMap>
            val start = actors.filterIsInstance<TextButton>().first { it.text.toString() == "Start Server" }
            assertEquals("custom", dropdown.selected.name)
            assertFalse(start.isDisabled)
            dropdown.selectedIndex = 0
            stage.act(0f)
            bus.drainIntents { }
            start.listeners.filterIsInstance<ClickListener>().forEach {
                it.clicked(InputEvent(), 0f, 0f)
            }
            val intents = mutableListOf<MenuIntent>()
            bus.drainIntents { intents += it }
            assertEquals("arena", intents.filterIsInstance<MenuIntent.StartHosting>().single().form.map)
            assertEquals("arena — Arena title", dropdown.selected.toString())
            dropdown.showScrollPane()
            assertTrue(dropdown.scrollPane.hasParent())
            dropdown.hideScrollPane()
            bus.postState(bus.latestState().copy(hostGame = HostingSetupState()))
            stage.act(0f)
            assertTrue(start.isDisabled)
            assertTrue(dropdown.isDisabled)
            assertTrue(labels(stage.root).contains("No maps found"))
        } finally {
            stage.dispose()
        }
    }

    private fun verifySavedGames(viewport: UiViewport, style: GameUiStyle) {
        val entries = listOf(
            HostSaveEntry("save0", "Autosave", "base1", 1000L, HostMode.SINGLEPLAYER, null, mapTitle = "Outer Base"),
            HostSaveEntry("manual", "Checkpoint", "base2", 2000L, HostMode.SINGLEPLAYER, null),
        ) + (1..24).map { HostSaveEntry("manual-$it", "Checkpoint $it", "base2", 2000L, HostMode.SINGLEPLAYER, null) }
        val bus = MenuEventBus(MenuStateSnapshot(activeScreen = MenuScreen.SAVE_GAME,
            saveAccess = HostSaveAccess(null, null),
            savedGames = SavedGamesState("profile", entries, "save0")))
        val stage = SavedGamesStage(viewport, bus, style, saving = true)
        try {
            stage.act(0f)
            stage.draw()
            val scroll = stage.root.findActor<com.badlogic.gdx.scenes.scene2d.ui.ScrollPane>("save-list-scroll")
            val preview = stage.root.findActor<Actor>("save-preview")
            assertTrue(scroll.widget is com.badlogic.gdx.scenes.scene2d.ui.List<*>)
            val firstRow = (scroll.widget as com.badlogic.gdx.scenes.scene2d.ui.List<*>).items.first().toString()
            assertTrue(firstRow.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2} - Outer Base - base1")), firstRow)
            assertTrue(scroll.maxY > 0, "Checkpoint list must scroll within its own pane")
            val listPosition = scroll.localToStageCoordinates(com.badlogic.gdx.math.Vector2())
            val previewPosition = preview.localToStageCoordinates(com.badlogic.gdx.math.Vector2())
            assertTrue(previewPosition.x >= listPosition.x + scroll.width)
            val primary = stage.root.findActor<TextButton>("save-new")
            val overwrite = stage.root.findActor<TextButton>("save-overwrite")
            assertFalse(primary.isDisabled)
            assertTrue(overwrite.isDisabled)
            bus.postState(bus.latestState().copy(savedGames = bus.latestState().savedGames.copy(
                selectedSlot = "manual", pendingOverwriteSlot = "manual", statusMessage = "Replace this saved game?")))
            stage.act(0f)
            stage.draw()
            assertTrue(primary.isDisabled)
            assertTrue(overwrite.isDisabled)
            val confirm = stage.root.findActor<TextButton>("confirm-overwrite")
            val position = confirm.localToStageCoordinates(com.badlogic.gdx.math.Vector2())
            assertTrue(confirm.width > 0 && confirm.height > 0)
            assertTrue(position.y >= 0 && position.y + confirm.height <= viewport.worldHeight)
            confirm.listeners.filterIsInstance<ClickListener>().forEach { it.clicked(InputEvent(), 0f, 0f) }
            val intents = mutableListOf<MenuIntent>()
            bus.drainIntents { intents += it }
            assertTrue(intents.contains(MenuIntent.ConfirmOverwrite("profile", "manual")))
            assertTrue(stage.keyUp(Input.Keys.ESCAPE))
            bus.drainIntents { intents += it }
            assertEquals(MenuIntent.CancelSaveConfirmation, intents.last())
            bus.postState(bus.latestState().copy(savedGames = bus.latestState().savedGames.copy(
                pendingOverwriteSlot = null, pendingDeleteSlot = "manual", statusMessage = "Delete this saved game?")))
            stage.act(0f)
            val deleteConfirm = stage.root.findActor<TextButton>("confirm-delete")
            assertEquals("Delete Save", deleteConfirm.text.toString())
            deleteConfirm.listeners.filterIsInstance<ClickListener>().forEach { it.clicked(InputEvent(), 0f, 0f) }
            bus.drainIntents { intents += it }
            assertEquals(MenuIntent.ConfirmDeleteSave("profile", "manual"), intents.last())
            bus.postState(bus.latestState().copy(savedGames = SavedGamesState("other")))
            stage.act(0f)
            assertTrue(overwrite.isDisabled)
            assertTrue(labels(stage.root).contains("No saved games for this profile."))
            stage.keyUp(Input.Keys.ESCAPE)
            bus.drainIntents { intents += it }
            assertEquals(MenuIntent.OpenSingleplayerMenu, intents.last())
        } finally {
            stage.dispose()
        }
    }

    private fun verifySingleplayerHub(viewport: UiViewport, style: GameUiStyle) {
        val bus = MenuEventBus(MenuStateSnapshot(canHostGame = false,
            saveAccess = HostSaveAccess(null, null)))
        val main = MainMenuStage(viewport, bus, style, showHosting = true)
        val hub = SingleplayerMenuStage(viewport, bus, style)
        val difficulty = HostingSetupStage(viewport, bus, style, true)
        val load = SavedGamesStage(viewport, bus, style, saving = false)
        try {
            main.act(0f)
            assertFalse(main.root.findActor<TextButton>("singleplayer-menu").isDisabled)
            assertFalse(labels(main.root).any { it == "Load Game" || it == "Save Game" })
            hub.act(0f)
            hub.draw()
            assertTrue(hub.root.findActor<TextButton>("new-game-menu").isDisabled)
            assertFalse(hub.root.findActor<TextButton>("load-game-menu").isDisabled)
            assertFalse(hub.root.findActor<TextButton>("save-game-menu").isDisabled)
            bus.postState(bus.latestState().copy(canHostGame = true,
                saveAccess = HostSaveAccess(null, "Start a game first")))
            hub.act(0f)
            assertFalse(hub.root.findActor<TextButton>("new-game-menu").isDisabled)
            assertFalse(hub.root.findActor<TextButton>("load-game-menu").isDisabled)
            assertTrue(hub.root.findActor<TextButton>("save-game-menu").isDisabled)
            val button = hub.root.findActor<TextButton>("new-game-menu")
            bus.drainIntents { }
            button.listeners.filterIsInstance<ClickListener>().forEach { it.clicked(InputEvent(), 0f, 0f) }
            val intents = mutableListOf<MenuIntent>()
            bus.drainIntents { intents += it }
            assertEquals(listOf(MenuIntent.OpenHostingSetup(true)), intents)
            for (stage in listOf(difficulty, load)) {
                stage.keyUp(Input.Keys.ESCAPE)
                bus.drainIntents { intents += it }
                assertEquals(MenuIntent.OpenSingleplayerMenu, intents.last())
            }
            hub.keyUp(Input.Keys.ESCAPE)
            bus.drainIntents { intents += it }
            assertEquals(MenuIntent.OpenMainMenu, intents.last())
        } finally {
            listOf(main, hub, difficulty, load).forEach { it.dispose() }
        }
    }

    private fun descendants(actor: Actor): List<Actor> =
        listOf(actor) + if (actor is Group) actor.children.flatMap(::descendants) else emptyList()

    private fun labels(actor: Actor): List<String> = when (actor) {
        is Label -> listOf(actor.text.toString())
        is Group -> actor.children.flatMap(::labels)
        else -> emptyList()
    }
}
