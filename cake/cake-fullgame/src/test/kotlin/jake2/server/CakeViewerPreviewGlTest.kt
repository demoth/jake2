package jake2.server

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.g3d.ModelInstance
import org.demoth.cake.assets.md2.Md2Asset
import org.demoth.cake.assets.md2.Md2SkinTexturesAttribute
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Image
import jake2.qcommon.filesystem.Bsp
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import org.demoth.cake.viewer.CakeViewer
import org.demoth.cake.ui.menu.MenuEventBus
import org.demoth.cake.ui.menu.MenuScreen
import org.demoth.cake.ui.menu.PlayerSetupState
import org.demoth.cake.ui.menu.PlayerSetupFormState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path

/** Uses the fullgame test resources for the viewer's bundled skin and shaders. */
@EnabledIfEnvironmentVariable(named = "CAKE_HOST_GL_TESTS", matches = "1")
class CakeViewerPreviewGlTest {
    @TempDir lateinit var temp: Path

    @Test
    fun `loose and archive previews share navigation and release resources on replacement`() {
        val fixture = listOf(Path.of("../core/src/test/resources/_missing.md2"),
            Path.of("cake/core/src/test/resources/_missing.md2")).first(Files::exists)
        temp = Files.createDirectories(temp.resolve("content"))
        val model = Files.readAllBytes(fixture)
        // Keep the existing geometry/animation fixture, with two self-contained skin dependencies.
        for ((index, name) in listOf("a.pcx", "b.pcx").withIndex()) {
            model.fill(0, 68 + index * 64, 68 + (index + 1) * 64)
            name.toByteArray().copyInto(model, 68 + index * 64)
        }
        // An extreme later pose must not enlarge or shift the initial preview framing.
        val header = ByteBuffer.wrap(model).order(ByteOrder.LITTLE_ENDIAN)
        val lastFrame = header.getInt(56) + (header.getInt(40) - 1) * header.getInt(16)
        header.putFloat(lastFrame + 12, 1000f)
        val firstPoints = jake2.qcommon.filesystem.Md2Model(
            ByteBuffer.wrap(model).order(ByteOrder.LITTLE_ENDIAN)).frames.first().points
        val firstBounds = com.badlogic.gdx.math.collision.BoundingBox().apply {
            inf()
            firstPoints.forEach { ext(it.position.x, it.position.y, it.position.z) }
        }
        val player = model.copyOf().also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(20, 0) }
        val entries = linkedMapOf("model.md2" to model, "a.pcx" to pcx(0xff0000), "b.pcx" to pcx(0x00ff00))
        entries["players/test/tris.md2"] = player
        repeat(14) { entries["players/test/skin%02d.pcx".format(it)] = pcx(if (it == 0) 0xff0000 else 0x00ff00) }
        entries["players/test/skin00_i.pcx"] = pcx(0)
        entries["players/test/weapon.pcx"] = pcx(0)
        entries["players/test/disguise.pcx"] = pcx(0)
        entries["players/test/zzbroken.pcx"] = byteArrayOf(0)
        val mapBytes = javaClass.getResourceAsStream("/baseq2/maps/testbox.bsp")!!.use { it.readBytes() }
        val map = Bsp(ByteBuffer.wrap(mapBytes))
        entries["testbox.bsp"] = mapBytes
        entries["sprite.sp2"] = sprite()
        entries["sound.wav"] = wav()
        map.textures.forEach { entries["textures/${it.name}.wal"] = wal() }
        entries.forEach { (name, bytes) ->
            val target = temp.resolve(name)
            Files.createDirectories(target.parent)
            Files.write(target, bytes)
        }
        val pak = temp.resolve("models.pak")
        writePak(pak, entries)
        val broken = Files.write(temp.resolve("broken.md2"), byteArrayOf(0))
        var failure: Throwable? = null
        Lwjgl3Application(object : ApplicationAdapter() {
            private var viewer: CakeViewer? = null
            override fun create() {
                try {
                    jake2.qcommon.MainCommon.registerCvars(false)
                    org.demoth.cake.CakeCvars.registerAll()
                    val app = CakeViewer(arrayOf(temp.resolve("model.md2").toString())).also {
                        viewer = it
                        it.create()
                    }
                    app.render()
                    val stage = Gdx.input.inputProcessor as Stage // No fly-camera multiplexer for MD2.
                    val pane = actors(stage.root).single { it.javaClass.simpleName == "Md2PreviewPane" }
                    assertTrue(pane.width > 0 && pane.height > 0)
                    val loadedModel = field(field(app, "scenePreview"), "md2") as Md2Asset
                    assertEquals(firstBounds.min, loadedModel.bounds.min)
                    assertEquals(firstBounds.max, loadedModel.bounds.max,
                        "Later animation poses must not affect MD2 preview bounds")
                    val selector = actors(stage.root).filterIsInstance<SelectBox<*>>().single()
                    assertEquals(2, selector.items.size)
                    selector.selectedIndex = 1
                    stage.keyboardFocus = pane
                    stage.keyDown(Input.Keys.RIGHT)
                    app.render()
                    assertTrue(labels(stage).any { it.startsWith("Frame 2/200") })
                    selector.showScrollPane()
                    stage.keyDown(Input.Keys.ESCAPE) // Close dropdown, not the viewer.
                    repeat(3) { stage.act(0.5f) }
                    assertFalse(selector.scrollPane.hasParent())
                    app.resize(800, 600)
                    app.render()
                    assertTrue(pane.width > 0 && pane.height > 0)

                    app.openFile(pak.toString())
                    app.render()
                    assertNull(pane.stage)
                    openTile(stage, "model.md2")
                    app.render()
                    val archivePane = actors(stage.root).single { it.javaClass.simpleName == "Md2PreviewPane" }
                    assertEquals(2, actors(stage.root).filterIsInstance<SelectBox<*>>().single().items.size)
                    stage.keyboardFocus = archivePane
                    stage.keyDown(Input.Keys.ESCAPE)
                    app.render()
                    assertNull(archivePane.stage)

                    app.openFile(temp.resolve("a.pcx").toString())
                    app.render()
                    val looseImage = verifyImageNavigation(app, stage)
                    app.openFile(pak.toString())
                    app.render()
                    assertNull(looseImage.stage)
                    openTile(stage, "a.pcx")
                    app.render()
                    verifyImageNavigation(app, stage)

                    app.openFile(temp.resolve("testbox.bsp").toString())
                    app.render()
                    val looseMap = verifyMapFocus(stage)
                    val camera = camera(looseMap)
                    val spawn = map.entities.first { it["classname"] == "info_player_start" }
                    val origin = spawn.getValue("origin").trim().split(Regex("\\s+")).map(String::toFloat)
                    assertEquals(origin[0], camera.position.x)
                    assertEquals(origin[1], camera.position.y)
                    assertEquals(origin[2] + 22f, camera.position.z)
                    app.openFile(pak.toString())
                    app.render()
                    assertNull(looseMap.stage)
                    openTile(stage, "testbox.bsp")
                    app.render()
                    val archiveMap = verifyMapFocus(stage)
                    assertEquals(camera.position, camera(archiveMap).position)
                    app.resize(900, 700)
                    app.render()
                    assertTrue(archiveMap.width > 0 && archiveMap.height > 0)

                    app.openFile(temp.resolve("sprite.sp2").toString())
                    app.render()
                    assertTrue(actors(stage.root).any { it.javaClass.simpleName == "SpritePreview" })
                    stage.keyDown(Input.Keys.RIGHT)
                    app.render()
                    assertTrue(labels(stage).any { it.startsWith("Frame 2/2") })
                    app.openFile(temp.resolve("sound.wav").toString())
                    app.render()
                    assertTrue(actors(stage.root).any { it.javaClass.simpleName == "SoundPreviewControls" })
                    assertTrue(stage.keyDown(Input.Keys.S))

                    app.openFile(temp.resolve("players/test/tris.md2").toString())
                    app.render()
                    verifyExternalSkins(app, stage)
                    app.openFile(pak.toString())
                    app.render()
                    openTile(stage, "players"); app.render()
                    openTile(stage, "test"); app.render()
                    openTile(stage, "tris.md2"); app.render()
                    verifyExternalSkins(app, stage)

                    app.openFile(broken.toString())
                    app.render()
                    app.openFile(temp.resolve("model.md2").toString())
                    app.render()
                    assertEquals(1, actors((Gdx.input.inputProcessor as Stage).root)
                        .count { it.javaClass.simpleName == "Md2PreviewPane" })
                    verifyProfilePlayerPreview(app, pak)
                    assertEquals(0, Gdx.gl.glGetError())
                } catch (error: Throwable) {
                    failure = error
                } finally {
                    Gdx.app.exit()
                }
            }
            override fun dispose() {
                try { viewer?.dispose() } catch (error: Throwable) { failure = failure ?: error }
            }
        }, Lwjgl3ApplicationConfiguration().apply {
            setInitialVisible(false)
            disableAudio(true)
            setWindowedMode(1024, 768)
            setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL32, 3, 2)
        })
        failure?.let { throw it }
    }

    private fun verifyProfilePlayerPreview(app: CakeViewer, pak: Path) {
        val profile = Files.createDirectories(temp.resolve("profile/baseq2"))
        Files.copy(pak, profile.resolve("pak0.pak"))
        val resolver = org.demoth.cake.assets.CakeFileResolver(profile.parent.toString())
        val assets = AssetManager(resolver).apply {
            setLoader(com.badlogic.gdx.graphics.Texture::class.java, "pcx", org.demoth.cake.assets.PcxLoader(resolver))
        }
        val skin = (field(app, "assetManager") as AssetManager)
            .get(org.demoth.cake.cakeSkin, com.badlogic.gdx.scenes.scene2d.ui.Skin::class.java)
        ktx.scene2d.Scene2DSkin.defaultSkin = skin
        val style = org.demoth.cake.ui.EngineUiStyle(skin)
        val viewport = org.demoth.cake.ui.UiViewport().apply { update(1024, 768, true) }
        val bus = MenuEventBus()
        val menus = org.demoth.cake.stages.MenuStages(viewport, bus,
            assets, resolver, false) { "test" }
        try {
            menus.rebuild(style)
            val playerStage = (field(menus, "stages") as Map<*, *>)[MenuScreen.PLAYER_SETUP] as Stage
            val playerAssets = field(playerStage, "previewAssets") as AssetManager
            assertEquals(0, playerAssets.loadedAssets, "Eager stage construction must not load player assets")
            menus.select(org.demoth.cake.ui.menu.MenuScreen.CONTENT_BROWSER)
            menus.render(0f)
            val stage = menus.inputProcessor as Stage
            openTile(stage, "players"); menus.render(0f)
            openTile(stage, "test"); menus.render(0f)
            openTile(stage, "tris.md2"); menus.render(0f)
            val selector = actors(stage.root).filterIsInstance<SelectBox<*>>().single()
            assertEquals(15, selector.items.size)
            selector.selectedIndex = 13
            menus.render(0f)
            val pane = actors(stage.root).single { it.javaClass.simpleName == "Md2PreviewPane" }
            menus.select(org.demoth.cake.ui.menu.MenuScreen.MAIN)
            assertNull(pane.stage, "Leaving the profile browser releases its preview")
            var draft = PlayerSetupFormState(model = "test", skin = "skin00")
            fun publish() {
                bus.postState(bus.latestState().copy(playerSetup = PlayerSetupState(
                    availableModels = listOf("test", "missing"),
                    availableSkins = listOf("skin00", "skin13", "zzbroken"), form = draft)))
            }
            publish()
            menus.select(MenuScreen.PLAYER_SETUP)
            menus.render(0f)
            val scene = field(playerStage, "preview")
            val instance = field(scene, "instance") as ModelInstance
            fun bound() = (instance.materials.first().get(Md2SkinTexturesAttribute.Type)
                as Md2SkinTexturesAttribute).textures.single()
            val firstTexture = bound()
            val playerPane = actors(playerStage.root).single { it.javaClass.simpleName == "Md2PreviewPane" }
            val position = camera(playerPane).position.cpy()
            assertEquals(3, actors(playerStage.root).filterIsInstance<SelectBox<*>>().size,
                "Only the form's model, skin and hand selectors are shown")
            draft = draft.copy(skin = "skin13")
            publish(); menus.render(1f)
            assertSame(instance, field(scene, "instance"))
            assertNotSame(firstTexture, bound())
            assertEquals(position, camera(playerPane).position)
            val selectedTexture = bound()
            draft = draft.copy(name = "changed", password = "secret", hand = 1)
            publish(); menus.render(1f)
            assertSame(instance, field(scene, "instance"))
            assertSame(selectedTexture, bound())
            val frame = instance.userData as org.demoth.cake.assets.md2.Md2CustomData
            assertEquals(0, frame.frame1, "The setup preview remains on the first frame")
            assertEquals(0f, frame.interpolation)
            draft = draft.copy(skin = "zzbroken")
            publish(); menus.render(0f)
            assertSame(selectedTexture, bound())
            assertTrue(labels(playerStage).any { it.contains("Preview skin unchanged") })
            draft = draft.copy(skin = "skin00")
            publish(); menus.render(0f)
            assertNotSame(selectedTexture, bound())
            viewport.setReferenceHeight(640f)
            viewport.update(640, 480, true)
            menus.render(0f)
            assertTrue(playerPane.width > 0f && playerPane.height > 0f)
            val formTable = field(playerStage, "formTable") as Actor
            val previewPanel = field(playerStage, "previewPanel") as Actor
            assertTrue(previewPanel.x >= formTable.x + formTable.width,
                "The preview stays to the right of the form on narrow menu canvases")
            assertTrue(previewPanel.x + previewPanel.width <= previewPanel.parent.width,
                "Both columns fit within the form container")
            assertTrue(previewPanel.parent.width <= kotlin.math.ceil(viewport.minWorldWidth - 32f),
                "Container=${previewPanel.parent.width}, form=${formTable.width}, preview=${previewPanel.width}, canvas=${viewport.minWorldWidth}")
            menus.select(MenuScreen.MAIN)
            assertNull(playerPane.stage)
            assertEquals(0, playerAssets.loadedAssets)
            menus.select(MenuScreen.PLAYER_SETUP); menus.render(0f)
            assertNotSame(instance, field(scene, "instance"))
            menus.hideContentPreviews()
            assertEquals(0, playerAssets.loadedAssets, "Release before changing profile resolution")
            draft = draft.copy(model = "missing")
            publish(); menus.render(0f)
            assertTrue(labels(playerStage).contains("Player preview unavailable."))
            menus.render(0f) // A failed preview must leave the menu usable.
            draft = draft.copy(model = "test")
            publish(); menus.render(0f)
            assertTrue(actors(playerStage.root).any { it.javaClass.simpleName == "Md2PreviewPane" })

        } finally {
            menus.dispose()
            style.dispose()
            assets.dispose()
        }
    }

    private fun verifyExternalSkins(app: CakeViewer, stage: Stage) {
        val selector = actors(stage.root).filterIsInstance<SelectBox<*>>().single()
        assertEquals(15, selector.items.size, "All body skins, including >12 choices, excluding icons/auxiliary textures")
        val scene = field(app, "scenePreview")
        val instance = field(scene, "instance") as ModelInstance
        val model = field(scene, "md2") as Md2Asset
        fun bound() = instance.materials.first().get(Md2SkinTexturesAttribute.Type) as Md2SkinTexturesAttribute
        val originalTexture = bound().textures.single()
        assertNotSame(model.skins.single(), originalTexture)
        val pane = actors(stage.root).single { it.javaClass.simpleName == "Md2PreviewPane" }
        stage.keyboardFocus = pane
        stage.keyDown(Input.Keys.RIGHT)
        app.render()
        val position = camera(pane).position.cpy()
        selector.selectedIndex = 13
        app.render()
        val selectedTexture = bound().textures.single()
        assertNotSame(originalTexture, selectedTexture)
        assertSame(instance, field(scene, "instance"))
        assertEquals(position, camera(pane).position)
        assertTrue(labels(stage).any { it.startsWith("Frame 2/200") })
        assertSame(model.skins.single(),
            (model.model.materials.first().get(Md2SkinTexturesAttribute.Type) as Md2SkinTexturesAttribute).textures.single(),
            "External selection only changes the preview instance's material")
        selector.selectedIndex = 14 // corrupt texture
        app.render()
        assertEquals(13, selector.selectedIndex)
        assertSame(selectedTexture, bound().textures.single())
        assertTrue(labels(stage).any { it.contains("Keeping previous skin") })
        selector.selectedIndex = 0 // a failed load must not prevent later selection
        app.render()
        assertEquals(0, selector.selectedIndex)
        assertNotSame(selectedTexture, bound().textures.single())
        val assets = field(app, "assetManager") as AssetManager
        assertTrue(assets.isLoaded(field(scene, "resources").let { resources ->
            resources.javaClass.getMethod("getPath").invoke(resources) as String
        }))
    }

    private fun field(owner: Any, name: String): Any = owner.javaClass.getDeclaredField(name).let {
        it.isAccessible = true
        it.get(owner)
    }

    private fun openTile(stage: Stage, name: String) {
        val tile = actors(stage.root).filterIsInstance<Label>().filter { it.text.toString().removeSuffix("/") == name }
            .firstNotNullOf { label -> generateSequence<Actor>(label) { it.parent }.filterIsInstance<Button>().firstOrNull() }
        tile.listeners.filterIsInstance<ClickListener>().forEach {
            it.tapCount = 2
            it.clicked(InputEvent(), 0f, 0f)
        }
    }

    private fun verifyImageNavigation(app: CakeViewer, stage: Stage): Actor {
        val pane = actors(stage.root).single { it.javaClass.simpleName == "ImagePreviewPane" }
        val image = (pane as Group).children.single() as Image
        val before = image.width
        val center = pane.localToStageCoordinates(Vector2(pane.width / 2, pane.height / 2))
        pane.fire(InputEvent().apply {
            type = InputEvent.Type.scrolled; stageX = center.x; stageY = center.y; scrollAmountY = -1f
        })
        app.render()
        assertTrue(image.width > before, "Wheel zoom applies in both hosts")
        val oldX = image.x
        pane.fire(InputEvent().apply {
            type = InputEvent.Type.touchDown; button = Input.Buttons.LEFT
            stageX = center.x; stageY = center.y
        })
        pane.fire(InputEvent().apply {
            type = InputEvent.Type.touchDragged; stageX = center.x + 20f; stageY = center.y
        })
        app.render()
        assertEquals(oldX + 20f, image.x, 0.01f, "Dragging pans the image")
        val ratio = image.width / minOf(pane.width, pane.height)
        app.resize(800, 650)
        app.render()
        assertEquals(ratio, image.width / minOf(pane.width, pane.height), 0.01f, "Resize retains relative zoom")
        return pane
    }

    private fun verifyMapFocus(stage: Stage): Actor {
        assertSame(stage, Gdx.input.inputProcessor)
        val pane = actors(stage.root).single { it.javaClass.simpleName == "BspPreviewPane" }
        pane.fire(InputEvent().apply { type = InputEvent.Type.touchDown; button = Input.Buttons.LEFT })
        assertSame(pane, stage.keyboardFocus)
        assertTrue(stage.keyDown(Input.Keys.W))
        stage.root.fire(InputEvent().apply { type = InputEvent.Type.touchDown; button = Input.Buttons.LEFT })
        assertNull(stage.keyboardFocus, "Click outside the pane releases flight input")
        return pane
    }

    private fun camera(pane: Actor): Camera = pane.javaClass.superclass.getDeclaredField("camera").let {
        it.isAccessible = true
        it.get(pane) as Camera
    }

    private fun sprite(): ByteArray = ByteBuffer.allocate(12 + 2 * 80).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("IDS2".toByteArray()); putInt(2); putInt(2)
        for (name in listOf("a.pcx", "b.pcx")) {
            repeat(2) { putInt(1) }; repeat(2) { putInt(0) }
            put(ByteArray(64).also { name.toByteArray().copyInto(it) })
        }
    }.array()

    private fun wav(): ByteArray = ByteBuffer.allocate(844).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray()); putInt(836); put("WAVEfmt ".toByteArray()); putInt(16)
        putShort(1); putShort(1); putInt(8000); putInt(8000); putShort(1); putShort(8)
        put("data".toByteArray()); putInt(800); put(ByteArray(800) { 128.toByte() })
    }.array()

    private fun wal(): ByteArray = ByteBuffer.allocate(101).order(ByteOrder.LITTLE_ENDIAN).apply {
        position(32); putInt(1); putInt(1)
        repeat(4) { putInt(100) }
        put(100, 1.toByte())
    }.array()

    private fun actors(actor: Actor): List<Actor> =
        listOf(actor) + if (actor is Group) actor.children.flatMap(::actors) else emptyList()
    private fun labels(stage: Stage) = actors(stage.root).filterIsInstance<Label>().map { it.text.toString() }

    private fun pcx(rgb: Int): ByteArray = ByteArray(128 + 1 + 1 + 768).apply {
        this[0] = 10; this[1] = 5; this[2] = 1; this[3] = 8
        this[65] = 1; this[66] = 1
        this[128] = 1; this[129] = 12
        this[133] = (rgb shr 16).toByte(); this[134] = (rgb shr 8).toByte(); this[135] = rgb.toByte()
    }

    private fun writePak(path: Path, entries: Map<String, ByteArray>) {
        val directory = 12 + entries.values.sumOf { it.size }
        val buffer = ByteBuffer.allocate(directory + entries.size * 64).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("PACK".toByteArray()).putInt(directory).putInt(entries.size * 64)
        entries.values.forEach { buffer.put(it) }
        var offset = 12
        entries.forEach { (name, bytes) ->
            val field = ByteArray(56)
            name.toByteArray().copyInto(field)
            buffer.put(field).putInt(offset).putInt(bytes.size)
            offset += bytes.size
        }
        Files.write(path, buffer.array())
    }
}
