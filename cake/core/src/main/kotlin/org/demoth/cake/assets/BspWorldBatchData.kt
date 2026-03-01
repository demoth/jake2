package org.demoth.cake.assets

/**
 * Precomputed world BSP geometry + draw-command metadata for batched rendering.
 *
 * Design target mirrors Q2PRO world batching flow:
 * - `q2pro/src/refresh/world.c` (`GL_DrawWorld`, `GL_WorldNode_r`),
 * - `q2pro/src/refresh/tess.c` (`GL_AddSolidFace`, `GL_DrawSolidFaces`, `GL_Flush3D`).
 */
data class BspWorldBatchData(
    val chunks: List<BspWorldBatchChunk>,
    val surfaces: List<BspWorldBatchSurface>,
)

/**
 * Explicit world-surface pass classification derived from BSP `SURF_*` flags.
 */
enum class BspWorldSurfacePass {
    OPAQUE_LIGHTMAPPED,
    OPAQUE_UNLIT,
    TRANSLUCENT,
    WARP,
    SKY,
}

/**
 * One chunk of world geometry constrained to <= 65535 vertices for 16-bit index buffers.
 */
data class BspWorldBatchChunk(
    /** Interleaved vertices: position.xyz, diffuseUv.xy, lightmapUv.xy (7 floats per vertex). */
    val vertices: FloatArray,
    /** Triangle index buffer for this chunk. */
    val indices: ShortArray,
)

/**
 * One surface draw range in a [BspWorldBatchChunk].
 */
data class BspWorldBatchSurface(
    /** Index into [BspWorldRenderData.surfaces]. */
    val worldSurfaceIndex: Int,
    val faceIndex: Int,
    val chunkIndex: Int,
    val indexOffset: Int,
    val indexCount: Int,
    val batchKey: BspWorldBatchKey,
)

/**
 * Q2PRO-style state/material grouping key used for batching compatible surfaces.
 */
data class BspWorldBatchKey(
    /** BSP texinfo index used for diffuse texture animation chain resolution. */
    val textureInfoIndex: Int,
    /** Texture flags (`SURF_*`) used to split passes/state groups. */
    val textureFlags: Int,
    /** Packed lightmap atlas page index; `-1` for non-lightmapped surfaces. */
    val lightmapPageIndex: Int,
    /** Explicit pass classification used by the world renderer. */
    val surfacePass: BspWorldSurfacePass,
)
