package org.demoth.cake.viewer

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.utils.Scaling
import jake2.qcommon.vfs.VfsEntry
import ktx.assets.TextAssetLoader
import ktx.graphics.use
import org.demoth.cake.ByteArrayLoader
import org.demoth.cake.cakeSkin
import org.demoth.cake.audio.SoundDurationRegistry
import org.demoth.cake.audio.SoundPlayback
import org.demoth.cake.ui.SceneResourcePreview
import org.demoth.cake.ui.ContentBrowserStyles
import org.demoth.cake.ui.PreviewResources
import org.demoth.cake.ui.FramePlayback
import org.demoth.cake.ui.SpriteFrameImage
import org.demoth.cake.ui.SpritePreview
import org.demoth.cake.ui.SoundPreviewControls
import org.demoth.cake.initializeShaderCompatibility
import org.demoth.cake.assets.bsp.BspLoader
import org.demoth.cake.assets.bsp.BspMapAsset
import org.demoth.cake.assets.md2.Md2Asset
import org.demoth.cake.assets.md2.Md2Loader
import org.demoth.cake.assets.ObjectLoader
import org.demoth.cake.assets.PcxLoader
import org.demoth.cake.assets.ConvertingSoundLoader
import org.demoth.cake.assets.Sp2Asset
import org.demoth.cake.assets.Sp2Loader
import org.demoth.cake.assets.WalLoader
import org.demoth.cake.assets.getLoaded
import org.demoth.cake.ui.ImagePreviewPane
import org.demoth.cake.ui.ImageThumbnails
import org.demoth.cake.ui.ContentBrowser
import java.io.File
import kotlin.system.measureTimeMillis

private val SUPPORTED_FORMATS = listOf(".bsp", ".pcx", ".md2", ".wal", ".tga", ".wav", ".sp2", ".pak")

class CakeViewer(val args: Array<String>) : ApplicationAdapter() {
    private lateinit var batch: SpriteBatch
    private var image: Texture? = null
    private var sprite: Sp2Asset? = null
    private var playback: FramePlayback? = null
    private val spriteFrame: Sp2Asset.Frame? get() = sprite?.frames?.get(playback!!.frameIndex)
    private var sound: Sound? = null
    private var soundPlayback: SoundPlayback? = null
    private lateinit var fileResolver: ViewerFileResolver
    private var currentFile: File? = null
    private var archiveEntryCount: Int? = null
    private lateinit var archiveThumbnails: ImageThumbnails
    private lateinit var previewResources: PreviewResources
    private lateinit var scenePreview: SceneResourcePreview
    private lateinit var assetManager: AssetManager
    private lateinit var camera: Camera
    private lateinit var cameraInputController: CameraInputController
    // rendered with the default libgdx shader
    private lateinit var stage: Stage
    private lateinit var previewHost: Container<Actor>
    private val browser: ContentBrowser? get() = previewHost.actor as? ContentBrowser
    private lateinit var filenameLabel: Label
    private lateinit var controlsLabel: Label
    private lateinit var errorLabel: Label
    private lateinit var resourceInfo: Label
    private lateinit var frameTimeLabel: Label
    private lateinit var emptyState: Table
    private lateinit var infoPanel: Table
    private var frameTime = 0f // to have an idea of the fps

    override fun create() {
        initializeShaderCompatibility()

        if (args.isNotEmpty() && SUPPORTED_FORMATS.none { args.first().lowercase().endsWith(it) }) {
            println("Usage: provide $SUPPORTED_FORMATS file as the first argument")
            Gdx.app.exit()
            return
        }
        val file = args.firstOrNull()?.let { File(expandTildePath(it)) }
        if (file != null && (!file.exists() || !file.canRead())) {
            println("File $file does not exist or is unreadable")
            Gdx.app.exit()
            return
        }
        fileResolver = ViewerFileResolver()
        assetManager = AssetManager(fileResolver).apply {
            setLoader(String::class.java, TextAssetLoader(fileResolver))
            setLoader(Any::class.java, ObjectLoader(fileResolver))
            setLoader(ByteArray::class.java, ByteArrayLoader(fileResolver))
            setLoader(Texture::class.java, "pcx", PcxLoader(fileResolver))
            setLoader(Texture::class.java, "wal", WalLoader(fileResolver))
            setLoader(Sound::class.java, ConvertingSoundLoader(fileResolver))
            setLoader(Sp2Asset::class.java, Sp2Loader(fileResolver))
            setLoader(BspMapAsset::class.java, "bsp", BspLoader(fileResolver))
            setLoader(Md2Asset::class.java, "md2", Md2Loader(fileResolver))
        }

        archiveThumbnails = ImageThumbnails(assetManager, fileResolver)
        previewResources = PreviewResources(assetManager)
        batch = SpriteBatch()

        camera = PerspectiveCamera(90f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        camera.near = 1f
        camera.far = 4096f
        resetCamera()

        createStage()
        if (file != null) openFile(file.absolutePath)
    }

    /** Replaces the preview. Call on the application thread, between render calls. */
    fun openFile(path: String) {
        val file = File(expandTildePath(path)).absoluteFile
        val rejection = when {
            SUPPORTED_FORMATS.none { file.name.lowercase().endsWith(it) } -> "Unsupported file: ${file.name}"
            !file.isFile || !file.canRead() -> "File is missing or unreadable: ${file.name}"
            else -> null
        }
        if (rejection != null) {
            errorLabel.setText(rejection)
            return
        }

        closePreview()
        resetCamera()
        try {
            currentFile = file
            if (file.extension.equals("pak", ignoreCase = true)) {
                val entries = fileResolver.configureArchive(file.absolutePath)
                archiveEntryCount = entries.size
                previewHost.actor = ContentBrowser(entries, file.name, assetManager.getLoaded<Skin>(cakeSkin),
                    ::previewArchiveResource, archiveThumbnails::load, archiveThumbnails::clear, ::selectArchiveResource)
                previewHost.isVisible = true
            } else {
                val hints = detectViewerStartupHints(file.absolutePath)
                fileResolver.configure(file.absolutePath, hints.basedir, hints.gamemod)
                loadPreview(file.absolutePath)
                if (scenePreview.md2 != null) {
                    previewHost.actor = scenePreview.actor
                    previewHost.isVisible = true
                }
                if (sound != null) replaySound()
            }
            filenameLabel.setText(file.name)
            controlsLabel.setText(when {
                archiveEntryCount != null -> "Click: select / play sound or sprite | Double-click: open | Escape: close"
                sound != null -> "Space: play/pause | S: stop | Escape: close"
                sprite != null || scenePreview.md2 != null -> "Space: play/pause | Left/Right: step | Escape: close"
                else -> "Escape: close"
            })
            errorLabel.setText("")
            updateResourceInfo()
        } catch (error: Exception) {
            Gdx.app.error("Cake Viewer", "Could not open ${file.absolutePath}", error)
            recoverFromLoadError("Could not load ${file.name}. Drop another resource to continue.")
        }
    }

    private fun selectArchiveResource(entry: VfsEntry?): Actor? {
        when (entry?.normalizedPath?.substringAfterLast('.')) {
            "wav" -> if (loadArchiveResource(entry)) replaySound()
            "sp2" -> if (loadArchiveResource(entry)) {
                return playback?.let {
                    it.stop()
                    it.play()
                    SpriteFrameImage(sprite!!, it, Scaling.fit)
                }
            }
            else -> closeResourcePreview()
        }
        return null
    }

    private fun previewArchiveResource(entry: VfsEntry?): Actor? {
        errorLabel.setText("")
        if (entry == null) {
            closeResourcePreview()
            return null
        }
        if (entry.normalizedPath.substringAfterLast('.') !in listOf("pcx", "wal", "tga", "wav", "sp2", "md2", "bsp")) return null
        if (!loadArchiveResource(entry)) return null
        // Presentation borrows the active resource; opening controls does not start playback.
        return sound?.let { SoundPreviewControls(it, soundPlayback!!, assetManager.getLoaded<Skin>(cakeSkin)) }
            ?: image?.let {
                Table().apply {
                    add(ImagePreviewPane(Image(it))).grow().row()
                    add(Label("Drag: pan | Wheel: zoom", assetManager.getLoaded<Skin>(cakeSkin))).pad(4f)
                }
            }
            ?: sprite?.let { SpritePreview(it, playback!!, assetManager.getLoaded<Skin>(cakeSkin)) }
            ?: scenePreview.actor
    }

    private fun loadArchiveResource(entry: VfsEntry): Boolean {
        errorLabel.setText("")
        if (previewResources.path == entry.normalizedPath || scenePreview.path == entry.normalizedPath) return true
        val archive = currentFile
        return try {
            loadPreview(entry.normalizedPath)
            true
        } catch (error: Exception) {
            Gdx.app.error("Cake Viewer", "Could not preview ${entry.displayPath}", error)
            recoverFromLoadError("Could not preview ${entry.displayPath}.", archive)
            false
        }
    }

    private fun replaySound() {
        soundPlayback?.replay()
    }

    private fun recoverFromLoadError(message: String, archiveToReopen: File? = null) {
        closePreview()
        // Failed dependency loads can leave assets and dependency records behind.
        stage.dispose()
        assetManager.clear()
        createStage()
        if (archiveToReopen != null) openFile(archiveToReopen.absolutePath)
        errorLabel.setText(message)
    }

    /** Same resource lifetime for loose absolute paths and archive logical paths. */
    private fun loadPreview(path: String) {
        closeResourcePreview()
        when (path.substringAfterLast('.').lowercase()) {
            "sp2" -> {
                sprite = previewResources.load(path, Sp2Asset::class.java)
                playback = FramePlayback(sprite!!.frames.size)
            }
            "wav" -> {
                sound = previewResources.load(path, Sound::class.java)
                soundPlayback = SoundPlayback(sound!!)
            }
            "pcx", "wal", "tga" -> {
                image = previewResources.load(path, Texture::class.java)
            }
            "md2" -> {
                scenePreview.openMd2(path)
                playback = scenePreview.animation
            }
            "bsp" -> scenePreview.openBsp(path)
        }
        updateInputHandlers()
    }

    private fun updateInputHandlers() {
        // Only loose BSP viewing uses the standalone fly camera; panes handle their own input.
        Gdx.input.inputProcessor = if (archiveEntryCount == null && scenePreview.map != null)
            InputMultiplexer(stage, cameraInputController) else stage
    }

    private fun resetCamera() {
        camera.position.setZero()
        camera.up.set(0f, 0f, 1f)
        camera.direction.set(0f, 1f, 0f)
        camera.update()
        cameraInputController = FlyingCameraController(camera)
    }

    private fun createStage() {
        val skin = assetManager.getLoaded<Skin>(cakeSkin)
        scenePreview = SceneResourcePreview(assetManager, ContentBrowserStyles.fromSkin(skin))
        stage = Stage(ScreenViewport())
        stage.viewport.update(Gdx.graphics.width, Gdx.graphics.height, true)
        resourceInfo = Label("", skin)
        frameTimeLabel = Label("", skin)
        filenameLabel = Label("No resource", skin).apply { setWrap(true) }
        controlsLabel = Label("Drop a resource to view | Escape: close", skin).apply { setWrap(true) }
        errorLabel = Label("", skin).apply { setWrap(true) }
        previewHost = Container<Actor>().apply {
            fill()
            isVisible = false
        }
        stage.addListener(object : InputListener() {
            override fun keyDown(event: InputEvent, keycode: Int): Boolean {
                if (event.isHandled) return false
                if (browser?.keyDown(keycode) == true) return true
                return when (keycode) {
                    Input.Keys.ESCAPE -> {
                        if (browser?.navigateBack() != true) Gdx.app.exit()
                        true
                    }
                    Input.Keys.SPACE -> {
                        if (sound != null) soundPlayback?.toggle() else playback?.toggle()
                        sound != null || playback != null
                    }
                    Input.Keys.S -> {
                        soundPlayback?.stop()
                        soundPlayback != null
                    }
                    Input.Keys.RIGHT, Input.Keys.LEFT -> {
                        playback?.step(if (keycode == Input.Keys.RIGHT) 1 else -1)
                        playback != null
                    }
                    else -> false
                }
            }
        })
        emptyState = Table().apply {
            setFillParent(true)
            add(Label("Drop a resource to view", skin))
        }
        stage.addActor(emptyState)
        infoPanel = Table(skin).apply {
            background = skin.getDrawable("window")
            pad(12f)
            defaults().left().growX().padBottom(4f)
            add(filenameLabel).row()
            add(resourceInfo).row()
            add(controlsLabel).row()
            add(errorLabel).row()
            add(frameTimeLabel)
        }
        stage.addActor(Table().apply {
            setFillParent(true)
            top().left().pad(12f)
            add(infoPanel).growX().row()
            add(previewHost).grow()
        })
        updateResourceInfo()
        updateInputHandlers()
    }

    private fun updateResourceInfo() {
        val frame = spriteFrame
        val bsp = scenePreview.map
        val md2 = scenePreview.md2
        val durationMs = sound?.let { SoundDurationRegistry.durationMs(it) }
        resourceInfo.setText(when {
            archiveEntryCount != null -> "Files: $archiveEntryCount"
            frame != null -> "Frame ${playback!!.frameIndex + 1}/${sprite!!.frames.size} | ${frame.width} x ${frame.height}"
            image != null -> "${image!!.width} x ${image!!.height}"
            md2 != null -> "Frame ${playback!!.frameIndex + 1}/${md2.frames}: ${md2.frameNames[playback!!.frameIndex]} | Skin textures: ${md2.skins.size}"
            sound != null -> durationMs?.let { "Duration: ${it / 1000f} s" } ?: "Duration: unknown"
            bsp != null -> "World surfaces: ${bsp.worldRenderData.surfaces.size} | Inline brush models: ${bsp.inlineRenderData.size}"
            else -> ""
        })
        resourceInfo.isVisible = resourceInfo.text.isNotEmpty()
    }

    override fun resize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (::stage.isInitialized) stage.viewport.update(width, height, true)
        if (::batch.isInitialized) batch.projectionMatrix.setToOrtho2D(0f, 0f, width.toFloat(), height.toFloat())
        if (::camera.isInitialized) {
            camera.viewportWidth = width.toFloat()
            camera.viewportHeight = height.toFloat()
            camera.update()
        }
    }

    override fun render() {
        if (!::camera.isInitialized || !::batch.isInitialized || !::stage.isInitialized) {
            return
        }

        soundPlayback?.update()
        if (scenePreview.animation == null) playback?.update(Gdx.graphics.deltaTime)
        scenePreview.act(Gdx.graphics.deltaTime)
        if (playback != null) updateResourceInfo()

        frameTime = measureTimeMillis {
            Gdx.gl.glClearColor(0.0f, 0.0f, 0.0f, 1f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
            camera.update()

            if (archiveEntryCount == null && scenePreview.map != null) scenePreview.render(camera)

            batch.use {
                val currentFrame = if (archiveEntryCount == null) spriteFrame else null
                val currentImage = if (archiveEntryCount == null) currentFrame?.texture ?: image else null
                if (currentImage != null) {
                    // draw image in the center of the screen
                    val width = (currentFrame?.width ?: currentImage.width).toFloat()
                    val height = (currentFrame?.height ?: currentImage.height).toFloat()
                    val x = (Gdx.graphics.width - width) / 2f
                    val y = (Gdx.graphics.height - height) / 2f
                    it.draw(currentImage, x, y, width, height)
                }

            }

            if (archiveEntryCount == null && scenePreview.map != null) cameraInputController.update()
            emptyState.isVisible = currentFile == null
            infoPanel.isVisible = currentFile != null || errorLabel.text.isNotEmpty()
            frameTimeLabel.setText("Frame time ms: $frameTime")
            stage.act(Gdx.graphics.deltaTime)
            stage.viewport.apply()
            stage.draw()
        }.toFloat()
    }

    override fun dispose() {
        closePreview()
        if (::stage.isInitialized) {
            Gdx.input.inputProcessor = null
            stage.dispose()
        }
        if (::batch.isInitialized) {
            batch.dispose()
        }
        if (::assetManager.isInitialized) {
            assetManager.dispose()
        }
    }

    private fun closePreview() {
        closeResourcePreview()
        archiveThumbnails.clear()
        archiveEntryCount = null
        if (::previewHost.isInitialized) {
            previewHost.actor = null
            previewHost.isVisible = false
        }
        currentFile = null
    }

    private fun closeResourcePreview() {
        soundPlayback?.stop()
        if (::previewResources.isInitialized) previewResources.close()
        if (::scenePreview.isInitialized) {
            // Detach borrowed actors before disposing their render targets and model resources.
            scenePreview.actor?.remove()
            scenePreview.close()
        }
        if (::stage.isInitialized) Gdx.input.inputProcessor = stage
        image = null
        sound = null
        soundPlayback = null
        sprite = null
        playback = null
    }

}

internal fun expandTildePath(path: String): String {
    if (path == "~") {
        return System.getProperty("user.home")
    }
    if (path.startsWith("~/") || path.startsWith("~\\")) {
        return System.getProperty("user.home") + path.substring(1)
    }
    return path
}

fun createOriginArrows(size: Float): ModelInstance {
    val modelBuilder = ModelBuilder()
    val origin = modelBuilder.createXYZCoordinates(size, Material(), (Usage.Position or Usage.ColorUnpacked).toLong())
    return ModelInstance(origin)
}
