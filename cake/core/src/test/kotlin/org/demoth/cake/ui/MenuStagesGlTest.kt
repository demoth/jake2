package org.demoth.cake.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.utils.ScreenUtils
import jake2.qcommon.Globals
import org.demoth.cake.automation.AutomationCaptures
import org.demoth.cake.automation.AutomationHost
import org.demoth.cake.automation.AutomationRunner
import org.demoth.cake.automation.AutomationUi
import org.demoth.cake.automation.CaptureLayer
import org.demoth.cake.testing.HiddenGlTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.Locale
import com.badlogic.gdx.assets.AssetManager
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
import jake2.qcommon.exec.find
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

@EnabledIfEnvironmentVariable(named = "CAKE_VIDEO_GL_TESTS", matches = "1")
class MenuStagesGlTest {
    @TempDir lateinit var temp: Path

    @Test
    fun `navigation and rebuild retain active screen and refresh browser presentation`() {
        HiddenGlTest.run(home = temp) { verifyMenuStages() }
    }

    private suspend fun HiddenGlTest.verifyMenuStages() {
        org.demoth.cake.CakeStartupBootstrap.ensureInitialized()
        val developer = Globals.developer.typedValue
        onCleanup { Globals.developer.forceSet(developer) }
        Globals.developer.forceSet(true)
        val skin = Skin(Gdx.files.internal("../../assets/ui/uiskin.json"))
        Scene2DSkin.defaultSkin = skin
        val style = EngineUiStyle(skin)
        val assets = AssetManager()
        val bus = MenuEventBus()
        val viewport = UiViewport().apply { update(Gdx.graphics.width, Gdx.graphics.height, true) }
        val menus = MenuStages(viewport, bus, assets, CakeFileResolver(), false) { null }
        val release = onCleanup {
            menus.dispose()
            style.dispose()
            assets.dispose()
            skin.dispose()
        }
        try {
            verifyMissingTextures()
            verifyModelTextureReplacement()
            verifyModsBrowser(viewport, style)
            // Exercise the conchars-based game theme as well as the engine skin.
            val atlasPixels = com.badlogic.gdx.graphics.Pixmap(128, 128, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888)
            val atlas = com.badlogic.gdx.graphics.Texture(atlasPixels)
            atlasPixels.dispose()
            val gameFont = ConcharsFontLoader.createBitmapFont(atlas)
            val releaseGameStyle = onCleanup { gameFont.dispose(); atlas.dispose() }
            try {
                val gameStyle = object : GameUiStyle by style {
                    override val menuWidgets = createMenuWidgetStyles(skin, gameFont, atlas)
                }
                verifyModsBrowser(viewport, gameStyle)
            } finally { releaseGameStyle() }
            verifyHostMapSelection(viewport, style)
            verifySavedGames(viewport, style)
            verifySingleplayerHub(viewport, style)
            verifyBrowserDemoNavigation(viewport, style)
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
        } finally { release() }
    }

    private fun verifyModelTextureReplacement() {
        val root = java.nio.file.Files.createTempDirectory("cake-model-replacement-")
        val archive = root.resolve("model.zip")
        val modelPath = "models/test/tris.md2"
        val skinPath = "models/test/skin.pcx"
        val model = java.nio.ByteBuffer.allocate(228).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        listOf(jake2.qcommon.filesystem.IDALIASHEADER, 8, 64, 64, 52, 1, 3, 0, 0, 11, 1,
            68, 132, 132, 132, 184, 228).forEach { model.putInt(it) }
        model.put(skinPath.toByteArray())
        model.position(132)
        repeat(3) { model.putFloat(1f) }
        repeat(3) { model.putFloat(0f) }
        model.put(ByteArray(16))
        model.put(byteArrayOf(0, 0, 0, 0, 10, 0, 0, 0, 0, 10, 0, 0))
        model.putInt(3)
        repeat(3) { model.putFloat(if (it == 1) 1f else 0f); model.putFloat(if (it == 2) 1f else 0f); model.putInt(it) }
        model.putInt(0)
        val jpeg = java.io.ByteArrayOutputStream().also {
            javax.imageio.ImageIO.write(java.awt.image.BufferedImage(16, 8, java.awt.image.BufferedImage.TYPE_INT_RGB), "jpg", it)
        }.toByteArray()
        java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(archive)).use { zip ->
            for ((path, bytes) in listOf(modelPath to model.array(), "models/test/skin.jpg" to jpeg)) {
                zip.putNextEntry(java.util.zip.ZipEntry(path)); zip.write(bytes); zip.closeEntry()
            }
        }
        val source = org.demoth.cake.assets.CakeVfsAssetSource()
        source.configureArchive(archive.toString())
        val resolver = object : org.demoth.cake.assets.TextureCandidateResolver {
            override fun textureCandidates() = source.listEntries().map { it.normalizedPath }
            override fun resolve(path: String) = source.resolve(path)
        }
        val assets = AssetManager(resolver).apply {
            setLoader(org.demoth.cake.assets.md2.Md2Asset::class.java, org.demoth.cake.assets.md2.Md2Loader(resolver))
            setLoader(com.badlogic.gdx.graphics.Texture::class.java, ".pcx", org.demoth.cake.assets.PcxLoader(resolver))
        }
        org.demoth.cake.assets.ModelTextureReplacements.reset()
        try {
            assets.load(modelPath, org.demoth.cake.assets.md2.Md2Asset::class.java)
            assets.finishLoading()
            val loaded = assets.get(modelPath, org.demoth.cake.assets.md2.Md2Asset::class.java)
            assertEquals(listOf("models/test/skin.jpg"), loaded.skinNames)
            assertEquals(16, loaded.skins.single().width)
            assertEquals(8, loaded.skins.single().height)
            assets.unload(modelPath)
            assertFalse(assets.isLoaded("models/test/skin.jpg"))
            jake2.qcommon.exec.Cvar.getInstance().find<Boolean>("r_texture_replacements").forceSet(false)
            assets.load(modelPath, org.demoth.cake.assets.md2.Md2Asset::class.java)
            assets.finishLoading()
            val original = assets.get(modelPath, org.demoth.cake.assets.md2.Md2Asset::class.java)
            assertEquals(listOf(skinPath), original.skinNames)
            assertEquals(64, original.skins.single().width, "Absent PCX uses checkerboard when replacements are off")
        } finally {
            assets.dispose()
            source.configure(null, null, false)
            root.toFile().deleteRecursively()
            org.demoth.cake.assets.ModelTextureReplacements.reset()
        }
    }

    private fun verifyMissingTextures() {
        val resolver = com.badlogic.gdx.assets.loaders.FileHandleResolver { path ->
            when {
                path.startsWith("null") -> null
                path.startsWith("absent") -> Gdx.files.absolute("/nonexistent-cake-test/$path")
                else -> throw org.demoth.cake.assets.MissingResourceException(path,
                    org.demoth.cake.assets.ResourceKind.PCX, "test")
            }
        }
        val assets = AssetManager(resolver).apply {
            setLoader(com.badlogic.gdx.graphics.Texture::class.java, ".pcx", org.demoth.cake.assets.PcxLoader(resolver))
            setLoader(com.badlogic.gdx.graphics.Texture::class.java, ".wal", org.demoth.cake.assets.WalLoader(resolver))
        }
        try {
            for (prefix in listOf("null", "absent", "throws")) {
                assets.load("$prefix.pcx", com.badlogic.gdx.graphics.Texture::class.java)
                assets.load("$prefix.wal", com.badlogic.gdx.graphics.Texture::class.java,
                    org.demoth.cake.assets.WalLoader.Parameters(wrapU = com.badlogic.gdx.graphics.Texture.TextureWrap.Repeat,
                        wrapV = com.badlogic.gdx.graphics.Texture.TextureWrap.Repeat))
            }
            assets.finishLoading()
            for (prefix in listOf("null", "absent", "throws")) {
                val skin = assets.get("$prefix.pcx", com.badlogic.gdx.graphics.Texture::class.java)
                val wall = assets.get("$prefix.wal", com.badlogic.gdx.graphics.Texture::class.java)
                assertEquals(64, skin.width)
                assertEquals(64, wall.height)
                assertEquals(com.badlogic.gdx.graphics.Texture.TextureWrap.Repeat, wall.uWrap)
                assertNotSame(skin, wall, "Each asset owns its placeholder for independent unloading")
                assets.unload("$prefix.pcx")
                assertTrue(assets.isLoaded("$prefix.wal"))
            }
            assertFalse(assets.isLoaded("q2palette.bin"), "Missing WAL textures need no game palette")
        } finally { assets.dispose() }
    }

    private fun verifyWrappedText(style: GameUiStyle) {
        val file = java.nio.file.Files.createTempFile("cake-wrapped-text-", ".txt")
        java.nio.file.Files.writeString(file, "Wide readme fixture " + "words on a very long line ".repeat(80))
        val assets = AssetManager(com.badlogic.gdx.assets.loaders.FileHandleResolver {
            com.badlogic.gdx.files.FileHandle(it)
        })
        val preview = FileResourcePreview(assets, ContentBrowserStyles.fromMenu(style.menuWidgets))
        try {
            val view = preview.open(file.toString()) as com.badlogic.gdx.scenes.scene2d.ui.Table
            view.setSize(360f, 140f)
            repeat(3) { view.validate() }
            val text = view.findActor<Label>("resource-text")
            val scroll = view.findActor<com.badlogic.gdx.scenes.scene2d.ui.ScrollPane>("resource-text-scroll")
            val wideHeight = text.prefHeight
            assertTrue(text.wrap)
            assertFalse(scroll.isScrollX, "Long text must fit the viewport width")
            assertTrue(scroll.maxY > 0f, "Wrapped text remains vertically scrollable")
            assertTrue(text.width <= scroll.width)
            view.setSize(180f, 140f)
            repeat(3) { view.validate() }
            assertTrue(text.prefHeight > wideHeight, "Text reflows when the preview becomes narrower")
            assertFalse(scroll.isScrollX)
        } finally {
            preview.close(); assets.dispose(); java.nio.file.Files.delete(file)
        }
    }

    private suspend fun HiddenGlTest.verifyModsBrowser(viewport: UiViewport, style: GameUiStyle) {
        verifyWrappedText(style)
        val root = java.nio.file.Files.createTempDirectory("cake-mods-ui-")
        val folder = java.nio.file.Files.createDirectories(root.resolve("catalog"))
        val archive = folder.resolve("example.zip")
        java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(archive)).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("readme.txt"))
            zip.write("Archive readme fixture".toByteArray())
            zip.closeEntry()
        }
        val profile = org.demoth.cake.profile.CakeGameProfile("archive", root.toString(), "catalog")
        val bus = MenuEventBus(MenuStateSnapshot(activeScreen = MenuScreen.MODS,
            mods = org.demoth.cake.ui.menu.ModsState(profile = profile,
                archives = org.demoth.cake.profile.ProfileArchives.discover(profile, false),
                automount = false, selectedArchive = "catalog/example.zip", canEdit = true)))
        val assets = AssetManager()
        val resolver = CakeFileResolver()
        val menus = MenuStages(viewport, bus, assets, resolver, false) { profile.id }
        val intents = mutableListOf<MenuIntent>()
        val host = object : AutomationHost {
            override val automationUi = AutomationUi({ menus.inputProcessor as Stage }, bus::postIntent)
            override val automationCaptures = AutomationCaptures()
            override val presentedAutomationScreen get() = menus.activeScreen.name.lowercase(Locale.ROOT)
            override val isGamePresentationReady = false
            override fun openAutomationSample(game: Boolean?) = error("No gallery in this stage fixture")
        }
        val runner = AutomationRunner(host)
        val release = onCleanup {
            renderWith {}
            runner.dispose()
            host.automationCaptures.cancelAll("Mods fixture disposed")
            menus.dispose()
            assets.dispose()
            root.toFile().deleteRecursively()
        }
        try {
            menus.rebuild(style)
            renderWith {
                runner.update()
                host.automationCaptures.beginFrame()
                bus.drainIntents { intent ->
                    intents += intent
                    // Component fixture: apply navigation only; backend requests remain assertions.
                    val screen = when (intent) {
                        MenuIntent.OpenMods -> MenuScreen.MODS
                        MenuIntent.OpenMainMenu -> MenuScreen.MAIN
                        else -> null
                    }
                    if (screen != null) {
                        bus.postState(bus.latestState().copy(activeScreen = screen))
                        menus.select(screen)
                    }
                }
                if (Gdx.graphics.backBufferWidth > 0 && Gdx.graphics.backBufferHeight > 0) {
                    ScreenUtils.clear(0f, 0f, 0f, 1f)
                    menus.render(Gdx.graphics.deltaTime)
                    host.automationCaptures.afterDraw(CaptureLayer.PRESENTATION)
                    host.automationCaptures.afterDraw(CaptureLayer.FINAL)
                    runner.frameRendered()
                }
            }
            await(runner.runText("""
                automation_open mods
                automation_await screen mods
                automation_await element mod-checkbox:catalog/example.zip enabled
                automation_frames 2
            """.trimIndent()))
            var stage = host.automationUi.stage
            assertTrue(labels(stage.root).any { it.contains("Archive readme fixture") })
            assertTrue(resolver.listContentEntries().isEmpty(), "Inspecting must not change gameplay mounts")
            val checkbox = host.automationUi.element("mod-checkbox:catalog/example.zip") as TextButton
            val row = host.automationUi.element("mod-row:catalog/example.zip") as TextButton
            assertEquals(checkbox.height, row.height)
            assertEquals(checkbox.y, row.y)
            assertSame(checkbox.style.up, row.style.up)
            assertTrue(row.isChecked)
            assertEquals(com.badlogic.gdx.graphics.Color(0.35f, 0.95f, 0.35f, 1f), row.style.checkedFontColor)
            assertEquals(row.style.checkedFontColor, row.style.checkedOverFontColor)
            assertFalse(checkbox.isDisabled)
            assertFalse(checkbox.isChecked)

            // Capture remains optional for sequences; stop for JUnit assertions before requesting it.
            val theme = if (style is EngineUiStyle) "engine" else "game"
            val output = Path.of("build/reports/menu-stages-$theme.png").toAbsolutePath()
            await(runner.runText("automation_capture \"$output\" presentation"))
            val image = Pixmap(Gdx.files.absolute(output.toString()))
            try {
                assertEquals(Gdx.graphics.backBufferWidth, image.width)
                assertEquals(Gdx.graphics.backBufferHeight, image.height)
            } finally { image.dispose() }

            await(runner.runText("automation_click mod-checkbox:catalog/example.zip"))
            assertTrue(intents.contains(MenuIntent.SetModIncluded(profile.id, "catalog/example.zip", true)))
            menus.rebuild(style)
            await(runner.runText("automation_frames 1"))
            assertNotSame(stage, host.automationUi.stage)
            stage = host.automationUi.stage
            assertTrue(labels(stage.root).any { it.contains("Archive readme fixture") }, "Rebuild must restore inspection")
            // Missing packages remain listed and removable, with a visible inspection error.
            java.nio.file.Files.delete(archive)
            bus.postState(bus.latestState().copy(mods = bus.latestState().mods.copy(revision = 1,
                profile = profile.copy(includedArchives = listOf("catalog/example.zip")),
                archives = listOf(org.demoth.cake.profile.ProfileArchive("catalog/example.zip", true, false, true)))))
            await(runner.runText("automation_frames 1"))
            assertTrue(labels(stage.root).any { it.contains("Could not inspect") })
            val missingCheckbox = host.automationUi.element("mod-checkbox:catalog/example.zip") as TextButton
            assertFalse(missingCheckbox.isDisabled)
            assertTrue(missingCheckbox.isChecked)
            await(runner.runText("automation_click mod-checkbox:catalog/example.zip"))
            assertTrue(intents.contains(MenuIntent.SetModIncluded(profile.id, "catalog/example.zip", false)))
            await(runner.runText("automation_open main; automation_await screen main"))
        } finally { release() }
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

    private fun verifyBrowserDemoNavigation(viewport: UiViewport, style: GameUiStyle) {
        val entries = (0..39).map { number ->
            val path = "demos/nested/clip${number.toString().padStart(2, '0')}.dm2"
            jake2.qcommon.vfs.VfsEntry(path, path, jake2.qcommon.vfs.VfsLayer.BASE_PACK, 0,
                jake2.qcommon.vfs.VfsSource(jake2.qcommon.vfs.VfsSourceType.PACKAGE_ENTRY,
                    java.nio.file.Path.of("fixture.pak"), path, "pak", true, false), 100, 0)
        }
        val bus = MenuEventBus()
        val assets = AssetManager()
        var profile = "profile"
        val menus = MenuStages(viewport, bus, assets, CakeFileResolver(), false,
            listContentEntries = { entries },
            readDemoMetadata = { org.demoth.cake.demo.DemoMetadata(26, "baseq2", "Test", "maps/test.bsp") },
            activeProfileId = { profile })
        fun stage() = menus.inputProcessor as Stage
        fun browser() = descendants(stage().root).filterIsInstance<ContentBrowser>().single()
        fun clickTile(name: String, count: Int) {
            val tile = descendants(stage().root).filterIsInstance<Label>().filter { it.text.toString().removeSuffix("/") == name }
                .firstNotNullOf { label -> generateSequence<Actor>(label) { it.parent }
                    .filterIsInstance<com.badlogic.gdx.scenes.scene2d.ui.Button>().firstOrNull() }
            tile.listeners.filterIsInstance<ClickListener>().forEach { it.tapCount = count; it.clicked(InputEvent(), 0f, 0f) }
        }
        fun settle() { repeat(3) { menus.render(0f) } }
        try {
            menus.rebuild(style)
            menus.select(MenuScreen.CONTENT_BROWSER)
            settle()
            clickTile("demos", 2); settle()
            clickTile("nested", 2); settle()
            clickTile("clip30.dm2", 1)
            browser().keyDown(Input.Keys.EQUALS); settle()
            val gridScroll = descendants(browser()).filterIsInstance<com.badlogic.gdx.scenes.scene2d.ui.ScrollPane>()
                .first { it.isScrollingDisabledX }
            gridScroll.scrollY = 250f
            gridScroll.updateVisualScroll()
            val saved = browser().location()
            assertTrue(saved.scrollY > 0f)
            bus.drainIntents { }
            stage().keyDown(Input.Keys.ENTER)
            val intents = mutableListOf<MenuIntent>()
            bus.drainIntents { intents += it }
            assertEquals(listOf(MenuIntent.PlayDemoRequested("profile", entries[30])), intents)
            menus.hideContentPreviews()
            menus.rebuild(style)
            settle()
            assertEquals(saved, browser().location(), "Playback cleanup and theme rebuild must retain the browser location")
            menus.showContentBrowser("Demo playback failed: synthetic failure")
            settle()
            assertEquals(saved, browser().location())
            assertTrue(labels(stage().root).contains("Demo playback failed: synthetic failure"))
            bus.drainIntents { }
            clickTile("clip30.dm2", 2)
            stage().keyDown(Input.Keys.ENTER)
            intents.clear()
            bus.drainIntents { intents += it }
            assertEquals(List(2) { MenuIntent.PlayDemoRequested("profile", entries[30]) }, intents)
            profile = "other"
            menus.showContentBrowser()
            settle()
            assertEquals("", browser().location().directory)
            assertNull(browser().location().selectedPath)
        } finally { menus.dispose(); assets.dispose() }
    }

    private fun verifySavedGames(viewport: UiViewport, style: GameUiStyle) {
        val entries = listOf(
            HostSaveEntry("autosave", "Autosave", "base1", 1000L, HostMode.SINGLEPLAYER, null, mapTitle = "Outer Base"),
            HostSaveEntry("manual", "Checkpoint", "base2", 2000L, HostMode.SINGLEPLAYER, null),
        ) + (1..24).map { HostSaveEntry("manual-$it", "Checkpoint $it", "base2", 2000L, HostMode.SINGLEPLAYER, null) }
        val bus = MenuEventBus(MenuStateSnapshot(activeScreen = MenuScreen.SAVE_GAME,
            saveAccess = HostSaveAccess(null, null),
            savedGames = SavedGamesState("profile", entries, "autosave")))
        val pixels = com.badlogic.gdx.graphics.Pixmap(32, 24, com.badlogic.gdx.graphics.Pixmap.Format.RGB888)
        pixels.setColor(com.badlogic.gdx.graphics.Color.RED)
        pixels.fill()
        val png = try { encodeSavePreview(pixels) } finally { pixels.dispose() }
        var reads = 0
        val stage = SavedGamesStage(viewport, bus, style, saving = true, readPreview = { _, slot ->
            reads++
            when (slot) { "manual" -> png; "manual-1" -> byteArrayOf(1, 2, 3); else -> null }
        })
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
            val image = stage.root.findActor<com.badlogic.gdx.scenes.scene2d.ui.Image>("save-preview-image")
            assertNotNull(image.drawable)
            val previousReads = reads
            stage.act(0f)
            assertEquals(previousReads, reads, "Unchanged selection must not reload the image")
            stage.hidePreview()
            assertNull(image.drawable)
            stage.act(0f)
            assertNotNull(image.drawable)
            assertEquals(previousReads + 1, reads)
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
            bus.postState(bus.latestState().copy(savedGames = SavedGamesState("profile", entries, "manual-1")))
            stage.act(0f)
            assertNull(image.drawable, "Corrupt preview falls back to placeholder")
            assertFalse(overwrite.isDisabled, "A corrupt preview must not disable checkpoint actions")
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
