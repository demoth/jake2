package org.demoth.cake.assets

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.AssetDescriptor
import com.badlogic.gdx.assets.AssetLoaderParameters
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader
import com.badlogic.gdx.assets.loaders.FileHandleResolver
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.Array
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * Sound loader that transparently converts PCM 8-bit WAV files to PCM 16-bit WAV.
 *
 * LibGDX LWJGL3 backend rejects 8-bit WAV files. This loader keeps the normal
 * `Gdx.audio.newSound` path and only rewrites WAV bytes when needed.
 */
// todo: check on real sound files
class ConvertingSoundLoader(resolver: FileHandleResolver) :
    AsynchronousAssetLoader<Sound, ConvertingSoundLoader.Parameters>(resolver) {

    class Parameters : AssetLoaderParameters<Sound>()

    private var cache: Sound? = null

    override fun loadAsync(
        manager: AssetManager,
        fileName: String,
        file: FileHandle,
        parameter: Parameters?
    ) {
        cache = null
        val convertedTemp = maybeConvert8BitWav(file)
        val source = convertedTemp ?: file
        cache = try {
            val loaded = Gdx.audio.newSound(source)
            if (convertedTemp != null) {
                TempFileBackedSound(loaded, convertedTemp)
            } else {
                loaded
            }
        } catch (e: Throwable) {
            if (convertedTemp != null && convertedTemp.exists()) {
                convertedTemp.delete()
            }
            throw e
        }
    }

    override fun loadSync(
        manager: AssetManager,
        fileName: String,
        file: FileHandle,
        parameter: Parameters?
    ): Sound {
        cache?.let {
            cache = null
            return it
        }

        val convertedTemp = maybeConvert8BitWav(file)
        val source = convertedTemp ?: file
        return try {
            val loaded = Gdx.audio.newSound(source)
            if (convertedTemp != null) TempFileBackedSound(loaded, convertedTemp) else loaded
        } catch (e: Throwable) {
            if (convertedTemp != null && convertedTemp.exists()) {
                convertedTemp.delete()
            }
            throw e
        }
    }

    override fun getDependencies(
        fileName: String,
        file: FileHandle,
        parameter: Parameters?
    ): Array<AssetDescriptor<*>>? = null

    private fun maybeConvert8BitWav(file: FileHandle): FileHandle? {
        if (!file.extension().equals("wav", ignoreCase = true)) {
            return null
        }
        val converted = WavPcm8To16Converter.convertIfNeeded(file.readBytes()) ?: return null
        val tempPath = Files.createTempFile("jake2-snd-", ".wav")
        tempPath.toFile().deleteOnExit()
        val tempHandle = FileHandle(tempPath.toFile())
        tempHandle.writeBytes(converted, false)
        return tempHandle
    }

    private class TempFileBackedSound(
        private val delegate: Sound,
        private val tempFile: FileHandle
    ) : Sound by delegate {
        override fun dispose() {
            delegate.dispose()
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }
}

internal object WavPcm8To16Converter {
    private data class WavMeta(
        val formatTag: Int,
        val channels: Int,
        val sampleRate: Int,
        val bitsPerSample: Int,
        val dataOffset: Int,
        val dataLength: Int,
    )

    fun convertIfNeeded(wavBytes: ByteArray): ByteArray? {
        val meta = parse(wavBytes) ?: return null
        if (meta.formatTag != 1) return null // PCM only.
        if (meta.bitsPerSample != 8) return null
        if (meta.channels !in 1..2) return null

        val dataEnd = (meta.dataOffset + meta.dataLength).coerceAtMost(wavBytes.size)
        if (meta.dataOffset >= dataEnd) return null
        val sourceData = wavBytes.copyOfRange(meta.dataOffset, dataEnd)
        val convertedPcm = ByteArray(sourceData.size * 2)

        var out = 0
        sourceData.forEach { sampleByte ->
            val unsignedSample = sampleByte.toInt() and 0xFF
            val signed16 = (unsignedSample - 128) shl 8
            convertedPcm[out++] = (signed16 and 0xFF).toByte()
            convertedPcm[out++] = ((signed16 ushr 8) and 0xFF).toByte()
        }
        return buildPcm16Wav(convertedPcm, meta.channels, meta.sampleRate)
    }

    private fun parse(bytes: ByteArray): WavMeta? {
        if (bytes.size < 12) return null
        if (!isChunkId(bytes, 0, "RIFF")) return null
        if (!isChunkId(bytes, 8, "WAVE")) return null

        var offset = 12
        var formatTag = -1
        var channels = -1
        var sampleRate = -1
        var bitsPerSample = -1
        var dataOffset = -1
        var dataLength = -1

        while (offset + 8 <= bytes.size) {
            val chunkSize = readLE32(bytes, offset + 4)
            if (chunkSize < 0) return null

            val payloadOffset = offset + 8
            if (payloadOffset > bytes.size) return null
            val available = bytes.size - payloadOffset
            val payloadSize = chunkSize.coerceAtMost(available)

            if (isChunkId(bytes, offset, "fmt ")) {
                if (payloadSize >= 16) {
                    formatTag = readLE16(bytes, payloadOffset)
                    channels = readLE16(bytes, payloadOffset + 2)
                    sampleRate = readLE32(bytes, payloadOffset + 4)
                    bitsPerSample = readLE16(bytes, payloadOffset + 14)
                }
            } else if (isChunkId(bytes, offset, "data")) {
                dataOffset = payloadOffset
                dataLength = payloadSize
                break
            }

            val paddedSize = chunkSize + (chunkSize and 1)
            val next = payloadOffset + paddedSize
            if (next <= offset) return null
            offset = next.coerceAtMost(bytes.size)
        }

        if (formatTag == -1 || channels == -1 || sampleRate <= 0 || bitsPerSample == -1) return null
        if (dataOffset < 0 || dataLength < 0) return null
        return WavMeta(formatTag, channels, sampleRate, bitsPerSample, dataOffset, dataLength)
    }

    private fun buildPcm16Wav(pcm16Data: ByteArray, channels: Int, sampleRate: Int): ByteArray {
        val fmtChunkSize = 16
        val dataChunkSize = pcm16Data.size
        val riffChunkSize = 4 + (8 + fmtChunkSize) + (8 + dataChunkSize)
        val out = ByteArray(44 + dataChunkSize)

        writeChunkId(out, 0, "RIFF")
        writeLE32(out, 4, riffChunkSize)
        writeChunkId(out, 8, "WAVE")

        writeChunkId(out, 12, "fmt ")
        writeLE32(out, 16, fmtChunkSize)
        writeLE16(out, 20, 1) // PCM
        writeLE16(out, 22, channels)
        writeLE32(out, 24, sampleRate)
        writeLE32(out, 28, sampleRate * channels * 2) // byte rate
        writeLE16(out, 32, channels * 2) // block align
        writeLE16(out, 34, 16)

        writeChunkId(out, 36, "data")
        writeLE32(out, 40, dataChunkSize)
        System.arraycopy(pcm16Data, 0, out, 44, dataChunkSize)
        return out
    }

    private fun isChunkId(bytes: ByteArray, offset: Int, id: String): Boolean {
        if (offset < 0 || offset + 4 > bytes.size) return false
        return bytes[offset] == id[0].code.toByte()
            && bytes[offset + 1] == id[1].code.toByte()
            && bytes[offset + 2] == id[2].code.toByte()
            && bytes[offset + 3] == id[3].code.toByte()
    }

    private fun writeChunkId(bytes: ByteArray, offset: Int, id: String) {
        val idBytes = id.toByteArray(StandardCharsets.US_ASCII)
        bytes[offset] = idBytes[0]
        bytes[offset + 1] = idBytes[1]
        bytes[offset + 2] = idBytes[2]
        bytes[offset + 3] = idBytes[3]
    }

    private fun readLE16(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun readLE32(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun writeLE16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }

    private fun writeLE32(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        bytes[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        bytes[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }
}
