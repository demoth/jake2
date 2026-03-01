package org.demoth.cake.stages.ingame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.GdxRuntimeException
import jake2.qcommon.Defines
import org.demoth.cake.assets.BspLightmapAtlasPageTextures
import org.demoth.cake.assets.BspWorldBatchData
import org.demoth.cake.assets.BspWorldBatchSurface
import org.demoth.cake.assets.BspWorldRenderData
import org.demoth.cake.assets.BspWorldSurfacePass
import kotlin.math.cos
import kotlin.math.sin

private const val BATCH_LIGHT_STYLE_SLOTS = 4

private data class FrameBatchKey(
    val activeTextureInfoIndex: Int,
    val lightmapPageIndex: Int,
    val textureFlags: Int,
    val surfacePass: BspWorldSurfacePass,
    val lightStyleSignature: Long,
)

private data class FrameTranslucentBatchKey(
    val activeTextureInfoIndex: Int,
    val textureFlags: Int,
)

data class BspWorldBatchRenderStats(
    val visibleSurfaceCount: Int = 0,
    val groupedSurfaceCount: Int = 0,
    val drawCalls: Int = 0,
)

/**
 * Dedicated world BSP renderer for the in-progress Q2PRO-style batching path.
 * Draws world surfaces in explicit opaque and translucent passes.
 *
 * Q2PRO references:
 * - `q2pro/src/refresh/world.c` (`GL_DrawWorld`, `GL_WorldNode_r`),
 * - `q2pro/src/refresh/tess.c` (`GL_AddSolidFace`, `GL_DrawSolidFaces`, `GL_Flush3D`).
 */
class BspWorldBatchRenderer(
    private val worldRenderData: BspWorldRenderData,
    private val worldBatchData: BspWorldBatchData,
    private val lightmapAtlasPages: List<BspLightmapAtlasPageTextures>,
    private val assetManager: AssetManager,
) : Disposable {
    private val textureInfosByIndex = worldRenderData.textureInfos.associateBy { it.textureInfoIndex }
    private val textureAnimationChains = worldRenderData.textureInfos.associate { textureInfo ->
        textureInfo.textureInfoIndex to resolveTextureAnimationChain(textureInfo.textureInfoIndex, textureInfosByIndex)
    }
    private val texturesByTexInfoIndex = HashMap<Int, Texture>()

    private val chunkMeshes: List<Mesh>
    private val ownedSurfaceMask = BooleanArray(worldRenderData.surfaces.size)
    private val handledOpaqueSurfaceMask = BooleanArray(worldRenderData.surfaces.size)
    private val handledTranslucentSurfaceMask = BooleanArray(worldRenderData.surfaces.size)
    private val whiteTexture: Texture

    private val shaderProgram: ShaderProgram
    private val dynamicLightPosRadius = FloatArray(DynamicLightSystem.MAX_SHADER_DYNAMIC_LIGHTS * 4)
    private val dynamicLightColors = FloatArray(DynamicLightSystem.MAX_SHADER_DYNAMIC_LIGHTS * 4)

    private val uProjViewTrans: Int
    private val uDiffuseTexture: Int
    private val uLightmapTexture0: Int
    private val uLightmapTexture1: Int
    private val uLightmapTexture2: Int
    private val uLightmapTexture3: Int
    private val uDiffuseUvTransform: Int
    private val uLightStyleWeights: Int
    private val uOpacity: Int
    private val uGammaExponent: Int
    private val uIntensity: Int
    private val uOverbrightBits: Int
    private val uDynamicLightCount: Int
    private val uDynamicLightPosRadius: Int
    private val uDynamicLightColor: Int

    var lastOpaqueStats: BspWorldBatchRenderStats = BspWorldBatchRenderStats()
        private set
    var lastTranslucentStats: BspWorldBatchRenderStats = BspWorldBatchRenderStats()
        private set

    init {
        preloadDiffuseTextures()
        markHandledSurfaces()
        whiteTexture = createWhiteTexture()

        chunkMeshes = worldBatchData.chunks.map { chunk ->
            Mesh(
                false,
                chunk.vertices.size / 7,
                chunk.indices.size,
                VertexAttribute.Position(),
                VertexAttribute.TexCoords(0),
                VertexAttribute.TexCoords(1),
            ).apply {
                setVertices(chunk.vertices)
                setIndices(chunk.indices)
            }
        }

        shaderProgram = ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        if (!shaderProgram.isCompiled) {
            throw GdxRuntimeException("Failed to compile BSP world batch shader: ${shaderProgram.log}")
        }

        uProjViewTrans = shaderProgram.fetchUniformLocation("u_projViewTrans", false)
        uDiffuseTexture = shaderProgram.fetchUniformLocation("u_diffuseTexture", false)
        uLightmapTexture0 = shaderProgram.fetchUniformLocation("u_lightmapTexture0", false)
        uLightmapTexture1 = shaderProgram.fetchUniformLocation("u_lightmapTexture1", false)
        uLightmapTexture2 = shaderProgram.fetchUniformLocation("u_lightmapTexture2", false)
        uLightmapTexture3 = shaderProgram.fetchUniformLocation("u_lightmapTexture3", false)
        uDiffuseUvTransform = shaderProgram.fetchUniformLocation("u_diffuseUVTransform", false)
        uLightStyleWeights = shaderProgram.fetchUniformLocation("u_lightStyleWeights", false)
        uOpacity = shaderProgram.fetchUniformLocation("u_opacity", false)
        uGammaExponent = shaderProgram.fetchUniformLocation("u_gammaExponent", false)
        uIntensity = shaderProgram.fetchUniformLocation("u_intensity", false)
        uOverbrightBits = shaderProgram.fetchUniformLocation("u_overbrightbits", false)
        uDynamicLightCount = shaderProgram.fetchUniformLocation("u_dynamicLightCount", false)
        uDynamicLightPosRadius = shaderProgram.fetchUniformLocation("u_dynamicLightPosRadius", false)
        uDynamicLightColor = shaderProgram.fetchUniformLocation("u_dynamicLightColor", false)
    }

    fun suppressedSurfacesMask(): BooleanArray =
        ownedSurfaceMask.copyOf()

    fun render(
        camera: Camera,
        visibleSurfaceMask: BooleanArray,
        currentTimeMs: Int,
        lightStyleResolver: (Int) -> Float,
        dynamicLights: List<SceneDynamicLight>,
    ) {
        if (worldBatchData.surfaces.isEmpty()) {
            lastOpaqueStats = BspWorldBatchRenderStats()
            return
        }

        val drawGroups = LinkedHashMap<FrameBatchKey, MutableMap<Int, MutableList<BspWorldBatchSurface>>>()
        var visibleSurfaceCount = 0
        worldBatchData.surfaces.forEach { surface ->
            if (surface.worldSurfaceIndex !in visibleSurfaceMask.indices || !visibleSurfaceMask[surface.worldSurfaceIndex]) {
                return@forEach
            }
            if (surface.worldSurfaceIndex !in handledOpaqueSurfaceMask.indices || !handledOpaqueSurfaceMask[surface.worldSurfaceIndex]) {
                return@forEach
            }
            visibleSurfaceCount++
            val worldSurface = worldRenderData.surfaces[surface.worldSurfaceIndex]
            val activeTextureInfoIndex = resolveActiveTextureInfoIndex(surface.batchKey.textureInfoIndex, currentTimeMs)
            val key = FrameBatchKey(
                activeTextureInfoIndex = activeTextureInfoIndex,
                lightmapPageIndex = surface.batchKey.lightmapPageIndex,
                textureFlags = surface.batchKey.textureFlags,
                surfacePass = surface.batchKey.surfacePass,
                lightStyleSignature = packLightStyleSignature(worldSurface),
            )
            val byChunk = drawGroups.getOrPut(key) { LinkedHashMap() }
            byChunk.getOrPut(surface.chunkIndex) { ArrayList() } += surface
        }

        if (drawGroups.isEmpty()) {
            lastOpaqueStats = BspWorldBatchRenderStats(visibleSurfaceCount = visibleSurfaceCount)
            return
        }

        var drawCallCount = 0
        var groupedSurfaceCount = 0
        configureOpaquePipeline()
        try {
            shaderProgram.bind()
            shaderProgram.setUniformMatrix(uProjViewTrans, camera.combined)
            shaderProgram.setUniformf(uGammaExponent, RenderTuningCvars.gammaExponent())
            shaderProgram.setUniformf(uIntensity, RenderTuningCvars.intensity())
            shaderProgram.setUniformf(uOverbrightBits, RenderTuningCvars.overbrightBits())
            uploadDynamicLights(dynamicLights)

            drawGroups.forEach { (key, chunkGroups) ->
                val diffuseTexture = texturesByTexInfoIndex[key.activeTextureInfoIndex] ?: return@forEach
                if (key.surfacePass == BspWorldSurfacePass.SKY || key.surfacePass == BspWorldSurfacePass.TRANSLUCENT) {
                    return@forEach
                }

                val useAtlasLightmap = key.surfacePass == BspWorldSurfacePass.OPAQUE_LIGHTMAPPED
                val atlasPage = if (useAtlasLightmap) lightmapAtlasPages.getOrNull(key.lightmapPageIndex) else null
                if (useAtlasLightmap && (atlasPage == null || atlasPage.textures.size < BATCH_LIGHT_STYLE_SLOTS)) {
                    return@forEach
                }

                diffuseTexture.bind(0)
                if (atlasPage != null) {
                    atlasPage.textures[0].bind(1)
                    atlasPage.textures[1].bind(2)
                    atlasPage.textures[2].bind(3)
                    atlasPage.textures[3].bind(4)
                } else {
                    whiteTexture.bind(1)
                    whiteTexture.bind(2)
                    whiteTexture.bind(3)
                    whiteTexture.bind(4)
                }
                shaderProgram.setUniformi(uDiffuseTexture, 0)
                shaderProgram.setUniformi(uLightmapTexture0, 1)
                shaderProgram.setUniformi(uLightmapTexture1, 2)
                shaderProgram.setUniformi(uLightmapTexture2, 3)
                shaderProgram.setUniformi(uLightmapTexture3, 4)

                val (offsetU, offsetV, scaleU, scaleV) = when {
                    key.surfacePass == BspWorldSurfacePass.WARP -> computeWarpUvTransform(currentTimeMs)
                    (key.textureFlags and Defines.SURF_FLOWING) != 0 -> floatArrayOf(computeFlowingOffsetU(currentTimeMs), 0f, 1f, 1f)
                    else -> floatArrayOf(0f, 0f, 1f, 1f)
                }
                shaderProgram.setUniformf(uDiffuseUvTransform, offsetU, offsetV, scaleU, scaleV)

                val styleWeights = if (useAtlasLightmap) {
                    val referenceSurface = chunkGroups.values.firstOrNull()?.firstOrNull()?.let { groupedSurface ->
                        worldRenderData.surfaces[groupedSurface.worldSurfaceIndex]
                    }
                    if (referenceSurface != null) {
                        computeLightmapStyleWeights(
                            lightMapStyles = referenceSurface.lightMapStyles,
                            primaryLightStyleIndex = referenceSurface.primaryLightStyleIndex,
                            lightStyleResolver = lightStyleResolver,
                        )
                    } else {
                        floatArrayOf(1f, 0f, 0f, 0f)
                    }
                } else {
                    floatArrayOf(1f, 0f, 0f, 0f)
                }
                shaderProgram.setUniformf(uLightStyleWeights, styleWeights[0], styleWeights[1], styleWeights[2], styleWeights[3])
                shaderProgram.setUniformf(uOpacity, 1f)

                for ((chunkIndex, surfaces) in chunkGroups) {
                    val chunk = worldBatchData.chunks.getOrNull(chunkIndex) ?: continue
                    val mesh = chunkMeshes.getOrNull(chunkIndex) ?: continue
                    val indexCount = surfaces.sumOf { it.indexCount }
                    if (indexCount <= 0) {
                        continue
                    }
                    groupedSurfaceCount += surfaces.size
                    val indices = ShortArray(indexCount)
                    var cursor = 0
                    surfaces.forEach { groupedSurface ->
                        System.arraycopy(chunk.indices, groupedSurface.indexOffset, indices, cursor, groupedSurface.indexCount)
                        cursor += groupedSurface.indexCount
                    }
                    mesh.setIndices(indices, 0, indices.size)
                    mesh.render(shaderProgram, GL20.GL_TRIANGLES, 0, indices.size)
                    drawCallCount++
                }
            }
        } finally {
            restoreDefaultPipelineState()
        }
        lastOpaqueStats = BspWorldBatchRenderStats(
            visibleSurfaceCount = visibleSurfaceCount,
            groupedSurfaceCount = groupedSurfaceCount,
            drawCalls = drawCallCount,
        )
    }

    /**
     * Draws batched translucent world surfaces (`SURF_TRANS33` / `SURF_TRANS66`) in a dedicated pass.
     */
    fun renderTranslucent(
        camera: Camera,
        visibleSurfaceMask: BooleanArray,
        currentTimeMs: Int,
    ) {
        if (worldBatchData.surfaces.isEmpty()) {
            lastTranslucentStats = BspWorldBatchRenderStats()
            return
        }

        val drawGroups = LinkedHashMap<FrameTranslucentBatchKey, MutableMap<Int, MutableList<BspWorldBatchSurface>>>()
        var visibleSurfaceCount = 0
        worldBatchData.surfaces.forEach { surface ->
            if (surface.worldSurfaceIndex !in visibleSurfaceMask.indices || !visibleSurfaceMask[surface.worldSurfaceIndex]) {
                return@forEach
            }
            if (
                surface.worldSurfaceIndex !in handledTranslucentSurfaceMask.indices ||
                !handledTranslucentSurfaceMask[surface.worldSurfaceIndex]
            ) {
                return@forEach
            }
            if (surface.batchKey.surfacePass != BspWorldSurfacePass.TRANSLUCENT) {
                return@forEach
            }
            visibleSurfaceCount++
            val key = FrameTranslucentBatchKey(
                activeTextureInfoIndex = resolveActiveTextureInfoIndex(surface.batchKey.textureInfoIndex, currentTimeMs),
                textureFlags = surface.batchKey.textureFlags,
            )
            val byChunk = drawGroups.getOrPut(key) { LinkedHashMap() }
            byChunk.getOrPut(surface.chunkIndex) { ArrayList() } += surface
        }

        if (drawGroups.isEmpty()) {
            lastTranslucentStats = BspWorldBatchRenderStats(visibleSurfaceCount = visibleSurfaceCount)
            return
        }

        var drawCallCount = 0
        var groupedSurfaceCount = 0
        configureTranslucentPipeline()
        try {
            shaderProgram.bind()
            shaderProgram.setUniformMatrix(uProjViewTrans, camera.combined)
            shaderProgram.setUniformf(uGammaExponent, RenderTuningCvars.gammaExponent())
            shaderProgram.setUniformf(uIntensity, RenderTuningCvars.intensity())
            shaderProgram.setUniformf(uOverbrightBits, 1f)
            shaderProgram.setUniformi(uDynamicLightCount, 0)
            shaderProgram.setUniformf(uLightStyleWeights, 1f, 0f, 0f, 0f)

            drawGroups.forEach { (key, chunkGroups) ->
                val trans33 = (key.textureFlags and Defines.SURF_TRANS33) != 0
                val trans66 = (key.textureFlags and Defines.SURF_TRANS66) != 0
                if (!trans33 && !trans66) {
                    return@forEach
                }

                val diffuseTexture = texturesByTexInfoIndex[key.activeTextureInfoIndex] ?: return@forEach
                diffuseTexture.bind(0)
                whiteTexture.bind(1)
                whiteTexture.bind(2)
                whiteTexture.bind(3)
                whiteTexture.bind(4)
                shaderProgram.setUniformi(uDiffuseTexture, 0)
                shaderProgram.setUniformi(uLightmapTexture0, 1)
                shaderProgram.setUniformi(uLightmapTexture1, 2)
                shaderProgram.setUniformi(uLightmapTexture2, 3)
                shaderProgram.setUniformi(uLightmapTexture3, 4)

                val flowingOffset = if ((key.textureFlags and Defines.SURF_FLOWING) != 0) {
                    computeFlowingOffsetU(currentTimeMs)
                } else {
                    0f
                }
                shaderProgram.setUniformf(uDiffuseUvTransform, flowingOffset, 0f, 1f, 1f)
                shaderProgram.setUniformf(uOpacity, if (trans33) 0.33f else 0.66f)

                for ((chunkIndex, surfaces) in chunkGroups) {
                    val chunk = worldBatchData.chunks.getOrNull(chunkIndex) ?: continue
                    val mesh = chunkMeshes.getOrNull(chunkIndex) ?: continue
                    val indexCount = surfaces.sumOf { it.indexCount }
                    if (indexCount <= 0) {
                        continue
                    }
                    groupedSurfaceCount += surfaces.size
                    val indices = ShortArray(indexCount)
                    var cursor = 0
                    surfaces.forEach { groupedSurface ->
                        System.arraycopy(chunk.indices, groupedSurface.indexOffset, indices, cursor, groupedSurface.indexCount)
                        cursor += groupedSurface.indexCount
                    }
                    mesh.setIndices(indices, 0, indices.size)
                    mesh.render(shaderProgram, GL20.GL_TRIANGLES, 0, indices.size)
                    drawCallCount++
                }
            }
        } finally {
            restoreDefaultPipelineState()
        }
        lastOpaqueStats = BspWorldBatchRenderStats(
            visibleSurfaceCount = visibleSurfaceCount,
            groupedSurfaceCount = groupedSurfaceCount,
            drawCalls = drawCallCount,
        )
    }

    override fun dispose() {
        chunkMeshes.forEach { it.dispose() }
        whiteTexture.dispose()
        shaderProgram.dispose()
    }

    private fun preloadDiffuseTextures() {
        textureInfosByIndex.forEach { (textureInfoIndex, textureInfo) ->
            val path = "textures/${textureInfo.textureName.trim()}.wal"
            if (assetManager.isLoaded(path, Texture::class.java)) {
                texturesByTexInfoIndex[textureInfoIndex] = assetManager.get(path, Texture::class.java)
            }
        }
    }

    private fun markHandledSurfaces() {
        worldBatchData.surfaces.forEach { surface ->
            val worldSurfaceIndex = surface.worldSurfaceIndex
            if (worldSurfaceIndex !in ownedSurfaceMask.indices) {
                return@forEach
            }
            when (surface.batchKey.surfacePass) {
                BspWorldSurfacePass.OPAQUE_LIGHTMAPPED,
                BspWorldSurfacePass.OPAQUE_UNLIT,
                BspWorldSurfacePass.WARP -> {
                    ownedSurfaceMask[worldSurfaceIndex] = true
                    handledOpaqueSurfaceMask[worldSurfaceIndex] = true
                }
                BspWorldSurfacePass.TRANSLUCENT -> {
                    ownedSurfaceMask[worldSurfaceIndex] = true
                    handledTranslucentSurfaceMask[worldSurfaceIndex] = true
                }
                BspWorldSurfacePass.SKY -> {
                    ownedSurfaceMask[worldSurfaceIndex] = true
                }
            }
        }
    }

    private fun configureOpaquePipeline() {
        val gl = Gdx.gl
        gl.glEnable(GL20.GL_DEPTH_TEST)
        gl.glDepthMask(true)
        gl.glDisable(GL20.GL_BLEND)
        gl.glDisable(GL20.GL_CULL_FACE)
    }

    private fun configureTranslucentPipeline() {
        val gl = Gdx.gl
        gl.glEnable(GL20.GL_DEPTH_TEST)
        gl.glDepthMask(false)
        gl.glEnable(GL20.GL_BLEND)
        gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        gl.glDisable(GL20.GL_CULL_FACE)
    }

    /**
     * Resets state mutated by custom world-batch passes so subsequent render paths
     * (ModelBatch, particle/sprite systems) start from predictable defaults.
     */
    private fun restoreDefaultPipelineState() {
        val gl = Gdx.gl
        gl.glDepthMask(true)
        gl.glDisable(GL20.GL_BLEND)
        gl.glActiveTexture(GL20.GL_TEXTURE0)
    }

    private fun uploadDynamicLights(lights: List<SceneDynamicLight>) {
        val count = minOf(lights.size, DynamicLightSystem.MAX_SHADER_DYNAMIC_LIGHTS)
        shaderProgram.setUniformi(uDynamicLightCount, count)
        if (count <= 0) {
            return
        }
        repeat(count) { index ->
            val light = lights[index]
            val base = index * 4
            dynamicLightPosRadius[base] = light.origin.x
            dynamicLightPosRadius[base + 1] = light.origin.y
            dynamicLightPosRadius[base + 2] = light.origin.z
            dynamicLightPosRadius[base + 3] = light.radius
            dynamicLightColors[base] = light.red
            dynamicLightColors[base + 1] = light.green
            dynamicLightColors[base + 2] = light.blue
            dynamicLightColors[base + 3] = 1f
        }
        shaderProgram.setUniform4fv(uDynamicLightPosRadius, dynamicLightPosRadius, 0, count * 4)
        shaderProgram.setUniform4fv(uDynamicLightColor, dynamicLightColors, 0, count * 4)
    }

    private fun resolveActiveTextureInfoIndex(textureInfoIndex: Int, currentTimeMs: Int): Int {
        val chain = textureAnimationChains[textureInfoIndex] ?: return textureInfoIndex
        return selectTextureAnimationTexInfo(chain, currentTimeMs) ?: textureInfoIndex
    }

    private fun packLightStyleSignature(surface: org.demoth.cake.assets.BspWorldSurfaceRecord): Long {
        var signature = (surface.primaryLightStyleIndex ?: 255).toLong() and 0xFF
        repeat(4) { slot ->
            val style = surface.lightMapStyles.getOrNull(slot)?.toInt()?.and(0xFF) ?: 255
            signature = signature or (style.toLong() shl ((slot + 1) * 8))
        }
        return signature
    }

    private fun createWhiteTexture(): Texture {
        val pixmap = com.badlogic.gdx.graphics.Pixmap(1, 1, com.badlogic.gdx.graphics.Pixmap.Format.RGB888)
        pixmap.setColor(1f, 1f, 1f, 1f)
        pixmap.fill()
        return Texture(pixmap).also { pixmap.dispose() }
    }

    private fun computeWarpUvTransform(currentTimeMs: Int): FloatArray {
        val timeSeconds = currentTimeMs / 1000f
        val offsetU = sin(timeSeconds * 1.3f) * 0.02f
        val offsetV = cos(timeSeconds * 0.9f) * 0.02f
        return floatArrayOf(offsetU, offsetV, 1f, 1f)
    }

    companion object {
        private const val VERTEX_SHADER = """
attribute vec3 a_position;
attribute vec2 a_texCoord0;
attribute vec2 a_texCoord1;

uniform mat4 u_projViewTrans;
uniform vec4 u_diffuseUVTransform;

varying vec2 v_diffuseUv;
varying vec2 v_lightmapUv;
varying vec3 v_worldPos;

void main() {
    vec4 worldPos = vec4(a_position, 1.0);
    v_diffuseUv = a_texCoord0 * u_diffuseUVTransform.zw + u_diffuseUVTransform.xy;
    v_lightmapUv = a_texCoord1;
    v_worldPos = worldPos.xyz;
    gl_Position = u_projViewTrans * worldPos;
}
"""

        private const val FRAGMENT_SHADER = """
#ifdef GL_ES
precision mediump float;
#endif

uniform sampler2D u_diffuseTexture;
uniform sampler2D u_lightmapTexture0;
uniform sampler2D u_lightmapTexture1;
uniform sampler2D u_lightmapTexture2;
uniform sampler2D u_lightmapTexture3;
uniform vec4 u_lightStyleWeights;
uniform float u_opacity;
uniform float u_gammaExponent;
uniform float u_intensity;
uniform float u_overbrightbits;
uniform int u_dynamicLightCount;
uniform vec4 u_dynamicLightPosRadius[8];
uniform vec4 u_dynamicLightColor[8];

varying vec2 v_diffuseUv;
varying vec2 v_lightmapUv;
varying vec3 v_worldPos;

vec3 accumulateDynamicLights(vec3 worldPos) {
    vec3 sum = vec3(0.0);
    for (int i = 0; i < 8; ++i) {
        if (i >= u_dynamicLightCount) {
            break;
        }
        vec3 lightPos = u_dynamicLightPosRadius[i].xyz;
        float radius = max(u_dynamicLightPosRadius[i].w, 0.001);
        float distanceToLight = distance(lightPos, worldPos);
        if (distanceToLight >= radius) {
            continue;
        }
        float attenuation = 1.0 - (distanceToLight / radius);
        sum += u_dynamicLightColor[i].rgb * attenuation;
    }
    return sum;
}

void main() {
    vec4 albedo = texture2D(u_diffuseTexture, v_diffuseUv);
    vec3 light0 = texture2D(u_lightmapTexture0, v_lightmapUv).rgb * u_lightStyleWeights.x;
    vec3 light1 = texture2D(u_lightmapTexture1, v_lightmapUv).rgb * u_lightStyleWeights.y;
    vec3 light2 = texture2D(u_lightmapTexture2, v_lightmapUv).rgb * u_lightStyleWeights.z;
    vec3 light3 = texture2D(u_lightmapTexture3, v_lightmapUv).rgb * u_lightStyleWeights.w;
    vec3 light = (light0 + light1 + light2 + light3) * u_overbrightbits;
    light += accumulateDynamicLights(v_worldPos);
    if (max(light.r, max(light.g, light.b)) < 0.001) {
        light = vec3(1.0);
    }
    vec3 lit = albedo.rgb * light;
    lit *= u_intensity;
    lit = pow(max(lit, vec3(0.0)), vec3(u_gammaExponent));
    gl_FragColor = vec4(lit, albedo.a * u_opacity);
}
"""
    }
}
