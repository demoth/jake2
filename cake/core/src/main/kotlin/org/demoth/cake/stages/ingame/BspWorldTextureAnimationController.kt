package org.demoth.cake.stages.ingame

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.model.Node
import com.badlogic.gdx.graphics.g3d.model.NodePart
import org.demoth.cake.assets.BspWorldRenderData
import org.demoth.cake.assets.BspWorldTextureInfoRecord

private const val WORLD_TEXTURE_ANIMATION_FRAME_MS = 500

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
    private val textureInfosByIndex = worldRenderData.textureInfos.associateBy { it.textureInfoIndex }
    private val surfaceNodeParts: Array<NodePart?> = Array(worldRenderData.surfaces.size) { null }
    private val surfaceTexInfoChains: Array<IntArray> = Array(worldRenderData.surfaces.size) { intArrayOf() }
    private val texturesByTexInfoIndex = HashMap<Int, Texture>()
    private var lastAnimationFrame: Int = Int.MIN_VALUE

    init {
        indexWorldSurfaceNodeParts()
        buildSurfaceAnimationChains()
        cacheAnimationTextures()
    }

    /**
     * Applies the animated texture frame for each world surface.
     *
     * The update is throttled to legacy cadence (2 Hz): if the frame index did not change,
     * no material mutations are performed.
     */
    fun update(currentTimeMs: Int) {
        if (surfaceNodeParts.isEmpty()) {
            return
        }

        val animationFrame = currentTimeMs / WORLD_TEXTURE_ANIMATION_FRAME_MS
        if (animationFrame == lastAnimationFrame) {
            return
        }

        worldRenderData.surfaces.forEachIndexed { surfaceIndex, _ ->
            val nodePart = surfaceNodeParts[surfaceIndex] ?: return@forEachIndexed
            val chain = surfaceTexInfoChains[surfaceIndex]
            val texInfoIndex = selectTextureAnimationTexInfo(chain, currentTimeMs) ?: return@forEachIndexed
            val texture = texturesByTexInfoIndex[texInfoIndex] ?: return@forEachIndexed
            applyDiffuseTexture(nodePart, texture)
        }

        lastAnimationFrame = animationFrame
    }

    private fun indexWorldSurfaceNodeParts() {
        // Build a fast meshPartId -> NodePart table once; render loop only does array lookups.
        val nodePartsById = HashMap<String, NodePart>()
        modelInstance.nodes.forEach { node ->
            collectNodeParts(node, nodePartsById)
        }
        worldRenderData.surfaces.forEachIndexed { surfaceIndex, surface ->
            surfaceNodeParts[surfaceIndex] = nodePartsById[surface.meshPartId]
        }
    }

    private fun buildSurfaceAnimationChains() {
        // Precompute one texinfo chain per world surface so runtime animation stays allocation-free.
        worldRenderData.surfaces.forEachIndexed { surfaceIndex, surface ->
            surfaceTexInfoChains[surfaceIndex] = resolveTextureAnimationChain(
                startTexInfoIndex = surface.textureInfoIndex,
                textureInfosByIndex = textureInfosByIndex,
            )
        }
    }

    private fun cacheAnimationTextures() {
        // Resolve and cache all textures used by all discovered texinfo chains.
        // Missing textures are tolerated and simply skipped at render time.
        for (chain in surfaceTexInfoChains) {
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

private fun toWalPath(textureName: String): String = "textures/${textureName.trim()}.wal"
