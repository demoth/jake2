package org.demoth.cake.modelviewer

import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.GL20.GL_TRIANGLES
import com.badlogic.gdx.graphics.VertexAttribute.TexCoords
import com.badlogic.gdx.graphics.VertexAttributes.Usage.Generic
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Diffuse
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import jake2.qcommon.filesystem.Md2Model
import jake2.qcommon.filesystem.Md2VertexData
import jake2.qcommon.filesystem.PCX
import jake2.qcommon.filesystem.buildVertexData
import org.demoth.cake.ResourceLocator
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class Md2ModelLoader(private val locator: ResourceLocator) {

    fun loadStaticMd2Model(
        modelName: String,
        playerSkin: String? = null,
        skinIndex: Int = 0,
        frameIndex: Int = 0,
    ): Model? {
        val findModel = locator.findModel(modelName)
            ?: return null
        val md2Model: Md2Model = readMd2Model(findModel)

        val skins = md2Model.skinNames.map {
            locator.findSkin(it)
        }

        val modelBuilder = ModelBuilder()
        modelBuilder.begin()
        val modelSkin: ByteArray = if (skins.isNotEmpty()) {
            skins[skinIndex]
        } else {
            if (playerSkin != null) {
                locator.findSkin(playerSkin)
            } else throw IllegalStateException("No skin found in the model, no player skin provided")
        }
        val meshBuilder = modelBuilder.part(
            "part1",
            GL_TRIANGLES,
            VertexAttributes(
                VertexAttribute.Position(), // 3 floats per vertex
                VertexAttribute.TexCoords(0) // 2 floats per vertex
            ),
            Material(
                TextureAttribute(
                    TextureAttribute.Diffuse,
                    Texture(PCXTextureData(fromPCX(PCX(modelSkin)))),
                )
            )
        )
        val frameVertices = md2Model.getFrameVertices(frameIndex)
        val size = frameVertices.size / 5 // 5 floats per vertex : fixme: not great
        meshBuilder.addMesh(frameVertices, (0..<size).map { it.toShort() }.toShortArray())
        val model = modelBuilder.end()
        return model
    }

    fun loadAnimatedModel(
        modelName: String,
        playerSkin: String? = null,
        skinIndex: Int,
    ): Md2ShaderModel? {
        val findModel = locator.findModel(modelName) ?: return null
        val md2Model: Md2Model = readMd2Model(findModel)

        val embeddedSkins = md2Model.skinNames.map {
            locator.findSkin(it)
        }
        val modelSkin: ByteArray = if (embeddedSkins.isNotEmpty()) {
            embeddedSkins[skinIndex]
        } else {
            if (playerSkin != null) {
                locator.findSkin(playerSkin)
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
        return Md2ShaderModel(
            mesh = mesh,
            vat = createVat(vertexData),
            diffuse = diffuse,
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





fun createModel(mesh: Mesh, diffuseTexture: Texture, animationTexture: Texture): Model {
    // need to call static init explicitly?
    // without it, I get the error about an invalid attribute type from 'register'
    AnimationTextureAttribute.init()

    // create the material with diffuse and an "animation" attribute
    val material = Material(
        TextureAttribute(Diffuse, diffuseTexture),
        AnimationTextureAttribute(animationTexture)
    )

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

