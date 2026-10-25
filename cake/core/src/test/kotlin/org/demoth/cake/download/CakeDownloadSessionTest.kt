package org.demoth.cake.download

import jake2.qcommon.MainCommon
import jake2.qcommon.exec.Cvar
import jake2.qcommon.network.messages.server.DownloadMessage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class CakeDownloadSessionTest {
    @TempDir
    lateinit var temp: Path
    private val cvars = Cvar().also(MainCommon::registerDownloads)
    private val sent = mutableListOf<String>()

    private fun session() = CakeDownloadSession(
        sendReliable = { sent += it },
        downloadManager = CakeDownloadManager(CakeDownloadCache(temp), cvars),
    )

    @Test
    fun `partial transfer requests next block then retries precache after publishing file`() {
        val downloads = session()
        assertTrue(downloads.requestMissingResource("maps/base1.bsp", null, 42))
        assertEquals(listOf("download maps/base1.bsp"), sent)
        assertEquals(CakeDownloadSession.Result.Continue,
            downloads.handle(DownloadMessage(byteArrayOf(1, 2), 50)))
        assertEquals(listOf("download maps/base1.bsp", "nextdl"), sent)
        assertFalse(Files.exists(temp.resolve("baseq2/maps/base1.bsp")))

        assertEquals(CakeDownloadSession.Result.RetryPrecache(42),
            downloads.handle(DownloadMessage(byteArrayOf(3), 100)))
        assertArrayEquals(byteArrayOf(1, 2, 3), Files.readAllBytes(temp.resolve("baseq2/maps/base1.bsp")))
        assertFalse(Files.exists(temp.resolve("baseq2/maps/base1.tmp")))
        assertEquals(2, sent.size, "Precache is returned to the caller, not sent to the server")
        downloads.precacheCompleted()
        assertEquals(CakeDownloadSession.Result.Continue, downloads.handle(DownloadMessage(byteArrayOf(4), 100)))
        assertArrayEquals(byteArrayOf(1, 2, 3), Files.readAllBytes(temp.resolve("baseq2/maps/base1.bsp")))
    }

    @Test
    fun `resume command includes partial file offset and respects mod cache`() {
        val partial = temp.resolve("rogue/maps/base1.tmp")
        Files.createDirectories(partial.parent)
        Files.write(partial, byteArrayOf(1, 2, 3))
        val downloads = session()

        downloads.requestMissingResource("maps/base1.bsp", "rogue", 7)
        assertEquals(listOf("download maps/base1.bsp 3"), sent)
        assertEquals(CakeDownloadSession.Result.RetryPrecache(7),
            downloads.handle(DownloadMessage(byteArrayOf(4), 100)))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), Files.readAllBytes(temp.resolve("rogue/maps/base1.bsp")))
        assertFalse(Files.exists(temp.resolve("baseq2/maps/base1.bsp")))
    }

    @Test
    fun `queued downloads drain before retry and pending requests are deduplicated`() {
        val downloads = session()
        downloads.requestMissingResource("maps/base1.bsp", null, 3)
        downloads.requestMissingResource("sound/world/door.wav", null, 3)
        assertTrue(downloads.requestMissingResource("sound/world/door.wav", null, 3))
        assertEquals(1, sent.size)

        assertEquals(CakeDownloadSession.Result.Continue,
            downloads.handle(DownloadMessage(byteArrayOf(1), 100)))
        assertEquals(listOf("download maps/base1.bsp", "download sound/world/door.wav"), sent)
        assertEquals(CakeDownloadSession.Result.RetryPrecache(3),
            downloads.handle(DownloadMessage(byteArrayOf(2), 100)))
        assertEquals(2, sent.size)
    }

    @Test
    fun `reset cancels active and queued work but retains partial file for reconnect`() {
        val downloads = session()
        downloads.requestMissingResource("maps/base1.bsp", null, 1)
        downloads.handle(DownloadMessage(byteArrayOf(1, 2), 50))
        downloads.requestMissingResource("sound/world/door.wav", null, 1)
        downloads.reset()
        sent.clear()

        assertEquals(CakeDownloadSession.Result.Continue,
            downloads.handle(DownloadMessage(byteArrayOf(9), 100)))
        assertTrue(sent.isEmpty())
        assertArrayEquals(byteArrayOf(1, 2), Files.readAllBytes(temp.resolve("baseq2/maps/base1.tmp")))
        downloads.requestMissingResource("maps/base1.bsp", null, 99)
        assertEquals(listOf("download maps/base1.bsp 2"), sent)
        assertEquals(CakeDownloadSession.Result.RetryPrecache(99),
            downloads.handle(DownloadMessage(byteArrayOf(3), 100)))
        assertEquals(1, sent.size, "Reset must discard the old queued sound")
    }

    @Test
    fun `missing server file returns failure without starting queued work or retrying`() {
        val downloads = session()
        downloads.requestMissingResource("maps/base1.bsp", null, 5)
        downloads.requestMissingResource("sound/world/door.wav", null, 5)
        assertEquals(CakeDownloadSession.Result.MissingOnServer, downloads.handle(DownloadMessage()))
        assertEquals(listOf("download maps/base1.bsp"), sent)
        // Cake responds to failure by disconnecting, which resets this session.
        downloads.reset()
        assertEquals(CakeDownloadSession.Result.Continue, downloads.handle(DownloadMessage()))
        assertFalse(Files.exists(temp.resolve("baseq2/maps/base1.bsp")))
    }

    @Test
    fun `rejected resource requests send no reliable commands`() {
        val downloads = session()
        assertFalse(downloads.requestMissingResource("../maps/base1.bsp", null, 1))
        assertFalse(downloads.requestMissingResource("pics/status.pcx", null, 1))
        cvars.setResult("allow_download_maps", "0")
        assertFalse(downloads.requestMissingResource("maps/base1.bsp", null, 1))
        cvars.setResult("allow_download", "0")
        assertFalse(downloads.requestMissingResource("sound/world/door.wav", null, 1))
        assertTrue(sent.isEmpty())
        assertEquals(CakeDownloadSession.Result.Continue, downloads.handle(DownloadMessage()))
    }

    @Test
    fun `successful precache clears retained retry token`() {
        val downloads = session()
        downloads.requestMissingResource("maps/base1.bsp", null, 10)
        downloads.precacheCompleted()
        assertEquals(CakeDownloadSession.Result.Continue,
            downloads.handle(DownloadMessage(byteArrayOf(1), 100)))
    }
}
