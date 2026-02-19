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
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Loaded BSP map data.
 *
 * [models] are generated GPU resources owned by this asset instance.
 * [worldRenderData] stores per-surface and per-leaf world representation for runtime visibility/lighting passes.
 * [inlineRenderData] stores stable per-part metadata for inline brush models (`*1`, `*2`, ...).
 * BSP textures are loaded as independent AssetManager dependencies and are not disposed here.
 */
class BspMapAsset(
    val mapData: ByteArray,
    val models: List<Model>,
    val worldRenderData: BspWorldRenderData,
    val inlineRenderData: List<BspInlineModelRenderData>,
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
 * [textureInfos] contains BSP texinfo metadata needed to resolve animation chains
 * even when intermediate chain frames have no directly referenced world faces.
 */
data class BspWorldRenderData(
    val surfaces: List<BspWorldSurfaceRecord>,
    val leaves: List<BspWorldLeafRecord>,
    val textureInfos: List<BspWorldTextureInfoRecord>,
)

data class BspWorldSurfaceRecord(
    /** Absolute face index in BSP face lump. */
    val faceIndex: Int,
    /** Stable libGDX mesh-part id used to locate the runtime [com.badlogic.gdx.graphics.g3d.model.NodePart]. */
    val meshPartId: String,
    /** BSP texinfo index referenced by this face. */
    val textureInfoIndex: Int,
    /** Base WAL texture name (`textures/<name>.wal`). */
    val textureName: String,
    /** Quake2 `SURF_*` flags from texinfo. */
    val textureFlags: Int,
    /** BSP texinfo `nexttexinfo` index (`<=0` means no animated successor). */
    val textureAnimationNext: Int,
    /** Raw lightstyle slots for this face. */
    val lightMapStyles: ByteArray,
    /** BSP lightmap lump byte offset for this face. */
    val lightMapOffset: Int,
    /** Average per-style baked light contributions for this face (computed from BSP lightmap lump). */
    val lightStyleContributions: List<BspLightStyleContributionRecord> = emptyList(),
)

data class BspWorldLeafRecord(
    /** Absolute BSP leaf index. */
    val leafIndex: Int,
    /** PVS cluster id (`-1` means invalid cluster). */
    val cluster: Int,
    /** Area id used by areabits portal gating. */
    val area: Int,
    /** Indices into [BspWorldRenderData.surfaces] visible from this leaf. */
    val surfaceIndices: IntArray,
)

data class BspWorldTextureInfoRecord(
    /** Absolute BSP texinfo index. */
    val textureInfoIndex: Int,
    /** WAL texture name for this texinfo. */
    val textureName: String,
    /** Quake2 `SURF_*` flags for this texinfo. */
    val textureFlags: Int,
    /** Animated successor texinfo index (`<=0` means chain end). */
    val textureAnimationNext: Int,
)

/**
 * Runtime representation for one inline BSP model (model index > 0).
 *
 * Each part maps one texinfo bucket to one mesh part id in the generated libGDX model.
 */
data class BspInlineModelRenderData(
    val modelIndex: Int,
    val parts: List<BspInlineModelPartRecord>,
)

data class BspInlineModelPartRecord(
    val modelIndex: Int,
    val meshPartId: String,
    val textureInfoIndex: Int,
    val textureName: String,
    val textureFlags: Int,
    val textureAnimationNext: Int,
    /** Average per-style baked light contributions aggregated across faces in this inline part. */
    val lightStyleContributions: List<BspLightStyleContributionRecord> = emptyList(),
)

data class BspLightStyleContributionRecord(
    val styleIndex: Int,
    val red: Float,
    val green: Float,
    val blue: Float,
)

/**
 * Loads Quake2 BSP maps into renderable libGDX models.
 *
 * Loader flow:
 * 1. [getDependencies] parses BSP bytes and declares all referenced WAL textures.
     * 2. [load] builds one libGDX [Model] per BSP model (world model + inline brush models).
     * 3. World model faces are emitted as per-surface mesh parts and captured in [BspWorldRenderData].
     * 4. Inline brush models are emitted as stable texinfo-part buckets captured in [BspInlineModelRenderData].
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

    /**
     * Synchronously parses BSP bytes and builds render/runtime structures.
     *
     * Returned [BspMapAsset] owns only generated [Model] instances.
     * Textures are external dependencies managed by [AssetManager].
     */
    override fun load(
        manager: AssetManager,
        fileName: String,
        file: FileHandle,
        parameter: Parameters?
    ): BspMapAsset {
        val mapData = file.readBytes()
        val bsp = Bsp(ByteBuffer.wrap(mapData))
        val worldSurfaces = collectWorldSurfaceRecords(bsp)
        val inlineRenderData = collectInlineModelRenderData(bsp)
        return BspMapAsset(
            mapData = mapData,
            models = buildModels(bsp, manager, worldSurfaces, inlineRenderData),
            worldRenderData = buildWorldRenderData(bsp, worldSurfaces),
            inlineRenderData = inlineRenderData,
        )
    }

    /**
     * Declares WAL texture dependencies required by BSP face texinfos and animation chains.
     */
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
     * Inline models use stable part ids keyed by texinfo index.
     *
     * Quake2 `model 0` (the whole world) => one libGDX [Model] where each world surface is a distinct mesh part.
     * Quake2 `model N>0` (inline brush models like doors) => one libGDX [Model] with texinfo-part buckets.
     */
    private fun buildModels(
        bsp: Bsp,
        manager: AssetManager,
        worldSurfaces: List<BspWorldSurfaceRecord>,
        inlineRenderData: List<BspInlineModelRenderData>,
    ): List<Model> {
        val inlineRenderDataByModel = inlineRenderData.associateBy { it.modelIndex }
        return bsp.models.mapIndexed { modelIndex, model ->
            val modelBuilder = ModelBuilder()
            modelBuilder.begin()
            if (modelIndex == 0) {
                worldSurfaces.forEach { surface ->
                    val texture = manager.get(toWalPath(surface.textureName), Texture::class.java)
                    val material = Material(TextureAttribute(TextureAttribute.Diffuse, texture))
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
                val faceIndicesByTexInfo = (0..<model.faceCount)
                    .map { offset -> model.firstFace + offset }
                    .groupBy { faceIndex -> bsp.faces[faceIndex].textureInfoIndex }
                val inlineParts = inlineRenderDataByModel[modelIndex]?.parts.orEmpty()
                inlineParts.forEach { part ->
                    val texturePath = toWalPath(part.textureName)
                    val texture = manager.get(texturePath, Texture::class.java)
                    val meshBuilder = modelBuilder.part(
                        part.meshPartId,
                        GL_TRIANGLES,
                        VertexAttributes(VertexAttribute.Position(), VertexAttribute.TexCoords(0)),
                        Material(TextureAttribute(TextureAttribute.Diffuse, texture))
                    )
                    faceIndicesByTexInfo[part.textureInfoIndex]
                        .orEmpty()
                        .forEach { faceIndex ->
                            addFaceAsTriangles(bsp, bsp.faces[faceIndex], texture, meshBuilder)
                        }
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
        // Reconstruct polygon boundary from signed surfedges, then emit a triangle fan.
        val vertices = extractFaceVertexIndices(bsp, face)
        if (vertices.size < 3) {
            return
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
 * Collects stable texinfo-part metadata for inline BSP models (`model index > 0`).
 */
internal fun collectInlineModelRenderData(bsp: Bsp): List<BspInlineModelRenderData> {
    return bsp.models.mapIndexedNotNull { modelIndex, model ->
        if (modelIndex == 0) {
            return@mapIndexedNotNull null
        }

        val texInfoIndices = LinkedHashSet<Int>()
        repeat(model.faceCount) { offset ->
            val face = bsp.faces[model.firstFace + offset]
            texInfoIndices += face.textureInfoIndex
        }

        val partRecords = texInfoIndices.mapNotNull { textureInfoIndex ->
            val texInfo = bsp.textures.getOrNull(textureInfoIndex) ?: return@mapNotNull null
            if (!shouldLoadWalTexture(texInfo.name)) {
                return@mapNotNull null
            }
            val faceIndices = (0..<model.faceCount)
                .map { offset -> model.firstFace + offset }
                .filter { faceIndex -> bsp.faces[faceIndex].textureInfoIndex == textureInfoIndex }
            BspInlineModelPartRecord(
                modelIndex = modelIndex,
                meshPartId = inlineMeshPartId(modelIndex, textureInfoIndex),
                textureInfoIndex = textureInfoIndex,
                textureName = texInfo.name,
                textureFlags = texInfo.flags,
                textureAnimationNext = texInfo.next,
                lightStyleContributions = aggregatePartLightStyleContributions(
                    bsp = bsp,
                    faceIndices = faceIndices
                ),
            )
        }

        BspInlineModelRenderData(
            modelIndex = modelIndex,
            parts = partRecords
        )
    }
}

/**
 * Parses BSP bytes and returns unique WAL texture dependency paths.
 *
 * Includes texinfo animation-chain textures (`nexttexinfo`) so world animations can
 * switch to frames not directly referenced by any face.
 *
 * Legacy counterpart:
 * `client/render/fast/Model.Mod_LoadTexinfo` + `Surf.R_TextureAnimation`.
 */
internal fun collectWalTexturePaths(bspData: ByteArray): List<String> {
    val bsp = Bsp(ByteBuffer.wrap(bspData))
    val texturePaths = LinkedHashSet<String>()
    val texInfoIndices = LinkedHashSet<Int>()

    bsp.models.forEach { model ->
        repeat(model.faceCount) { offset ->
            val face = bsp.faces[model.firstFace + offset]
            texInfoIndices += face.textureInfoIndex
        }
    }
    texInfoIndices.forEach { texInfoIndex ->
        collectTextureAnimationChainIndices(bsp.textures, texInfoIndex).forEach { index ->
            val textureName = bsp.textures[index].name
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
            lightMapOffset = face.lightMapOffset,
            lightStyleContributions = buildFaceLightStyleContributions(bsp, face),
        )
    }
}

/**
 * Builds leaf->surface mapping for the world model using parsed BSP leaf-face indices.
 *
 * The output is the join point between:
 * - collision/PVS data (leafs/clusters/areas), and
 * - render data (surface mesh parts).
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
    val textureInfos = bsp.textures.mapIndexedNotNull { index, textureInfo ->
        if (!shouldLoadWalTexture(textureInfo.name)) {
            return@mapIndexedNotNull null
        }
        BspWorldTextureInfoRecord(
            textureInfoIndex = index,
            textureName = textureInfo.name,
            textureFlags = textureInfo.flags,
            textureAnimationNext = textureInfo.next,
        )
    }
    return BspWorldRenderData(
        surfaces = surfaces,
        leaves = leaves,
        textureInfos = textureInfos,
    )
}

private fun collectTextureAnimationChainIndices(
    textureInfos: kotlin.Array<jake2.qcommon.filesystem.BspTextureInfo>,
    startIndex: Int,
): IntArray {
    if (startIndex !in textureInfos.indices) {
        return intArrayOf()
    }

    val indices = ArrayList<Int>()
    val visited = HashSet<Int>()
    var texInfoIndex = startIndex
    // Follow `nexttexinfo` until chain end, invalid index, or cycle back.
    while (texInfoIndex in textureInfos.indices && visited.add(texInfoIndex)) {
        indices += texInfoIndex
        val next = textureInfos[texInfoIndex].next
        if (next <= 0 || next !in textureInfos.indices) {
            break
        }
        texInfoIndex = next
    }
    return indices.toIntArray()
}

private data class WeightedLightStyleContribution(
    val styleIndex: Int,
    var redSum: Float,
    var greenSum: Float,
    var blueSum: Float,
    var texelWeight: Int,
)

private fun aggregatePartLightStyleContributions(
    bsp: Bsp,
    faceIndices: List<Int>,
): List<BspLightStyleContributionRecord> {
    if (faceIndices.isEmpty()) {
        return emptyList()
    }
    val byStyle = LinkedHashMap<Int, WeightedLightStyleContribution>()
    faceIndices.forEach { faceIndex ->
        val face = bsp.faces[faceIndex]
        val faceContrib = buildFaceLightStyleContributions(bsp, face)
        val sampleCount = computeFaceLightmapSampleCount(bsp, face)
        if (sampleCount <= 0) {
            return@forEach
        }
        faceContrib.forEach { contribution ->
            val aggregate = byStyle.getOrPut(contribution.styleIndex) {
                WeightedLightStyleContribution(
                    styleIndex = contribution.styleIndex,
                    redSum = 0f,
                    greenSum = 0f,
                    blueSum = 0f,
                    texelWeight = 0,
                )
            }
            aggregate.redSum += contribution.red * sampleCount
            aggregate.greenSum += contribution.green * sampleCount
            aggregate.blueSum += contribution.blue * sampleCount
            aggregate.texelWeight += sampleCount
        }
    }
    return byStyle.values.mapNotNull { aggregate ->
        if (aggregate.texelWeight <= 0) {
            return@mapNotNull null
        }
        BspLightStyleContributionRecord(
            styleIndex = aggregate.styleIndex,
            red = aggregate.redSum / aggregate.texelWeight,
            green = aggregate.greenSum / aggregate.texelWeight,
            blue = aggregate.blueSum / aggregate.texelWeight,
        )
    }
}

private fun buildFaceLightStyleContributions(
    bsp: Bsp,
    face: jake2.qcommon.filesystem.BspFace,
): List<BspLightStyleContributionRecord> {
    if (face.lightMapOffset < 0 || bsp.lighting.isEmpty()) {
        return emptyList()
    }
    val sampleCount = computeFaceLightmapSampleCount(bsp, face)
    if (sampleCount <= 0) {
        return emptyList()
    }
    val styles = face.lightMapStyles
        .map { it.toInt() and 0xFF }
        .takeWhile { it != 255 }
    if (styles.isEmpty()) {
        return emptyList()
    }

    val bytesPerStyle = sampleCount * 3
    return styles.mapIndexedNotNull { styleSlot, styleIndex ->
        val styleOffset = face.lightMapOffset + styleSlot * bytesPerStyle
        val endOffset = styleOffset + bytesPerStyle
        if (styleOffset < 0 || endOffset > bsp.lighting.size) {
            return@mapIndexedNotNull null
        }
        var red = 0f
        var green = 0f
        var blue = 0f
        var cursor = styleOffset
        repeat(sampleCount) {
            red += (bsp.lighting[cursor++].toInt() and 0xFF) / 255f
            green += (bsp.lighting[cursor++].toInt() and 0xFF) / 255f
            blue += (bsp.lighting[cursor++].toInt() and 0xFF) / 255f
        }
        BspLightStyleContributionRecord(
            styleIndex = styleIndex,
            red = red / sampleCount,
            green = green / sampleCount,
            blue = blue / sampleCount,
        )
    }
}

private fun computeFaceLightmapSampleCount(
    bsp: Bsp,
    face: jake2.qcommon.filesystem.BspFace,
): Int {
    if (face.numEdges < 3) {
        return 0
    }
    val textureInfo = bsp.textures[face.textureInfoIndex]
    val vertexIndices = extractFaceVertexIndices(bsp, face)
    if (vertexIndices.isEmpty()) {
        return 0
    }
    var minS = Float.POSITIVE_INFINITY
    var maxS = Float.NEGATIVE_INFINITY
    var minT = Float.POSITIVE_INFINITY
    var maxT = Float.NEGATIVE_INFINITY
    vertexIndices.forEach { vertexIndex ->
        val vertex = bsp.vertices[vertexIndex]
        val s = vertex.x * textureInfo.uAxis.x + vertex.y * textureInfo.uAxis.y + vertex.z * textureInfo.uAxis.z + textureInfo.uOffset
        val t = vertex.x * textureInfo.vAxis.x + vertex.y * textureInfo.vAxis.y + vertex.z * textureInfo.vAxis.z + textureInfo.vOffset
        minS = minOf(minS, s)
        maxS = maxOf(maxS, s)
        minT = minOf(minT, t)
        maxT = maxOf(maxT, t)
    }

    val minSBlock = floor(minS / 16f)
    val maxSBlock = ceil(maxS / 16f)
    val minTBlock = floor(minT / 16f)
    val maxTBlock = ceil(maxT / 16f)

    val smax = (maxSBlock - minSBlock).toInt() + 1
    val tmax = (maxTBlock - minTBlock).toInt() + 1
    if (smax <= 0 || tmax <= 0) {
        return 0
    }
    return smax * tmax
}

private fun extractFaceVertexIndices(
    bsp: Bsp,
    face: jake2.qcommon.filesystem.BspFace,
): List<Int> {
    if (face.numEdges < 3) {
        return emptyList()
    }
    return (0..<face.numEdges).map { edgeIndex ->
        val faceEdge = bsp.faceEdges[face.firstEdgeIndex + edgeIndex]
        if (faceEdge > 0) {
            bsp.edges[faceEdge].v2
        } else {
            bsp.edges[-faceEdge].v1
        }
    }
}

private fun inlineMeshPartId(modelIndex: Int, textureInfoIndex: Int): String =
    "inline_${modelIndex}_texinfo_$textureInfoIndex"

/** Maps raw BSP texture name to conventional Quake2 WAL asset path. */
private fun toWalPath(textureName: String): String = "textures/${textureName.trim()}.wal"

/** Filters out non-renderable or separately handled pseudo-textures. */
private fun shouldLoadWalTexture(textureName: String): Boolean {
    val normalized = textureName.trim()
    // sky is loaded separately, see SkyLoader
    return normalized.isNotEmpty() && !normalized.contains("sky", ignoreCase = true)
}

/** Default texture sampling for brush surfaces: repeating UVs along both axes. */
private fun defaultWalParameters() = WalLoader.Parameters(
    wrapU = Texture.TextureWrap.Repeat,
    wrapV = Texture.TextureWrap.Repeat,
)
