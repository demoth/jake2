package org.demoth.cake.download

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.file.Path

class CakeDownloadCacheTest {
    @Test
    fun `uses baseq2 when gamemod is absent`() {
        val cache = CakeDownloadCache(Path.of("/tmp/cake-downloads"))

        assertEquals(Path.of("/tmp/cake-downloads/baseq2"), cache.resolveRoot(null))
        assertEquals(Path.of("/tmp/cake-downloads/baseq2"), cache.resolveRoot("   "))
    }

    @Test
    fun `uses gamemod directory when provided`() {
        val cache = CakeDownloadCache(Path.of("/tmp/cake-downloads"))

        assertEquals(Path.of("/tmp/cake-downloads/xatrix"), cache.resolveRoot("xatrix"))
    }

    @Test
    fun `rejects unsafe gamemod token`() {
        val cache = CakeDownloadCache(Path.of("/tmp/cake-downloads"))

        assertThrows(IllegalArgumentException::class.java) {
            cache.resolveRoot("../rogue")
        }
    }
}
