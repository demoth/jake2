package org.demoth.cake

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NetworkDebugSamplerTest {
    @Test
    fun reportsRollingPerSecondRates() {
        val sampler = NetworkDebugSampler()

        sampler.recordInbound(500, 100)
        sampler.recordInbound(500, 400)
        sampler.recordOutbound(300, 400)
        sampler.recordDownload(2048, 700)

        val snapshot = sampler.snapshot(900, pingMs = 42)

        assertEquals(1000, snapshot.inBytesPerSec)
        assertEquals(300, snapshot.outBytesPerSec)
        assertEquals(42, snapshot.pingMs)
        assertEquals(2, snapshot.downloadKilobytesPerSec)
    }

    @Test
    fun dropsSamplesOlderThanOneSecond() {
        val sampler = NetworkDebugSampler()

        sampler.recordInbound(500, 0)
        sampler.recordInbound(700, 1200)

        val snapshot = sampler.snapshot(1200, pingMs = 0)

        assertEquals(700, snapshot.inBytesPerSec)
    }
}
