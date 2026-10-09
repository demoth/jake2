package org.demoth.cake.stages.ingame

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import org.demoth.cake.describeWindowBuffer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/** Opt-in driver test: CAKE_VIDEO_GL_TESTS=1 ./gradlew :cake:core:test --tests '*SceneFrameBufferGlTest' */
@EnabledIfEnvironmentVariable(named = "CAKE_VIDEO_GL_TESTS", matches = "1")
class SceneFrameBufferGlTest {
    @Test
    fun sceneDepthAndMultisampleResolveSurviveRecreation() {
        var failure: Throwable? = null
        val configuration = Lwjgl3ApplicationConfiguration().apply {
            setInitialVisible(false)
            disableAudio(true)
            setWindowedMode(160, 120)
            setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL32, 3, 2)
            setBackBufferConfig(8, 8, 8, 8, 24, 0, 0)
        }
        Lwjgl3Application(object : ApplicationAdapter() {
            override fun create() {
                try {
                    assertTrue(describeWindowBuffer().contains("depth=24"))
                    assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError(), "Window attribute queries")
                    for (depth in listOf(16, 24, 32)) {
                        for (samples in listOf(0, 4)) {
                            for (size in listOf(64 to 48, 96 to 80)) {
                                verifyScene(depth, samples, size.first, size.second)
                            }
                        }
                    }
                } catch (error: Throwable) {
                    failure = error
                } finally {
                    Gdx.app.exit()
                }
            }
        }, configuration)
        failure?.let { throw it }
    }

    private fun verifyScene(depth: Int, samples: Int, width: Int, height: Int) {
        val scene = SceneFrameBuffer.create(width, height, depth, samples)
        val shader = ShaderProgram(
            "#version 150\nin vec3 a_position; void main() { gl_Position = vec4(a_position, 1.0); }",
            "#version 150\nuniform vec4 u_color; out vec4 color; void main() { color = u_color; }",
        )
        val mesh = Mesh(true, 3, 0, VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"))
        try {
            assertEquals(depth, scene.depthBits)
            if (samples > 0) assertTrue(scene.samples > 0, "Driver must support scene MSAA for this test")
            assertTrue(shader.isCompiled, shader.log)
            scene.renderTarget.begin()
            Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
            Gdx.gl.glDepthMask(true)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
            Gdx.gl.glDepthFunc(GL20.GL_LESS)
            Gdx.gl.glDisable(GL20.GL_BLEND)
            shader.bind()
            mesh.setVertices(floatArrayOf(-1f, -1f, -0.5f, 1f, -1f, -0.5f, -1f, 1f, -0.5f))
            shader.setUniformf("u_color", 1f, 0f, 0f, 1f)
            mesh.render(shader, GL20.GL_TRIANGLES)
            // A farther green triangle must not overwrite the nearer red triangle.
            mesh.setVertices(floatArrayOf(-1f, -1f, 0.5f, 1f, -1f, 0.5f, -1f, 1f, 0.5f))
            shader.setUniformf("u_color", 0f, 1f, 0f, 1f)
            mesh.render(shader, GL20.GL_TRIANGLES)
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
            scene.renderTarget.end()
            scene.resolve()
            scene.resolved.begin()
            val pixels = Pixmap.createFromFrameBuffer(0, 0, width, height)
            try {
                assertEquals(0xff0000ff.toInt(), pixels.getPixel(2, 2), "Depth test and resolve")
                if (samples > 0) {
                    assertTrue((0 until width).any { x ->
                        (0 until height).any { y -> ((pixels.getPixel(x, y) ushr 24) and 255) in 1..254 }
                    }, "Resolved image should contain antialiased triangle edges")
                }
            } finally {
                pixels.dispose()
                scene.resolved.end()
            }
            assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError(), scene.description)
        } finally {
            mesh.dispose()
            shader.dispose()
            scene.dispose()
        }
    }
}
