package org.demoth.cake.modelviewer

import com.badlogic.gdx.graphics.GL20.GL_TRIANGLES
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import jake2.qcommon.filesystem.Md2Model
import jake2.qcommon.filesystem.PCX
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Md2ModelLoader {
    fun loadMd2Model(modelFile: File): ModelInstance {
        val md2Model: Md2Model = readMd2Model(modelFile.absolutePath)
        // strip skin names and expect them to be located along with the .md2 file
        val skins = md2Model.skinNames.map {
            val name = if (it.contains("/"))
                it.substring(it.lastIndexOf("/") + 1)
            else
                it
            File(modelFile.parentFile, name)
        }

        val first = md2Model.frames.first()

        val vertexBuffer = md2Model.glCommands.flatMap {
            it.toFloats(first.points)
        }

        val modelBuilder = ModelBuilder()
        modelBuilder.begin()
        val meshBuilder = modelBuilder.part(
            "part1",
            GL_TRIANGLES,
            VertexAttributes(VertexAttribute.Position(), VertexAttribute.TexCoords(0)),
            Material(
                TextureAttribute(TextureAttribute.Diffuse,
                    Texture(PCXTextureData(fromPCX(PCX(skins.first().readBytes())))),)
            )
        )
        val size = vertexBuffer.size / 5 // 5 floats per vertex : fixme: not great
        meshBuilder.addMesh(vertexBuffer.toFloatArray(), (0..<size).map { it.toShort() }.toShortArray())

        val model = modelBuilder.end()
        val modelInstance = ModelInstance(model)
        // fix axis difference between q2 (z up) and libGDX (y up)
        modelInstance.transform.rotate(Vector3(1f, 0f, 0f), -90f)
        modelInstance.transform.rotate(Vector3(0f, 0f, 1f), 90f)
        return modelInstance
    }

    fun readMd2Model(modelPath: String): Md2Model {
        val byteBuffer = ByteBuffer
            .wrap(File(modelPath).readBytes())
            .order(ByteOrder.LITTLE_ENDIAN)
        return Md2Model(byteBuffer)
    }

}
