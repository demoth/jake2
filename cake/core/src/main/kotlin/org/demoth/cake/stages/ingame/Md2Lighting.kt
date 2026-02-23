package org.demoth.cake.stages.ingame

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val SHADEDOT_QUANT = 16

internal data class Md2ShadeVector(
    val x: Float,
    val y: Float,
    val z: Float,
)

/**
 * Computes MD2 alias shade vector from entity yaw.
 *
 * Legacy counterpart:
 * - `client/render/fast/Mesh.R_DrawAliasModel`
 * - `../yquake2/src/client/refresh/gl3/gl3_mesh.c`
 *
 * Legacy quantized mode mirrors `shadedots` yaw bucketing:
 * `((int)(yaw * (16 / 360.0))) & 15`.
 */
internal fun computeMd2ShadeVector(yawDegrees: Float, legacyQuantized: Boolean): Md2ShadeVector {
    val shadeYaw = if (legacyQuantized) {
        quantizeLegacyShadedotYawDegrees(yawDegrees)
    } else {
        yawDegrees
    }

    val angleRad = Math.toRadians(shadeYaw.toDouble()).toFloat()
    val shadeX = cos(-angleRad)
    val shadeY = sin(-angleRad)
    val shadeZ = 1f
    val length = sqrt(shadeX * shadeX + shadeY * shadeY + shadeZ * shadeZ).coerceAtLeast(0.0001f)
    return Md2ShadeVector(
        x = shadeX / length,
        y = shadeY / length,
        z = shadeZ / length,
    )
}

/**
 * Quantizes yaw to legacy shadedot buckets and returns the representative bucket angle in degrees.
 */
internal fun quantizeLegacyShadedotYawDegrees(yawDegrees: Float): Float {
    val bucket = ((yawDegrees * (SHADEDOT_QUANT / 360f)).toInt()) and (SHADEDOT_QUANT - 1)
    return bucket * (360f / SHADEDOT_QUANT)
}

