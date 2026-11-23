package org.demoth.cake.viewer

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g3d.*
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
import org.demoth.cake.assets.reportResourceReadFailures
import org.demoth.cake.assets.findResourceFailure
import org.demoth.cake.ui.ImagePreviewPane
import org.demoth.cake.ui.ImageThumbnails
import org.demoth.cake.ui.ContentBrowser
import org.demoth.cake.ui.FileResourcePreview
import java.io.File
import java.io.IOException
import java.nio.file.Path
import org.demoth.cake.ui.writeScreenshot
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import org.demoth.cake.ui.RESOURCE_PREVIEW_HELP
import org.demoth.cake.ui.showPreviewDetails

private val SUPPORTED_FORMATS = listOf(".bsp", ".md2", ".wav", ".sp2", ".pak") +
    (ImageThumbnails.imageExtensions + FileResourcePreview.extensions).map { ".$it" }

class CakeViewer(val args: Array<String>, private val captureOutput: Path? = null) : ApplicationAdapter() {
    private var captureFrames = 0
    private var image: Texture? = null
    private var sprite: Sp2Asset? = null
    private var playback: FramePlayback? = null
    private val spriteFrame: Sp2Asset.Frame? get() = sprite?.frames?.get(playback!!.frameIndex)
    private var sound: Sound? = null
    private var soundPlayback: SoundPlayback? = null
    private lateinit var fileResolver: ViewerFileResolver
    private var currentFile: File? = null
    private var browserEntryCount: Int? = null
    private lateinit var browserThumbnails: ImageThumbnails
    private lateinit var previewResources: PreviewResources
    private var filePreview: FileResourcePreview? = null
    private lateinit var scenePreview: SceneResourcePreview
    private lateinit var assetManager: AssetManager
    private lateinit var stage: Stage
    private lateinit var previewHost: Container<Actor>
    private val browser: ContentBrowser? get() = previewHost.actor as? ContentBrowser
    private lateinit var filenameLabel: Label
    private lateinit var errorLabel: Label
    private lateinit var resourceInfo: Label
    private lateinit var emptyState: Table
    private lateinit var infoPanel: Table
    private lateinit var errorHost: Container<Actor>
    private lateinit var summaryLabel: Label

    override fun create() {
        initializeShaderCompatibility()

        val file = args.firstOrNull()?.let { File(expandTildePath(it)) }
        if (file != null && !file.isDirectory && SUPPORTED_FORMATS.none { file.name.lowercase().endsWith(it) }) {
            require(captureOutput == null) { "Unsupported file: ${args.first()}" }
            println("Usage: provide a folder or $SUPPORTED_FORMATS file as the first argument")
            Gdx.app.exit()
            return
        }
        if (file != null && (!file.exists() || !file.canRead())) {
            require(captureOutput == null) { "File $file does not exist or is unreadable" }
            println("File $file does not exist or is unreadable")
            Gdx.app.exit()
            return
        }
        fileResolver = ViewerFileResolver()
        assetManager = AssetManager(fileResolver).apply {
            reportResourceReadFailures()
            setLoader(String::class.java, TextAssetLoader(fileResolver))
            setLoader(Any::class.java, ObjectLoader(fileResolver))
            setLoader(ByteArray::class.java, ByteArrayLoader(fileResolver))
            PcxLoader(fileResolver).registerWith(this)
            setLoader(Texture::class.java, "wal", WalLoader(fileResolver))
            setLoader(Sound::class.java, ConvertingSoundLoader(fileResolver))
            setLoader(Sp2Asset::class.java, Sp2Loader(fileResolver))
            setLoader(BspMapAsset::class.java, "bsp", BspLoader(fileResolver))
            setLoader(Md2Asset::class.java, "md2", Md2Loader(fileResolver))
        }

        browserThumbnails = ImageThumbnails(assetManager, fileResolver)
        previewResources = PreviewResources(assetManager)
        createStage()
        if (file != null) openFile(file.absolutePath)
    }

    /** Replaces the preview. Call on the application thread, between render calls. */
    fun openFile(path: String) {
        val file = File(expandTildePath(path)).absoluteFile
        val rejection = when {
            !file.exists() || !file.canRead() -> "Path is missing or unreadable: ${file.name}"
            !file.isDirectory && (!file.isFile || SUPPORTED_FORMATS.none { file.name.lowercase().endsWith(it) }) -> "Unsupported file: ${file.name}"
            else -> null
        }
        if (rejection != null) {
            check(captureOutput == null) { rejection }
            errorLabel.setText(rejection)
            return
        }

        stage.root.findActor<Actor>("resource-details")?.remove()
        closePreview()
        try {
            currentFile = file
            if (file.isDirectory || file.extension.equals("pak", ignoreCase = true)) {
                val entries = if (file.isDirectory) fileResolver.configureDirectory(file.absolutePath)
                    else fileResolver.configureArchive(file.absolutePath)
                browserEntryCount = entries.size
                previewHost.actor = ContentBrowser(entries, file.name.ifEmpty { file.path }, assetManager.getLoaded<Skin>(cakeSkin),
                    ::previewBrowserResource, browserThumbnails::load, browserThumbnails::clear, ::selectBrowserResource)
                previewHost.isVisible = true
            } else {
                val hints = detectViewerStartupHints(file.absolutePath)
                fileResolver.configure(file.absolutePath, hints.basedir, hints.gamemod)
                loadPreview(file.absolutePath)
                previewHost.actor = createPreviewActor()
                previewHost.isVisible = true
                if (sound != null && captureOutput == null) replaySound()
            }
            filenameLabel.setText(file.name.ifEmpty { file.path })
            errorLabel.setText("")
            updateResourceInfo()
        } catch (error: Exception) {
            val failure = error.findResourceFailure() ?: (error as? IOException) ?: throw error
            if (captureOutput != null) throw IllegalStateException("Could not load ${file.absolutePath}", error)
            Gdx.app.error("Cake Viewer", "Could not open ${file.absolutePath}", error)
            recoverFromLoadError(failure.message
                ?: "Could not load ${file.name}. Drop another file or folder to continue.")
        }
    }

    private fun selectBrowserResource(entry: VfsEntry?): Actor? {
        when (entry?.normalizedPath?.substringAfterLast('.')) {
            "wav" -> if (loadBrowserResource(entry)) replaySound()
            "sp2" -> if (loadBrowserResource(entry)) {
                return playback?.let {
                    it.stop()
                    it.play(loop = false)
                    SpriteFrameImage(sprite!!, it, Scaling.fit)
                }
            }
            else -> closeResourcePreview()
        }
        return null
    }

    private fun previewBrowserResource(entry: VfsEntry?): Actor? {
        errorLabel.setText("")
        if (entry == null) {
            closeResourcePreview()
            return null
        }
        if (entry.normalizedPath.substringAfterLast('.') !in (ImageThumbnails.imageExtensions + listOf("wav", "sp2", "md2", "bsp") + FileResourcePreview.extensions)) return null
        if (!loadBrowserResource(entry)) return null
        return createPreviewActor()
    }

    /** Both entry paths borrow the loaded resources through the same presentation widgets. */
    private fun createPreviewActor(): Actor? {
        return sound?.let { SoundPreviewControls(it, soundPlayback!!, assetManager.getLoaded<Skin>(cakeSkin)) }
            ?: image?.let {
                Table().apply {
                    add(ImagePreviewPane(Image(it))).grow().row()
                }
            }
            ?: sprite?.let { SpritePreview(it, playback!!, assetManager.getLoaded<Skin>(cakeSkin)) }
            ?: scenePreview.actor
            ?: filePreview?.actor
    }

    private fun loadBrowserResource(entry: VfsEntry): Boolean {
        errorLabel.setText("")
        if (previewResources.path == entry.normalizedPath || scenePreview.path == entry.normalizedPath) return true
        val location = currentFile
        return try {
            loadPreview(entry.normalizedPath)
            true
        } catch (error: Exception) {
            val failure = error.findResourceFailure() ?: (error as? IOException) ?: throw error
            Gdx.app.error("Cake Viewer", "Could not preview ${entry.displayPath}", error)
            recoverFromLoadError(failure.message ?: "Could not preview ${entry.displayPath}.", location)
            false
        }
    }

    private fun replaySound() {
        soundPlayback?.replay()
    }

    private fun recoverFromLoadError(message: String, browserToReopen: File? = null) {
        closePreview()
        // Failed dependency loads can leave assets and dependency records behind.
        stage.dispose()
        assetManager.clear()
        createStage()
        if (browserToReopen != null) openFile(browserToReopen.absolutePath)
        errorLabel.setText(message)
    }

    /** Same resource lifetime for loose absolute paths and archive logical paths. */
    private fun loadPreview(path: String) {
        closeResourcePreview()
        fileResolver.preparePreview(path)
        when (path.substringAfterLast('.').lowercase()) {
            "sp2" -> {
                sprite = previewResources.load(path, Sp2Asset::class.java)
                playback = FramePlayback(sprite!!.frames.size)
            }
            "wav" -> {
                sound = previewResources.load(path, Sound::class.java)
                soundPlayback = SoundPlayback(sound!!)
            }
            in ImageThumbnails.imageExtensions -> {
                image = previewResources.load(path, Texture::class.java)
            }
            "md2" -> {
                scenePreview.openMd2(path, fileResolver.playerSkinPaths(path))
                playback = scenePreview.animation
            }
            "bsp" -> scenePreview.openBsp(path)
            in FileResourcePreview.extensions -> checkNotNull(filePreview).open(path)
        }
    }

    private fun createStage() {
        val skin = assetManager.getLoaded<Skin>(cakeSkin)
        scenePreview = SceneResourcePreview(assetManager, ContentBrowserStyles.fromSkin(skin))
        filePreview = FileResourcePreview(assetManager, ContentBrowserStyles.fromSkin(skin))
        stage = Stage(ScreenViewport())
        stage.viewport.update(Gdx.graphics.width, Gdx.graphics.height, true)
        resourceInfo = Label("", skin)
        filenameLabel = Label("No resource", skin).apply { setEllipsis(true) }
        summaryLabel = Label("", skin)
        errorHost = Container<Actor>().fillX()
        errorLabel = Label("", skin).apply { setWrap(true) }
        previewHost = Container<Actor>().apply {
            fill()
            isVisible = false
        }
        stage.addListener(object : InputListener() {
            override fun keyDown(event: InputEvent, keycode: Int): Boolean {
                if (event.isHandled) return false
                if (browser?.keyDown(keycode) == true) return true
                if (filePreview?.keyDown(keycode) == true) return true
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
                        if (soundPlayback != null) soundPlayback?.stop() else playback?.stop()
                        soundPlayback != null || playback != null
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
            add(Label("Drop a file or folder to view", skin))
        }
        stage.addActor(emptyState)
        val styles = ContentBrowserStyles.fromSkin(skin)
        infoPanel = Table().apply {
            background = styles.background
            pad(4f)
            add(filenameLabel).minWidth(0f).growX().left().padLeft(4f).padRight(4f)
            add(summaryLabel).padLeft(8f).padRight(8f)
            fun detailsButton(title: String, content: () -> String) {
                add(TextButton(title, styles.button).apply {
                    addListener(object : ChangeListener() {
                        override fun changed(event: ChangeEvent, actor: Actor) {
                            showPreviewDetails(actor, title, content(), styles)
                        }
                    })
                }).padLeft(4f)
            }
            detailsButton("Info") {
                listOfNotNull(currentFile?.absolutePath, browser?.selectedDetails() ?: resourceInfo.text.toString())
                    .filter { it.isNotEmpty() }.joinToString("\n\n")
            }
            detailsButton("Help") { RESOURCE_PREVIEW_HELP + "\nEscape: return through folders, then close the Viewer." }
        }
        stage.addActor(Table().apply {
            setFillParent(true)
            top().left().pad(8f)
            add(infoPanel).minWidth(0f).growX().row()
            add(errorHost).minWidth(0f).growX().row()
            add(previewHost).minSize(0f).grow()
        })
        updateResourceInfo()
        Gdx.input.inputProcessor = stage
    }

    private fun updateResourceInfo() {
        val frame = spriteFrame
        val bsp = scenePreview.map
        val md2 = scenePreview.md2
        val durationMs = sound?.let { SoundDurationRegistry.durationMs(it) }
        resourceInfo.setText(when {
            browserEntryCount != null -> "Files: $browserEntryCount"
            frame != null -> "Frame ${playback!!.frameIndex + 1}/${sprite!!.frames.size} | ${frame.width} x ${frame.height}"
            image != null -> "${image!!.width} x ${image!!.height}"
            md2 != null -> "Frame ${playback!!.frameIndex + 1}/${md2.frames}: ${md2.frameNames[playback!!.frameIndex]} | Skin textures: ${md2.skins.size}"
            sound != null -> durationMs?.let { "Duration: ${it / 1000f} s" } ?: "Duration: unknown"
            bsp != null -> "World surfaces: ${bsp.worldRenderData.surfaces.size} | Inline brush models: ${bsp.inlineRenderData.size}"
            else -> ""
        })
        summaryLabel.setText(when {
            browserEntryCount != null -> "${browserEntryCount} files"
            image != null -> "${image!!.width} x ${image!!.height}"
            else -> ""
        })
    }

    override fun resize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (::stage.isInitialized) stage.viewport.update(width, height, true)
    }

    override fun render() {
        if (!::stage.isInitialized) {
            return
        }

        val delta = if (captureOutput == null) Gdx.graphics.deltaTime else 0f
        soundPlayback?.update()
        if (scenePreview.animation == null) playback?.update(delta)
        scenePreview.act(delta)
        if (playback != null) updateResourceInfo()

        run {
            Gdx.gl.glClearColor(0.0f, 0.0f, 0.0f, 1f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
            emptyState.isVisible = currentFile == null
            infoPanel.isVisible = currentFile != null || errorLabel.text.isNotEmpty()
            val error = errorLabel.takeIf { it.text.isNotEmpty() }
            if (errorHost.actor !== error) errorHost.actor = error
            stage.act(delta)
            stage.viewport.apply()
            stage.draw()
        }
        if (captureOutput != null) {
            if (Gdx.graphics.backBufferWidth > 0 && Gdx.graphics.backBufferHeight > 0 && ++captureFrames == 2) {
                writeScreenshot(Gdx.files.absolute(captureOutput.toAbsolutePath().toString()))
                Gdx.app.exit()
            }
        }
    }

    override fun dispose() {
        closePreview()
        if (::stage.isInitialized) {
            Gdx.input.inputProcessor = null
            stage.dispose()
        }
        if (::assetManager.isInitialized) {
            assetManager.dispose()
        }
    }

    private fun closePreview() {
        closeResourcePreview()
        if (::browserThumbnails.isInitialized) browserThumbnails.clear()
        browserEntryCount = null
        if (::previewHost.isInitialized) {
            previewHost.actor = null
            previewHost.isVisible = false
        }
        currentFile = null
    }

    private fun closeResourcePreview() {
        filePreview?.close()
        soundPlayback?.stop()
        // Loose widgets must detach before their borrowed textures are released.
        if (::previewHost.isInitialized && browserEntryCount == null) previewHost.actor = null
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
