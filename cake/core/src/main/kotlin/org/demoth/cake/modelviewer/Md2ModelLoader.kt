package org.demoth.cake.modelviewer

import com.badlogic.gdx.graphics.GL20.GL_TRIANGLES
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.utils.Disposable
import jake2.qcommon.filesystem.Md2Model
import jake2.qcommon.filesystem.PCX
import org.demoth.cake.ResourceLocator
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CakeMd2Model(
    private val locator: ResourceLocator,
    private val modelName: String,
    private val playerSkin: String? = null,
): Disposable {
    val model: Model
    private val vertexBuffers: List<List<Float>>

    init {
        val findModel = locator.findModel(modelName) ?: throw IllegalStateException("Model $modelName not found")
        val md2Model: Md2Model = readMd2Model(findModel)

        val skins = md2Model.skinNames.map {
            locator.findSkin(it)
        }

        // buffers for all frames
        vertexBuffers = md2Model.frames.map { frame ->
            md2Model.glCommands.flatMap {
                it.toFloats(frame.points)
            }
        }

        val modelBuilder = ModelBuilder()
        modelBuilder.begin()
        val modelSkin: ByteArray = if (skins.isNotEmpty()) {
            skins[0]
        } else {
            if (playerSkin != null) {
                locator.findSkin(playerSkin)
            } else throw IllegalStateException("No skin found in the model, no player skin provided")
        }
        val meshBuilder = modelBuilder.part(
            "part1",
            GL_TRIANGLES,
            VertexAttributes(VertexAttribute.Position(), VertexAttribute.TexCoords(0)),
            Material(
                TextureAttribute(
                    TextureAttribute.Diffuse,
                    Texture(PCXTextureData(fromPCX(PCX(modelSkin)))),
                )
            )
        )
        // initialize with the first frame, will be changed later
        val size = vertexBuffers.first().size / 5 // 5 floats per vertex : fixme: not great
        meshBuilder.addMesh(vertexBuffers.first().toFloatArray(), (0..<size).map { it.toShort() }.toShortArray())
        model = modelBuilder.end()
    }

    fun setFrame(frameIndex: Int) {
        check(frameIndex < vertexBuffers.size) { "Frame index out of bounds" }
        // assume only one mesh
        model.meshes.first().setVertices(vertexBuffers[frameIndex].toFloatArray())
    }

    override fun dispose() {
        model.dispose()
    }
}

class Md2ModelLoader(val locator: ResourceLocator) {
    fun loadMd2Model(
        modelName: String,
        playerSkin: String? = null,
        skinIndex: Int,
        frameIndex: Int,
    ): Model? {
        val findModel = locator.findModel(modelName)
            ?: return null
        val md2Model: Md2Model = readMd2Model(findModel)

        val skins = md2Model.skinNames.map {
            locator.findSkin(it)
        }

        val first = md2Model.frames[frameIndex]

        val vertexBuffer = md2Model.glCommands.flatMap {
            it.toFloats(first.points)
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
            VertexAttributes(VertexAttribute.Position(), VertexAttribute.TexCoords(0)),
            Material(
                TextureAttribute(
                    TextureAttribute.Diffuse,
                    Texture(PCXTextureData(fromPCX(PCX(modelSkin)))),
                )
            )
        )
        val size = vertexBuffer.size / 5 // 5 floats per vertex : fixme: not great
        meshBuilder.addMesh(vertexBuffer.toFloatArray(), (0..<size).map { it.toShort() }.toShortArray())
        val model = modelBuilder.end()
        return model
    }
}

private fun readMd2Model(modelData: ByteArray): Md2Model {
    val byteBuffer = ByteBuffer
        .wrap(modelData)
        .order(ByteOrder.LITTLE_ENDIAN)
    return Md2Model(byteBuffer)
}

