package org.demoth.cake.lwjgl3

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.SharedLibraryLoader
import org.demoth.cake.ModelViewerResourceLocator
import org.demoth.cake.clientcommon.FlyingCameraController
import org.demoth.cake.modelviewer.*


class Md2ShaderTest : ApplicationAdapter(), Disposable {

    private var animationTime = 0f
    private val animationDuration = 0.2f // Example: 2 seconds animation duration

    private lateinit var camera: Camera
    private lateinit var cameraInputController : CameraInputController

    private var playing = false
    private lateinit var modelBatch: ModelBatch
    private lateinit var modelInstance: ModelInstance


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

        val md2 = loadMd2Format()

        val model = createModel(md2.mesh, md2.diffuse, md2.vat)
        modelInstance = ModelInstance(model) // ok
        modelInstance.userData = Md2CustomData(
            0,
            if (md2.frames > 1) 1 else 0,
            0f,
            md2.frames
        )

        val tempRenderable = Renderable()
        val md2Shader = Md2Shader(
            modelInstance.getRenderable(tempRenderable), // may not be obvious, but it's required for the shader initialization, the renderable is not used after that
            DefaultShader.Config(
                Gdx.files.internal("shaders/vat.glsl").readString(),
                null, // use default fragment shader
            )
        )
        md2Shader.init()

        modelBatch = ModelBatch(Md2ShaderProvider(md2Shader))
    }

    private fun loadMd2Format(): Md2ShaderModel {
        val pathToFile = "/home/daniil/.steam/steam/steamapps/common/Quake 2/baseq2/models/monsters/infantry"
        val locator = ModelViewerResourceLocator(pathToFile)
        return Md2ModelLoader(locator).loadAnimatedModel("$pathToFile/tris.md2", null, 0)!!
    }

    override fun render() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            playing = !playing
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            Gdx.app.exit()
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT)) {
            changeFrame(1, modelInstance.getMd2CustomData())
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT)) {
            changeFrame(-1, modelInstance.getMd2CustomData())
        }

        camera.update()

        // Clear the screen
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        // inside render()
        modelBatch.begin(camera)
        modelBatch.render(modelInstance)
        modelBatch.end()

        modelInstance.getMd2CustomData().interpolation = animationTime / animationDuration

        if (playing) {
            animationTime += Gdx.graphics.deltaTime

            if (animationTime > animationDuration) {
                changeFrame(1, modelInstance.getMd2CustomData())
            }
        }
    }

    private fun changeFrame(delta: Int, md2CustomData: Md2CustomData) {
        animationTime = 0f
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

    override fun dispose() {
        modelInstance.model.dispose()
    }
}

fun ModelInstance.getMd2CustomData(): Md2CustomData = userData as Md2CustomData

private const val width = 1024
private const val height = 768


fun main() {
    val config = Lwjgl3ApplicationConfiguration()
//    config.setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode())
    config.setResizable(true)
    config.setWindowedMode(width, height)

    // fixme: didn't really quite get why it has to be explicitly loaded,
    // otherwise PerspectiveCamera(..) raises UnsatisfiedLinkError
    SharedLibraryLoader().load("gdx")

    Lwjgl3Application(Md2ShaderTest(), config)
}