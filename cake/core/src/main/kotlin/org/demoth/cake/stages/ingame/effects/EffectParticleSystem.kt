package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.model.Node
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import jake2.qcommon.Globals
import org.demoth.cake.stages.ingame.RenderTuningCvars
import java.util.ArrayDeque
import kotlin.math.max

/**
 * Lightweight world-space particle runtime used by temp effects.
 *
 * Implementation notes:
 * - renders particles as tiny translucent cubes (no texture atlas dependency),
 * - keeps one shared mesh and pooled per-particle render state (material + instance transform),
 * - intended as a parity bridge until a dedicated high-throughput particle renderer lands.
 */
class EffectParticleSystem : Disposable {
    private val model: Model
    private val freeRenderables = ArrayDeque<ParticleRenderable>()
    private val activeParticles = ArrayList<Particle>(512)

    init {
        val material = Material(
            ColorAttribute.createDiffuse(Color.WHITE),
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 1f),
            DepthTestAttribute(GL20.GL_LEQUAL, 0f, 1f, false),
            IntAttribute.createCullFace(GL20.GL_NONE),
        )
        model = ModelBuilder().createBox(
            1f,
            1f,
            1f,
            material,
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong(),
        )
    }

    fun emitBurst(
        origin: Vector3,
        direction: FloatArray?,
        count: Int,
        color: Color,
        speedMin: Float,
        speedMax: Float,
        spread: Float,
        gravity: Float,
        startAlpha: Float,
        endAlpha: Float,
        sizeMin: Float,
        sizeMax: Float,
        lifetimeMinMs: Int,
        lifetimeMaxMs: Int,
    ) {
        if (!RenderTuningCvars.particlesEnabled()) {
            return
        }
        repeat(max(0, count)) {
            val particleDirection = randomDirection(direction, spread)
            val speed = randomFloat(speedMin, speedMax)
            val lifeMs = randomInt(lifetimeMinMs, lifetimeMaxMs)
            val renderable = obtainRenderable()
            renderable.diffuse.color.set(color.r, color.g, color.b, 1f)
            activeParticles += Particle(
                position = Vector3(origin),
                velocity = particleDirection.scl(speed),
                spawnTimeMs = Globals.curtime,
                lifetimeMs = max(1, lifeMs),
                startAlpha = startAlpha,
                endAlpha = endAlpha,
                size = randomFloat(sizeMin, sizeMax).coerceAtLeast(0.1f),
                gravity = gravity,
                renderable = renderable,
            )
        }
    }

    fun update(deltaSeconds: Float, nowMs: Int) {
        if (activeParticles.isEmpty()) {
            return
        }
        val iterator = activeParticles.iterator()
        while (iterator.hasNext()) {
            val particle = iterator.next()
            val elapsedMs = nowMs - particle.spawnTimeMs
            if (elapsedMs >= particle.lifetimeMs) {
                recycleRenderable(particle.renderable)
                iterator.remove()
                continue
            }
            particle.velocity.z += particle.gravity * deltaSeconds
            particle.position.mulAdd(particle.velocity, deltaSeconds)
        }
    }

    fun render(modelBatch: ModelBatch) {
        if (!RenderTuningCvars.particlesEnabled()) {
            return
        }
        val nowMs = Globals.curtime
        activeParticles.forEach { particle ->
            val life = ((nowMs - particle.spawnTimeMs).toFloat() / particle.lifetimeMs.toFloat()).coerceIn(0f, 1f)
            val alpha = particle.startAlpha + (particle.endAlpha - particle.startAlpha) * life
            if (alpha <= 0f) {
                return@forEach
            }
            val renderable = particle.renderable
            renderable.blending.opacity = alpha
            renderable.instance.transform.idt()
            renderable.instance.transform.setToTranslation(particle.position)
            renderable.instance.transform.scale(particle.size, particle.size, particle.size)
            modelBatch.render(renderable.instance)
        }
    }

    override fun dispose() {
        activeParticles.clear()
        freeRenderables.clear()
        model.dispose()
    }

    private fun obtainRenderable(): ParticleRenderable {
        return if (freeRenderables.isEmpty()) {
            createRenderable()
        } else {
            freeRenderables.removeFirst()
        }
    }

    private fun recycleRenderable(renderable: ParticleRenderable) {
        freeRenderables.addLast(renderable)
    }

    private fun createRenderable(): ParticleRenderable {
        val instance = ModelInstance(model)
        val material = Material(
            ColorAttribute.createDiffuse(Color.WHITE),
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 1f),
            DepthTestAttribute(GL20.GL_LEQUAL, 0f, 1f, false),
            IntAttribute.createCullFace(GL20.GL_NONE),
        )
        instance.materials.clear()
        instance.materials.add(material)
        instance.nodes.forEach { node -> applyMaterialRecursive(node, material) }
        val diffuse = material.get(ColorAttribute.Diffuse) as ColorAttribute
        val blending = material.get(BlendingAttribute.Type) as BlendingAttribute
        return ParticleRenderable(instance, diffuse, blending)
    }

    private fun applyMaterialRecursive(node: Node, material: Material) {
        node.parts.forEach { it.material = material }
        node.children.forEach { child -> applyMaterialRecursive(child, material) }
    }

    private data class ParticleRenderable(
        val instance: ModelInstance,
        val diffuse: ColorAttribute,
        val blending: BlendingAttribute,
    )

    private fun randomDirection(direction: FloatArray?, spread: Float): Vector3 {
        val base = if (direction != null && direction.size >= 3) {
            Vector3(direction[0], direction[1], direction[2]).nor()
        } else {
            Vector3(0f, 0f, 1f)
        }
        base.x += randomFloat(-spread, spread)
        base.y += randomFloat(-spread, spread)
        base.z += randomFloat(-spread, spread)
        if (base.isZero) {
            base.set(0f, 0f, 1f)
        }
        return base.nor()
    }

    private fun randomFloat(min: Float, max: Float): Float {
        if (min >= max) {
            return min
        }
        return min + Globals.rnd.nextFloat() * (max - min)
    }

    private fun randomInt(min: Int, max: Int): Int {
        if (min >= max) {
            return min
        }
        return min + Globals.rnd.nextInt(max - min + 1)
    }

    private data class Particle(
        val position: Vector3,
        val velocity: Vector3,
        val spawnTimeMs: Int,
        val lifetimeMs: Int,
        val startAlpha: Float,
        val endAlpha: Float,
        val size: Float,
        val gravity: Float,
        val renderable: ParticleRenderable,
    )
}
