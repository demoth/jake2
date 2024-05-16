package org.demoth.cake.modelviewer

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20.GL_TRIANGLES
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import jake2.qcommon.filesystem.Md2Model
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Md2ModelLoader {
    fun loadMd2Model(modelPath: String, texturePath: String): ModelInstance {
        val md2Model: Md2Model = readMd2Model(modelPath)
        val vertexBuffer = md2Model.glCommands.flatMap {
            it.toFloats(md2Model.frames.first().points)
        }

        val modelBuilder = ModelBuilder()
        modelBuilder.begin()
        val meshBuilder = modelBuilder.part(
            "part1",
            GL_TRIANGLES,
            VertexAttributes(VertexAttribute.Position(), VertexAttribute.TexCoords(0)),
            Material(
                TextureAttribute(TextureAttribute.Diffuse, Texture(Gdx.files.internal(texturePath)))
            )
        )
        val size = vertexBuffer.size / 5 // 5 floats per vertex : fixme: not great
        meshBuilder.addMesh(vertexBuffer.toFloatArray(), (0..<size).map { it.toShort() }.toShortArray())
        val model = modelBuilder.end()
        return ModelInstance(model)
    }

    fun readMd2Model(modelPath: String): Md2Model {
        val byteBuffer = ByteBuffer
            .wrap(Gdx.files.internal(modelPath).readBytes())
            .order(ByteOrder.LITTLE_ENDIAN)
        return Md2Model(byteBuffer)
    }

}
