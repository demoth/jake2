package org.demoth.cake.stages.ingame

import jake2.qcommon.Defines
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.CvarTypes

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

    private val vidGamma = cvars.register(
        "vid_gamma", CvarTypes.FloatType(min = 0.01f), 1.2f,
        Defines.CVAR_ARCHIVE or Defines.CVAR_OPTIONS, "Display gamma",
    )
    private val gl3Intensity = cvars.register("gl3_intensity", CvarTypes.FloatType(min = 0f), 1.5f, Defines.CVAR_ARCHIVE, "Lightmap intensity scale")
    private val gl3OverbrightBits = cvars.register("gl3_overbrightbits", CvarTypes.FloatType(min = Float.MIN_VALUE), 1.3f, Defines.CVAR_ARCHIVE, "Overbright intensity scale")
    private val rDlights = cvars.register("r_dlights", CvarTypes.BOOLEAN, true, Defines.CVAR_ARCHIVE or Defines.CVAR_OPTIONS, "Dynamic lights")
    private val rParticles = cvars.register("r_particles", CvarTypes.IntegerType(min = 0, max = Defines.MAX_PARTICLES), Defines.MAX_PARTICLES, Defines.CVAR_ARCHIVE or Defines.CVAR_OPTIONS, "Particle budget")
    private val rBspBatchDebug = cvars.register("r_bsp_batch_debug", CvarTypes.BOOLEAN, false, Defines.CVAR_ARCHIVE, "Log BSP batch renderer stats")
    val rPostVignette = cvars.register("r_post_vignette", CvarTypes.BOOLEAN, true, Defines.CVAR_ARCHIVE or Defines.CVAR_OPTIONS, "Postprocess vignette")
    val rPostVignetteStrength = cvars.register("r_post_vignette_strength", CvarTypes.FloatType(min = 0f), 1.30f, Defines.CVAR_ARCHIVE or Defines.CVAR_OPTIONS, "Vignette strength")
    private val rUnderwaterWarp = cvars.register("r_underwater_warp", CvarTypes.BOOLEAN, true, Defines.CVAR_ARCHIVE or Defines.CVAR_OPTIONS, "Underwater warp")

    /** Triggers object initialization to register shared rendering cvars during startup. */
    fun register() = Unit

    /**
     * Shader exponent used in `pow(color, gammaExponent)`.
     *
     * Legacy GL3 stores this as `1.0 / vid_gamma`.
     */
    fun gammaExponent(): Float {
        return 1f / vidGamma.typedValue
    }

    fun intensity(): Float = gl3Intensity.typedValue

    fun overbrightBits(): Float = gl3OverbrightBits.typedValue

    fun dynamicLightsEnabled(): Boolean = rDlights.typedValue

    /**
     * Global particle budget (`r_particles`).
     *
     * Legacy clients expose an on/off particle cvar; Cake uses the same key as a budget cap:
     * - `0` disables particles,
     * - positive values cap live particle count,
     * - values outside `0..MAX_PARTICLES` are rejected when written.
     */
    fun particleBudget(): Int {
        return rParticles.typedValue
    }

    fun particlesEnabled(): Boolean = particleBudget() > 0

    /**
     * Enables periodic world/entity pass diagnostics for BSP batching migration.
     */
    fun bspBatchDebugEnabled(): Boolean = rBspBatchDebug.typedValue

    /**
     * Enables postprocess underwater screen warp when `RDF_UNDERWATER` is active.
     */
    fun underwaterWarpEnabled(): Boolean = rUnderwaterWarp.typedValue
}
