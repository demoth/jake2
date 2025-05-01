package org.demoth.cake.modelviewer

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.TextureData
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.GdxRuntimeException
import java.nio.FloatBuffer

// Helper class to create TextureData from a FloatBuffer
// This is a simplified version; a more robust implementation might handle different formats
// and potential OpenGL ES extensions for float textures.
class FloatTextureData(
    private val width: Int,
    private val height: Int,
    private val format: Pixmap.Format?,
    private val glInternalFormat: Int,
    private val glType: Int,
    private val useMipMaps: Boolean,
    private val buffer: FloatBuffer?
) : TextureData {
    private var isPrepared = false

    override fun getType(): TextureData.TextureDataType {
        return TextureData.TextureDataType.Custom
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
    private val textureUnit: Int = 0,
): Disposable {

    private val textureWidth = animationTexture.width.toFloat()
    private val textureHeight = animationTexture.height.toFloat()

    fun render(shader: ShaderProgram, worldTrans: Matrix4) {
        shader.bind()

        shader.setUniformi("u_frame1", frame1)
        shader.setUniformi("u_frame2", frame2)
        shader.setUniformf("u_interpolation", interpolation)

        shader.setUniformMatrix("u_worldTrans", worldTrans)
        shader.setUniformi("u_vertexAnimationTexture", textureUnit)

        shader.setUniformf("u_textureHeight", textureHeight) // number of frames
        shader.setUniformf("u_textureWidth", textureWidth) // number of vertices


        /*
                // TEMPORARY
                shader.setUniformf("u_animationDuration", 2f)
                shader.setUniformf("u_animationTime", animationTime)
        */


        // Bind the vertex texture to a texture unit (e.g., unit 0)
        animationTexture.bind(textureUnit)
        mesh.render(shader, GL20.GL_TRIANGLES)
    }

    override fun dispose() {
        mesh.dispose()
        animationTexture.dispose()
    }

}