package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
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
    private var alphaPointVertices = FloatArray(INITIAL_MAX_PARTICLES * POINT_FLOATS_PER_VERTEX)
    private var additivePointVertices = FloatArray(INITIAL_MAX_PARTICLES * POINT_FLOATS_PER_VERTEX)
    private var alphaBillboardParticles = FloatArray(INITIAL_MAX_PARTICLES * PARTICLE_FLOATS)
    private var additiveBillboardParticles = FloatArray(INITIAL_MAX_PARTICLES * PARTICLE_FLOATS)
    private var alphaPointCount = 0
    private var additivePointCount = 0
    private var alphaBillboardCount = 0
    private var additiveBillboardCount = 0
    private var pointSizeScale = BASE_POINT_SIZE
    private var cameraCombined: Matrix4 = Matrix4()
    private var cameraX = 0f
    private var cameraY = 0f
    private var cameraZ = 0f
    private var pointMesh = createPointMesh(INITIAL_MAX_PARTICLES)
    private var billboardMesh = createBillboardMesh(INITIAL_MAX_PARTICLES * BILLBOARD_VERTICES_PER_PARTICLE)
    private var billboardVertices = FloatArray(INITIAL_MAX_PARTICLES * BILLBOARD_VERTICES_PER_PARTICLE * BILLBOARD_FLOATS_PER_VERTEX)
    private val pointShader = ShaderProgram(POINT_VERTEX_SHADER, POINT_FRAGMENT_SHADER).also { shader ->
        if (!shader.isCompiled) {
            throw GdxRuntimeException("Failed to compile point particle shader: ${shader.log}")
        }
    }
    private val billboardShader = ShaderProgram(BILLBOARD_VERTEX_SHADER, BILLBOARD_FRAGMENT_SHADER).also { shader ->
        if (!shader.isCompiled) {
            throw GdxRuntimeException("Failed to compile billboard particle shader: ${shader.log}")
        }
    }
    private val tempRight = Vector3()
    private val tempUp = Vector3()
    private val tempForward = Vector3()

    fun begin(camera: Camera) {
        cameraCombined = camera.combined
        alphaPointCount = 0
        additivePointCount = 0
        alphaBillboardCount = 0
        additiveBillboardCount = 0
        pointSizeScale = BASE_POINT_SIZE * (Gdx.graphics.height.toFloat() / 480f).coerceAtLeast(0.1f)
        cameraX = camera.position.x
        cameraY = camera.position.y
        cameraZ = camera.position.z
        tempForward.set(camera.direction).nor()
        tempRight.set(tempForward).crs(camera.up).nor()
        tempUp.set(tempRight).crs(tempForward).nor()
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
        when (mode) {
            ParticleRenderMode.POINT_SPRITE -> when (blend) {
                ParticleBlendMode.ALPHA -> appendPointVertex(
                    alpha = true,
                    x = x,
                    y = y,
                    z = z,
                    red = red,
                    green = green,
                    blue = blue,
                    alphaValue = alpha,
                    size = size,
                )

                ParticleBlendMode.ADDITIVE -> appendPointVertex(
                    alpha = false,
                    x = x,
                    y = y,
                    z = z,
                    red = red,
                    green = green,
                    blue = blue,
                    alphaValue = alpha,
                    size = size,
                )
            }

            ParticleRenderMode.BILLBOARD_SPRITE -> when (blend) {
                ParticleBlendMode.ALPHA -> appendBillboardParticle(
                    alpha = true,
                    x = x,
                    y = y,
                    z = z,
                    red = red,
                    green = green,
                    blue = blue,
                    alphaValue = alpha,
                    size = size,
                )

                ParticleBlendMode.ADDITIVE -> appendBillboardParticle(
                    alpha = false,
                    x = x,
                    y = y,
                    z = z,
                    red = red,
                    green = green,
                    blue = blue,
                    alphaValue = alpha,
                    size = size,
                )
            }
        }
    }

    fun flush() {
        val total = alphaPointCount + additivePointCount + alphaBillboardCount + additiveBillboardCount
        if (total == 0) {
            return
        }
        ensurePointMeshCapacity(max(alphaPointCount, additivePointCount))
        ensureBillboardMeshCapacity(max(alphaBillboardCount, additiveBillboardCount))

        val gl = Gdx.gl
        gl.glEnable(GL20.GL_DEPTH_TEST)
        gl.glDepthMask(false)
        gl.glEnable(GL20.GL_BLEND)
        gl.glDisable(GL20.GL_CULL_FACE)
        gl.glEnable(GL_PROGRAM_POINT_SIZE)

        if (alphaPointCount > 0) {
            sortByDepthDescending(alphaPointVertices, alphaPointCount, POINT_FLOATS_PER_VERTEX)
            gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
            drawPoints(alphaPointVertices, alphaPointCount)
        }
        if (additivePointCount > 0) {
            gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
            drawPoints(additivePointVertices, additivePointCount)
        }
        if (alphaBillboardCount > 0) {
            sortByDepthDescending(alphaBillboardParticles, alphaBillboardCount, PARTICLE_FLOATS)
            gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
            drawBillboards(alphaBillboardParticles, alphaBillboardCount)
        }
        if (additiveBillboardCount > 0) {
            gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
            drawBillboards(additiveBillboardParticles, additiveBillboardCount)
        }

        gl.glDisable(GL_PROGRAM_POINT_SIZE)
        gl.glDisable(GL20.GL_BLEND)
        gl.glDepthMask(true)
    }

    override fun dispose() {
        pointMesh.dispose()
        billboardMesh.dispose()
        pointShader.dispose()
        billboardShader.dispose()
    }

    private fun appendPointVertex(
        alpha: Boolean,
        x: Float,
        y: Float,
        z: Float,
        red: Float,
        green: Float,
        blue: Float,
        alphaValue: Float,
        size: Float,
    ) {
        val targetCount = if (alpha) alphaPointCount else additivePointCount
        var vertices = if (alpha) alphaPointVertices else additivePointVertices
        val nextCount = targetCount + 1
        val minSize = nextCount * POINT_FLOATS_PER_VERTEX
        if (minSize > vertices.size) {
            var newSize = vertices.size
            while (newSize < minSize) {
                newSize = newSize shl 1
            }
            vertices = vertices.copyOf(newSize)
            if (alpha) {
                alphaPointVertices = vertices
            } else {
                additivePointVertices = vertices
            }
        }

        val base = targetCount * POINT_FLOATS_PER_VERTEX
        vertices[base] = x
        vertices[base + 1] = y
        vertices[base + 2] = z
        vertices[base + 3] = red
        vertices[base + 4] = green
        vertices[base + 5] = blue
        vertices[base + 6] = alphaValue
        vertices[base + 7] = max(1f, size * pointSizeScale)
        if (alpha) {
            alphaPointCount = nextCount
        } else {
            additivePointCount = nextCount
        }
    }

    private fun appendBillboardParticle(
        alpha: Boolean,
        x: Float,
        y: Float,
        z: Float,
        red: Float,
        green: Float,
        blue: Float,
        alphaValue: Float,
        size: Float,
    ) {
        val targetCount = if (alpha) alphaBillboardCount else additiveBillboardCount
        var particles = if (alpha) alphaBillboardParticles else additiveBillboardParticles
        val nextCount = targetCount + 1
        val minSize = nextCount * PARTICLE_FLOATS
        if (minSize > particles.size) {
            var newSize = particles.size
            while (newSize < minSize) {
                newSize = newSize shl 1
            }
            particles = particles.copyOf(newSize)
            if (alpha) {
                alphaBillboardParticles = particles
            } else {
                additiveBillboardParticles = particles
            }
        }

        val base = targetCount * PARTICLE_FLOATS
        particles[base] = x
        particles[base + 1] = y
        particles[base + 2] = z
        particles[base + 3] = red
        particles[base + 4] = green
        particles[base + 5] = blue
        particles[base + 6] = alphaValue
        particles[base + 7] = max(0.01f, size)
        if (alpha) {
            alphaBillboardCount = nextCount
        } else {
            additiveBillboardCount = nextCount
        }
    }

    private fun ensurePointMeshCapacity(requiredVertices: Int) {
        if (requiredVertices <= 0 || pointMesh.maxVertices >= requiredVertices) {
            return
        }
        var maxVertices = pointMesh.maxVertices
        while (maxVertices < requiredVertices) {
            maxVertices = maxVertices shl 1
        }
        pointMesh.dispose()
        pointMesh = createPointMesh(maxVertices)
    }

    private fun ensureBillboardMeshCapacity(requiredParticles: Int) {
        if (requiredParticles <= 0) {
            return
        }
        val requiredVertices = requiredParticles * BILLBOARD_VERTICES_PER_PARTICLE
        if (billboardMesh.maxVertices >= requiredVertices) {
            return
        }
        var maxVertices = billboardMesh.maxVertices
        while (maxVertices < requiredVertices) {
            maxVertices = maxVertices shl 1
        }
        billboardMesh.dispose()
        billboardMesh = createBillboardMesh(maxVertices)
        val requiredFloatCapacity = maxVertices * BILLBOARD_FLOATS_PER_VERTEX
        if (billboardVertices.size < requiredFloatCapacity) {
            billboardVertices = FloatArray(requiredFloatCapacity)
        }
    }

    private fun drawPoints(vertices: FloatArray, particleCount: Int) {
        pointShader.bind()
        pointShader.setUniformMatrix("u_projViewTrans", cameraCombined)
        pointShader.setUniformf("u_gammaExponent", RenderTuningCvars.gammaExponent())
        pointShader.setUniformf("u_intensity", RenderTuningCvars.intensity())
        pointShader.setUniformf("u_overbrightbits", RenderTuningCvars.overbrightBits())
        pointShader.setUniformf("u_particleFadeFactor", PARTICLE_FADE_FACTOR)
        val floatCount = particleCount * POINT_FLOATS_PER_VERTEX
        pointMesh.setVertices(vertices, 0, floatCount)
        pointMesh.render(pointShader, GL20.GL_POINTS, 0, particleCount)
    }

    private fun drawBillboards(particles: FloatArray, particleCount: Int) {
        billboardShader.bind()
        billboardShader.setUniformMatrix("u_projViewTrans", cameraCombined)
        billboardShader.setUniformf("u_gammaExponent", RenderTuningCvars.gammaExponent())
        billboardShader.setUniformf("u_intensity", RenderTuningCvars.intensity())
        billboardShader.setUniformf("u_overbrightbits", RenderTuningCvars.overbrightBits())
        billboardShader.setUniformf("u_particleFadeFactor", PARTICLE_FADE_FACTOR)
        writeBillboardVertices(particles, particleCount)
        val vertexCount = particleCount * BILLBOARD_VERTICES_PER_PARTICLE
        billboardMesh.setVertices(
            billboardVertices,
            0,
            vertexCount * BILLBOARD_FLOATS_PER_VERTEX,
        )
        billboardMesh.render(billboardShader, GL20.GL_TRIANGLES, 0, vertexCount)
    }

    private fun writeBillboardVertices(particles: FloatArray, particleCount: Int) {
        var write = 0
        repeat(particleCount) { particleIndex ->
            val particleBase = particleIndex * PARTICLE_FLOATS
            val x = particles[particleBase]
            val y = particles[particleBase + 1]
            val z = particles[particleBase + 2]
            val red = particles[particleBase + 3]
            val green = particles[particleBase + 4]
            val blue = particles[particleBase + 5]
            val alpha = particles[particleBase + 6]
            val halfSize = particles[particleBase + 7] * 0.5f

            val rx = tempRight.x * halfSize
            val ry = tempRight.y * halfSize
            val rz = tempRight.z * halfSize
            val ux = tempUp.x * halfSize
            val uy = tempUp.y * halfSize
            val uz = tempUp.z * halfSize

            val topLeftX = x - rx + ux
            val topLeftY = y - ry + uy
            val topLeftZ = z - rz + uz
            val topRightX = x + rx + ux
            val topRightY = y + ry + uy
            val topRightZ = z + rz + uz
            val bottomRightX = x + rx - ux
            val bottomRightY = y + ry - uy
            val bottomRightZ = z + rz - uz
            val bottomLeftX = x - rx - ux
            val bottomLeftY = y - ry - uy
            val bottomLeftZ = z - rz - uz

            write = writeBillboardVertex(write, topLeftX, topLeftY, topLeftZ, red, green, blue, alpha, 0f, 0f)
            write = writeBillboardVertex(write, topRightX, topRightY, topRightZ, red, green, blue, alpha, 1f, 0f)
            write = writeBillboardVertex(write, bottomRightX, bottomRightY, bottomRightZ, red, green, blue, alpha, 1f, 1f)
            write = writeBillboardVertex(write, topLeftX, topLeftY, topLeftZ, red, green, blue, alpha, 0f, 0f)
            write = writeBillboardVertex(write, bottomRightX, bottomRightY, bottomRightZ, red, green, blue, alpha, 1f, 1f)
            write = writeBillboardVertex(write, bottomLeftX, bottomLeftY, bottomLeftZ, red, green, blue, alpha, 0f, 1f)
        }
    }

    private fun writeBillboardVertex(
        writeOffset: Int,
        x: Float,
        y: Float,
        z: Float,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        u: Float,
        v: Float,
    ): Int {
        billboardVertices[writeOffset] = x
        billboardVertices[writeOffset + 1] = y
        billboardVertices[writeOffset + 2] = z
        billboardVertices[writeOffset + 3] = red
        billboardVertices[writeOffset + 4] = green
        billboardVertices[writeOffset + 5] = blue
        billboardVertices[writeOffset + 6] = alpha
        billboardVertices[writeOffset + 7] = u
        billboardVertices[writeOffset + 8] = v
        return writeOffset + BILLBOARD_FLOATS_PER_VERTEX
    }

    private fun createPointMesh(maxVertices: Int): Mesh {
        return Mesh(
            true,
            maxVertices,
            0,
            VertexAttribute(VertexAttributes.Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE),
            VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 4, ShaderProgram.COLOR_ATTRIBUTE),
            VertexAttribute(VertexAttributes.Usage.Generic, 1, PARTICLE_SIZE_ATTRIBUTE),
        )
    }

    private fun createBillboardMesh(maxVertices: Int): Mesh {
        return Mesh(
            true,
            maxVertices,
            0,
            VertexAttribute(VertexAttributes.Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE),
            VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 4, ShaderProgram.COLOR_ATTRIBUTE),
            VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, ShaderProgram.TEXCOORD_ATTRIBUTE + "0"),
        )
    }

    private fun sortByDepthDescending(data: FloatArray, count: Int, stride: Int) {
        if (count < 2) {
            return
        }
        quickSortByDepth(data, 0, count - 1, stride)
    }

    private fun quickSortByDepth(data: FloatArray, low: Int, high: Int, stride: Int) {
        var i = low
        var j = high
        val pivot = depthOf(data, (low + high) ushr 1, stride)
        while (i <= j) {
            while (depthOf(data, i, stride) > pivot) {
                i++
            }
            while (depthOf(data, j, stride) < pivot) {
                j--
            }
            if (i <= j) {
                if (i != j) {
                    swapRecords(data, i, j, stride)
                }
                i++
                j--
            }
        }
        if (low < j) {
            quickSortByDepth(data, low, j, stride)
        }
        if (i < high) {
            quickSortByDepth(data, i, high, stride)
        }
    }

    private fun depthOf(data: FloatArray, index: Int, stride: Int): Float {
        val base = index * stride
        val dx = data[base] - cameraX
        val dy = data[base + 1] - cameraY
        val dz = data[base + 2] - cameraZ
        return dx * dx + dy * dy + dz * dz
    }

    private fun swapRecords(data: FloatArray, a: Int, b: Int, stride: Int) {
        val baseA = a * stride
        val baseB = b * stride
        repeat(stride) { offset ->
            val tmp = data[baseA + offset]
            data[baseA + offset] = data[baseB + offset]
            data[baseB + offset] = tmp
        }
    }

    companion object {
        private const val POINT_FLOATS_PER_VERTEX = 8
        private const val BILLBOARD_FLOATS_PER_VERTEX = 9
        private const val PARTICLE_FLOATS = 8
        private const val BILLBOARD_VERTICES_PER_PARTICLE = 6
        private const val INITIAL_MAX_PARTICLES = 2048
        private const val BASE_POINT_SIZE = 40f
        private const val PARTICLE_FADE_FACTOR = 1.2f
        private const val PARTICLE_SIZE_ATTRIBUTE = "a_size"
        private const val GL_PROGRAM_POINT_SIZE = 0x8642

        private const val POINT_VERTEX_SHADER = """
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

        private const val POINT_FRAGMENT_SHADER = """
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

        private const val BILLBOARD_VERTEX_SHADER = """
attribute vec3 a_position;
attribute vec4 a_color;
attribute vec2 a_texCoord0;

uniform mat4 u_projViewTrans;

varying vec4 v_color;
varying vec2 v_uv;

void main() {
    v_color = a_color;
    v_uv = a_texCoord0;
    gl_Position = u_projViewTrans * vec4(a_position, 1.0);
}
"""

        private const val BILLBOARD_FRAGMENT_SHADER = """
varying vec4 v_color;
varying vec2 v_uv;

uniform float u_gammaExponent;
uniform float u_intensity;
uniform float u_overbrightbits;
uniform float u_particleFadeFactor;

void main() {
    vec2 uv = v_uv * 2.0 - 1.0;
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
