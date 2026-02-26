package org.demoth.cake.assets

import com.badlogic.gdx.assets.AssetDescriptor
import com.badlogic.gdx.assets.AssetLoaderParameters
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.FileHandleResolver
import com.badlogic.gdx.assets.loaders.SynchronousAssetLoader
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.GL20.GL_TRIANGLES
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Attribute
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable
import jake2.qcommon.Defines
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
 * [inlineRenderData] stores stable per-face part metadata for inline brush models (`*1`, `*2`, ...).
 * [generatedTextures] are runtime-generated lightmap textures (one per face/style slot for world model surfaces).
 * BSP textures are loaded as independent AssetManager dependencies and are not disposed here.
 */
class BspMapAsset(
    val mapData: ByteArray,
    val models: List<Model>,
    val worldRenderData: BspWorldRenderData,
    val inlineRenderData: List<BspInlineModelRenderData>,
    val generatedTextures: List<Texture> = emptyList(),
) : Disposable {
    override fun dispose() {
        generatedTextures.forEach { it.dispose() }
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
    /** First valid lightstyle index (`null` when the face has no baked styles). */
    val primaryLightStyleIndex: Int?,
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
 * Each part maps one BSP face to one mesh part id in the generated libGDX model.
 */
data class BspInlineModelRenderData(
    val modelIndex: Int,
    val parts: List<BspInlineModelPartRecord>,
)

data class BspInlineModelPartRecord(
    val modelIndex: Int,
    val faceIndex: Int,
    val meshPartId: String,
    val textureInfoIndex: Int,
    val textureName: String,
    val textureFlags: Int,
    val textureAnimationNext: Int,
    val lightMapStyles: ByteArray = byteArrayOf(),
    val primaryLightStyleIndex: Int? = null,
    val lightMapOffset: Int = -1,
    /** Average per-style baked light contributions for this inline face (fallback path for non-lightmapped faces). */
    val lightStyleContributions: List<BspLightStyleContributionRecord> = emptyList(),
)

data class BspLightStyleContributionRecord(
    val styleIndex: Int,
    val red: Float,
    val green: Float,
    val blue: Float,
)

private data class FaceLightmapGeometry(
    val textureMinS: Float,
    val textureMinT: Float,
    val sMax: Int,
    val tMax: Int,
)

private data class FaceLightmapData(
    val styleTextures: List<FaceStyleLightmapTexture>,
    val geometry: FaceLightmapGeometry,
)

private data class FaceStyleLightmapTexture(
    val styleIndex: Int,
    val texture: Texture,
)

/**
 * Loads Quake2 BSP maps into renderable libGDX models.
 *
 * Loader flow:
 * 1. [getDependencies] parses BSP bytes and declares all referenced WAL textures.
 * 2. [load] builds one libGDX [Model] per BSP model (world model + inline brush models).
 * 3. World model faces are emitted as per-surface mesh parts and captured in [BspWorldRenderData].
 * 4. Inline brush models are emitted as stable per-face mesh parts captured in [BspInlineModelRenderData].
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
        // Custom TextureAttribute aliases must be registered into TextureAttribute.Mask
        // before constructing materials, otherwise libGDX throws "Invalid type specified".
        BspLightmapTextureAttribute.init()
        BspLightmapTexture1Attribute.init()
        BspLightmapTexture2Attribute.init()
        BspLightmapTexture3Attribute.init()
        val mapData = file.readBytes()
        val bsp = Bsp(ByteBuffer.wrap(mapData))
        val worldSurfaces = collectWorldSurfaceRecords(bsp)
        val inlineRenderData = collectInlineModelRenderData(bsp)
        val generatedTextures = mutableListOf<Texture>()
        return BspMapAsset(
            mapData = mapData,
            models = buildModels(bsp, manager, worldSurfaces, inlineRenderData, generatedTextures),
            worldRenderData = buildWorldRenderData(bsp, worldSurfaces),
            inlineRenderData = inlineRenderData,
            generatedTextures = generatedTextures,
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
     * Inline models use stable part ids keyed by BSP face index.
     *
     * Quake2 `model 0` (the whole world) => one libGDX [Model] where each world surface is a distinct mesh part.
     * Quake2 `model N>0` (inline brush models like doors) => one libGDX [Model] with per-face mesh parts.
     */
    private fun buildModels(
        bsp: Bsp,
        manager: AssetManager,
        worldSurfaces: List<BspWorldSurfaceRecord>,
        inlineRenderData: List<BspInlineModelRenderData>,
        generatedTextures: MutableList<Texture>,
    ): List<Model> {
        val inlineRenderDataByModel = inlineRenderData.associateBy { it.modelIndex }
        return bsp.models.mapIndexed { modelIndex, _ ->
            val modelBuilder = ModelBuilder()
            modelBuilder.begin()
            if (modelIndex == 0) {
                worldSurfaces.forEach { surface ->
                    val texture = manager.get(toWalPath(surface.textureName), Texture::class.java)
                    val face = bsp.faces[surface.faceIndex]
                    val vertexIndices = extractFaceVertexIndices(bsp, face) ?: return@forEach
                    val lightmap = if (shouldUseBspFaceLightmap(surface.textureFlags)) {
                        buildFaceLightmapData(bsp, face)
                    } else {
                        null
                    }
                    if (lightmap != null) {
                        generatedTextures += lightmap.styleTextures.map { it.texture }
                    }
                    val materialAttributes = mutableListOf<Attribute>(
                        TextureAttribute(TextureAttribute.Diffuse, texture),
                    )
                    lightmap?.styleTextures?.forEachIndexed { styleSlot, styleTexture ->
                        // Slot order must stay stable across loader + shader + runtime style weights:
                        // 0..3 -> lightMapStyles[0..3] / u_lightStyleWeights.rgba.
                        when (styleSlot) {
                            0 -> materialAttributes += BspLightmapTextureAttribute(styleTexture.texture)
                            1 -> materialAttributes += BspLightmapTexture1Attribute(styleTexture.texture)
                            2 -> materialAttributes += BspLightmapTexture2Attribute(styleTexture.texture)
                            3 -> materialAttributes += BspLightmapTexture3Attribute(styleTexture.texture)
                        }
                    }
                    val material = Material(*materialAttributes.toTypedArray())
                    val meshBuilder = modelBuilder.part(
                        surface.meshPartId,
                        GL_TRIANGLES,
                        VertexAttributes(
                            VertexAttribute.Position(),
                            VertexAttribute.TexCoords(0),
                            VertexAttribute.TexCoords(1),
                        ),
                        material
                    )
                    addFaceAsTriangles(
                        bsp = bsp,
                        face = face,
                        vertexIndices = vertexIndices,
                        texture = texture,
                        meshBuilder = meshBuilder,
                        includeLightmapUv = true,
                        lightmapGeometry = lightmap?.geometry,
                    )
                }
            } else {
                val inlineParts = inlineRenderDataByModel[modelIndex]?.parts.orEmpty()
                inlineParts.forEach { part ->
                    val texturePath = toWalPath(part.textureName)
                    val texture = manager.get(texturePath, Texture::class.java)
                    val face = bsp.faces[part.faceIndex]
                    val vertexIndices = extractFaceVertexIndices(bsp, face) ?: return@forEach
                    val lightmap = if (shouldUseBspFaceLightmap(part.textureFlags)) {
                        buildFaceLightmapData(bsp, face)
                    } else {
                        null
                    }
                    if (lightmap != null) {
                        generatedTextures += lightmap.styleTextures.map { it.texture }
                    }
                    val materialAttributes = mutableListOf<Attribute>(
                        TextureAttribute(TextureAttribute.Diffuse, texture),
                    )
                    lightmap?.styleTextures?.forEachIndexed { styleSlot, styleTexture ->
                        when (styleSlot) {
                            0 -> materialAttributes += BspLightmapTextureAttribute(styleTexture.texture)
                            1 -> materialAttributes += BspLightmapTexture1Attribute(styleTexture.texture)
                            2 -> materialAttributes += BspLightmapTexture2Attribute(styleTexture.texture)
                            3 -> materialAttributes += BspLightmapTexture3Attribute(styleTexture.texture)
                        }
                    }
                    val meshBuilder = modelBuilder.part(
                        part.meshPartId,
                        GL_TRIANGLES,
                        VertexAttributes(
                            VertexAttribute.Position(),
                            VertexAttribute.TexCoords(0),
                            VertexAttribute.TexCoords(1),
                        ),
                        Material(*materialAttributes.toTypedArray())
                    )
                    addFaceAsTriangles(
                        bsp = bsp,
                        face = face,
                        vertexIndices = vertexIndices,
                        texture = texture,
                        meshBuilder = meshBuilder,
                        includeLightmapUv = true,
                        lightmapGeometry = lightmap?.geometry,
                    )
                }
            }
            modelBuilder.end()
        }
    }

private fun addFaceAsTriangles(
    bsp: Bsp,
    face: jake2.qcommon.filesystem.BspFace,
    vertexIndices: List<Int>,
    texture: Texture,
    meshBuilder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder,
    includeLightmapUv: Boolean,
    lightmapGeometry: FaceLightmapGeometry?,
) {
    // Reconstruct polygon boundary from signed surfedges, then emit a triangle fan.
    if (vertexIndices.size < 3) {
        return
    }
    val textureInfo = bsp.textures[face.textureInfoIndex]

    val vertexBuffer = vertexIndices.drop(1).windowed(2).flatMap { (i1, i2) ->
        val v0 = bsp.vertices[vertexIndices.first()]
        val v1 = bsp.vertices[i1]
        val v2 = bsp.vertices[i2]
        val uv0 = textureInfo.calculateUV(v0, texture.width, texture.height)
        val uv1 = textureInfo.calculateUV(v1, texture.width, texture.height)
        val uv2 = textureInfo.calculateUV(v2, texture.width, texture.height)
            val lm0 = calculateLightmapUv(v0, textureInfo, lightmapGeometry)
            val lm1 = calculateLightmapUv(v1, textureInfo, lightmapGeometry)
            val lm2 = calculateLightmapUv(v2, textureInfo, lightmapGeometry)
            if (includeLightmapUv) {
                listOf(
                    v2.x, v2.y, v2.z, uv2.first(), uv2.last(), lm2.first, lm2.second,
                    v1.x, v1.y, v1.z, uv1.first(), uv1.last(), lm1.first, lm1.second,
                    v0.x, v0.y, v0.z, uv0.first(), uv0.last(), lm0.first, lm0.second,
                )
            } else {
                listOf(
                    v2.x, v2.y, v2.z, uv2.first(), uv2.last(),
                    v1.x, v1.y, v1.z, uv1.first(), uv1.last(),
                    v0.x, v0.y, v0.z, uv0.first(), uv0.last(),
                )
            }
        }
        val floatsPerVertex = if (includeLightmapUv) 7 else 5
        val size = vertexBuffer.size / floatsPerVertex
        meshBuilder.addMesh(vertexBuffer.toFloatArray(), (0..<size).map { it.toShort() }.toShortArray())
    }
}

/**
 * Collects stable per-face metadata for inline BSP models (`model index > 0`).
 */
internal fun collectInlineModelRenderData(bsp: Bsp): List<BspInlineModelRenderData> {
    return bsp.models.mapIndexedNotNull { modelIndex, model ->
        if (modelIndex == 0) {
            return@mapIndexedNotNull null
        }

        val partRecords = (0..<model.faceCount).mapNotNull { offset ->
            val faceIndex = model.firstFace + offset
            val face = bsp.faces[faceIndex]
            extractFaceVertexIndices(bsp, face) ?: return@mapNotNull null // ensure face has enough edges
            val textureInfoIndex = face.textureInfoIndex
            val texInfo = bsp.textures.getOrNull(textureInfoIndex) ?: return@mapNotNull null
            if (!shouldLoadWalTexture(texInfo.name)) {
                return@mapNotNull null
            }
            val isLightmappedFace = shouldUseBspFaceLightmap(texInfo.flags)
            BspInlineModelPartRecord(
                modelIndex = modelIndex,
                faceIndex = faceIndex,
                meshPartId = inlineMeshPartId(modelIndex, faceIndex),
                textureInfoIndex = textureInfoIndex,
                textureName = texInfo.name,
                textureFlags = texInfo.flags,
                textureAnimationNext = texInfo.next,
                lightMapStyles = face.lightMapStyles.copyOf(),
                primaryLightStyleIndex = extractLightStyles(face).firstOrNull(),
                lightMapOffset = face.lightMapOffset,
                lightStyleContributions = if (isLightmappedFace) buildFaceLightStyleContributions(bsp, face) else emptyList(),
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
        extractFaceVertexIndices(bsp, face) ?: return@mapNotNull null // ensure face has enough edges
        val texInfo = bsp.textures[face.textureInfoIndex]
        if (!shouldLoadWalTexture(texInfo.name)) {
            return@mapNotNull null
        }
        val isLightmappedFace = shouldUseBspFaceLightmap(texInfo.flags)
        BspWorldSurfaceRecord(
            faceIndex = faceIndex,
            meshPartId = "surface_$faceIndex",
            textureInfoIndex = face.textureInfoIndex,
            textureName = texInfo.name,
            textureFlags = texInfo.flags,
            textureAnimationNext = texInfo.next,
            lightMapStyles = face.lightMapStyles.copyOf(),
            primaryLightStyleIndex = extractLightStyles(face).firstOrNull(),
            lightMapOffset = face.lightMapOffset,
            lightStyleContributions = if (isLightmappedFace) buildFaceLightStyleContributions(bsp, face) else emptyList(),
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

private fun calculateLightmapUv(
    vertex: jake2.qcommon.math.Vector3f,
    textureInfo: jake2.qcommon.filesystem.BspTextureInfo,
    lightmapGeometry: FaceLightmapGeometry?,
): Pair<Float, Float> {
    if (lightmapGeometry == null) {
        return 0f to 0f
    }
    val s = vertex.x * textureInfo.uAxis.x +
        vertex.y * textureInfo.uAxis.y +
        vertex.z * textureInfo.uAxis.z + textureInfo.uOffset
    val t = vertex.x * textureInfo.vAxis.x +
        vertex.y * textureInfo.vAxis.y +
        vertex.z * textureInfo.vAxis.z + textureInfo.vOffset

    // Legacy counterpart:
    // `client/render/fast/Surf.BuildPolygonFromSurface` lightmap coordinate path
    // (`s -= texturemins; s += 8; s /= smax*16` and same for `t`).
    val lmU = (s - lightmapGeometry.textureMinS + 8f) / (lightmapGeometry.sMax * 16f)
    val lmV = (t - lightmapGeometry.textureMinT + 8f) / (lightmapGeometry.tMax * 16f)
    return lmU to lmV
}

private fun buildFaceLightmapData(
    bsp: Bsp,
    face: jake2.qcommon.filesystem.BspFace,
): FaceLightmapData? {
    // Decodes up to 4 BSP lightstyle blocks for one face into separate GPU textures.
    //
    // Slot order is preserved (style slot 0..3) and must stay aligned with:
    // - BspLightmapTexture*Attribute assignment in buildModels(...)
    // - runtime RGBA style weights in BspSurfaceMaterialController
    // - sampler usage in BspLightmapShader.
    if (face.lightMapOffset < 0 || bsp.lighting.isEmpty()) {
        return null
    }
    val styles = extractLightStyles(face)
    if (styles.isEmpty()) {
        return null
    }
    val geometry = computeFaceLightmapGeometry(bsp, face) ?: return null
    val sampleCount = geometry.sMax * geometry.tMax
    if (sampleCount <= 0) {
        return null
    }
    val bytesPerStyle = sampleCount * 3
    val styleTextures = ArrayList<FaceStyleLightmapTexture>(styles.size)
    styles.forEachIndexed { styleSlot, styleIndex ->
        val styleOffset = face.lightMapOffset + styleSlot * bytesPerStyle
        val endOffset = styleOffset + bytesPerStyle
        if (styleOffset < 0 || endOffset > bsp.lighting.size) {
            return@forEachIndexed
        }
        val pixmap = Pixmap(geometry.sMax, geometry.tMax, Pixmap.Format.RGB888)
        var cursor = styleOffset
        for (y in 0 until geometry.tMax) {
            for (x in 0 until geometry.sMax) {
                val r = bsp.lighting[cursor++].toInt() and 0xFF
                val g = bsp.lighting[cursor++].toInt() and 0xFF
                val b = bsp.lighting[cursor++].toInt() and 0xFF
                pixmap.drawPixel(x, y, (r shl 24) or (g shl 16) or (b shl 8) or 0xFF)
            }
        }
        val texture = Texture(pixmap).apply {
            setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
            setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge)
        }
        pixmap.dispose()
        styleTextures.add(
            FaceStyleLightmapTexture(
                styleIndex = styleIndex,
                texture = texture,
            )
        )
    }
    if (styleTextures.isEmpty()) {
        return null
    }
    return FaceLightmapData(
        styleTextures = styleTextures,
        geometry = geometry,
    )
}

private fun shouldUseBspFaceLightmap(textureFlags: Int): Boolean =
    (textureFlags and (Defines.SURF_TRANS33 or Defines.SURF_TRANS66 or Defines.SURF_WARP)) == 0

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
    val styles = extractLightStyles(face)
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
    val geometry = computeFaceLightmapGeometry(bsp, face) ?: return 0
    if (geometry.sMax <= 0 || geometry.tMax <= 0) {
        return 0
    }
    return geometry.sMax * geometry.tMax
}

private fun computeFaceLightmapGeometry(
    bsp: Bsp,
    face: jake2.qcommon.filesystem.BspFace,
): FaceLightmapGeometry? {
    // Legacy counterpart:
    // `client/render/fast/Surf.GL_CreateSurfaceLightmap` block extents from texture-space bounds.
    //
    // BSP lightmaps are stored on a 16x16 luxel grid; this computes face-local
    // texture-space bounds and resulting (sMax, tMax) sample dimensions.
    val textureInfo = bsp.textures[face.textureInfoIndex]
    val vertexIndices = extractFaceVertexIndices(bsp, face) ?: return null
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
        return null
    }
    return FaceLightmapGeometry(
        textureMinS = minSBlock * 16f,
        textureMinT = minTBlock * 16f,
        sMax = smax,
        tMax = tmax,
    )
}

private fun extractLightStyles(face: jake2.qcommon.filesystem.BspFace): List<Int> {
    // Legacy references:
    // - `qcommon/Defines.MAXLIGHTMAPS` (=4)
    // - `client/render/fast/Light.R_BuildLightMap` loops style slots until 255 terminator.
    return face.lightMapStyles
        .map { it.toInt() and 0xFF }
        .takeWhile { it != 255 }
}

/**
 * Extracts ordered polygon vertex indices for one BSP face from signed surfedges.
 *
 * Returns `null` when `face.numEdges < 3` (non-triangulatable face).
 *
 * Why this can happen:
 * - malformed BSP face records in custom/modded maps,
 * - degenerate compile output where a face collapses below triangle cardinality.
 *
 * Note:
 * this function currently preserves raw edge order and does not de-duplicate repeated vertex ids.
 */
private fun extractFaceVertexIndices(
    bsp: Bsp,
    face: jake2.qcommon.filesystem.BspFace,
): List<Int>? {
    if (face.numEdges < 3) {
        return null
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

private fun inlineMeshPartId(modelIndex: Int, faceIndex: Int): String =
    "inline_${modelIndex}_face_$faceIndex"

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
