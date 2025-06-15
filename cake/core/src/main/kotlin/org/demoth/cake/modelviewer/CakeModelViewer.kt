package org.demoth.cake.modelviewer

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
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
import jake2.qcommon.filesystem.PCX
import jake2.qcommon.filesystem.WAL
import ktx.graphics.use
import org.demoth.cake.ModelViewerResourceLocator
import org.demoth.cake.clientcommon.FlyingCameraController
import java.io.File
import kotlin.system.measureTimeMillis

private const val GRID_SIZE = 16f
private const val GRID_DIVISIONS = 8

private val SUPPORTED_FORMATS = listOf(".bsp", ".pcx", ".md2", ".wal")

class CakeModelViewer(val args: Array<String>) : ApplicationAdapter() {
    private lateinit var batch: SpriteBatch
    private var image: Texture? = null
    private lateinit var modelBatch: ModelBatch
    private lateinit var camera: Camera
    private lateinit var cameraInputController: CameraInputController
    // rendered with the default libgdx shader
    private val instances: MutableList<ModelInstance> = mutableListOf()
    private lateinit var environment: Environment
    private lateinit var font: BitmapFont
    private var frameTime = 0f // to have an idea of the fps

    // md2 related stuff
    private var md2Instance: ModelInstance? = null // to control the model animation
    private var md2AnimationFrameTime = 0.1f
    private var md2AnimationTime = 0.0f
    private var playingMd2Animation = false

    override fun create() {

        if (args.isEmpty() || SUPPORTED_FORMATS.none { args.first().endsWith(it) }) {
            println("Usage: provide $SUPPORTED_FORMATS file as the first argument")
            Gdx.app.exit()
        }
        val file = File(args[0])
        if (!file.exists() || !file.canRead()) {
            println("File $file does not exist or is unreadable")
            Gdx.app.exit()
        }
        val locator = ModelViewerResourceLocator(file.parent)
        font = BitmapFont()

        when (file.extension) {
            "pcx" -> {
                image = Texture(PCXTextureData(fromPCX(PCX(file.readBytes()))))
            }
            "wal" -> {
                image = Texture(WalTextureData(fromWal(WAL(file.readBytes()), readPaletteFile(Gdx.files.internal("q2palette.bin").read()))))
            }
            "md2" -> {

                val md2 = Md2ModelLoader(locator).loadAnimatedModel(file.absolutePath, null, 0)!!
                val model = createModel(md2.mesh, md2.diffuse, md2.vat)
                md2Instance = ModelInstance(model) // ok
                md2Instance!!.userData = Md2CustomData(
                    0,
                    if (md2.frames > 1) 1 else 0,
                    0f,
                    md2.frames
                )

                val tempRenderable = Renderable()
                val md2Shader = Md2Shader(
                    md2Instance!!.getRenderable(tempRenderable), // may not be obvious, but it's required for the shader initialization, the renderable is not used after that
                    DefaultShader.Config(
                        Gdx.files.internal("shaders/vat.glsl").readString(),
                        null, // use default fragment shader
                    )
                )
                md2Shader.init()

                modelBatch = ModelBatch(Md2ShaderProvider(md2Shader))

                instances.add(md2Instance!!)
                instances.add(createOriginArrows(GRID_SIZE))
                instances.add(createGrid(GRID_SIZE, GRID_DIVISIONS))
            }
            "bsp" -> {
                modelBatch = ModelBatch()
//                models.add(BspLoader().loadBSPModelWireFrame(file).transformQ2toLibgdx())
//                models.addAll(BspLoader(gameDir).loadBspModels(file))
                instances.add(createOriginArrows(GRID_SIZE))
                instances.add(createGrid(GRID_SIZE, GRID_DIVISIONS))
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
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            playingMd2Animation = !playingMd2Animation
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            Gdx.app.exit()
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT)) {
            md2AnimationTime = 0f
            changeFrame(1, md2Instance!!.getMd2CustomData())
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT)) {
            md2AnimationTime = 0f
            changeFrame(-1, md2Instance!!.getMd2CustomData())
        }

        if (playingMd2Animation) {
            md2AnimationTime += Gdx.graphics.deltaTime
            if (md2AnimationTime >  md2AnimationFrameTime) {
                md2AnimationTime = 0f
                changeFrame(1, md2Instance!!.getMd2CustomData())
            }
            md2Instance!!.getMd2CustomData().interpolation = md2AnimationTime / md2AnimationFrameTime

        }

        frameTime = measureTimeMillis {
            Gdx.gl.glClearColor(0.1f, 0.1f, 0.1f, 1f)
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
        batch.dispose()
        image?.dispose()
        instances.forEach { it.model.dispose() }
        modelBatch.dispose()
    }
}

private fun changeFrame(delta: Int, md2CustomData: Md2CustomData) {
    // advance animation frames: frame1++ frame2++, keep in mind number of frames
    md2CustomData.frame1 = (md2CustomData.frame1 + delta) % md2CustomData.frames
    md2CustomData.frame2 = (md2CustomData.frame2 + delta) % md2CustomData.frames
    if (md2CustomData.frame1 < 0) {
        md2CustomData.frame1 += md2CustomData.frames
    }
    if (md2CustomData.frame2 < 0) {
        md2CustomData.frame2 += md2CustomData.frames
    }
    md2CustomData.interpolation = 0f
}

fun ModelInstance.getMd2CustomData(): Md2CustomData = userData as Md2CustomData

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
