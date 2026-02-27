package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.GdxRuntimeException
import org.demoth.cake.stages.ingame.RenderTuningCvars
import kotlin.math.max

/**
 * Dedicated particle renderer that streams dynamic point vertices into a GPU buffer.
 *
 * This renderer intentionally bypasses ModelBatch to keep particle draw submissions bounded.
 */
class ParticleRenderer : Disposable {
    private var alphaVertices = FloatArray(INITIAL_MAX_PARTICLES * FLOATS_PER_VERTEX)
    private var additiveVertices = FloatArray(INITIAL_MAX_PARTICLES * FLOATS_PER_VERTEX)
    private var alphaCount = 0
    private var additiveCount = 0
    private var pointSizeScale = BASE_POINT_SIZE
    private var cameraCombined: Matrix4 = Matrix4()
    private var mesh = createMesh(INITIAL_MAX_PARTICLES)
    private val shaderProgram = ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER).also { shader ->
        if (!shader.isCompiled) {
            throw GdxRuntimeException("Failed to compile particle shader: ${shader.log}")
        }
    }

    fun begin(camera: Camera) {
        cameraCombined = camera.combined
        alphaCount = 0
        additiveCount = 0
        pointSizeScale = BASE_POINT_SIZE * (Gdx.graphics.height.toFloat() / 480f).coerceAtLeast(0.1f)
    }

    fun submit(
        mode: ParticleRenderMode,
        blend: ParticleBlendMode,
        x: Float,
        y: Float,
        z: Float,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        size: Float,
    ) {
        // Billboard path intentionally falls back to points for now.
        if (mode != ParticleRenderMode.POINT_SPRITE && mode != ParticleRenderMode.BILLBOARD_SPRITE) {
            return
        }
        when (blend) {
            ParticleBlendMode.ALPHA -> appendVertex(
                target = alphaVertices,
                count = alphaCount,
                x = x,
                y = y,
                z = z,
                red = red,
                green = green,
                blue = blue,
                alpha = alpha,
                size = size,
            ).also {
                alphaVertices = it.first
                alphaCount = it.second
            }

            ParticleBlendMode.ADDITIVE -> appendVertex(
                target = additiveVertices,
                count = additiveCount,
                x = x,
                y = y,
                z = z,
                red = red,
                green = green,
                blue = blue,
                alpha = alpha,
                size = size,
            ).also {
                additiveVertices = it.first
                additiveCount = it.second
            }
        }
    }

    fun flush() {
        val total = alphaCount + additiveCount
        if (total == 0) {
            return
        }
        ensureMeshCapacity(total)

        shaderProgram.bind()
        shaderProgram.setUniformMatrix("u_projViewTrans", cameraCombined)
        shaderProgram.setUniformf("u_gammaExponent", RenderTuningCvars.gammaExponent())
        shaderProgram.setUniformf("u_intensity", RenderTuningCvars.intensity())
        shaderProgram.setUniformf("u_overbrightbits", RenderTuningCvars.overbrightBits())
        shaderProgram.setUniformf("u_particleFadeFactor", PARTICLE_FADE_FACTOR)

        val gl = Gdx.gl
        gl.glEnable(GL20.GL_DEPTH_TEST)
        gl.glDepthMask(false)
        gl.glEnable(GL20.GL_BLEND)
        gl.glDisable(GL20.GL_CULL_FACE)
        gl.glEnable(GL_PROGRAM_POINT_SIZE)

        if (alphaCount > 0) {
            gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
            draw(alphaVertices, alphaCount)
        }
        if (additiveCount > 0) {
            gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
            draw(additiveVertices, additiveCount)
        }

        gl.glDisable(GL_PROGRAM_POINT_SIZE)
        gl.glDisable(GL20.GL_BLEND)
        gl.glDepthMask(true)
    }

    override fun dispose() {
        mesh.dispose()
        shaderProgram.dispose()
    }

    private fun appendVertex(
        target: FloatArray,
        count: Int,
        x: Float,
        y: Float,
        z: Float,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        size: Float,
    ): Pair<FloatArray, Int> {
        var vertices = target
        val nextCount = count + 1
        val minSize = nextCount * FLOATS_PER_VERTEX
        if (minSize > vertices.size) {
            var newSize = vertices.size
            while (newSize < minSize) {
                newSize = newSize shl 1
            }
            vertices = vertices.copyOf(newSize)
        }

        val base = count * FLOATS_PER_VERTEX
        vertices[base] = x
        vertices[base + 1] = y
        vertices[base + 2] = z
        vertices[base + 3] = red
        vertices[base + 4] = green
        vertices[base + 5] = blue
        vertices[base + 6] = alpha
        vertices[base + 7] = max(1f, size * pointSizeScale)
        return vertices to nextCount
    }

    private fun ensureMeshCapacity(requiredVertices: Int) {
        if (mesh.maxVertices >= requiredVertices) {
            return
        }
        var maxVertices = mesh.maxVertices
        while (maxVertices < requiredVertices) {
            maxVertices = maxVertices shl 1
        }
        mesh.dispose()
        mesh = createMesh(maxVertices)
    }

    private fun draw(vertices: FloatArray, particleCount: Int) {
        val floatCount = particleCount * FLOATS_PER_VERTEX
        mesh.setVertices(vertices, 0, floatCount)
        mesh.render(shaderProgram, GL20.GL_POINTS, 0, particleCount)
    }

    private fun createMesh(maxVertices: Int): Mesh {
        return Mesh(
            true,
            maxVertices,
            0,
            VertexAttribute(VertexAttributes.Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE),
            VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 4, ShaderProgram.COLOR_ATTRIBUTE),
            VertexAttribute(VertexAttributes.Usage.Generic, 1, PARTICLE_SIZE_ATTRIBUTE),
        )
    }

    companion object {
        private const val FLOATS_PER_VERTEX = 8
        private const val INITIAL_MAX_PARTICLES = 2048
        private const val BASE_POINT_SIZE = 40f
        private const val PARTICLE_FADE_FACTOR = 1.2f
        private const val PARTICLE_SIZE_ATTRIBUTE = "a_size"
        private const val GL_PROGRAM_POINT_SIZE = 0x8642

        private const val VERTEX_SHADER = """
attribute vec3 a_position;
attribute vec4 a_color;
attribute float a_size;

uniform mat4 u_projViewTrans;

varying vec4 v_color;

void main() {
    v_color = a_color;
    gl_Position = u_projViewTrans * vec4(a_position, 1.0);
    gl_PointSize = a_size;
}
"""

        private const val FRAGMENT_SHADER = """
varying vec4 v_color;

uniform float u_gammaExponent;
uniform float u_intensity;
uniform float u_overbrightbits;
uniform float u_particleFadeFactor;

void main() {
    vec2 uv = gl_PointCoord * 2.0 - 1.0;
    float distSquared = dot(uv, uv);
    if (distSquared > 1.0) {
        discard;
    }

    vec3 linear = v_color.rgb * u_intensity * u_overbrightbits;
    vec3 corrected = pow(max(linear, vec3(0.0)), vec3(u_gammaExponent));
    float edgeFade = min(1.0, u_particleFadeFactor * (1.0 - distSquared));
    gl_FragColor = vec4(corrected, v_color.a * edgeFade);
}
"""
    }
}
