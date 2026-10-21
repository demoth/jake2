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
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
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
import org.demoth.cake.ui.UiViewport
import java.io.File
import kotlin.system.measureTimeMillis

private const val GRID_SIZE = 16f
private const val GRID_DIVISIONS = 8

private val SUPPORTED_FORMATS = listOf(".bsp", ".pcx", ".md2", ".wal", ".tga", ".wav", ".sp2")

class CakeViewer(val args: Array<String>) : ApplicationAdapter() {
    private lateinit var batch: SpriteBatch
    private var image: Texture? = null
    private var sprite: Sp2Asset? = null
    private var spriteFrameIndex = 0
    private val spriteFrame: Sp2Asset.Frame? get() = sprite?.frames?.get(spriteFrameIndex)
    private var playingSpriteAnimation = false
    private var spriteElapsed = 0f
    private val spriteFrameDuration = 0.1f
    private var sound: Sound? = null
    private var bspMap: BspMapAsset? = null
    private var bspWorldRenderer: BspWorldBatchRenderer? = null
    private var bspVisibleSurfaces = BooleanArray(0)
    private var bspTime = 0f
    private lateinit var modelBatch: ModelBatch
    private lateinit var assetManager: AssetManager
    private lateinit var camera: Camera
    private lateinit var cameraInputController: CameraInputController
    // rendered with the default libgdx shader
    private val instances: MutableList<ModelInstance> = mutableListOf()
    // models created directly in the viewer (grid/origin), not owned by AssetManager
    private val ownedModels: MutableList<Model> = mutableListOf()
    private lateinit var environment: Environment
    private lateinit var stage: Stage
    private lateinit var resourceInfo: Label
    private lateinit var frameTimeLabel: Label
    private var frameTime = 0f // to have an idea of the fps

    // md2 related stuff
    private var md2Frames = 1
    private var md2SkinTextures = 0
    private var md2Instance: ModelInstance? = null // to control the model animation
    private var md2AnimationFrameTime = 0.1f
    private var md2FrameLerp = 0.0f
    private var playingMd2Animation = false

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

        val fileResolver = ViewerFileResolver(
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

        modelBatch = ModelBatch()

        when (file.extension.lowercase()) {
            "sp2" -> {
                sprite = assetManager.getLoaded<Sp2Asset>(file.absolutePath)
            }
            "wav" -> {
                sound = assetManager.getLoaded<Sound>(file.absolutePath).also { it.play() }
            }
            "pcx", "wal", "tga" -> {
                image = assetManager.getLoaded(file.absolutePath)
            }
            "md2" -> {
                // Viewer mode: prefer embedded MD2 skins, but allow rendering without them.
                val md2 = assetManager.getLoaded<Md2Asset>(
                    file.absolutePath,
                    Md2Loader.Parameters(
                        useDefaultSkinIfMissing = true,
                    ),
                )
                md2Instance = ModelInstance(md2.model).apply {
                    userData = Md2CustomData.empty()
                }
                md2Frames = md2.frames
                md2SkinTextures = md2.skins.size

                val tempRenderable = Renderable()
                val md2Shader = Md2Shader(
                    md2Instance!!.getRenderable(tempRenderable), // may not be obvious, but it's required for the shader initialization, the renderable is not used after that
                    DefaultShader.Config(
                        assetManager.getLoaded(md2VertexShader),
                        assetManager.getLoaded(md2FragmentShader),
                    )
                )
                md2Shader.init()

                modelBatch.dispose()
                modelBatch = ModelBatch(CakeShaderProvider(md2Shader))

                instances.add(md2Instance!!)
                addOwnedInstance(createOriginArrows(GRID_SIZE))
                addOwnedInstance(createGrid(GRID_SIZE, GRID_DIVISIONS))
            }
            "bsp" -> {
                val bspMap = assetManager.getLoaded<BspMapAsset>(file.absolutePath)
                this.bspMap = bspMap
                bspWorldRenderer = BspWorldBatchRenderer(
                    worldRenderData = bspMap.worldRenderData,
                    worldBatchData = bspMap.worldBatchData,
                    lightmapAtlasPages = bspMap.lightmapAtlasPages,
                    assetManager = assetManager,
                )
                // Free-flight inspection shows the whole world without gameplay visibility state.
                bspVisibleSurfaces = BooleanArray(bspMap.worldRenderData.surfaces.size) { true }
                bspMap.models.forEach { model ->
                    instances.add(ModelInstance(model))
                }
                addOwnedInstance(createOriginArrows(GRID_SIZE))
                addOwnedInstance(createGrid(GRID_SIZE, GRID_DIVISIONS))
            }
            else -> {
                println("Unsupported file format ${file.extension}")
                Gdx.app.exit()
                return
            }
        }

        batch = SpriteBatch()

        camera = PerspectiveCamera(90f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        camera.position.set(0f, 0f, 0f);
        camera.near = 1f
        camera.far = 4096f
        camera.up.set(0f, 0f, 1f) // make z up
        camera.direction.set(0f, 1f, 0f) // make y forward

        cameraInputController = FlyingCameraController(camera)

        environment = Environment()
        environment.set(ColorAttribute(ColorAttribute.AmbientLight, 0.8f, 0.8f, 0.8f, 1f))
        environment.add(DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.2f, 0.8f))
        createStage(file)
        Gdx.input.inputProcessor = InputMultiplexer(stage, cameraInputController)
    }

    private fun createStage(file: File) {
        val skin = assetManager.getLoaded<Skin>(cakeSkin)
        stage = Stage(UiViewport())
        stage.viewport.update(Gdx.graphics.width, Gdx.graphics.height, true)
        resourceInfo = Label("", skin)
        frameTimeLabel = Label("", skin)
        val controls = when {
            sound != null -> "Space: replay | S: stop | Escape: close"
            sprite != null || md2Instance != null -> "Space: play/pause | Left/Right: step | Escape: close"
            else -> "Escape: close"
        }
        val panel = Table(skin).apply {
            background = skin.getDrawable("window")
            pad(12f)
            defaults().left().growX().padBottom(4f)
            add(Label(file.name, skin).apply { setWrap(true) }).row()
            add(resourceInfo).row()
            add(Label(controls, skin).apply { setWrap(true) }).row()
            add(frameTimeLabel)
        }
        stage.addActor(Table().apply {
            setFillParent(true)
            top().left().pad(12f)
            add(panel).growX()
        })
        updateResourceInfo()
    }

    private fun updateResourceInfo() {
        val frame = spriteFrame
        val bsp = bspMap
        val durationMs = sound?.let { SoundDurationRegistry.durationMs(it) }
        resourceInfo.setText(when {
            frame != null -> "Frame ${spriteFrameIndex + 1}/${sprite!!.frames.size} | ${frame.width} x ${frame.height}"
            image != null -> "${image!!.width} x ${image!!.height}"
            md2Instance != null -> "Frames: $md2Frames | Skin textures: $md2SkinTextures"
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
        if (!::camera.isInitialized || !::batch.isInitialized || !::modelBatch.isInitialized) {
            return
        }

        val md2Data = md2Instance?.let { it.userData as? Md2CustomData }

        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            val currentSound = sound
            if (currentSound != null) {
                currentSound.stop()
                currentSound.play()
            } else if (md2Data != null) {
                playingMd2Animation = !playingMd2Animation
            } else if (sprite != null) {
                playingSpriteAnimation = !playingSpriteAnimation
            }
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            Gdx.app.exit()
        } else if (sound != null && Gdx.input.isKeyJustPressed(Input.Keys.S)) {
            sound?.stop()
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT)) {
            if (md2Data != null) {
                md2FrameLerp = 0f
                changeFrame(1, md2Data, md2Frames)
            } else if (sprite != null) {
                spriteElapsed = 0f
                changeSpriteFrame(1)
            }
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT)) {
            if (md2Data != null) {
                md2FrameLerp = 0f
                changeFrame(-1, md2Data, md2Frames)
            } else if (sprite != null) {
                spriteElapsed = 0f
                changeSpriteFrame(-1)
            }
        }

        if (playingSpriteAnimation) {
            spriteElapsed += Gdx.graphics.deltaTime
            val steps = (spriteElapsed / spriteFrameDuration).toInt()
            if (steps > 0) {
                changeSpriteFrame(steps)
                spriteElapsed %= spriteFrameDuration
            }
        }

        if (playingMd2Animation && md2Data != null) {
            md2FrameLerp += Gdx.graphics.deltaTime
            if (md2FrameLerp >=  md2AnimationFrameTime) {
                md2FrameLerp = 0f
                changeFrame(1, md2Data, md2Frames)
            }
            md2Data.interpolation = md2FrameLerp / md2AnimationFrameTime

        }

        frameTime = measureTimeMillis {
            Gdx.gl.glClearColor(0.0f, 0.0f, 0.0f, 1f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
            camera.update()

            bspTime += Gdx.graphics.deltaTime
            val currentTimeMs = (bspTime * 1000f).toInt()
            bspWorldRenderer?.render(
                camera = camera,
                visibleSurfaceMask = bspVisibleSurfaces,
                currentTimeMs = currentTimeMs,
                lightStyleResolver = { 1f },
                dynamicLights = emptyList(),
            )

            if (instances.isNotEmpty()) {
                modelBatch.begin(camera)
                instances.forEach {
                    modelBatch.render(it)
                }
                modelBatch.end()

            }

            bspWorldRenderer?.renderTranslucent(camera, bspVisibleSurfaces, currentTimeMs)

            batch.use {
                val currentFrame = spriteFrame
                val currentImage = currentFrame?.texture ?: image
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
        sound?.stop()
        bspWorldRenderer?.dispose()
        if (::stage.isInitialized) {
            Gdx.input.inputProcessor = null
            stage.dispose()
        }
        if (::batch.isInitialized) {
            batch.dispose()
        }
        ownedModels.forEach { it.dispose() }
        ownedModels.clear()
        if (::modelBatch.isInitialized) {
            modelBatch.dispose()
        }
        if (::assetManager.isInitialized) {
            assetManager.dispose()
        }
    }

    private fun changeSpriteFrame(delta: Int) {
        spriteFrameIndex = Math.floorMod(spriteFrameIndex + delta, sprite!!.frames.size)
        updateResourceInfo()
    }

    private fun addOwnedInstance(instance: ModelInstance) {
        instances.add(instance)
        ownedModels.add(instance.model)
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

private fun changeFrame(delta: Int, md2CustomData: Md2CustomData, md2Frames: Int) {
    // Keep frame1 as the previous pose and frame2 as the target pose for smooth interpolation.
    md2CustomData.frame1 = md2CustomData.frame2
    md2CustomData.frame2 = (md2CustomData.frame2 + delta) % md2Frames
    if (md2CustomData.frame1 < 0) {
        md2CustomData.frame1 += md2Frames
    }
    if (md2CustomData.frame2 < 0) {
        md2CustomData.frame2 += md2Frames
    }
    md2CustomData.interpolation = 0f
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
