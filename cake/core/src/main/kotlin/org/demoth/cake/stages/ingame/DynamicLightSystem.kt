package org.demoth.cake.stages.ingame

import com.badlogic.gdx.math.Vector3
import jake2.qcommon.Defines

/**
 * Runtime store for idTech2-style dynamic lights (`dlight_t`/`cdlight_t`).
 *
 * Responsibilities:
 * - tracks transient keyed lights (muzzle flashes, TE explosions),
 * - applies legacy-like expiry/decay behavior,
 * - accepts per-frame additive lights (entity `EF_*` emitters),
 * - provides a capped list for shader upload.
 *
 * Legacy references:
 * - `client/cl_lights.c` (`CL_AllocDlight`, `CL_RunDLights`, `CL_AddDLights`)
 * - `client/CL_ents.AddPacketEntities` (`V.AddLight` from `EF_*` flags)
 */
class DynamicLightSystem(
    private val maxTrackedLights: Int = Defines.MAX_DLIGHTS,
    private val maxShaderLights: Int = MAX_SHADER_DYNAMIC_LIGHTS,
) {
    private val tracked = Array(maxTrackedLights) { MutableDynamicLight() }
    private val frameLights = ArrayList<SceneDynamicLight>(maxTrackedLights + 8)
    private val uploadScratch = ArrayList<SceneDynamicLight>(maxShaderLights)
    private var nowMs: Int = 0

    /**
     * Starts a new frame and advances persistent light decay.
     */
    fun beginFrame(currentTimeMs: Int, deltaSeconds: Float) {
        nowMs = currentTimeMs
        frameLights.clear()
        uploadScratch.clear()
        if (!RenderTuningCvars.dynamicLightsEnabled()) {
            return
        }

        tracked.forEach { light ->
            if (!light.active) {
                return@forEach
            }
            // Legacy/Yamagi quirk: keep dlights alive for at least ~32 ms to avoid disappearing on very high FPS.
            if (light.dieTimeMs < currentTimeMs - MIN_VISIBILITY_EXTENSION_MS) {
                light.clear()
                return@forEach
            }
            if (light.decayPerSecond > 0f) {
                light.radius = (light.radius - deltaSeconds * light.decayPerSecond).coerceAtLeast(0f)
                if (light.radius <= 0f) {
                    light.clear()
                    return@forEach
                }
            }
            frameLights += light.snapshot()
        }
    }

    /**
     * Adds an immediate one-frame light (legacy `V.AddLight` equivalent).
     */
    fun addFrameLight(
        origin: Vector3,
        radius: Float,
        red: Float,
        green: Float,
        blue: Float,
    ) {
        if (!RenderTuningCvars.dynamicLightsEnabled()) {
            return
        }
        if (radius <= 0f) {
            return
        }
        frameLights += SceneDynamicLight(
            origin = Vector3(origin),
            radius = radius,
            red = red,
            green = green,
            blue = blue,
        )
    }

    /**
     * Spawns or reuses a keyed transient dynamic light.
     *
     * @param key Legacy key semantics: `0` means unkeyed; non-zero updates the same slot when possible.
     * @param lifetimeMs Lifetime in milliseconds; `0` is a short flash that still survives at least one frame.
     */
    fun spawnTransientLight(
        key: Int,
        origin: Vector3,
        radius: Float,
        red: Float,
        green: Float,
        blue: Float,
        lifetimeMs: Int = 0,
        decayPerSecond: Float = 0f,
        currentTimeMs: Int = nowMs,
    ) {
        if (!RenderTuningCvars.dynamicLightsEnabled()) {
            return
        }
        if (radius <= 0f) {
            return
        }

        nowMs = currentTimeMs
        val light = allocateTrackedLight(key)
        light.key = key
        light.origin.set(origin)
        light.radius = radius
        light.red = red
        light.green = green
        light.blue = blue
        light.decayPerSecond = decayPerSecond.coerceAtLeast(0f)
        light.dieTimeMs = currentTimeMs + lifetimeMs
        light.active = true
    }

    /**
     * Returns dynamic lights for shader upload (sorted by radius, capped to budget).
     */
    fun visibleLightsForShader(): List<SceneDynamicLight> {
        if (!RenderTuningCvars.dynamicLightsEnabled() || frameLights.isEmpty()) {
            return emptyList()
        }
        uploadScratch.clear()
        frameLights
            .asSequence()
            .sortedByDescending { it.radius }
            .take(maxShaderLights)
            .forEach(uploadScratch::add)
        return uploadScratch
    }

    /**
     * Computes dynamic-light contribution at one world position for model/entity shading.
     */
    fun sampleAt(point: Vector3): Vector3 {
        if (!RenderTuningCvars.dynamicLightsEnabled() || frameLights.isEmpty()) {
            return Vector3()
        }
        var red = 0f
        var green = 0f
        var blue = 0f
        frameLights.forEach { light ->
            val distance = point.dst(light.origin)
            if (distance >= light.radius) {
                return@forEach
            }
            val attenuation = (1f - distance / light.radius).coerceAtLeast(0f)
            red += light.red * attenuation
            green += light.green * attenuation
            blue += light.blue * attenuation
        }
        return Vector3(red, green, blue)
    }

    private fun allocateTrackedLight(key: Int): MutableDynamicLight {
        if (key != 0) {
            tracked.firstOrNull { it.active && it.key == key }?.let { return it }
        }
        tracked.firstOrNull { !it.active || it.dieTimeMs < nowMs }?.let { return it }
        return tracked[0]
    }

    private class MutableDynamicLight {
        var key: Int = 0
        val origin: Vector3 = Vector3()
        var radius: Float = 0f
        var red: Float = 1f
        var green: Float = 1f
        var blue: Float = 1f
        var dieTimeMs: Int = 0
        var decayPerSecond: Float = 0f
        var active: Boolean = false

        fun clear() {
            key = 0
            radius = 0f
            red = 1f
            green = 1f
            blue = 1f
            dieTimeMs = 0
            decayPerSecond = 0f
            active = false
        }

        fun snapshot(): SceneDynamicLight =
            SceneDynamicLight(
                origin = Vector3(origin),
                radius = radius,
                red = red,
                green = green,
                blue = blue,
            )
    }

    companion object {
        // Keep this in sync with BSP/MD2 shader uniform array sizes.
        const val MAX_SHADER_DYNAMIC_LIGHTS = 8
        private const val MIN_VISIBILITY_EXTENSION_MS = 32
    }
}

data class SceneDynamicLight(
    val origin: Vector3,
    val radius: Float,
    val red: Float,
    val green: Float,
    val blue: Float,
)
