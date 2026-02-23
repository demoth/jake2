package org.demoth.cake.stages.ingame

import jake2.qcommon.Globals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class Md2LightingTest {
    @Test
    fun quantizedYawMatchesLegacyBucketing() {
        assertEquals(0f, quantizeLegacyShadedotYawDegrees(0f), 0.0001f)
        assertEquals(0f, quantizeLegacyShadedotYawDegrees(22.49f), 0.0001f)
        assertEquals(22.5f, quantizeLegacyShadedotYawDegrees(22.5f), 0.0001f)
        assertEquals(337.5f, quantizeLegacyShadedotYawDegrees(-22.5f), 0.0001f)
    }

    @Test
    fun continuousModeDoesNotQuantizeYaw() {
        val a = computeMd2ShadeVector(10f, legacyQuantized = false)
        val b = computeMd2ShadeVector(11f, legacyQuantized = false)
        assertNotEquals(a.x, b.x)
    }

    @Test
    fun legacyShadedotResponseMatchesAnormtabSample() {
        // Legacy reference sample from anormtab row 0, index 0 is ~1.23.
        val shade = computeMd2ShadeVector(0f, legacyQuantized = true)
        val n = Globals.bytedirs[0]
        val l = n[0] * shade.x + n[1] * shade.y + n[2] * shade.z + 1f
        assertEquals(1.23f, l, 0.02f)
    }

    @Test
    fun shadeVectorIsNormalized() {
        val shade = computeMd2ShadeVector(137f, legacyQuantized = true)
        val len = kotlin.math.sqrt(shade.x * shade.x + shade.y * shade.y + shade.z * shade.z)
        assertEquals(1f, len, 0.0001f)
    }
}
