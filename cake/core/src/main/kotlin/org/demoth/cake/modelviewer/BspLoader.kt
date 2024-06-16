package org.demoth.cake.modelviewer

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.GL20.GL_TRIANGLES
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import jake2.qcommon.filesystem.Bsp
import jake2.qcommon.filesystem.WAL
import java.io.File
import java.nio.ByteBuffer

class BspLoader(val gameDir: String) {

    fun loadBspModelInstances(file: File): List<ModelInstance> {
/*
        return loadBspModels(file).mapIndexed { i, it ->
            val instance = ModelInstance(it)
            instance.transformQ2toLibgdx()

            // some brush models need their position to be adjusted based on the origin of the respective entity
            if (i != 0) { // skip worldspawn
                // find an entity by model index
                val entity = bsp.entities.find { it.entries.any { (k, v) -> k == "model" && v == "*$i" } }
                val origin = entity?.get("origin")?.split(" ")?.map { it.toFloat() }
                if (origin != null) {
                    instance.transform.translate(origin[0], origin[1], origin[2])
                }
            }
            instance
        }
*/
        TODO()
    }

    fun loadBspModels(file: File): List<Model> {
        val bsp = Bsp(ByteBuffer.wrap(file.readBytes()))
        val palette = readPaletteFile(Gdx.files.internal("q2palette.bin").read())

        // create libgdx model instances from bsp models
        return bsp.models.mapIndexed { i, model ->
            val modelBuilder = ModelBuilder()
            modelBuilder.begin()

            val modelFaces = (0..<model.faceCount).map { it + model.firstFace }.map { bsp.faces[it] }

            // split all faces by texture name
            val facesByTexture = modelFaces.groupBy { bsp.textures[it.textureInfoIndex].name }

            facesByTexture.forEach { (textureName, faces) ->
                val walTexture = WAL(findFile(textureName).readBytes()) // todo: cache
                val texture = Texture(WalTextureData(fromWal(walTexture, palette)))
                // todo: bsp level textures always wrap?
                texture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat)
                val meshBuilder = modelBuilder.part(
                    "part1",
                    GL_TRIANGLES,
                    VertexAttributes(VertexAttribute.Position(), VertexAttribute.TexCoords(0)),
                    Material(
                        TextureAttribute(
                            TextureAttribute.Diffuse,
                            texture,
                        )
                    )
                )

                faces.forEach { f ->
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

                    // draw face using triangle fan
                    val vertexBuffer = vertices.drop(1).windowed(2).flatMap { (i1, i2) ->
                        val v0 = bsp.vertices[vertices.first()]
                        val v1 = bsp.vertices[i1]
                        val v2 = bsp.vertices[i2]
                        val uv0 = textureInfo.calculateUV(v0, walTexture.width, walTexture.height)
                        val uv1 = textureInfo.calculateUV(v1, walTexture.width, walTexture.height)
                        val uv2 = textureInfo.calculateUV(v2, walTexture.width, walTexture.height)
                        listOf(
                            v2.x, v2.y, v2.z, uv2.first(), uv2.last(),
                            v1.x, v1.y, v1.z, uv1.first(), uv1.last(),
                            v0.x, v0.y, v0.z, uv0.first(), uv0.last(),
                        )
                    }
                    val size = vertexBuffer.size / 5 // 5 floats per vertex : fixme: not great
                    meshBuilder.addMesh(vertexBuffer.toFloatArray(), (0..<size).map { it.toShort() }.toShortArray())
                }
            }
            modelBuilder.end()
        }
    }

    private fun findFile(textureName: String): File {
        val file = File("$gameDir/textures/$textureName.wal")
        return if (file.exists()) {
            file
        } else {
            println("Warn: $textureName was found by lowercase name")
            File("$gameDir/textures/${textureName.lowercase()}.wal")
        }
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