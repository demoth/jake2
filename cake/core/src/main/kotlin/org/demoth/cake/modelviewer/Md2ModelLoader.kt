package org.demoth.cake.modelviewer

import com.badlogic.gdx.graphics.GL20.GL_TRIANGLES
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import jake2.qcommon.filesystem.Md2Model
import jake2.qcommon.filesystem.PCX
import org.demoth.cake.ResourceLocator
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Md2ModelLoader(private val locator: ResourceLocator) {
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
    ): Md2AnimatedModel? {
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
        val frameVertices = md2Model.getFrameVertices(0)
        val size = frameVertices.size / 5 // 5 floats per vertex : fixme: not great
        meshBuilder.addMesh(frameVertices, (0..<size).map { it.toShort() }.toShortArray())
        val model = modelBuilder.end()
        val frameBuffers = List(md2Model.frames.size) { i -> md2Model.getFrameVertices(i) }
        return Md2AnimatedModel(frameBuffers, 0, model)
    }
}

// no interpolation between frames yet
// for cake model viewer only, because the mesh is shared between instances
class Md2AnimatedModel(
    val frames: List<FloatArray>,
    var currentFrame: Int = 0,
    val model: Model
) {
    fun nextFrame() {
        model.meshes.first().updateVertices(0, frames[currentFrame])
        currentFrame++
        if (currentFrame >= frames.size)
            currentFrame = 0
    }
}

private fun readMd2Model(modelData: ByteArray): Md2Model {
    val byteBuffer = ByteBuffer
        .wrap(modelData)
        .order(ByteOrder.LITTLE_ENDIAN)
    return Md2Model(byteBuffer)
}

