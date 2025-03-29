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
import com.badlogic.gdx.graphics.g3d.shaders.BaseShader
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController
import com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.glutils.ShaderProgram
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
    private val models: MutableList<ModelInstance> = mutableListOf()
    private lateinit var environment: Environment
    private lateinit var font: BitmapFont
    private var frameTime = 0f
    private var model: Model? = null
    private var md2AnimationTimer = 0.1f


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
                model = Md2ModelLoader(locator).loadMd2Model(
                    modelName = file.path, // will be passed to the ResourceLocator
                    playerSkin = null,
                )
                // todo: uncomment when implemented
                val modelInstance = ModelInstance(model)
                modelInstance.userData = "md2animated" // marker to use the custom shader
                models.add(modelInstance)
                models.add(createOriginArrows(GRID_SIZE))
                models.add(createGrid(GRID_SIZE, GRID_DIVISIONS))
            }
            "bsp" -> {
//                models.add(BspLoader().loadBSPModelWireFrame(file).transformQ2toLibgdx())
//                models.addAll(BspLoader(gameDir).loadBspModels(file))
                models.add(createOriginArrows(GRID_SIZE))
                models.add(createGrid(GRID_SIZE, GRID_DIVISIONS))
            }
        }

        batch = SpriteBatch()

        modelBatch = ModelBatch(CakeShaderProvider())
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
        // advance the model animation (if any) each 0.1 seconds
        md2AnimationTimer -= Gdx.graphics.deltaTime
        if (md2AnimationTimer < 0f) {
            md2AnimationTimer = 0.1f
            //model?.nextFrame()
        }

        frameTime = measureTimeMillis {
            if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
                Gdx.app.exit()
            }
            Gdx.gl.glClearColor(0.1f, 0.1f, 0.1f, 1f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
            camera.update();


            if (models.isNotEmpty()) {
                modelBatch.begin(camera)
                models.forEach {
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
        models.forEach { it.model.dispose() }
        modelBatch.dispose()
    }
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

class CakeShaderProvider: DefaultShaderProvider() {
    private val cakeMd2Shader = CakeMd2AnimationShader()

    override fun createShader(renderable: Renderable): Shader {
        return if (renderable.userData == "md2animated") {
            cakeMd2Shader
        } else {
            // return a default one for the static objects
            super.createShader(renderable)
        }
    }
}

class CakeMd2AnimationShader: BaseShader() {
    // the idea behind this shader is to calculate vertex positions on the GPU - interpolate two animation frames
    // the fragment shader can stay the same as the default one
    override fun init() {

        /*
        Initializes the Shader, must be called before the Shader can be used.
        This typically compiles a ShaderProgram,
        fetches uniform locations and performs other preparations for usage of the Shader.
         */
        val vertexShader = """
            attribute vec3 a_position;
            attribute vec2 a_texCoord0;

            uniform mat4 u_worldTrans;
            uniform mat4 u_projViewTrans;

            varying vec2 v_texCoord0;

            void main() {
            	v_texCoord0 = a_texCoord0;
            	gl_Position = u_projViewTrans * u_worldTrans * vec4(a_position, 1.0);
            }
        """.trimIndent()
        this.program = ShaderProgram(DefaultShader.getDefaultVertexShader(), DefaultShader.getDefaultFragmentShader())
        // ?
    }

    override fun render(renderable: Renderable?, combinedAttributes: Attributes?) {
        super.render(renderable, combinedAttributes)
    }

    override fun compareTo(other: Shader?): Int {
        if (other == null) return -1
        if (other == this) return 0
        return 0 // FIXME compare shaders on their impact on performance
    }

    override fun canRender(instance: Renderable?): Boolean {
        return true // todo
    }

}
