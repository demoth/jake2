package org.demoth.cake.modelviewer

import com.badlogic.gdx.graphics.GL20.GL_TRIANGLES
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import jake2.qcommon.filesystem.Md2Model
import jake2.qcommon.filesystem.PCX
import jake2.qcommon.filesystem.getVertexData
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

        val vertexData = getVertexData(md2Model.glCommands, md2Model.frames)
        return Md2ShaderModel(
            mesh = createMesh(vertexData.indices, vertexData.vertexAttributes),
            vat = createVat(vertexData.vertexPositions!!) to 0,
            diffuse = diffuse to 1,
        )
    }

    /**
     * The Mesh holds the vertex attributes, which in the VAT scenario are only texture coordinates.
     * The indices are implicitly provided and normals are just skipped in this example.
     *
     */
    private fun createMesh(indices: ShortArray, vertexAttributes: FloatArray): Mesh {
        // todo: calculate indices
        val mesh = Mesh(
            true,
            vertexAttributes.size,
            indices.size,
            VertexAttribute.TexCoords(1)
        )
        mesh.setVertices(vertexAttributes)
        mesh.setIndices(indices)
        return mesh
    }

    private fun createVat(data: FloatArray): Texture {
        TODO()
    }
}

private fun readMd2Model(modelData: ByteArray): Md2Model {
    val byteBuffer = ByteBuffer
        .wrap(modelData)
        .order(ByteOrder.LITTLE_ENDIAN)
    return Md2Model(byteBuffer)
}

