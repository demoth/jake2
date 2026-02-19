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

/**
 * Loaded BSP map data.
 *
 * [models] are generated GPU resources owned by this asset instance.
 * [worldRenderData] stores per-surface and per-leaf world representation for runtime visibility/lighting passes.
 * BSP textures are loaded as independent AssetManager dependencies and are not disposed here.
 */
class BspMapAsset(
    val mapData: ByteArray,
    val models: List<Model>,
    val worldRenderData: BspWorldRenderData,
) : Disposable {
    override fun dispose() {
        models.forEach { it.dispose() }
    }
}

/**
 * Runtime world representation for the BSP world model (model 0).
 *
 * This representation is intentionally independent from current draw policy:
 * it keeps stable per-face records and leaf->surface mapping required by upcoming
 * PVS/areabits, transparency passes, animated texinfo chains, and lightmap work.
 */
data class BspWorldRenderData(
    val surfaces: List<BspWorldSurfaceRecord>,
    val leaves: List<BspWorldLeafRecord>,
)

data class BspWorldSurfaceRecord(
    val faceIndex: Int,
    val meshPartId: String,
    val textureInfoIndex: Int,
    val textureName: String,
    val textureFlags: Int,
    val textureAnimationNext: Int,
    val lightMapStyles: ByteArray,
    val lightMapOffset: Int,
)

data class BspWorldLeafRecord(
    val leafIndex: Int,
    val cluster: Int,
    val area: Int,
    val surfaceIndices: IntArray,
)

/**
 * Loads Quake2 BSP maps into renderable libGDX models.
 *
 * Loader flow:
 * 1. [getDependencies] parses BSP bytes and declares all referenced WAL textures.
 * 2. [load] builds one libGDX [Model] per BSP model (world model + inline brush models).
 * 3. World model faces are emitted as per-surface mesh parts and captured in [BspWorldRenderData].
 * 4. Inline brush models keep grouped-by-texture batching for now.
 *
 * This keeps texture lifecycle in AssetManager while model lifecycle is handled by [BspMapAsset].
 */
class BspLoader(resolver: FileHandleResolver) : SynchronousAssetLoader<BspMapAsset, BspLoader.Parameters>(resolver) {

    /**
     * Parameters forwarded to dependent WAL texture loads.
     */
    data class Parameters(
        val walParameters: WalLoader.Parameters = defaultWalParameters()
    ) : AssetLoaderParameters<BspMapAsset>()

    override fun load(
        manager: AssetManager,
        fileName: String,
        file: FileHandle,
        parameter: Parameters?
    ): BspMapAsset {
        val mapData = file.readBytes()
        val bsp = Bsp(ByteBuffer.wrap(mapData))
        val worldSurfaces = collectWorldSurfaceRecords(bsp)
        return BspMapAsset(
            mapData = mapData,
            models = buildModels(bsp, manager, worldSurfaces),
            worldRenderData = buildWorldRenderData(bsp, worldSurfaces)
        )
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

    /**
     * Builds one [Model] per BSP model.
     * World model (index 0) is emitted as one mesh part per face to preserve stable surface identity.
     * Inline models remain grouped by texture for now.
     */
    private fun buildModels(
        bsp: Bsp,
        manager: AssetManager,
        worldSurfaces: List<BspWorldSurfaceRecord>
    ): List<Model> {
        return bsp.models.mapIndexed { modelIndex, model ->
            val modelBuilder = ModelBuilder()
            modelBuilder.begin()

            val modelFaces = (0..<model.faceCount).map { bsp.faces[it + model.firstFace] }
            if (modelIndex == 0) {
                val materialCache = HashMap<String, Material>()
                worldSurfaces.forEach { surface ->
                    val texture = manager.get(toWalPath(surface.textureName), Texture::class.java)
                    val material = materialCache.getOrPut(surface.textureName) {
                        Material(TextureAttribute(TextureAttribute.Diffuse, texture))
                    }
                    val face = bsp.faces[surface.faceIndex]
                    val meshBuilder = modelBuilder.part(
                        surface.meshPartId,
                        GL_TRIANGLES,
                        VertexAttributes(VertexAttribute.Position(), VertexAttribute.TexCoords(0)),
                        material
                    )
                    addFaceAsTriangles(bsp, face, texture, meshBuilder)
                }
            } else {
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
                        Material(TextureAttribute(TextureAttribute.Diffuse, texture))
                    )
                    faces.forEach { addFaceAsTriangles(bsp, it, texture, meshBuilder) }
                }
            }
            modelBuilder.end()
        }
    }

    private fun addFaceAsTriangles(
        bsp: Bsp,
        face: jake2.qcommon.filesystem.BspFace,
        texture: Texture,
        meshBuilder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder
    ) {
        if (face.numEdges < 3) {
            return
        }
        val edgeIndices = (0..<face.numEdges).map { edgeIndex ->
            bsp.faceEdges[face.firstEdgeIndex + edgeIndex]
        }

        // list of vertex indices in clockwise order, forming a triangle fan
        val vertices = edgeIndices.map { edgeIndex ->
            if (edgeIndex > 0) {
                bsp.edges[edgeIndex].v2
            } else {
                bsp.edges[-edgeIndex].v1
            }
        }
        val textureInfo = bsp.textures[face.textureInfoIndex]

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
        val size = vertexBuffer.size / 5 // 5 floats per vertex
        meshBuilder.addMesh(vertexBuffer.toFloatArray(), (0..<size).map { it.toShort() }.toShortArray())
    }
}

/**
 * Parses BSP bytes and returns unique WAL texture dependency paths.
 */
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

/**
 * Collects stable world-surface records (model 0 only) used by runtime visibility/lighting passes.
 */
internal fun collectWorldSurfaceRecords(bsp: Bsp): List<BspWorldSurfaceRecord> {
    val worldModel = bsp.models.firstOrNull() ?: return emptyList()
    return (0..<worldModel.faceCount).mapNotNull { localOffset ->
        val faceIndex = worldModel.firstFace + localOffset
        val face = bsp.faces[faceIndex]
        val texInfo = bsp.textures[face.textureInfoIndex]
        if (!shouldLoadWalTexture(texInfo.name)) {
            return@mapNotNull null
        }
        BspWorldSurfaceRecord(
            faceIndex = faceIndex,
            meshPartId = "surface_$faceIndex",
            textureInfoIndex = face.textureInfoIndex,
            textureName = texInfo.name,
            textureFlags = texInfo.flags,
            textureAnimationNext = texInfo.next,
            lightMapStyles = face.lightMapStyles.copyOf(),
            lightMapOffset = face.lightMapOffset
        )
    }
}

/**
 * Builds leaf->surface mapping for the world model using parsed BSP leaf-face indices.
 */
internal fun buildWorldRenderData(bsp: Bsp, surfaces: List<BspWorldSurfaceRecord>): BspWorldRenderData {
    val faceToSurface = surfaces.mapIndexed { index, surface -> surface.faceIndex to index }.toMap()
    val leaves = bsp.leaves.mapIndexed { leafIndex, leaf ->
        val indices = ArrayList<Int>()
        val rangeEnd = (leaf.firstLeafFace + leaf.numLeafFaces).coerceAtMost(bsp.leafFaces.size)
        for (i in leaf.firstLeafFace until rangeEnd) {
            val faceIndex = bsp.leafFaces[i]
            faceToSurface[faceIndex]?.let(indices::add)
        }
        BspWorldLeafRecord(
            leafIndex = leafIndex,
            cluster = leaf.cluster,
            area = leaf.area,
            surfaceIndices = indices.toIntArray()
        )
    }
    return BspWorldRenderData(
        surfaces = surfaces,
        leaves = leaves
    )
}

private fun toWalPath(textureName: String): String = "textures/${textureName.trim()}.wal"

private fun shouldLoadWalTexture(textureName: String): Boolean {
    val normalized = textureName.trim()
    // sky is loaded separately, see SkyLoader
    return normalized.isNotEmpty() && !normalized.contains("sky", ignoreCase = true)
}

private fun defaultWalParameters() = WalLoader.Parameters(
    wrapU = Texture.TextureWrap.Repeat,
    wrapV = Texture.TextureWrap.Repeat,
)
