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
import org.demoth.cake.clientcommon.FlyingCameraController
import org.demoth.cake.modelviewer.Md2ModelLoader
import org.demoth.cake.modelviewer.Md2ShaderModel


class Md2ShaderTest : ApplicationAdapter(), Disposable {

    private lateinit var md2Shader: ShaderProgram
    private var animationTime = 0f

    private val animationDuration = 0.1f // Example: 2 seconds animation duration

    private lateinit var camera: Camera
    private lateinit var cameraInputController : CameraInputController

    private lateinit var md2ShaderModel: Md2ShaderModel
    private var playing = false

    override fun create() {
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL)
        Gdx.gl.glEnable(GL20.GL_CULL_FACE)
        Gdx.gl.glCullFace(GL20.GL_BACK)


        // copied from model viewer
        camera = PerspectiveCamera(90f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        camera.position.set(0f, 0f, 0f);
        camera.near = 1f
        camera.far = 4096f
        camera.up.set(0f, 0f, 1f) // make z up
        camera.direction.set(0f, 1f, 0f) // make y forward

        cameraInputController = FlyingCameraController(camera)
        Gdx.input.inputProcessor = cameraInputController

        md2Shader = createShaderProgram()
        val pathToFile = "/home/daniil/.steam/steam/steamapps/common/Quake 2/baseq2/models/monsters/infantry"
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
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            playing = !playing
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            Gdx.app.exit()
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT)) {
            changeFrame(1)
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT)) {
            changeFrame(-1)
        }

        camera.update()

        // Clear the screen
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)


        val interpolation = animationTime / animationDuration
        md2ShaderModel.interpolation = interpolation
        md2ShaderModel.render(md2Shader, camera.combined)

        if (playing) {
            animationTime += Gdx.graphics.deltaTime

            if (animationTime > animationDuration) {
                changeFrame(1)
            }
        }
    }

    private fun changeFrame(delta: Int) {
        animationTime = 0f
        // advance animation frames: frame1++ frame2++, keep in mind number of frames
        md2ShaderModel.frame1 = (md2ShaderModel.frame1 + delta) % md2ShaderModel.frames
        if (md2ShaderModel.frame1 < 0) {
            md2ShaderModel.frame1 += md2ShaderModel.frames
        }
        if (md2ShaderModel.frame2 < 0) {
            md2ShaderModel.frame2 += md2ShaderModel.frames
        }
        md2ShaderModel.frame2 = (md2ShaderModel.frame2 + delta) % md2ShaderModel.frames
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
    config.setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode())
//    config.setResizable(true)
//    config.setWindowedMode(width, height)

    // fixme: didn't really quite get why it has to be explicitly loaded,
    // otherwise PerspectiveCamera(..) raises UnsatisfiedLinkError
    SharedLibraryLoader().load("gdx")

    Lwjgl3Application(Md2ShaderTest(), config)
}