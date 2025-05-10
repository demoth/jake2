package org.demoth.cake.modelviewer

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.TextureData
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.GdxRuntimeException
import java.nio.Buffer

// Helper class to create TextureData from a vertex position data buffer
class CustomTextureData(
    private val width: Int,
    private val height: Int,
    private val glInternalFormat: Int,
    private val glFormat: Int,
    private val glType: Int,
    private val buffer: Buffer?
) : TextureData {
    private var isPrepared = false

    override fun getType(): TextureData.TextureDataType {
        // means it doesn't rely on the pixmap format
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

        Gdx.gl.glTexImage2D(
            /* target = */ target,
            /* level = */ 0,
            /* internalformat = */ glInternalFormat,
            /* width = */ width,
            /* height = */ height,
            /* border = */ 0,
            /* format = */ glFormat,
            /* type = */ glType,
            /* pixels = */ buffer
        )
    }

    override fun getWidth(): Int {
        return width
    }

    override fun getHeight(): Int {
        return height
    }

    override fun getFormat(): Pixmap.Format? {
        throw GdxRuntimeException("This TextureData does not use a Pixmap")
    }

    override fun useMipMaps(): Boolean {
        return false
    }

    override fun isManaged(): Boolean {
        return true // LibGDX will manage this texture
    }
}

class Md2ShaderModel(
    // model related, managed resources, todo: extract into a model class
    private val mesh: Mesh,
    private val vat: Pair<Texture, Int>,
    private val diffuse: Pair<Texture, Int>,
    // instance related, mutable state
    var frame1: Int = 0,
    var frame2: Int = 1,
    var interpolation: Float = 0.0f,
    val entityTransform: Matrix4 = Matrix4()
): Disposable {

    private val textureWidth = vat.first.width.toFloat()
    private val textureHeight = vat.first.height.toFloat()

    fun render(shader: ShaderProgram, cameraTransform: Matrix4) {
        shader.bind()

        shader.setUniformf("u_frame1", frame1.toFloat()) // not like!
        shader.setUniformf("u_frame2", frame2.toFloat()) // not like!
        shader.setUniformf("u_interpolation", interpolation)

        shader.setUniformMatrix("u_projViewTrans", cameraTransform);
        shader.setUniformMatrix("u_worldTrans", entityTransform)

        shader.setUniformi("u_vertexAnimationTexture", vat.second)
        shader.setUniformi("u_diffuseTexture", diffuse.second)

        shader.setUniformf("u_textureHeight", textureHeight) // number of frames
        shader.setUniformf("u_textureWidth", textureWidth) // number of vertices

        vat.first.bind(vat.second)
        diffuse.first.bind(diffuse.second)

        mesh.render(shader, GL20.GL_TRIANGLES)
    }

    override fun dispose() {
        mesh.dispose()
        vat.first.dispose()
        diffuse.first.dispose()
    }

}