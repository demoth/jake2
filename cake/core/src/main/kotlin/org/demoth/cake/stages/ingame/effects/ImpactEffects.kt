package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import jake2.qcommon.Defines.*
import jake2.qcommon.math.Vector3f
import jake2.qcommon.network.messages.server.*
import kotlin.random.Random

internal data class ImpactSoundRequest(val path: String, val origin: Vector3f, val attenuation: Float = ATTN_NORM.toFloat())

/**
 * Particle and sound components of CL_ParticleEffect/2/3 and CL_BlasterParticles/2.
 * Positions scatter along the impact normal; velocities scatter independently, with directed
 * velocity for blaster impacts. The outer controller retains model flashes and dynamic lights.
 */
internal class ImpactEffects(
    private val particles: GdxParticleRuntime,
    private val paletteColor: (Int) -> Color,
    private val playSound: (ImpactSoundRequest) -> Unit,
    private val random: Random = Random.Default,
) {
    private data class Recipe(val count: Int, val palette: Int, val distance: Float = 31f,
                              val speed: Float = 20f, val forwardSpeed: Float = 0f,
                              val variants: Int = 8, val gravity: Float = -80f)
    private val direction = Vector3()
    private val position = Vector3()
    private val velocity = Vector3()
    private val acceleration = Vector3()

    fun process(message: TEMessage, nowMs: Int): Boolean {
        when (message) {
            is PointDirectionTEMessage -> {
                val recipe = when (message.style) {
                    TE_BLOOD -> Recipe(60, 0xE8)
                    TE_MOREBLOOD -> Recipe(250, 0xE8)
                    TE_GREENBLOOD -> Recipe(30, 0xDF, distance = 7f)
                    TE_GUNSHOT -> Recipe(40, 0)
                    TE_SPARKS, TE_BULLET_SPARKS -> Recipe(6, 0xE0)
                    TE_SCREEN_SPARKS -> Recipe(40, 0xD0)
                    TE_SHIELD_SPARKS -> Recipe(40, 0xB0)
                    TE_ELECTRIC_SPARKS -> Recipe(40, 0x75)
                    TE_SHOTGUN -> Recipe(20, 0)
                    TE_BLASTER -> blaster(0xE0)
                    TE_BLASTER2 -> blaster(0xD0)
                    TE_FLECHETTE -> blaster(0x6F)
                    else -> return false
                }
                emit(message.position, message.direction, recipe, nowMs)
                when (message.style) {
                    TE_GUNSHOT, TE_BULLET_SPARKS -> {
                        val roll = random.nextInt(16)
                        if (roll in 1..3) playSound(ImpactSoundRequest("sound/world/ric$roll.wav", message.position))
                    }
                    TE_SCREEN_SPARKS, TE_SHIELD_SPARKS, TE_ELECTRIC_SPARKS, TE_BLASTER, TE_BLASTER2, TE_FLECHETTE ->
                        playSound(ImpactSoundRequest("sound/weapons/lashit.wav", message.position))
                }
            }
            is SplashTEMessage -> {
                val recipe = when (message.style) {
                    TE_SPLASH -> Recipe(message.count, SPLASH_COLORS.getOrElse(message.param) { 0 })
                    TE_LASER_SPARKS, TE_WELDING_SPARKS -> Recipe(message.count, message.param, distance = 7f)
                    TE_TUNNEL_SPARKS -> Recipe(message.count, message.param, distance = 7f, variants = 1, gravity = 80f)
                    else -> return false
                }
                emit(message.position, message.direction, recipe, nowMs)
                if (message.style == TE_SPLASH && message.param == SPLASH_SPARKS) {
                    val sound = when (random.nextInt(4)) { 0 -> 5; 1 -> 6; else -> 7 }
                    playSound(ImpactSoundRequest("sound/world/spark$sound.wav", message.position, ATTN_STATIC.toFloat()))
                }
            }
            is TrailTEMessage -> {
                if (message.style != TE_BLUEHYPERBLASTER) return false
                // This subtype transmits a direction with position precision, rather than a trail endpoint.
                emit(message.position, message.destination, blaster(0xE0), nowMs)
            }
            is PointTEMessage -> {
                if (message.style != TE_CHAINFIST_SMOKE) return false
                emitSmoke(message.position, nowMs)
            }
            else -> return false
        }
        return true
    }

    private fun blaster(palette: Int) = Recipe(40, palette, distance = 15f, speed = 40f, forwardSpeed = 30f)

    private fun emit(origin: Vector3f, normal: Vector3f, recipe: Recipe, nowMs: Int) {
        direction.set(normal.x, normal.y, normal.z).nor()
        acceleration.set(0f, 0f, recipe.gravity)
        repeat(recipe.count) {
            position.set(origin.x + signedRandom() * 4f, origin.y + signedRandom() * 4f, origin.z + signedRandom() * 4f)
                .mulAdd(direction, random.nextFloat() * recipe.distance)
            velocity.set(signedRandom() * recipe.speed, signedRandom() * recipe.speed, signedRandom() * recipe.speed)
                .mulAdd(direction, recipe.forwardSpeed)
            val color = (recipe.palette + random.nextInt(recipe.variants)) and 255
            if (!particles.emit(position, velocity, acceleration, paletteColor(color), size = .5f,
                    lifetimeMillis = random.nextInt(500, 801), nowMs = nowMs)) return
        }
    }

    private fun emitSmoke(origin: Vector3f, nowMs: Int) {
        repeat(20) {
            position.set(origin.x + signedRandom() * 2f, origin.y + signedRandom() * 2f, origin.z + signedRandom() * 2f)
            velocity.set(signedRandom() * 7f, signedRandom() * 7f, 20f)
            if (!particles.emit(position, velocity, Vector3.Zero, paletteColor(random.nextInt(8)), size = .7f,
                    lifetimeMillis = random.nextInt(500, 801), nowMs = nowMs)) return
        }
    }

    private fun signedRandom() = random.nextFloat() * 2f - 1f

    companion object { private val SPLASH_COLORS = intArrayOf(0x00, 0xE0, 0xB0, 0x50, 0xD0, 0xE0, 0xE8) }
}
