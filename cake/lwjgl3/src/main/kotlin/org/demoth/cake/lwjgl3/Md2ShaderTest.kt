package org.demoth.cake.lwjgl3

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.TextureData.TextureDataType
import com.badlogic.gdx.graphics.g3d.utils.MeshBuilder
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.BufferUtils
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.GdxRuntimeException
import java.nio.FloatBuffer
import kotlin.math.sin


class Md2ShaderTest : ApplicationAdapter(), Disposable {

    private lateinit var md2Shader: ShaderProgram
    private lateinit var mesh: Mesh
    private lateinit var vertexTexture: Texture // The Vertex Animation Texture
    private val worldTrans = Matrix4()
    private var animationTime = 0f

    // --- Model Data (Replace with your actual loaded data) ---
    private val numberOfVertices = 100 // Example: 100 vertices
    private val numberOfFrames = 60 // Example: 60 animation frames
    private val animationDuration = 2.0f // Example: 2 seconds animation duration

    // Dummy model data for demonstration
    private val vertexData = Array<Array<Vector3?>?>(numberOfVertices) {
        arrayOfNulls<Vector3>(numberOfFrames)
    }
    private val textureCoords = FloatArray(numberOfVertices * 2)

    override fun create() {
        // --- Dummy Data Initialization (Replace with your actual loading) ---
        for (i in 0..<numberOfVertices) {
            // Simple animation: vertices move up and down in a wave
            for (j in 0..<numberOfFrames) {
                val y =
                    sin(j.toDouble() / (numberOfFrames - 1) * Math.PI * 2 + i.toDouble() / numberOfVertices * Math.PI).toFloat()
                vertexData[i]!![j] = Vector3(i.toFloat() / numberOfVertices - 0.5f, y * 0.1f, 0f)
            }
            // Simple texture coordinates mapping x to vertex index
            textureCoords[i * 2] = i.toFloat() / (numberOfVertices - 1)
            textureCoords[i * 2 + 1] = 0.5f // Dummy y-coord
        }


        // Create the shader program
        val vertexShader =
            Gdx.files.internal("shaders/vat.glsl").readString() // Assuming vat.vert contains the shader code above
        val fragmentShader = "void main() { gl_FragColor = vec4(1.0); }" // Simple dummy fragment shader
        md2Shader = ShaderProgram(vertexShader, fragmentShader)
        if (!md2Shader.isCompiled) {
            Gdx.app.error("Shader Error", md2Shader.getLog())
            Gdx.app.exit()
        }


        // Create the VAT Texture
        // We need to pack the Vector3f data into a FloatBuffer
        val floatBuffer: FloatBuffer =
            BufferUtils.newFloatBuffer(numberOfVertices * numberOfFrames * 3) // 3 floats per Vector3
        for (j in 0..<numberOfFrames) { // Iterate through frames (rows in texture)
            for (i in 0..<numberOfVertices) { // Iterate through vertices (columns in texture)
                floatBuffer.put(vertexData[i]!![j]!!.x)
                floatBuffer.put(vertexData[i]!![j]!!.y)
                floatBuffer.put(vertexData[i]!![j]!!.z)
            }
        }
        floatBuffer.flip() // Prepare buffer for reading


        // Create a Pixmap with the correct format for a float texture
        // RGBA8888 is used here as a common format, but ideally, you'd use a float format
        // if supported by the target device and LibGDX version (e.g., Pixmap.Format.RGB888 or RGBA8888
        // and then tell OpenGL how to interpret it as float, or use a format like RGB32F if available).
        // For simplicity and broader compatibility with ES 2.0, we'll use RGBA8888 and
        // pack/unpack floats into bytes if necessary, but a true float texture is better.
        // Let's try using FloatTextureData which is designed for this.
        vertexTexture = Texture(
            FloatTextureData(
                numberOfVertices,  // width (vertices)
                numberOfFrames,  // height (frames)
                Pixmap.Format.RGB888,  // Format for the texture data (can be RGB888, RGBA8888, etc.)
                GL20.GL_RGB,  // Internal format (e.g., GL_RGB, GL_RGBA)
                GL20.GL_FLOAT,  // Data type (GL_FLOAT for float texture)
                false,  // Use MipMaps
                floatBuffer // The FloatBuffer containing the vertex data
            )
        )

        // Set texture parameters
        vertexTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest); // Nearest filtering for exact frame sampling
        vertexTexture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge); // Clamp to edge to avoid issues at boundaries


        // Create the mesh
        // We only need position (can be dummy) and texture coordinates for VAT lookup
        val meshBuilder = MeshBuilder()
        meshBuilder.begin(
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.TextureCoordinates).toLong(),
            GL20.GL_TRIANGLES
        )


        // Add vertices. We can use dummy positions here as the actual positions come from the texture.
        // The texture coordinates are crucial as they determine which vertex's data is sampled from the VAT.
        for (i in 0..<numberOfVertices) {
            // Dummy position, the real position comes from the texture
            val DUMMY = Vector3(0f, 0f, 0f)
            meshBuilder.vertex(DUMMY, null, null, Vector2(textureCoords[i * 2], textureCoords[i * 2 + 1]))
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
        mesh = meshBuilder.end();

        // Set up camera or world transformation if needed
        worldTrans.idt(); // Identity matrix for now


    }

    override fun render() {

        // Clear the screen
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)


        // Update animation time
        animationTime += Gdx.graphics.getDeltaTime()


        // Bind the shader
        md2Shader.bind()

        worldTrans.rotate(Vector3.Z, 1f * Gdx.graphics.deltaTime)
        // Set uniforms
        md2Shader.setUniformMatrix("u_worldTrans", worldTrans)
        md2Shader.setUniformf("u_animationTime", animationTime)
//        shader.setUniformf("u_textureWidth", numberOfVertices.toFloat())
        md2Shader.setUniformf("u_textureHeight", numberOfFrames.toFloat())
        md2Shader.setUniformf("u_animationDuration", animationDuration)


        // Bind the vertex texture to a texture unit (e.g., unit 0)
        vertexTexture.bind(0)
        md2Shader.setUniformi("u_vertexTexture", 0) // Tell shader which texture unit to use


        // Render the mesh
        // Use GL_TRIANGLES, GL_TRIANGLE_STRIP, or GL_POINTS depending on your mesh topology
        mesh.render(md2Shader, GL20.GL_TRIANGLES) // Adjust rendering primitive if needed


        // Unbind the shader and texture
        md2Shader.end()
        // Texture is automatically unbound when shader ends or another texture is bound


    }

    override fun dispose() {
        md2Shader.dispose()
        mesh.dispose()
        vertexTexture.dispose()
    }
}

// Helper class to create TextureData from a FloatBuffer
// This is a simplified version; a more robust implementation might handle different formats
// and potential OpenGL ES extensions for float textures.
private class FloatTextureData(
    private val width: Int,
    private val height: Int,
    private val format: Pixmap.Format?,
    private val glInternalFormat: Int,
    private val glType: Int,
    private val useMipMaps: Boolean,
    private val buffer: FloatBuffer?
) : TextureData {
    private var isPrepared = false

    override fun getType(): TextureDataType {
        return TextureDataType.Custom
    }

    override fun isPrepared(): Boolean {
        return isPrepared
    }

    override fun prepare() {
        if (isPrepared) throw GdxRuntimeException("Already prepared!")
        isPrepared = true
    }

    override fun consumePixmap(): Pixmap? {
        throw GdxRuntimeException("This TextureData does not return a Pixmap")
    }

    override fun disposePixmap(): Boolean {
        return false
    }

    override fun consumeCustomData(target: Int) {
        if (!isPrepared) throw GdxRuntimeException("Call prepare() first")

        Gdx.gl.glTexImage2D(target, 0, glInternalFormat, width, height, 0, glInternalFormat, glType, buffer)

        if (useMipMaps) {
            Gdx.gl20.glGenerateMipmap(target)
        }
    }

    override fun getWidth(): Int {
        return width
    }

    override fun getHeight(): Int {
        return height
    }

    override fun getFormat(): Pixmap.Format? {
        return format
    }

    override fun useMipMaps(): Boolean {
        return useMipMaps
    }

    override fun isManaged(): Boolean {
        return true // LibGDX will manage this texture
    }
}

class Md2ShaderModel(
    private val mesh: Mesh,
    private val animationTexture: Texture,
    var frame1: Int = 0,
    var frame2: Int = 0,
    var interpolation: Float = 0.5f,
) {
    fun render(shader: ShaderProgram) {
        shader.setUniformi("u_frame1", frame1)
        shader.setUniformi("u_frame2", frame2)
        shader.setUniformf("u_interpolation", interpolation)
        animationTexture.bind(0)
        mesh.render(shader, GL20.GL_TRIANGLES)
    }
}


fun main() {
    val config = Lwjgl3ApplicationConfiguration()
    config.setResizable(true)
    config.setWindowedMode(1024, 768)
    Lwjgl3Application(Md2ShaderTest(), config)
}