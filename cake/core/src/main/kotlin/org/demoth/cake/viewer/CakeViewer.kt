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
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.scenes.scene2d.Stage
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
import org.demoth.cake.initializeShaderCompatibility
import org.demoth.cake.md2FragmentShader
import org.demoth.cake.assets.bsp.BspLoader
import org.demoth.cake.assets.bsp.BspMapAsset
import org.demoth.cake.assets.bsp.BspWorldBatchRenderer
import org.demoth.cake.assets.md2.Md2Asset
import org.demoth.cake.assets.md2.Md2CustomData
import org.demoth.cake.assets.md2.Md2Loader
import org.demoth.cake.assets.ObjectLoader
import org.demoth.cake.assets.PcxLoader
import org.demoth.cake.assets.md2.Md2Shader
import org.demoth.cake.assets.CakeShaderProvider
import org.demoth.cake.assets.ConvertingSoundLoader
import org.demoth.cake.assets.Sp2Asset
import org.demoth.cake.assets.Sp2Loader
import org.demoth.cake.assets.WalLoader
import org.demoth.cake.assets.getLoaded
import org.demoth.cake.md2VertexShader
import org.demoth.cake.ui.ContentBrowser
import java.io.File
import kotlin.system.measureTimeMillis

private const val GRID_SIZE = 16f
private const val GRID_DIVISIONS = 8

private val SUPPORTED_FORMATS = listOf(".bsp", ".pcx", ".md2", ".wal", ".tga", ".wav", ".sp2", ".pak")

class CakeViewer(val args: Array<String>) : ApplicationAdapter() {
    private lateinit var batch: SpriteBatch
    private var image: Texture? = null
    private var sprite: Sp2Asset? = null
    private var playback: FramePlayback? = null
    private val spriteFrame: Sp2Asset.Frame? get() = sprite?.frames?.get(playback!!.frameIndex)
    private var sound: Sound? = null
    private var bspMap: BspMapAsset? = null
    private var bspWorldRenderer: BspWorldBatchRenderer? = null
    private var bspVisibleSurfaces = BooleanArray(0)
    private var bspTime = 0f
    private var modelBatch: ModelBatch? = null
    private lateinit var fileResolver: ViewerFileResolver
    private var currentFile: File? = null
    private var archiveEntryCount: Int? = null
    private val archiveThumbnails = mutableMapOf<String, Texture>()
    private val failedThumbnails = mutableSetOf<String>()
    private lateinit var previewResources: PreviewResources
    private lateinit var assetManager: AssetManager
    private lateinit var camera: Camera
    private lateinit var cameraInputController: CameraInputController
    // rendered with the default libgdx shader
    private val instances: MutableList<ModelInstance> = mutableListOf()
    private lateinit var environment: Environment
    private lateinit var stage: Stage
    private lateinit var browserHost: Container<ContentBrowser>
    private lateinit var filenameLabel: Label
    private lateinit var controlsLabel: Label
    private lateinit var errorLabel: Label
    private lateinit var resourceInfo: Label
    private lateinit var frameTimeLabel: Label
    private var frameTime = 0f // to have an idea of the fps

    // md2 related stuff
    private var md2Frames = 1
    private var md2SkinTextures = 0
    private var md2Instance: ModelInstance? = null // to control the model animation
    private var md2FrameNames: List<String> = emptyList()

    override fun create() {
        initializeShaderCompatibility()

        if (args.isEmpty() || SUPPORTED_FORMATS.none { args.first().lowercase().endsWith(it) }) {
            println("Usage: provide $SUPPORTED_FORMATS file as the first argument")
            Gdx.app.exit()
            return
        }
        val file = File(expandTildePath(args[0]))
        if (!file.exists() || !file.canRead()) {
            println("File $file does not exist or is unreadable")
            Gdx.app.exit()
            return
        }
        val startupHints = detectViewerStartupHints(file.absolutePath)

        fileResolver = ViewerFileResolver(
            openedFilePath = file.absolutePath,
            basedir = startupHints.basedir,
            gamemod = startupHints.gamemod,
        )
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

        previewResources = PreviewResources(assetManager)
        batch = SpriteBatch()

        camera = PerspectiveCamera(90f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        camera.near = 1f
        camera.far = 4096f
        resetCamera()

        environment = Environment()
        environment.set(ColorAttribute(ColorAttribute.AmbientLight, 0.8f, 0.8f, 0.8f, 1f))
        environment.add(DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.2f, 0.8f))
        createStage()
        openFile(file.absolutePath)
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
                browserHost.actor = ContentBrowser(entries, file.name, assetManager.getLoaded<Skin>(cakeSkin),
                    ::previewArchiveResource, ::loadArchiveThumbnail, ::clearArchiveThumbnails, ::selectArchiveResource)
                browserHost.isVisible = true
            } else {
                val hints = detectViewerStartupHints(file.absolutePath)
                fileResolver.configure(file.absolutePath, hints.basedir, hints.gamemod)
                loadPreview(file.absolutePath)
                if (md2Instance != null || bspMap != null) {
                    addOwnedInstance(createOriginArrows(GRID_SIZE))
                    addOwnedInstance(createGrid(GRID_SIZE, GRID_DIVISIONS))
                }
                if (sound != null) replaySound()
            }
            filenameLabel.setText(file.name)
            controlsLabel.setText(when {
                archiveEntryCount != null -> "Click: select / play sound or sprite | Double-click: open | Escape: close"
                sound != null -> "Space: replay | S: stop | Escape: close"
                sprite != null || md2Instance != null -> "Space: play/pause | Left/Right: step | Escape: close"
                else -> "Escape: close"
            })
            errorLabel.setText("")
            updateResourceInfo()
        } catch (error: Exception) {
            Gdx.app.error("Cake Viewer", "Could not open ${file.absolutePath}", error)
            recoverFromLoadError("Could not load ${file.name}. Drop another resource to continue.")
        }
    }

    private fun loadArchiveThumbnail(entry: VfsEntry): Texture? {
        val path = entry.normalizedPath
        archiveThumbnails[path]?.let { return it }
        if (path in failedThumbnails) return null
        return try {
            val file = requireNotNull(fileResolver.resolve(path))
            // Invoke existing loaders directly: these textures are viewer-owned, and a bad
            // thumbnail cannot leave failed tasks/dependency records in the shared manager.
            val texture = when (path.substringAfterLast('.')) {
                "pcx" -> PcxLoader(fileResolver).load(assetManager, path, file, null)
                "wal" -> WalLoader(fileResolver).load(assetManager, path, file, WalLoader.Parameters(
                    externalPalette = assetManager.getLoaded<Any>("q2palette.bin") as IntArray))
                else -> Texture(file)
            }
            archiveThumbnails[path] = texture
            texture
        } catch (error: Exception) {
            failedThumbnails += path
            Gdx.app.error("Cake Viewer", "Could not thumbnail ${entry.displayPath}", error)
            null
        }
    }

    private fun clearArchiveThumbnails() {
        archiveThumbnails.values.forEach { it.dispose() }
        archiveThumbnails.clear()
        failedThumbnails.clear()
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
        return sound?.let { SoundPreviewControls(it, assetManager.getLoaded<Skin>(cakeSkin)) }
            ?: image?.let { Image(it).apply { setScaling(Scaling.none) } }
            ?: sprite?.let { SpritePreview(it, playback!!, assetManager.getLoaded<Skin>(cakeSkin)) }
            ?: bspMap?.let { map ->
                val pane = previewResources.own(BspPreviewPane(map, ::renderScene))
                Table().apply {
                    add(pane).grow().row()
                    add(Label("Surfaces: ${map.worldRenderData.surfaces.size} | Brush models: ${map.inlineRenderData.size}",
                        assetManager.getLoaded<Skin>(cakeSkin))).pad(4f).row()
                    add(Label("Click to focus | WASD: fly | Q/E: down/up | Drag: look",
                        assetManager.getLoaded<Skin>(cakeSkin)).apply { setWrap(true) }).growX().pad(4f)
                }
            }
            ?: md2Instance?.let { instance ->
                val md2 = assetManager.get(entry.normalizedPath, Md2Asset::class.java)
                val animation = playback!!
                val pane = previewResources.own(Md2PreviewPane(md2.bounds) { paneCamera ->
                    modelBatch!!.begin(paneCamera)
                    modelBatch!!.render(instance)
                    modelBatch!!.end()
                })
                Table().apply {
                    add(pane).grow().row()
                    add(FramePlaybackControls(animation, assetManager.getLoaded<Skin>(cakeSkin)) {
                        "${md2.frameNames[animation.frameIndex]} | Skin textures: ${md2.skins.size}"
                    }).growX().row()
                    add(Label("Drag: orbit | Wheel: zoom", assetManager.getLoaded<Skin>(cakeSkin))).pad(4f)
                }
            }
    }

    private fun renderScene(camera: Camera) {
        val currentTimeMs = (bspTime * 1000f).toInt()
        bspWorldRenderer?.render(
            camera = camera,
            visibleSurfaceMask = bspVisibleSurfaces,
            currentTimeMs = currentTimeMs,
            lightStyleResolver = { 1f },
            dynamicLights = emptyList(),
        )

        modelBatch?.let { models ->
            models.begin(camera)
            instances.forEach { models.render(it) }
            models.end()
        }

        bspWorldRenderer?.renderTranslucent(camera, bspVisibleSurfaces, currentTimeMs)
    }

    private fun loadArchiveResource(entry: VfsEntry): Boolean {
        errorLabel.setText("")
        if (previewResources.path == entry.normalizedPath) return true
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
        sound?.let { it.stop(); it.play() }
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
            }
            "pcx", "wal", "tga" -> {
                image = previewResources.load(path, Texture::class.java)
            }
            "md2" -> {
                // Viewer mode: prefer embedded MD2 skins, but allow rendering without them.
                val md2 = previewResources.load(
                    path, Md2Asset::class.java,
                    Md2Loader.Parameters(
                        useDefaultSkinIfMissing = true,
                    ),
                )
                md2Instance = ModelInstance(md2.model).apply {
                    userData = Md2CustomData.empty()
                }
                md2Frames = md2.frames
                md2FrameNames = md2.frameNames
                playback = FramePlayback(md2.frames)
                md2SkinTextures = md2.skins.size

                val tempRenderable = Renderable()
                val md2Shader = Md2Shader(
                    md2Instance!!.getRenderable(tempRenderable), // may not be obvious, but it's required for the shader initialization, the renderable is not used after that
                    DefaultShader.Config(
                        assetManager.getLoaded(md2VertexShader),
                        assetManager.getLoaded(md2FragmentShader),
                    )
                )
                modelBatch = previewResources.own(ModelBatch(CakeShaderProvider(md2Shader)))
                md2Shader.init()

                instances.add(md2Instance!!)
            }
            "bsp" -> {
                val bspMap = previewResources.load(path, BspMapAsset::class.java)
                modelBatch = previewResources.own(ModelBatch())
                this.bspMap = bspMap
                bspWorldRenderer = previewResources.own(BspWorldBatchRenderer(
                    worldRenderData = bspMap.worldRenderData,
                    worldBatchData = bspMap.worldBatchData,
                    lightmapAtlasPages = bspMap.lightmapAtlasPages,
                    assetManager = assetManager,
                ))
                // Free-flight inspection shows the whole world without gameplay visibility state.
                bspVisibleSurfaces = BooleanArray(bspMap.worldRenderData.surfaces.size) { true }
                val invisibleBrushes = bspMap.entities.filter {
                    it["classname"]?.startsWith("trigger_") == true || it["classname"] == "func_areaportal"
                }.mapNotNull { it["model"]?.removePrefix("*")?.toIntOrNull() }.toSet()
                bspMap.models.forEachIndexed { index, model ->
                    if (index !in invisibleBrushes) instances.add(ModelInstance(model))
                }
            }
        }
    }

    private fun resetCamera() {
        camera.position.setZero()
        camera.up.set(0f, 0f, 1f)
        camera.direction.set(0f, 1f, 0f)
        camera.update()
        cameraInputController = FlyingCameraController(camera)
        if (::stage.isInitialized) Gdx.input.inputProcessor = InputMultiplexer(stage, cameraInputController)
    }

    private fun createStage() {
        val skin = assetManager.getLoaded<Skin>(cakeSkin)
        stage = Stage(ScreenViewport())
        stage.viewport.update(Gdx.graphics.width, Gdx.graphics.height, true)
        resourceInfo = Label("", skin)
        frameTimeLabel = Label("", skin)
        filenameLabel = Label("No resource", skin).apply { setWrap(true) }
        controlsLabel = Label("Drop a resource to view | Escape: close", skin).apply { setWrap(true) }
        errorLabel = Label("", skin).apply { setWrap(true) }
        browserHost = Container<ContentBrowser>().apply {
            fill()
            isVisible = false
        }
        val panel = Table(skin).apply {
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
            add(panel).growX().row()
            add(browserHost).grow()
        })
        updateResourceInfo()
        Gdx.input.inputProcessor = InputMultiplexer(stage, cameraInputController)
    }

    private fun updateResourceInfo() {
        val frame = spriteFrame
        val bsp = bspMap
        val durationMs = sound?.let { SoundDurationRegistry.durationMs(it) }
        resourceInfo.setText(when {
            archiveEntryCount != null -> "Files: $archiveEntryCount"
            frame != null -> "Frame ${playback!!.frameIndex + 1}/${sprite!!.frames.size} | ${frame.width} x ${frame.height}"
            image != null -> "${image!!.width} x ${image!!.height}"
            md2Instance != null -> "Frame ${playback!!.frameIndex + 1}/$md2Frames: ${md2FrameNames[playback!!.frameIndex]} | Skin textures: $md2SkinTextures"
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

        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            if (sound != null) replaySound() else playback?.toggle()
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            Gdx.app.exit()
        } else if (sound != null && Gdx.input.isKeyJustPressed(Input.Keys.S)) {
            sound?.stop()
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT)) {
            playback?.step(1)
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT)) {
            playback?.step(-1)
        }

        playback?.let {
            it.update(Gdx.graphics.deltaTime)
            (md2Instance?.userData as? Md2CustomData)?.apply {
                frame1 = it.frameIndex
                frame2 = it.nextFrameIndex
                interpolation = it.interpolation
            }
            updateResourceInfo()
        }

        frameTime = measureTimeMillis {
            Gdx.gl.glClearColor(0.0f, 0.0f, 0.0f, 1f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
            camera.update()

            bspTime += Gdx.graphics.deltaTime
            if (archiveEntryCount == null) renderScene(camera)

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

            cameraInputController.update()
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
        clearArchiveThumbnails()
        archiveEntryCount = null
        if (::browserHost.isInitialized) {
            browserHost.actor = null
            browserHost.isVisible = false
        }
        currentFile = null
    }

    private fun closeResourcePreview() {
        sound?.stop()
        if (::previewResources.isInitialized) previewResources.close()
        bspWorldRenderer = null
        modelBatch = null
        instances.clear()
        image = null
        sound = null
        sprite = null
        playback = null
        bspMap = null
        bspVisibleSurfaces = BooleanArray(0)
        bspTime = 0f
        md2Instance = null
        md2Frames = 1
        md2SkinTextures = 0
        md2FrameNames = emptyList()
    }

    private fun addOwnedInstance(instance: ModelInstance) {
        instances.add(instance)
        previewResources.own(instance.model)
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

fun createGrid(size: Float, divisions: Int): ModelInstance {
    val modelBuilder = ModelBuilder()
    // grid is in XZ plane
    val lineGrid = modelBuilder.createLineGrid(
        divisions, divisions, size, size, Material(
            ColorAttribute.createDiffuse(Color.GREEN)
        ), (Usage.Position or Usage.ColorUnpacked).toLong()
    )
    val modelInstance = ModelInstance(lineGrid).apply {
        // rotate into XY plane
        transform.rotate(Vector3.X, -90f)
    }
    return modelInstance
}

fun createOriginArrows(size: Float): ModelInstance {
    val modelBuilder = ModelBuilder()
    val origin = modelBuilder.createXYZCoordinates(size, Material(), (Usage.Position or Usage.ColorUnpacked).toLong())
    return ModelInstance(origin)
}
