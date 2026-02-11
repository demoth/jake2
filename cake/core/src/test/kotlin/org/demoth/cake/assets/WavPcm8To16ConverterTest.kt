package org.demoth.cake.assets

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WavPcm8To16ConverterTest {

    @Test
    fun converts8BitPcmTo16BitPcmWav() {
        val wav8 = createPcmWav(
            bitsPerSample = 8,
            channels = 1,
            sampleRate = 22050,
            pcmData = byteArrayOf(0x00, 0x80.toByte(), 0xFF.toByte())
        )

        val converted = WavPcm8To16Converter.convertIfNeeded(wav8)

        assertNotNull(converted)
        val wav16 = converted!!
        assertEquals(16, readLE16(wav16, 34))
        assertEquals(1, readLE16(wav16, 22))
        assertEquals(22050, readLE32(wav16, 24))
        assertEquals(6, readLE32(wav16, 40))
        assertArrayEquals(
            byteArrayOf(
                0x00, 0x80.toByte(), // -32768
                0x00, 0x00, // 0
                0x00, 0x7F // 32512
            ),
            wav16.copyOfRange(44, 50)
        )
    }

    @Test
    fun leaves16BitPcmUnchanged() {
        val wav16 = createPcmWav(
            bitsPerSample = 16,
            channels = 1,
            sampleRate = 11025,
            pcmData = byteArrayOf(0x00, 0x00, 0x7F, 0x00)
        )

        val converted = WavPcm8To16Converter.convertIfNeeded(wav16)

        assertNull(converted)
    }

    @Test
    fun rejectsNonWavData() {
        val converted = WavPcm8To16Converter.convertIfNeeded("not-a-wav".toByteArray())
        assertNull(converted)
    }

    private fun createPcmWav(
        bitsPerSample: Int,
        channels: Int,
        sampleRate: Int,
        pcmData: ByteArray
    ): ByteArray {
        val fmtChunkSize = 16
        val riffChunkSize = 4 + (8 + fmtChunkSize) + (8 + pcmData.size)
        val wav = ByteArray(44 + pcmData.size)

        writeAscii(wav, 0, "RIFF")
        writeLE32(wav, 4, riffChunkSize)
        writeAscii(wav, 8, "WAVE")

        writeAscii(wav, 12, "fmt ")
        writeLE32(wav, 16, fmtChunkSize)
        writeLE16(wav, 20, 1) // PCM
        writeLE16(wav, 22, channels)
        writeLE32(wav, 24, sampleRate)
        writeLE32(wav, 28, sampleRate * channels * (bitsPerSample / 8))
        writeLE16(wav, 32, channels * (bitsPerSample / 8))
        writeLE16(wav, 34, bitsPerSample)

        writeAscii(wav, 36, "data")
        writeLE32(wav, 40, pcmData.size)
        System.arraycopy(pcmData, 0, wav, 44, pcmData.size)
        return wav
    }

    private fun writeAscii(bytes: ByteArray, offset: Int, text: String) {
        bytes[offset] = text[0].code.toByte()
        bytes[offset + 1] = text[1].code.toByte()
        bytes[offset + 2] = text[2].code.toByte()
        bytes[offset + 3] = text[3].code.toByte()
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

    private fun readLE16(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun readLE32(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }
}
