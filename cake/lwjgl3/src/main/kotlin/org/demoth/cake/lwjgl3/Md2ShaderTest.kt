package org.demoth.cake.lwjgl3

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.SharedLibraryLoader
import org.demoth.cake.ModelViewerResourceLocator
import org.demoth.cake.modelviewer.Md2ModelLoader
import org.demoth.cake.modelviewer.Md2ShaderModel


class Md2ShaderTest : ApplicationAdapter(), Disposable {

    private lateinit var md2Shader: ShaderProgram
    private var animationTime = 0f

    private val animationDuration = 1f // Example: 2 seconds animation duration

    private lateinit var camera: Camera
    private lateinit var cameraInputController : CameraInputController

    private lateinit var md2ShaderModel: Md2ShaderModel

    override fun create() {
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL)
        Gdx.gl.glEnable(GL20.GL_CULL_FACE)
        Gdx.gl.glCullFace(GL20.GL_BACK)

        camera = PerspectiveCamera(67f, width.toFloat(), height.toFloat())
        camera.near = 0.1f
        camera.far = 1000f
        cameraInputController = CameraInputController(camera)
        Gdx.input.inputProcessor = cameraInputController

        md2Shader = createShaderProgram()
        val pathToFile = "berserk"
        val locator = ModelViewerResourceLocator(pathToFile)
        val md2 = Md2ModelLoader(locator).loadAnimatedModel("$pathToFile/tris.md2", null, 0)?.apply {
            frame1 = 0
            frame2 = if (frames > 1) 1 else 0
        }
        md2ShaderModel = md2!!
    }

    private fun createShaderProgram(): ShaderProgram {
        //ShaderProgram.pedantic = false // Disable strict checking to keep the example simple.

        val vertexShader = Gdx.files.internal("shaders/vat.glsl").readString() // Assuming vat.vert contains the shader code above
        val fragmentShader = Gdx.files.internal("shaders/md2-fragment.glsl").readString()

        val shaderProgram = ShaderProgram(vertexShader, fragmentShader)
        if (!shaderProgram.isCompiled) {
            Gdx.app.error("Shader Error", md2Shader.log)
            Gdx.app.exit()
        }
        return shaderProgram
    }

    override fun render() {
        camera.update()

        // Clear the screen
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)


        val interpolation = animationTime / animationDuration
        md2ShaderModel.interpolation = interpolation
        md2ShaderModel.render(md2Shader, camera.combined)

        animationTime += Gdx.graphics.deltaTime

        if (animationTime > animationDuration) {
            animationTime = 0f
            // advance animation frames: frame1++ frame2++, keep in mind number of frames
            md2ShaderModel.frame1 = md2ShaderModel.frame2
            val nextFrame = (md2ShaderModel.frame2 + 1) % md2ShaderModel.frames
            md2ShaderModel.frame2 = nextFrame
        }
    }

    override fun dispose() {
        md2Shader.dispose()
        md2ShaderModel.dispose()
    }
}

private const val width = 1024
private const val height = 768

fun main() {
    val config = Lwjgl3ApplicationConfiguration()
    config.setResizable(true)
    config.setWindowedMode(width, height)

    // fixme: didn't really quite get why it has to be explicitly loaded,
    // otherwise PerspectiveCamera(..) raises UnsatisfiedLinkError
    SharedLibraryLoader().load("gdx")

    Lwjgl3Application(Md2ShaderTest(), config)
}