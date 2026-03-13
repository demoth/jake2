package org.demoth.cake.stages.ingame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.AudioDevice
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.utils.Disposable
import jake2.qcommon.Com
import jake2.qcommon.network.messages.client.StringCmdMessage
import ktx.graphics.use
import org.demoth.cake.GameConfiguration
import org.demoth.cake.assets.tryResolveRaw

/**
 * Owns cinematic/picture-mode media state and skip control flow.
 *
 * Reference behavior:
 * - q2pro `src/client/cin.c` (`SCR_PlayCinematic`, `SCR_RunCinematic`, `SCR_FinishCinematic`)
 * - q2pro `src/client/keys.c` (click/key skip path into `SCR_FinishCinematic`)
 * - Yamagi `src/client/cl_cin.c` (14 FPS stepping and skip delay semantics)
 */
class CinematicPresentationController(
    private val assetManager: AssetManager,
    private val gameConfig: GameConfiguration,
) : Disposable {

    private var startTimeMs = Int.MIN_VALUE
    private var skipSent = false
    private var autoAdvancePending = false
    private var staticImage: Texture? = null
    private var frameTexture: Texture? = null
    private var framePixmap: Pixmap? = null
    private var streamDecoder: CinematicCinDecoder? = null
    private var audioDevice: AudioDevice? = null
    private var lastDebugLogTimeMs = Int.MIN_VALUE

    fun begin(cinematicName: String, currentTimeMs: Int) {
        releaseMedia()
        startTimeMs = currentTimeMs
        skipSent = false
        autoAdvancePending = false
        prepareMedia(cinematicName)
    }

    fun end() {
        releaseMedia()
        startTimeMs = Int.MIN_VALUE
        skipSent = false
        autoAdvancePending = false
    }

    fun render(currentTimeMs: Int, spriteBatch: SpriteBatch, screenWidth: Float, screenHeight: Float) {
        advanceStream(currentTimeMs)
        renderStaticImage(spriteBatch, screenWidth, screenHeight)
        logDiagnostics(currentTimeMs)
    }

    /**
     * Returns one-shot `nextserver` command when cinematic skip criteria are met.
     *
     * Legacy cross-reference:
     * - q2pro `keys.c`: key/button in cinematic calls `SCR_FinishCinematic()`.
     * - q2pro `cin.c`: `SCR_FinishCinematic()` sends `nextserver <servercount>`.
     * - q2pro/yamagi include a short guard delay before user-triggered skip.
     */
    fun pollSkipCommand(currentTimeMs: Int, spawnCount: Int, hasImmediateAction: Boolean): StringCmdMessage? {
        if (autoAdvancePending && !skipSent) {
            skipSent = true
            return StringCmdMessage("${StringCmdMessage.NEXT_SERVER} $spawnCount")
        }
        if (skipSent || startTimeMs == Int.MIN_VALUE) {
            return null
        }
        if (currentTimeMs - startTimeMs <= CINEMATIC_SKIP_DELAY_MS) {
            return null
        }
        if (!hasImmediateAction) {
            return null
        }
        skipSent = true
        return StringCmdMessage("${StringCmdMessage.NEXT_SERVER} $spawnCount")
    }

    /**
     * Load static image or stream media from server-provided cinematic map name.
     *
     * Legacy references:
     * - q2pro `src/client/cin.c` `SCR_PlayCinematic` (image path + `.cin` path split).
     * - Jake2 `client/SCR.PlayCinematic` (`pics/<name>` static image fallback).
     */
    private fun prepareMedia(cinematicName: String) {
        if (cinematicName.endsWith(".cin", ignoreCase = true)) {
            prepareStream(cinematicName)
            return
        }
        if (!cinematicName.endsWith(".pcx", ignoreCase = true)) {
            return
        }
        val imagePath = resolvePicturePath(cinematicName)
        if (assetManager.fileHandleResolver.tryResolveRaw(imagePath) == null) {
            Com.Warn("Cinematic image not found: $imagePath\n")
            return
        }
        staticImage = gameConfig.acquireAsset<Texture>(imagePath)
    }

    private fun prepareStream(cinematicName: String) {
        val streamPath = resolveStreamPath(cinematicName)
        val streamHandle = assetManager.fileHandleResolver.tryResolveRaw(streamPath)
        if (streamHandle == null || !streamHandle.exists()) {
            Com.Warn("Cinematic stream not found: $streamPath\n")
            autoAdvancePending = true
            return
        }

        val defaultPalette = (assetManager.get("q2palette.bin", Any::class.java) as? IntArray)
            ?.copyOf(PALETTE_COLOR_COUNT)
            ?: IntArray(PALETTE_COLOR_COUNT) { 0xFF }

        try {
            val decoder = CinematicCinDecoder(streamHandle.readBytes(), defaultPalette)
            prepareAudioDevice(decoder)
            val firstFrame = decoder.readNextFrame()
            if (firstFrame == null) {
                autoAdvancePending = true
                return
            }
            streamDecoder = decoder
            uploadFrame(
                width = decoder.width,
                height = decoder.height,
                indexedFrame = firstFrame.indexedFrame,
                palette = decoder.currentPalette(),
            ) || run {
                streamDecoder = null
                autoAdvancePending = true
                audioDevice?.dispose()
                audioDevice = null
                return
            }
            streamAudioSamples(
                pcmBytes = firstFrame.audioPcmBytes,
                sampleWidth = decoder.sampleWidth,
            )
        } catch (ex: RuntimeException) {
            Com.Warn("Failed to open cinematic stream $streamPath: ${ex.message}\n")
            autoAdvancePending = true
        }
    }

    /**
     * Advances `.cin` playback in legacy 14 FPS timebase and uploads latest decoded frame.
     *
     * Legacy cross-reference:
     * - q2pro `src/client/cin.c` `SCR_RunCinematic` frame stepping
     * - Jake2 `client/SCR.RunCinematic` dropped-frame catch-up
     */
    private fun advanceStream(currentTimeMs: Int) {
        val decoder = streamDecoder ?: return
        if (startTimeMs == Int.MIN_VALUE) {
            return
        }
        val elapsedMs = (currentTimeMs - startTimeMs).coerceAtLeast(0)
        val targetFrame = elapsedMs * CINEMATIC_FPS / 1000
        while (decoder.decodedFrameCount <= targetFrame) {
            val decodedFrame = decoder.readNextFrame() ?: run {
                streamDecoder = null
                autoAdvancePending = true
                audioDevice?.dispose()
                audioDevice = null
                return
            }
            uploadFrame(
                width = decoder.width,
                height = decoder.height,
                indexedFrame = decodedFrame.indexedFrame,
                palette = decoder.currentPalette(),
            ) || run {
                streamDecoder = null
                autoAdvancePending = true
                audioDevice?.dispose()
                audioDevice = null
                return
            }
            streamAudioSamples(
                pcmBytes = decodedFrame.audioPcmBytes,
                sampleWidth = decoder.sampleWidth,
            )
        }
    }

    private fun renderStaticImage(spriteBatch: SpriteBatch, screenWidth: Float, screenHeight: Float) {
        val image = frameTexture ?: staticImage ?: return
        val imageWidth = image.width.toFloat().coerceAtLeast(1f)
        val imageHeight = image.height.toFloat().coerceAtLeast(1f)
        val scale = minOf(screenWidth / imageWidth, screenHeight / imageHeight)
        val drawWidth = imageWidth * scale
        val drawHeight = imageHeight * scale
        val drawX = (screenWidth - drawWidth) * 0.5f
        val drawY = (screenHeight - drawHeight) * 0.5f

        spriteBatch.use {
            it.draw(image, drawX, drawY, drawWidth, drawHeight)
        }
    }

    private fun uploadFrame(
        width: Int,
        height: Int,
        indexedFrame: ByteArray,
        palette: IntArray,
    ): Boolean {
        if (indexedFrame.size < width * height) {
            Com.Warn("Cinematic frame is truncated (${indexedFrame.size} < ${width * height})\n")
            return false
        }
        val pixmap = ensureFramePixmap(width, height)
        var pixelOffset = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val paletteIndex = indexedFrame[pixelOffset++].toInt() and 0xFF
                pixmap.drawPixel(x, y, palette[paletteIndex])
            }
        }

        val existingTexture = frameTexture
        if (existingTexture == null || existingTexture.width != width || existingTexture.height != height) {
            existingTexture?.dispose()
            frameTexture = Texture(pixmap)
        } else {
            existingTexture.draw(pixmap, 0, 0)
        }
        return true
    }

    private fun ensureFramePixmap(width: Int, height: Int): Pixmap {
        val existing = framePixmap
        if (existing != null && existing.width == width && existing.height == height) {
            return existing
        }
        existing?.dispose()
        return Pixmap(width, height, Pixmap.Format.RGBA8888).also {
            framePixmap = it
        }
    }

    private fun streamAudioSamples(pcmBytes: ByteArray, sampleWidth: Int) {
        if (pcmBytes.isEmpty()) {
            return
        }
        val activeAudioDevice = audioDevice ?: return
        when (sampleWidth) {
            1 -> {
                // Unsigned 8-bit PCM -> signed 16-bit for libGDX AudioDevice.
                val samples = ShortArray(pcmBytes.size)
                for (i in pcmBytes.indices) {
                    samples[i] = (((pcmBytes[i].toInt() and 0xFF) - 128) shl 8).toShort()
                }
                activeAudioDevice.writeSamples(samples, 0, samples.size)
            }

            2 -> {
                val sampleCount = pcmBytes.size / 2
                if (sampleCount <= 0) {
                    return
                }
                val samples = ShortArray(sampleCount)
                var byteIndex = 0
                for (i in 0 until sampleCount) {
                    val lo = pcmBytes[byteIndex++].toInt() and 0xFF
                    val hi = pcmBytes[byteIndex++].toInt()
                    samples[i] = ((hi shl 8) or lo).toShort()
                }
                activeAudioDevice.writeSamples(samples, 0, samples.size)
            }

            else -> {
                Com.Warn("Unsupported cinematic audio sample width: $sampleWidth\n")
            }
        }
    }

    private fun prepareAudioDevice(decoder: CinematicCinDecoder) {
        audioDevice?.dispose()
        audioDevice = null
        if (decoder.sampleRate <= 0) {
            return
        }
        if (decoder.sampleChannels !in 1..2) {
            Com.Warn("Unsupported cinematic audio channels: ${decoder.sampleChannels}\n")
            return
        }
        audioDevice = Gdx.audio.newAudioDevice(decoder.sampleRate, decoder.sampleChannels == 1)
    }

    private fun resolvePicturePath(cinematicName: String): String {
        val normalized = cinematicName.trim().removePrefix("/")
        if (normalized.contains('/')) {
            return normalized
        }
        return "pics/$normalized"
    }

    private fun resolveStreamPath(cinematicName: String): String {
        val normalized = cinematicName.trim().removePrefix("/")
        if (normalized.contains('/')) {
            return normalized
        }
        return "video/$normalized"
    }

    private fun releaseMedia() {
        streamDecoder = null
        autoAdvancePending = false
        audioDevice?.dispose()
        audioDevice = null
        frameTexture?.dispose()
        frameTexture = null
        framePixmap?.dispose()
        framePixmap = null
        staticImage = null
    }

    private fun logDiagnostics(currentTimeMs: Int) {
        if (!RenderTuningCvars.bspBatchDebugEnabled()) {
            lastDebugLogTimeMs = Int.MIN_VALUE
            return
        }
        if (lastDebugLogTimeMs != Int.MIN_VALUE && currentTimeMs - lastDebugLogTimeMs < DEBUG_LOG_INTERVAL_MS) {
            return
        }
        lastDebugLogTimeMs = currentTimeMs
        val decoder = streamDecoder
        val elapsedMs = if (startTimeMs == Int.MIN_VALUE) 0 else (currentTimeMs - startTimeMs).coerceAtLeast(0)
        val targetFrame = elapsedMs * CINEMATIC_FPS / 1000
        val mediaKind = when {
            decoder != null -> "cin"
            frameTexture != null -> "stream-frame"
            staticImage != null -> "image"
            else -> "none"
        }
        Com.Printf(
            "cinematic_debug t=$currentTimeMs media=$mediaKind decoded=${decoder?.decodedFrameCount ?: 0} " +
                "target=$targetFrame auto=$autoAdvancePending skip=$skipSent audio=${audioDevice != null}\n"
        )
    }

    override fun dispose() {
        end()
    }

    private companion object {
        private const val CINEMATIC_FPS = 14
        private const val CINEMATIC_SKIP_DELAY_MS = 1000
        private const val PALETTE_COLOR_COUNT = 256
        private const val DEBUG_LOG_INTERVAL_MS = 1000
    }
}
