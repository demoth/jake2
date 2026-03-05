package org.demoth.cake.stages.ingame

import com.badlogic.gdx.math.Vector3
import jake2.qcommon.Defines
import jake2.qcommon.Globals
import kotlin.math.sin
import org.demoth.cake.ClientEntity
import org.demoth.cake.stages.ingame.effects.ClientEffectsSystem

/**
 * Collects replicated `EF_*` side effects from current packet entities.
 *
 * Legacy references:
 * - `client/CL_ents.AddPacketEntities` dynamic-light branches (`V.AddLight`)
 * - `client/CL_ents.AddPacketEntities` trail branches + `lerp_origin` advance
 */
internal class ReplicatedEntityEffectCollector(
    private val entityManager: ClientEntityManager,
    private val effectsSystem: ClientEffectsSystem,
    private val dynamicLightSystem: DynamicLightSystem,
) {

    /**
     * Adds per-frame dynamic lights driven by replicated entity effect flags (`EF_*`).
     */
    fun collectDynamicLights(lerpFrac: Float, currentTimeMs: Int) {
        entityManager.forEachCurrentEntity { entity, state ->
            val origin = interpolatedEntityRenderOrigin(entity, state.renderfx, lerpFrac)
            val effects = state.effects
            when {
                (effects and Defines.EF_ROCKET) != 0 -> {
                    dynamicLightSystem.addFrameLight(origin, 200f, 1f, 1f, 0f)
                }
                (effects and Defines.EF_BLASTER) != 0 -> {
                    if ((effects and Defines.EF_TRACKER) != 0) {
                        dynamicLightSystem.addFrameLight(origin, 200f, 0f, 1f, 0f)
                    } else {
                        dynamicLightSystem.addFrameLight(origin, 200f, 1f, 1f, 0f)
                    }
                }
                (effects and Defines.EF_HYPERBLASTER) != 0 -> {
                    if ((effects and Defines.EF_TRACKER) != 0) {
                        dynamicLightSystem.addFrameLight(origin, 200f, 0f, 1f, 0f)
                    } else {
                        dynamicLightSystem.addFrameLight(origin, 200f, 1f, 1f, 0f)
                    }
                }
                (effects and Defines.EF_BFG) != 0 -> {
                    val radius = if ((effects and Defines.EF_ANIM_ALLFAST) != 0) {
                        200f
                    } else {
                        BFG_LIGHT_RAMP[state.frame % BFG_LIGHT_RAMP.size].toFloat()
                    }
                    dynamicLightSystem.addFrameLight(origin, radius, 0f, 1f, 0f)
                }
                (effects and Defines.EF_TRAP) != 0 -> {
                    val radius = 100f + Globals.rnd.nextInt(100)
                    dynamicLightSystem.addFrameLight(origin, radius, 1f, 0.8f, 0.1f)
                }
                (effects and Defines.EF_FLAG1) != 0 -> {
                    dynamicLightSystem.addFrameLight(origin, 225f, 1f, 0.1f, 0.1f)
                }
                (effects and Defines.EF_FLAG2) != 0 -> {
                    dynamicLightSystem.addFrameLight(origin, 225f, 0.1f, 0.1f, 1f)
                }
                (effects and Defines.EF_TAGTRAIL) != 0 -> {
                    dynamicLightSystem.addFrameLight(origin, 225f, 1f, 1f, 0f)
                }
                (effects and Defines.EF_TRACKERTRAIL) != 0 -> {
                    if ((effects and Defines.EF_TRACKER) != 0) {
                        val radius = 50f + 500f * (sin(currentTimeMs / 500f) + 1f)
                        dynamicLightSystem.addFrameLight(origin, radius, -1f, -1f, -1f)
                    } else {
                        dynamicLightSystem.addFrameLight(origin, 155f, -1f, -1f, -1f)
                    }
                }
                (effects and Defines.EF_TRACKER) != 0 -> {
                    dynamicLightSystem.addFrameLight(origin, 200f, -1f, -1f, -1f)
                }
                (effects and Defines.EF_IONRIPPER) != 0 -> {
                    dynamicLightSystem.addFrameLight(origin, 100f, 1f, 0.5f, 0.5f)
                }
                (effects and Defines.EF_BLUEHYPERBLASTER) != 0 -> {
                    dynamicLightSystem.addFrameLight(origin, 200f, 0f, 0f, 1f)
                }
                (effects and Defines.EF_PLASMA) != 0 -> {
                    dynamicLightSystem.addFrameLight(origin, 130f, 1f, 0.5f, 0.5f)
                }
            }
        }
    }

    /**
     * Emits replicated projectile trails and advances per-entity trail origins.
     */
    fun collectTrails(lerpFrac: Float) {
        entityManager.forEachCurrentEntity { entity, state ->
            if (state.modelindex == 0) {
                return@forEachCurrentEntity
            }

            val endOrigin = interpolatedEntityRenderOrigin(entity, state.renderfx, lerpFrac)
            val endX = endOrigin.x
            val endY = endOrigin.y
            val endZ = endOrigin.z

            effectsSystem.emitReplicatedEntityTrail(
                entity = entity,
                effects = state.effects,
                endX = endX,
                endY = endY,
                endZ = endZ,
            )

            entity.lerp_origin[0] = endX
            entity.lerp_origin[1] = endY
            entity.lerp_origin[2] = endZ
        }
    }

    /**
     * Computes render-time entity origin using the same interpolation rules as packet-entity rendering.
     *
     * Legacy counterpart in `CL_AddPacketEntities`:
     * - `RF_FRAMELERP` / `RF_BEAM` use discrete `current.origin`,
     * - other entities use `prev.origin + lerpfrac * (current - prev)`.
     */
    private fun interpolatedEntityRenderOrigin(entity: ClientEntity, renderFx: Int, lerpFrac: Float): Vector3 {
        if ((renderFx and (Defines.RF_FRAMELERP or Defines.RF_BEAM)) != 0) {
            return Vector3(
                entity.current.origin[0],
                entity.current.origin[1],
                entity.current.origin[2],
            )
        }
        val frac = lerpFrac.coerceIn(0f, 1f)
        return Vector3(
            entity.prev.origin[0] + (entity.current.origin[0] - entity.prev.origin[0]) * frac,
            entity.prev.origin[1] + (entity.current.origin[1] - entity.prev.origin[1]) * frac,
            entity.prev.origin[2] + (entity.current.origin[2] - entity.prev.origin[2]) * frac,
        )
    }

    private companion object {
        private val BFG_LIGHT_RAMP = intArrayOf(300, 400, 600, 300, 150, 75)
    }
}
