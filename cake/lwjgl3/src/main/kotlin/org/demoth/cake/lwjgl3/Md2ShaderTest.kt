package org.demoth.cake.lwjgl3

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.BufferUtils
import com.badlogic.gdx.utils.Disposable
import org.demoth.cake.modelviewer.CustomTextureData
import org.demoth.cake.modelviewer.Md2ShaderModel
import java.nio.FloatBuffer


class Md2ShaderTest : ApplicationAdapter(), Disposable {

    private lateinit var md2Shader: ShaderProgram
    private val worldTrans = Matrix4()
    private var animationTime = 0f

    // --- Model Data (Replace with your actual loaded data) ---
    private val numberOfVertices = 3 // Example: 100 vertices
    private val numberOfFrames = 2 // Example: 60 animation frames
    private val animationDuration = 2.0f // Example: 2 seconds animation duration

    private var direction = 1f



    private lateinit var md2ShaderModel: Md2ShaderModel

    override fun create() {
        md2Shader = createShaderProgram()

        // vertex animation texture with all positional data for all vertices and frames
        val vat = createVatTexture()

        val diffuse = Texture(Gdx.files.internal("triangloid.png"))

        val mesh = createMesh()

        md2ShaderModel = Md2ShaderModel(
            mesh,
            vat to 0,
            diffuse to 1
        )
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

    /**
     * The Mesh holds the vertex attributes, which in the VAT scenario are only texture coordinates.
     * The indices are implicitly provided and normals are just skipped in this example.
     *
     */
    private fun createMesh(): Mesh {

        val iCount = (numberOfVertices - 2) * 3 // one triangle fan as in the sample

        val indices = ShortArray(iCount)

        /* fill indices ----------------------------------------------------------- */
        var p = 0
        for (v in 0..<numberOfVertices - 2) {
            indices[p++] = 0.toShort()
            indices[p++] = (v + 1).toShort()
            indices[p++] = (v + 2).toShort()
        }

        val mesh = Mesh(
            true,
            numberOfVertices,
            iCount,
            VertexAttribute.TexCoords(1) // in future, normals can also be added here
        )

        // v (vertical) component is flipped
        val textureCoords = floatArrayOf(
            0.0f, 1.0f, // bottom left
            1.0f, 1.0f, // bottom right
            0.5f, 0.0f, // top
        )

        mesh.setVertices(textureCoords)
        mesh.setIndices(indices)
        return mesh
    }

    private fun createVatTexture(): Texture {
        // vertex data represent a 2d array of model vertices and frames
        val vertexData = Array<Array<Vector3?>?>(numberOfVertices) {
            arrayOfNulls(numberOfFrames)
        }

        val s = 0.5f // half size of the triangle
        // sample vertex data: 2 frame - triangle with 3 vertices
        vertexData[0]!![0] = Vector3(-s, -s, 0f)
        vertexData[1]!![0] = Vector3(s, -s, 0f)
        vertexData[2]!![0] = Vector3(s, s, 0f)

        // 1 frame - mirrored triangle
        vertexData[0]!![1] = Vector3(-s, -s, 0f)
        vertexData[1]!![1] = Vector3(s, -s, 0f)
        vertexData[2]!![1] = Vector3(-s, s, 0f)

        // Create the VAT Texture buffer as a linear array
        val vertexBuffer: FloatBuffer =
            BufferUtils.newFloatBuffer(numberOfVertices * numberOfFrames * 3) // 3 floats per Vector3
        for (frameIndex in 0..<numberOfFrames) { // Iterate through frames (rows in texture)
            for (vertexIndex in 0..<numberOfVertices) { // Iterate through vertices (columns in texture)
                vertexBuffer.put(vertexData[vertexIndex]!![frameIndex]!!.x)
                vertexBuffer.put(vertexData[vertexIndex]!![frameIndex]!!.y)
                vertexBuffer.put(vertexData[vertexIndex]!![frameIndex]!!.z)
            }
        }
        vertexBuffer.flip() // Prepare the buffer for reading

        val vat = Texture(
            CustomTextureData(
                numberOfVertices,  // width (vertices)
                numberOfFrames,  // height (frames)
                GL30.GL_RGB16F,
                GL30.GL_RGB,
                GL20.GL_FLOAT,
                vertexBuffer
            )
        )
        vat.setFilter(
            Texture.TextureFilter.Nearest,
            Texture.TextureFilter.Nearest
        ); // Nearest filtering for exact frame sampling
        vat.setWrap(
            Texture.TextureWrap.ClampToEdge,
            Texture.TextureWrap.ClampToEdge
        ); // Clamp to edge to avoid issues at boundaries
        return vat
    }

    override fun render() {

        // Clear the screen
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)


        // bounce animation
        if (animationTime <= 0f) {
            animationTime = 0f
            direction = 1f
        } else if (animationTime > animationDuration) {
            animationTime = animationDuration
            direction = -1f
        }
        animationTime += Gdx.graphics.deltaTime * direction
        val interpolation = animationTime / animationDuration
        md2ShaderModel.interpolation = interpolation
        md2ShaderModel.render(md2Shader, worldTrans)
    }

    override fun dispose() {
        md2Shader.dispose()
        md2ShaderModel.dispose()
    }
}


fun main() {
    val config = Lwjgl3ApplicationConfiguration()
    config.setResizable(true)
    config.setWindowedMode(1024, 768)
    Lwjgl3Application(Md2ShaderTest(), config)
}