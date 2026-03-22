package org.demoth.cake.download

import jake2.qcommon.network.messages.server.DownloadMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class CakeDownloadTransferTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun `begins from zero when temp file is missing`() {
        val transfer = CakeDownloadTransfer()
        val request = request("maps/base1.bsp")

        val offset = transfer.begin(request)

        assertEquals(0L, offset)
        assertEquals(request, transfer.activeRequest())
    }

    @Test
    fun `resumes from existing temp file size`() {
        val transfer = CakeDownloadTransfer()
        val request = request("maps/base1.bsp")
        Files.createDirectories(request.tempPath.parent)
        Files.write(request.tempPath, byteArrayOf(1, 2, 3))

        val offset = transfer.begin(request)

        assertEquals(3L, offset)
    }

    @Test
    fun `appends partial chunks and keeps temp file`() {
        val transfer = CakeDownloadTransfer()
        val request = request("maps/base1.bsp")
        transfer.begin(request)

        val result = transfer.handle(DownloadMessage(byteArrayOf(1, 2), 25))

        val continued = assertInstanceOf(CakeDownloadTransferResult.Continue::class.java, result)
        assertEquals(25, continued.percent)
        assertTrue(Files.isRegularFile(request.tempPath))
        assertFalse(Files.exists(request.finalPath))
        assertEquals(listOf<Byte>(1, 2), Files.readAllBytes(request.tempPath).toList())
    }

    @Test
    fun `completes by renaming temp file to final path`() {
        val transfer = CakeDownloadTransfer()
        val request = request("models/items/armor/tris.md2")
        transfer.begin(request)
        transfer.handle(DownloadMessage(byteArrayOf(1, 2), 50))

        val result = transfer.handle(DownloadMessage(byteArrayOf(3, 4), 100.toByte()))

        assertInstanceOf(CakeDownloadTransferResult.Completed::class.java, result)
        assertTrue(Files.isRegularFile(request.finalPath))
        assertFalse(Files.exists(request.tempPath))
        assertEquals(listOf<Byte>(1, 2, 3, 4), Files.readAllBytes(request.finalPath).toList())
        assertNull(transfer.activeRequest())
    }

    @Test
    fun `reports missing file when server has no download`() {
        val transfer = CakeDownloadTransfer()
        val request = request("sound/world/doors.wav")
        transfer.begin(request)

        val result = transfer.handle(DownloadMessage())

        assertInstanceOf(CakeDownloadTransferResult.MissingOnServer::class.java, result)
        assertNull(transfer.activeRequest())
    }

    private fun request(logicalPath: String): CakeDownloadRequest {
        return CakeDownloadRequest(
            gameDirectory = "baseq2",
            logicalPath = logicalPath,
            category = CakeDownloadManager.categoryFor(logicalPath)!!,
            finalPath = temp.resolve(logicalPath),
            tempPath = temp.resolve(CakeDownloadManager.tempLogicalPath(logicalPath)),
        )
    }
}
