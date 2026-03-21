package org.demoth.cake.stages.ingame

import jake2.qcommon.Defines
import jake2.qcommon.exec.Cvar

/**
 * Shared rendering tuning cvars for Cake's modern shader path.
 *
 * Legacy/Yamagi references:
 * - `vid_gamma`
 * - `gl3_intensity`
 * - `gl3_overbrightbits`
 *
 * These cvars are intentionally centralized so BSP/MD2/particle paths use the same
 * brightness controls and behavior toggles.
 */
object RenderTuningCvars {
    private val cvars = Cvar.getInstance()

    private val vidGamma = cvars.Get("vid_gamma", "1.2", Defines.CVAR_ARCHIVE or Defines.CVAR_OPTIONS, "Display gamma")
    private val gl3Intensity = cvars.Get("gl3_intensity", "1.5", Defines.CVAR_ARCHIVE, "Lightmap intensity scale")
    private val gl3OverbrightBits = cvars.Get("gl3_overbrightbits", "1.3", Defines.CVAR_ARCHIVE, "Overbright intensity scale")
    private val rDlights = cvars.Get(
        "r_dlights",
        "1",
        Defines.CVAR_ARCHIVE or Defines.CVAR_OPTIONS,
        "Dynamic lights",
    )
    private val rParticles = cvars.Get(
        "r_particles",
        "${Defines.MAX_PARTICLES}",
        Defines.CVAR_ARCHIVE or Defines.CVAR_OPTIONS,
        "Particle budget",
    )
    private val rBspBatchDebug = cvars.Get("r_bsp_batch_debug", "0", Defines.CVAR_ARCHIVE)
    private val rPostVignette = cvars.Get(
        "r_post_vignette",
        "1",
        Defines.CVAR_ARCHIVE or Defines.CVAR_OPTIONS,
        "Postprocess vignette",
    )
    private val rPostVignetteStrength = cvars.Get(
        "r_post_vignette_strength",
        "1.30",
        Defines.CVAR_ARCHIVE or Defines.CVAR_OPTIONS,
        "Vignette strength",
    )
    private val rUnderwaterWarp = cvars.Get(
        "r_underwater_warp",
        "1",
        Defines.CVAR_ARCHIVE or Defines.CVAR_OPTIONS,
        "Underwater warp",
    )

    fun register() = Unit

    /**
     * Shader exponent used in `pow(color, gammaExponent)`.
     *
     * Legacy GL3 stores this as `1.0 / vid_gamma`.
     */
    fun gammaExponent(): Float {
        val gamma = vidGamma.value.coerceAtLeast(0.01f)
        return 1f / gamma
    }

    fun intensity(): Float = gl3Intensity.value.coerceAtLeast(0f)

    fun overbrightBits(): Float {
        val overbright = gl3OverbrightBits.value
        return if (overbright <= 0f) 1f else overbright
    }

    fun dynamicLightsEnabled(): Boolean = rDlights.value != 0f

    /**
     * Global particle budget (`r_particles`).
     *
     * Legacy clients expose an on/off particle cvar; Cake uses the same key as a budget cap:
     * - `0` disables particles,
     * - positive values cap live particle count,
     * - values above `MAX_PARTICLES` are clamped to `MAX_PARTICLES`.
     */
    fun particleBudget(): Int {
        return rParticles.value.toInt()
    }

    fun particlesEnabled(): Boolean = particleBudget() > 0

    /**
     * Enables periodic world/entity pass diagnostics for BSP batching migration.
     */
    fun bspBatchDebugEnabled(): Boolean = rBspBatchDebug.value != 0f

    /**
     * Enables postprocess vignette-style blending from `player_state_t.blend`.
     */
    fun postVignetteEnabled(): Boolean = rPostVignette.value != 0f

    /**
     * Scales vignette-style blend intensity (`player_state_t.blend`) in postprocess.
     */
    fun postVignetteStrength(): Float = rPostVignetteStrength.value.coerceAtLeast(0f)

    /**
     * Enables postprocess underwater screen warp when `RDF_UNDERWATER` is active.
     */
    fun underwaterWarpEnabled(): Boolean = rUnderwaterWarp.value != 0f
}
