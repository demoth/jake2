package org.demoth.cake.modelviewer

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import ktx.assets.TextAssetLoader
import ktx.graphics.use
import org.demoth.cake.ByteArrayLoader
import org.demoth.cake.initializeShaderCompatibility
import org.demoth.cake.md2FragmentShader
import org.demoth.cake.assets.BspLoader
import org.demoth.cake.assets.BspMapAsset
import org.demoth.cake.assets.Md2Asset
import org.demoth.cake.assets.Md2CustomData
import org.demoth.cake.assets.Md2Loader
import org.demoth.cake.assets.ObjectLoader
import org.demoth.cake.assets.PcxLoader
import org.demoth.cake.assets.Md2Shader
import org.demoth.cake.assets.Md2ShaderProvider
import org.demoth.cake.assets.WalLoader
import org.demoth.cake.assets.getLoaded
import org.demoth.cake.md2VatShader
import java.io.File
import kotlin.system.measureTimeMillis

private const val GRID_SIZE = 16f
private const val GRID_DIVISIONS = 8

private val SUPPORTED_FORMATS = listOf(".bsp", ".pcx", ".md2", ".wal")

class CakeModelViewer(val args: Array<String>) : ApplicationAdapter() {
    private lateinit var batch: SpriteBatch
    private var image: Texture? = null
    private lateinit var modelBatch: ModelBatch
    private lateinit var assetManager: AssetManager
    private lateinit var camera: Camera
    private lateinit var cameraInputController: CameraInputController
    // rendered with the default libgdx shader
    private val instances: MutableList<ModelInstance> = mutableListOf()
    // models created directly in the viewer (grid/origin), not owned by AssetManager
    private val ownedModels: MutableList<Model> = mutableListOf()
    private lateinit var environment: Environment
    private lateinit var font: BitmapFont
    private var frameTime = 0f // to have an idea of the fps

    // md2 related stuff
    private var md2Frames = 1
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

        val fileResolver = ModelViewerFileResolver(
            openedFilePath = file.absolutePath,
            basedir = System.getProperty("basedir"),
            gamemod = System.getProperty("game"),
        )
        assetManager = AssetManager(fileResolver).apply {
            setLoader(String::class.java, TextAssetLoader(fileResolver))
            setLoader(Any::class.java, ObjectLoader(fileResolver))
            setLoader(ByteArray::class.java, ByteArrayLoader(fileResolver))
            setLoader(Texture::class.java, "pcx", PcxLoader(fileResolver))
            setLoader(Texture::class.java, "wal", WalLoader(fileResolver))
            setLoader(BspMapAsset::class.java, "bsp", BspLoader(fileResolver))
            setLoader(Md2Asset::class.java, "md2", Md2Loader(fileResolver))
        }

        modelBatch = ModelBatch()
        font = BitmapFont()

        when (file.extension.lowercase()) {
            "pcx" -> {
                image = assetManager.getLoaded(file.absolutePath)
            }
            "wal" -> {
                image = assetManager.getLoaded(file.absolutePath)
            }
            "md2" -> {
                // Viewer mode: do not rely on embedded skin paths from the MD2 file.
                // This keeps standalone previews usable even when skin assets are absent.
                val md2 = assetManager.getLoaded<Md2Asset>(
                    file.absolutePath,
                    Md2Loader.Parameters(
                        loadEmbeddedSkins = false,
                        useDefaultSkinIfMissing = true,
                    ),
                )
                md2Instance = ModelInstance(md2.model).apply {
                    userData = Md2CustomData.empty()
                }
                md2Frames = md2.frames

                val tempRenderable = Renderable()
                val md2Shader = Md2Shader(
                    md2Instance!!.getRenderable(tempRenderable), // may not be obvious, but it's required for the shader initialization, the renderable is not used after that
                    DefaultShader.Config(
                        assetManager.getLoaded(md2VatShader),
                        assetManager.getLoaded(md2FragmentShader),
                    )
                )
                md2Shader.init()

                modelBatch.dispose()
                modelBatch = ModelBatch(Md2ShaderProvider(md2Shader))

                instances.add(md2Instance!!)
                addOwnedInstance(createOriginArrows(GRID_SIZE))
                addOwnedInstance(createGrid(GRID_SIZE, GRID_DIVISIONS))
            }
            "bsp" -> {
                val bspMap = assetManager.getLoaded<BspMapAsset>(file.absolutePath)
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
        Gdx.input.inputProcessor = cameraInputController

        environment = Environment()
        environment.set(ColorAttribute(ColorAttribute.AmbientLight, 0.8f, 0.8f, 0.8f, 1f))
        environment.add(DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.2f, 0.8f))
    }

    override fun render() {
        if (!::camera.isInitialized || !::batch.isInitialized || !::modelBatch.isInitialized) {
            return
        }

        val md2Data = md2Instance?.let { it.userData as? Md2CustomData }

        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            playingMd2Animation = !playingMd2Animation
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            Gdx.app.exit()
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT) && md2Data != null) {
            md2FrameLerp = 0f
            changeFrame(1, md2Data, md2Frames)
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT) && md2Data != null) {
            md2FrameLerp = 0f
            changeFrame(-1, md2Data, md2Frames)
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


            if (instances.isNotEmpty()) {
                modelBatch.begin(camera)
                instances.forEach {
                    modelBatch.render(it)
                }
                modelBatch.end()

            }

            batch.use {
                // draw frame time in the bottom left corner
                font.draw(batch, "Frame time ms: $frameTime", 0f, font.lineHeight)

                if (image != null) {
                    // draw image in the center of the screen
                    val x = (Gdx.graphics.width - image!!.width) / 2f
                    val y = (Gdx.graphics.height - image!!.height) / 2f
                    it.draw(image, x, y)
                }

            }

            cameraInputController.update()
        }.toFloat()
    }

    override fun dispose() {
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
