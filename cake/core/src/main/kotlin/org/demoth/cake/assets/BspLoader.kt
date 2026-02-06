package org.demoth.cake.assets

import com.badlogic.gdx.assets.AssetDescriptor
import com.badlogic.gdx.assets.AssetLoaderParameters
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.FileHandleResolver
import com.badlogic.gdx.assets.loaders.SynchronousAssetLoader
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.GL20.GL_TRIANGLES
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable
import jake2.qcommon.filesystem.Bsp
import java.nio.ByteBuffer
import java.util.LinkedHashSet

class BspMapAsset(
    val mapData: ByteArray,
    val models: List<Model>
) : Disposable {
    override fun dispose() {
        models.forEach { it.dispose() }
    }
}

class BspLoader(resolver: FileHandleResolver) : SynchronousAssetLoader<BspMapAsset, BspLoader.Parameters>(resolver) {

    class Parameters : AssetLoaderParameters<BspMapAsset>() {
        var walParameters: WalLoader.Parameters = defaultWalParameters()
    }

    override fun load(
        manager: AssetManager,
        fileName: String,
        file: FileHandle,
        parameter: Parameters?
    ): BspMapAsset {
        val mapData = file.readBytes()
        val bsp = Bsp(ByteBuffer.wrap(mapData))
        return BspMapAsset(mapData = mapData, models = buildModels(bsp, manager))
    }

    override fun getDependencies(fileName: String, file: FileHandle?, parameter: Parameters?): Array<AssetDescriptor<*>>? {
        if (file == null) {
            return null
        }

        val walParams = parameter?.walParameters ?: defaultWalParameters()
        val texturePaths = collectWalTexturePaths(file.readBytes())
        if (texturePaths.isEmpty()) {
            return null
        }

        return Array<AssetDescriptor<*>>(texturePaths.size).apply {
            texturePaths.forEach { path ->
                add(AssetDescriptor(path, Texture::class.java, walParams))
            }
        }
    }

    private fun buildModels(bsp: Bsp, manager: AssetManager): List<Model> {
        return bsp.models.map { model ->
            val modelBuilder = ModelBuilder()
            modelBuilder.begin()

            val modelFaces = (0..<model.faceCount).map { bsp.faces[it + model.firstFace] }
            val facesByTexture = modelFaces.groupBy { bsp.textures[it.textureInfoIndex].name }

            val filteredFacesByTexture = facesByTexture.filterKeys { shouldLoadWalTexture(it) }
            for ((textureIndex, entry) in filteredFacesByTexture.entries.withIndex()) {
                val textureName = entry.key
                val faces = entry.value
                val texturePath = toWalPath(textureName)
                val texture = manager.get(texturePath, Texture::class.java)
                val meshBuilder = modelBuilder.part(
                    "part_$textureIndex",
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
                        val uv0 = textureInfo.calculateUV(v0, texture.width, texture.height)
                        val uv1 = textureInfo.calculateUV(v1, texture.width, texture.height)
                        val uv2 = textureInfo.calculateUV(v2, texture.width, texture.height)
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
}

internal fun collectWalTexturePaths(bspData: ByteArray): List<String> {
    val bsp = Bsp(ByteBuffer.wrap(bspData))
    val texturePaths = LinkedHashSet<String>()

    bsp.models.forEach { model ->
        repeat(model.faceCount) { offset ->
            val face = bsp.faces[model.firstFace + offset]
            val textureName = bsp.textures[face.textureInfoIndex].name
            if (shouldLoadWalTexture(textureName)) {
                texturePaths.add(toWalPath(textureName))
            }
        }
    }
    return texturePaths.toList()
}

private fun toWalPath(textureName: String): String = "textures/${textureName.trim()}.wal"

private fun shouldLoadWalTexture(textureName: String): Boolean {
    val normalized = textureName.trim()
    // sky is loaded separately, see SkyLoader
    return normalized.isNotEmpty() && !normalized.contains("sky", ignoreCase = true)
}

private fun defaultWalParameters() = WalLoader.Parameters().apply {
    wrapU = Texture.TextureWrap.Repeat
    wrapV = Texture.TextureWrap.Repeat
}
