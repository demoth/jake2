package org.demoth.cake.modelviewer

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.GL20.GL_TRIANGLE_FAN
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import jake2.qcommon.filesystem.Bsp
import java.io.File
import java.nio.ByteBuffer

class BspLoader {
    fun loadBspModelTextured(file: File): ModelInstance {
        val bsp = Bsp(ByteBuffer.wrap(file.readBytes()))

        val modelBuilder = ModelBuilder()
        modelBuilder.begin()
        val texture = Texture(Gdx.files.internal("tile1.png"))
        val meshBuilder = modelBuilder.part(
            "part1",
            GL_TRIANGLE_FAN,
            VertexAttributes(VertexAttribute.Position(), VertexAttribute.TexCoords(0)),
            Material(
                TextureAttribute(
                    TextureAttribute.Diffuse,
                    texture,
                )
            )
        )
        val vertexBuffer = bsp.faces.flatMap { f ->
            val edgeIndices = (0..<f.numEdges).map { edgeIndex ->
                bsp.faceEdges[f.firstEdgeIndex + edgeIndex]
            }

            // list of vertex indices in clockwise order, forming a triangle fan
            val vertices = edgeIndices.map { edgeIndex ->
                if (edgeIndex > 0) {
                    val edge = bsp.edges[edgeIndex]
                    edge.v2
                } else {
                    val edge = bsp.edges[-edgeIndex]
                    edge.v1
                }
            }
            val textureInfo = bsp.textures[f.textureInfoIndex]

            vertices.flatMap { vi ->
                val v = bsp.vertices[vi]
                listOf(v.x, v.y, v.z) + textureInfo.calculateUV(v, 1024, 1024) // fixme: get real texture info size
            }
        }

        val size = vertexBuffer.size / 5 // 5 floats per vertex : fixme: not great
        meshBuilder.addMesh(vertexBuffer.toFloatArray(), (0..<size).map { it.toShort() }.toShortArray())

        val model = modelBuilder.end()
        return ModelInstance(model)
    }


    fun loadBSPModelWireFrame(file: File): ModelInstance {
        val bsp = Bsp(ByteBuffer.wrap(file.readBytes()))

        val modelBuilder = ModelBuilder()
        modelBuilder.begin()
        val partBuilder: MeshPartBuilder = modelBuilder.part(
            "lines",
            GL20.GL_LINES,
            (Usage.Position or Usage.ColorUnpacked).toLong(),
            Material(ColorAttribute.createDiffuse(Color.WHITE))
        )
        bsp.edges.forEach {
            val from = bsp.vertices[it.v1]
            val to = bsp.vertices[it.v2]
            partBuilder.line(
                from.x,
                from.y,
                from.z,
                to.x,
                to.y,
                to.z
            )
        }
        return ModelInstance(modelBuilder.end())

    }
}