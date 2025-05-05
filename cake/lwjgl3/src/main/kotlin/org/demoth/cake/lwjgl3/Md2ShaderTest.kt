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

    // Dummy model data for demonstration
    private val vertexData = Array<Array<Vector3?>?>(numberOfVertices) {
        arrayOfNulls(numberOfFrames)
    }

    // v (vertical) component is flipped
    private val textureCoords = floatArrayOf(
        0.0f, 1.0f, // bottom left
        1.0f, 1.0f, // bottom right
        0.5f, 0.0f, // top
    )

    private lateinit var md2ShaderModel: Md2ShaderModel

    override fun create() {

        //ShaderProgram.pedantic = false // Disable strict checking to keep the example simple.

        val s = 0.5f // half size of the triangle
        // sample vertex data: 1 frame - triangle with 3 vertices
        vertexData[0]!![0] = Vector3(-s, -s, 0f)
        vertexData[1]!![0] = Vector3(s, -s, 0f)
        vertexData[2]!![0] = Vector3(s, s, 0f)

        // 1 frame - mirrored triangle
        vertexData[0]!![1] = Vector3(-s, -s, 0f)
        vertexData[1]!![1] = Vector3(s, -s, 0f)
        vertexData[2]!![1] = Vector3(-s, s, 0f)


        // Create the shader program
        val vertexShader = Gdx.files.internal("shaders/vat.glsl").readString() // Assuming vat.vert contains the shader code above
        val fragmentShader = Gdx.files.internal("shaders/md2-fragment.glsl").readString()
        md2Shader = ShaderProgram(vertexShader, fragmentShader)
        if (!md2Shader.isCompiled) {
            Gdx.app.error("Shader Error", md2Shader.log)
            Gdx.app.exit()
        }


        // Create the VAT Texture buffer
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


        // vertex animation texture with all positional data for all vertices and frames
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

        val diffuse = Texture(Gdx.files.internal("triangloid.png"))

        // Set texture parameters
        vat.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest); // Nearest filtering for exact frame sampling
        vat.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge); // Clamp to edge to avoid issues at boundaries

        // Create the mesh
        val vCount = numberOfVertices
        val iCount = (vCount - 2) * 3 // one triangle fan as in the sample

        val indices = ShortArray(iCount)


        /* fill indices ----------------------------------------------------------- */
        var p = 0
        for (v in 0..<vCount - 2) {
            indices[p++] = 0.toShort()
            indices[p++] = (v + 1).toShort()
            indices[p++] = (v + 2).toShort()
        }


        /* create the mesh -------------------------------------------------------- */
        val mesh = Mesh(
            true,
            vCount,
            iCount,
            VertexAttribute.TexCoords(1) // in future, normals can also be added here
        )

        mesh.setVertices(textureCoords)
        mesh.setIndices(indices)

        md2ShaderModel = Md2ShaderModel(
            mesh, vat to 0,
            diffuse to 1
        )


        // Set up camera or world transformation if needed
        worldTrans.idt(); // Identity matrix for now
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