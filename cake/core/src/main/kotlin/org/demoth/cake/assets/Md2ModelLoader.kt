package org.demoth.cake.assets

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.GL20.GL_TRIANGLES
import com.badlogic.gdx.graphics.VertexAttribute.TexCoords
import com.badlogic.gdx.graphics.VertexAttributes.Usage.Generic
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Diffuse
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.utils.GdxRuntimeException
import jake2.qcommon.filesystem.Md2Model
import jake2.qcommon.filesystem.Md2VertexData
import jake2.qcommon.filesystem.PCX
import jake2.qcommon.filesystem.buildVertexData
import org.demoth.cake.ResourceLocator
import org.demoth.cake.modelviewer.AnimationTextureAttribute
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.jvm.java

class Md2ModelLoader(
    private val locator: ResourceLocator,
    private val assetManager: AssetManager
) {

    fun loadMd2ModelData(
        modelName: String,
        playerSkin: String? = null,
        skinIndex: Int,
    ): Md2ModelData? {
        val modelPath = locator.findModelPath(modelName) ?: return null
        val md2Model: Md2Model = readMd2Model(assetManager.getLoaded(modelPath))

        val embeddedSkins = md2Model.skinNames.map {
            val skinPath = requireNotNull(locator.findSkinPath(it)) { "Missing skin: $it" }
            assetManager.getLoaded<ByteArray>(skinPath)
        }
        val modelSkin: ByteArray = if (embeddedSkins.isNotEmpty()) {
            embeddedSkins[skinIndex]
        } else {
            if (playerSkin != null) {
                val skinPath = requireNotNull(locator.findSkinPath(playerSkin)) { "Missing skin: $playerSkin" }
                assetManager.getLoaded(skinPath)
            } else throw IllegalStateException("No skin found in the model, no player skin provided")
        }

        val diffuse = Texture(PCXTextureData(fromPCX(PCX(modelSkin))))

        val vertexData = buildVertexData(md2Model.glCommands, md2Model.frames)

        val mesh = Mesh(
            true,
            vertexData.vertexAttributes.size,
            vertexData.indices.size,
            VertexAttributes(
                VertexAttribute(Generic, 1, "a_vat_index"),
                TexCoords(1) // in future, normals can also be added here
            )
        )
        mesh.setVertices(vertexData.vertexAttributes)
        mesh.setIndices(vertexData.indices)
        return Md2ModelData(
            mesh = mesh,
            material = createMd2Material(diffuse, createVat(vertexData)),
            frames = md2Model.frames.size // :thinking: used only in the model viewer, otherwise we could have return a `Model` here
        )
    }

    private fun createMd2Material(diffuse: Texture, vat: Texture): Material {
        // need to call static init explicitly?
        // without it, I get the error about an invalid attribute type from 'register'
        AnimationTextureAttribute.init()

        // create the material with diffuse and an "animation" attribute
        return Material(
            TextureAttribute(Diffuse, diffuse),
            AnimationTextureAttribute(vat)
        )
    }

    private fun createVat(vertexData: Md2VertexData): Texture {
        return Texture(
            CustomTextureData(
                vertexData.vertices,
                vertexData.frames,
                GL30.GL_RGB16F,
                GL30.GL_RGB,
                GL20.GL_FLOAT,
                vertexData.vertexPositions.toFloatBuffer(),
            )
        )
    }
}

/**
 * Get an asset ensuring it's loaded. Blocking operation.
 */
inline fun <reified T> AssetManager.getLoaded(path: String): T {
    if (!isLoaded(path, T::class.java)) {
        load(path, T::class.java)
        finishLoadingAsset<T>(path)
    }
    return get(path, T::class.java)
}

class Md2ModelData(val mesh: Mesh, val material: Material, val frames: Int)

// Helper class to create TextureData from a vertex position data buffer
private class CustomTextureData(
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

fun createModel(mesh: Mesh, material: Material): Model {
    return ModelBuilder().apply {
        begin()
        part("part1", mesh, GL_TRIANGLES, material)
    }.end()
}

private fun FloatArray.toFloatBuffer(): FloatBuffer {
    val result = ByteBuffer
        .allocateDirect(size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
    result.put(this)
    result.flip()
    return result
}

private fun readMd2Model(modelData: ByteArray): Md2Model {
    val byteBuffer = ByteBuffer
        .wrap(modelData)
        .order(ByteOrder.LITTLE_ENDIAN)
    return Md2Model(byteBuffer)
}
