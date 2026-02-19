package org.demoth.cake.stages.ingame

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.model.Node
import com.badlogic.gdx.graphics.g3d.model.NodePart
import org.demoth.cake.assets.BspInlineModelRenderData
import org.demoth.cake.assets.BspWorldTextureInfoRecord
import java.util.IdentityHashMap

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
    private val inlineRenderDataByModel = inlineRenderData.associateBy { it.modelIndex }
    private val textureInfosByIndex = textureInfos.associateBy { it.textureInfoIndex }
    private val modelNodePartsCache = IdentityHashMap<Model, Map<String, NodePart>>()
    private val partAnimationChainsByModel = HashMap<Int, Map<String, IntArray>>()
    private val texturesByTexInfoIndex = HashMap<Int, Texture>()

    init {
        buildPartAnimationChains()
        cacheAnimationTextures()
    }

    /**
     * Applies the animated texinfo frame for one inline brush-model entity.
     */
    fun update(modelInstance: ModelInstance, inlineModelIndex: Int, entityFrame: Int) {
        val modelData = inlineRenderDataByModel[inlineModelIndex] ?: return
        val chainsByPart = partAnimationChainsByModel[inlineModelIndex] ?: return
        val nodePartsById = modelNodePartsCache.getOrPut(modelInstance.model) {
            collectNodePartsById(modelInstance)
        }

        modelData.parts.forEach { part ->
            val nodePart = nodePartsById[part.meshPartId] ?: return@forEach
            val chain = chainsByPart[part.meshPartId] ?: return@forEach
            val texInfoIndex = selectTextureAnimationTexInfoByEntityFrame(chain, entityFrame) ?: return@forEach
            val texture = texturesByTexInfoIndex[texInfoIndex] ?: return@forEach
            applyDiffuseTexture(nodePart, texture)
        }
    }

    private fun buildPartAnimationChains() {
        inlineRenderDataByModel.forEach { (modelIndex, modelData) ->
            val chainsByPart = modelData.parts.associate { part ->
                part.meshPartId to resolveTextureAnimationChain(part.textureInfoIndex, textureInfosByIndex)
            }
            partAnimationChainsByModel[modelIndex] = chainsByPart
        }
    }

    private fun cacheAnimationTextures() {
        for (chainsByPart in partAnimationChainsByModel.values) {
            for (chain in chainsByPart.values) {
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

internal fun selectTextureAnimationTexInfoByEntityFrame(chain: IntArray, entityFrame: Int): Int? {
    if (chain.isEmpty()) {
        return null
    }
    val chainIndex = Math.floorMod(entityFrame, chain.size)
    return chain[chainIndex]
}

private fun toWalPath(textureName: String): String = "textures/${textureName.trim()}.wal"
