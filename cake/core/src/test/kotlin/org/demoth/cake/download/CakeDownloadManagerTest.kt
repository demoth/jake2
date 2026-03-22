package org.demoth.cake.download

import jake2.qcommon.exec.Cvar
import org.demoth.cake.CakeCvars
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.nio.file.Path

class CakeDownloadManagerTest {
    private fun newManager(): CakeDownloadManager {
        Cvar.Init()
        CakeCvars.registerAll()
        return CakeDownloadManager(cache = CakeDownloadCache(Path.of("/tmp/cake-downloads")))
    }

    @Test
    fun `enqueues valid map download into baseq2 cache`() {
        val manager = newManager()

        val result = manager.enqueue("maps/base1.bsp", null)

        val enqueued = assertInstanceOf(CakeDownloadQueueResult.Enqueued::class.java, result)
        assertEquals(CakeDownloadCategory.MAPS, enqueued.request.category)
        assertEquals(Path.of("/tmp/cake-downloads/baseq2/maps/base1.bsp"), enqueued.request.finalPath)
        assertEquals(Path.of("/tmp/cake-downloads/baseq2/maps/base1.tmp"), enqueued.request.tempPath)
        assertEquals(1, manager.pendingCount())
    }

    @Test
    fun `rejects invalid download path`() {
        val manager = newManager()

        val result = manager.enqueue("../maps/base1.bsp", null)

        assertEquals(
            CakeDownloadQueueResult.Rejected(CakeDownloadRejectReason.INVALID_PATH),
            result,
        )
    }

    @Test
    fun `rejects unsupported category`() {
        val manager = newManager()

        val result = manager.enqueue("pics/status.pcx", null)

        assertEquals(
            CakeDownloadQueueResult.Rejected(CakeDownloadRejectReason.UNSUPPORTED_CATEGORY),
            result,
        )
    }

    @Test
    fun `rejects category disabled by cvar`() {
        val manager = newManager()
        Cvar.getInstance().Set("allow_download_players", "0")

        val result = manager.enqueue("players/male/tris.md2", null)

        assertEquals(
            CakeDownloadQueueResult.Rejected(CakeDownloadRejectReason.CATEGORY_DISABLED),
            result,
        )
    }

    @Test
    fun `deduplicates queued downloads`() {
        val manager = newManager()

        val first = manager.enqueue("maps/base1.bsp", "xatrix")
        val second = manager.enqueue("maps/base1.bsp", "xatrix")

        assertInstanceOf(CakeDownloadQueueResult.Enqueued::class.java, first)
        assertEquals(
            CakeDownloadQueueResult.Rejected(CakeDownloadRejectReason.ALREADY_QUEUED),
            second,
        )
        assertEquals(1, manager.pendingCount())
    }
}
