package org.demoth.cake.stages.ingame

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.model.Node
import com.badlogic.gdx.graphics.g3d.model.NodePart
import org.demoth.cake.assets.BspInlineModelRenderData
import org.demoth.cake.assets.BspWorldRenderData
import org.demoth.cake.assets.BspWorldTextureInfoRecord
import java.util.IdentityHashMap

private const val WORLD_TEXTURE_ANIMATION_FRAME_MS = 500

internal data class TextureAnimationPartBinding(
    val meshPartId: String,
    val texInfoChain: IntArray,
)

private data class TextureAnimationResources(
    val textureInfosByIndex: Map<Int, BspWorldTextureInfoRecord>,
    val texturesByTexInfoIndex: Map<Int, Texture>,
)

/**
 * Drives Quake2-style world texture animation using BSP texinfo `nexttexinfo` chains.
 *
 * Legacy counterpart:
 * - `client/render/fast/Surf.R_TextureAnimation` (texinfo chain walk by frame index).
 * - `client/render/fast/Surf.R_DrawWorld` (`currententity.frame = (int)(r_newrefdef.time * 2)`).
 */
class BspWorldTextureAnimationController(
    private val worldRenderData: BspWorldRenderData,
    private val modelInstance: ModelInstance,
    private val assetManager: AssetManager,
) {
    private val animationResources: TextureAnimationResources
    private val surfaceBindings: List<TextureAnimationPartBinding>
    private val nodePartsById: Map<String, NodePart>
    private var lastAnimationFrame: Int = Int.MIN_VALUE

    init {
        val textureInfosByIndex = worldRenderData.textureInfos.associateBy { it.textureInfoIndex }
        surfaceBindings = buildWorldSurfaceBindings(worldRenderData, textureInfosByIndex)
        animationResources = createTextureAnimationResources(
            textureInfosByIndex = textureInfosByIndex,
            chains = surfaceBindings.asSequence().map { it.texInfoChain },
            assetManager = assetManager,
        )
        nodePartsById = collectNodePartsById(modelInstance)
    }

    /**
     * Applies the animated texture frame for each world surface.
     *
     * The update is throttled to legacy cadence (2 Hz): if the frame index did not change,
     * no material mutations are performed.
     */
    fun update(currentTimeMs: Int) {
        if (surfaceBindings.isEmpty()) {
            return
        }

        val animationFrame = currentTimeMs / WORLD_TEXTURE_ANIMATION_FRAME_MS
        if (animationFrame == lastAnimationFrame) {
            return
        }

        applyAnimatedTextureBindings(
            bindings = surfaceBindings,
            nodePartsById = nodePartsById,
            texturesByTexInfoIndex = animationResources.texturesByTexInfoIndex,
            selectTexInfoIndex = { chain -> selectTextureAnimationTexInfo(chain, currentTimeMs) },
        )
        lastAnimationFrame = animationFrame
    }
}

/**
 * Drives texinfo-chain texture animation for inline BSP brush models (`*1`, `*2`, ...).
 *
 * Legacy counterpart:
 * `client/render/fast/Surf.R_DrawBrushModel` + `R_TextureAnimation` using
 * `currententity.frame % tex.numframes`.
 */
class BspInlineTextureAnimationController(
    inlineRenderData: List<BspInlineModelRenderData>,
    textureInfos: List<BspWorldTextureInfoRecord>,
    private val assetManager: AssetManager,
) {
    private val modelBindingsByIndex: Map<Int, List<TextureAnimationPartBinding>>
    private val animationResources: TextureAnimationResources
    private val modelNodePartsCache = IdentityHashMap<Model, Map<String, NodePart>>()

    init {
        val textureInfosByIndex = textureInfos.associateBy { it.textureInfoIndex }
        modelBindingsByIndex = buildInlineModelBindings(inlineRenderData, textureInfosByIndex)
        animationResources = createTextureAnimationResources(
            textureInfosByIndex = textureInfosByIndex,
            chains = modelBindingsByIndex.values.asSequence().flatten().map { it.texInfoChain },
            assetManager = assetManager,
        )
    }

    /**
     * Applies the animated texinfo frame for one inline brush-model entity.
     */
    fun update(modelInstance: ModelInstance, inlineModelIndex: Int, entityFrame: Int) {
        val bindings = modelBindingsByIndex[inlineModelIndex] ?: return
        val nodePartsById = modelNodePartsCache.getOrPut(modelInstance.model) {
            collectNodePartsById(modelInstance)
        }

        applyAnimatedTextureBindings(
            bindings = bindings,
            nodePartsById = nodePartsById,
            texturesByTexInfoIndex = animationResources.texturesByTexInfoIndex,
            selectTexInfoIndex = { chain -> selectTextureAnimationTexInfoByEntityFrame(chain, entityFrame) },
        )
    }
}

private fun buildWorldSurfaceBindings(
    worldRenderData: BspWorldRenderData,
    textureInfosByIndex: Map<Int, BspWorldTextureInfoRecord>,
): List<TextureAnimationPartBinding> {
    return worldRenderData.surfaces.map { surface ->
        TextureAnimationPartBinding(
            meshPartId = surface.meshPartId,
            texInfoChain = resolveTextureAnimationChain(surface.textureInfoIndex, textureInfosByIndex),
        )
    }
}

private fun buildInlineModelBindings(
    inlineRenderData: List<BspInlineModelRenderData>,
    textureInfosByIndex: Map<Int, BspWorldTextureInfoRecord>,
): Map<Int, List<TextureAnimationPartBinding>> {
    return inlineRenderData.associate { inlineModel ->
        inlineModel.modelIndex to inlineModel.parts.map { part ->
            TextureAnimationPartBinding(
                meshPartId = part.meshPartId,
                texInfoChain = resolveTextureAnimationChain(part.textureInfoIndex, textureInfosByIndex),
            )
        }
    }
}

private fun createTextureAnimationResources(
    textureInfosByIndex: Map<Int, BspWorldTextureInfoRecord>,
    chains: Sequence<IntArray>,
    assetManager: AssetManager,
): TextureAnimationResources {
    val texturesByTexInfoIndex = HashMap<Int, Texture>()
    for (chain in chains) {
        for (texInfoIndex in chain) {
            if (texturesByTexInfoIndex.containsKey(texInfoIndex)) {
                continue
            }
            val textureName = textureInfosByIndex[texInfoIndex]?.textureName ?: continue
            val texturePath = toWalPath(textureName)
            if (assetManager.isLoaded(texturePath, Texture::class.java)) {
                texturesByTexInfoIndex[texInfoIndex] = assetManager.get(texturePath, Texture::class.java)
            }
        }
    }
    return TextureAnimationResources(
        textureInfosByIndex = textureInfosByIndex,
        texturesByTexInfoIndex = texturesByTexInfoIndex,
    )
}

private fun applyAnimatedTextureBindings(
    bindings: List<TextureAnimationPartBinding>,
    nodePartsById: Map<String, NodePart>,
    texturesByTexInfoIndex: Map<Int, Texture>,
    selectTexInfoIndex: (IntArray) -> Int?,
) {
    bindings.forEach { binding ->
        val nodePart = nodePartsById[binding.meshPartId] ?: return@forEach
        val texInfoIndex = selectTexInfoIndex(binding.texInfoChain) ?: return@forEach
        val texture = texturesByTexInfoIndex[texInfoIndex] ?: return@forEach
        applyDiffuseTexture(nodePart, texture)
    }
}

private fun collectNodePartsById(modelInstance: ModelInstance): Map<String, NodePart> {
    val nodePartsById = HashMap<String, NodePart>()
    modelInstance.nodes.forEach { node ->
        collectNodeParts(node, nodePartsById)
    }
    return nodePartsById
}

private fun collectNodeParts(node: Node, outById: MutableMap<String, NodePart>) {
    node.parts.forEach { part ->
        outById.putIfAbsent(part.meshPart.id, part)
    }
    node.children.forEach { child ->
        collectNodeParts(child, outById)
    }
}

private fun applyDiffuseTexture(nodePart: NodePart, texture: Texture) {
    // Reuse existing TextureAttribute when possible to avoid per-frame object churn.
    val diffuse = nodePart.material.get(TextureAttribute.Diffuse) as? TextureAttribute
    if (diffuse == null) {
        nodePart.material.set(TextureAttribute(TextureAttribute.Diffuse, texture))
        return
    }
    if (diffuse.textureDescription.texture !== texture) {
        diffuse.textureDescription.texture = texture
    }
}

internal fun resolveTextureAnimationChain(
    startTexInfoIndex: Int,
    textureInfosByIndex: Map<Int, BspWorldTextureInfoRecord>,
): IntArray {
    // Mirrors legacy `nexttexinfo` walk with loop detection for malformed/self-referencing chains.
    val start = textureInfosByIndex[startTexInfoIndex] ?: return intArrayOf()
    val chain = ArrayList<Int>()
    val visited = HashSet<Int>()

    var current = start
    while (visited.add(current.textureInfoIndex)) {
        chain += current.textureInfoIndex
        val next = current.textureAnimationNext
        if (next <= 0) {
            break
        }
        current = textureInfosByIndex[next] ?: break
    }

    return chain.toIntArray()
}

/**
 * Returns currently active texinfo index for the provided chain at legacy world-animation time.
 *
 * Equivalent to `currententity.frame % tex.numframes` in legacy renderer where
 * `currententity.frame = floor(time * 2)`.
 */
internal fun selectTextureAnimationTexInfo(chain: IntArray, currentTimeMs: Int): Int? {
    if (chain.isEmpty()) {
        return null
    }
    val animationFrame = currentTimeMs / WORLD_TEXTURE_ANIMATION_FRAME_MS
    val chainIndex = Math.floorMod(animationFrame, chain.size)
    return chain[chainIndex]
}

internal fun selectTextureAnimationTexInfoByEntityFrame(chain: IntArray, entityFrame: Int): Int? {
    if (chain.isEmpty()) {
        return null
    }
    val chainIndex = Math.floorMod(entityFrame, chain.size)
    return chain[chainIndex]
}

private fun toWalPath(textureName: String): String = "textures/${textureName.trim()}.wal"
