package org.demoth.cake.lwjgl3

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g3d.utils.MeshBuilder
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

    // Dummy model data for demonstration
    private val vertexData = Array<Array<Vector3?>?>(numberOfVertices) {
        arrayOfNulls(numberOfFrames)
    }
    private val textureCoords = FloatArray(numberOfVertices * 2)

    private lateinit var md2ShaderModel: Md2ShaderModel

    override fun create() {
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
        val vertexShader =
            Gdx.files.internal("shaders/vat.glsl").readString() // Assuming vat.vert contains the shader code above
        val fragmentShader = "void main() { gl_FragColor = vec4(1.0); }" // Simple dummy fragment shader
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

        // Set texture parameters
        vat.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest); // Nearest filtering for exact frame sampling
        vat.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge); // Clamp to edge to avoid issues at boundaries

        // Create the mesh
        // We only need position (can be dummy) and texture coordinates for VAT lookup
        val meshBuilder = MeshBuilder()
        meshBuilder.begin(
            VertexAttributes(
                VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
                VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_index"),
                VertexAttribute.TexCoords(0) // 2 floats per vertex

            ),
            GL20.GL_TRIANGLES
        )


        // Add vertices. We can use dummy positions here as the actual positions come from the texture.
        // The texture coordinates are crucial as they determine which vertex's data is sampled from the VAT.
        for (i in 0..<numberOfVertices) {
            // Dummy position, the real position comes from the texture
            meshBuilder.vertex( 0f, 0f, 0f, i.toFloat(), 0f, 0f)
        }


        // Add indices to form triangles. This depends on your model's topology.
        // For a simple demonstration, let's assume a strip or just points.
        // If it's a complex model, you'd load your index data here.
        // Example: simple triangle strip (assuming numberOfVertices >= 3)
        if (numberOfVertices >= 3) {
            for (i in 0..<numberOfVertices - 2) {
                meshBuilder.triangle(i.toShort(), (i + 1).toShort(), (i + 2).toShort())
            }
        } else if (numberOfVertices > 0) {
            // If less than 3 vertices, just render as points for visualization
            // You would adjust the render call below to GL_POINTS
        }
        val mesh = meshBuilder.end();
        md2ShaderModel = Md2ShaderModel(mesh, vat)


        // Set up camera or world transformation if needed
        worldTrans.idt(); // Identity matrix for now
    }

    override fun render() {

        // Clear the screen
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)


        // Update animation time
        //animationTime += Gdx.graphics.deltaTime
        //md2ShaderModel.interpolation = (animationTime % animationDuration) / animationDuration
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