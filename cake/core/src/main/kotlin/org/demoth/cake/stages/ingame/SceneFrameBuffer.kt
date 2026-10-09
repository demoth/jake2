package org.demoth.cake.stages.ingame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GL30
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.GLFrameBuffer.FrameBufferBuilder
import com.badlogic.gdx.utils.BufferUtils
import com.badlogic.gdx.utils.Disposable
import jake2.qcommon.Com

/** Physical-pixel scene target, optionally resolved from MSAA before postprocessing/capture. */
internal class SceneFrameBuffer private constructor(
    val resolved: FrameBuffer,
    private val multisampled: FrameBuffer?,
    val depthBits: Int,
    val samples: Int,
) : Disposable {
    val renderTarget: FrameBuffer get() = multisampled ?: resolved
    val description: String
        get() = "${resolved.width}x${resolved.height}, depth=$depthBits, samples=$samples"

    fun resolve() {
        multisampled?.transfer(resolved, GL20.GL_COLOR_BUFFER_BIT)
        // transfer() binds framebuffer 0; end() restores libGDX's platform default and viewport.
        resolved.end()
    }

    override fun dispose() {
        multisampled?.dispose()
        resolved.dispose()
    }

    companion object {
        fun create(width: Int, height: Int, requestedDepth: Int, requestedSamples: Int): SceneFrameBuffer {
            val maxSamples = if (Gdx.graphics.isGL31Available) integer(GL30.GL_MAX_SAMPLES) else 0
            val samples = requestedSamples.coerceAtMost(maxSamples).coerceAtLeast(0)
            if (samples != requestedSamples) {
                Com.Warn("Scene MSAA requested $requestedSamples samples; maximum supported here is $maxSamples\n")
            }
            // Prefer keeping requested precision while reducing MSAA, then fall back to depth24/16.
            val candidates = listOf(requestedDepth to samples, requestedDepth to 0, 24 to 0, 16 to 0).distinct()
            var lastFailure: Exception? = null
            for ((depth, count) in candidates) {
                var resolved: FrameBuffer? = null
                var multisampled: FrameBuffer? = null
                try {
                    val depthFormat = when (depth) {
                        32 -> GL30.GL_DEPTH_COMPONENT32F
                        24 -> GL30.GL_DEPTH_COMPONENT24
                        else -> GL20.GL_DEPTH_COMPONENT16
                    }
                    val builder = FrameBufferBuilder(width, height)
                    builder.addBasicColorTextureAttachment(Pixmap.Format.RGBA8888)
                    if (count == 0) builder.addDepthRenderBuffer(depthFormat)
                    resolved = builder.build()
                    if (count > 0) {
                        val msaaBuilder = FrameBufferBuilder(width, height, count)
                        msaaBuilder.addColorRenderBuffer(GL30.GL_RGBA8)
                        msaaBuilder.addDepthRenderBuffer(depthFormat)
                        multisampled = msaaBuilder.build()
                    }
                    val target = multisampled ?: resolved
                    val actualDepth = renderBufferParameter(target.depthBufferHandle, GL30.GL_RENDERBUFFER_DEPTH_SIZE)
                    val actualSamples = if (multisampled != null) {
                        renderBufferParameter(target.getColorBufferHandle(0), GL30.GL_RENDERBUFFER_SAMPLES)
                    } else 0
                    val result = SceneFrameBuffer(resolved, multisampled, actualDepth, actualSamples)
                    if (actualDepth != requestedDepth || actualSamples != requestedSamples) {
                        Com.Warn("Scene buffer: ${result.description} (requested depth=$requestedDepth samples=$requestedSamples)\n")
                    }
                    return result
                } catch (failure: Exception) {
                    multisampled?.dispose()
                    resolved?.dispose()
                    lastFailure = failure
                    Com.Warn("Scene buffer depth=$depth samples=$count unavailable: ${failure.message}\n")
                }
            }
            throw IllegalStateException("No supported scene framebuffer configuration", lastFailure)
        }

        private fun integer(parameter: Int): Int {
            val result = BufferUtils.newIntBuffer(1)
            Gdx.gl.glGetIntegerv(parameter, result)
            return result[0]
        }

        private fun renderBufferParameter(handle: Int, parameter: Int): Int {
            val previous = integer(GL20.GL_RENDERBUFFER_BINDING)
            val result = BufferUtils.newIntBuffer(1)
            Gdx.gl.glBindRenderbuffer(GL20.GL_RENDERBUFFER, handle)
            try {
                Gdx.gl.glGetRenderbufferParameteriv(GL20.GL_RENDERBUFFER, parameter, result)
                return result[0]
            } finally {
                Gdx.gl.glBindRenderbuffer(GL20.GL_RENDERBUFFER, previous)
            }
        }
    }
}
