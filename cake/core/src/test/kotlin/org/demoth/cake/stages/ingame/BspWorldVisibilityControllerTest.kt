package org.demoth.cake.stages.ingame

import org.demoth.cake.assets.BspWorldLeafRecord
import org.demoth.cake.assets.BspWorldRenderData
import org.demoth.cake.assets.BspWorldSurfaceRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class BspWorldVisibilityControllerTest {

    @Test
    fun computeVisibleSurfaceMaskUsesPvsAndAreaBits() {
        val world = BspWorldRenderData(
            surfaces = listOf(
                surface(10, "s0"),
                surface(11, "s1"),
                surface(12, "s2"),
            ),
            leaves = listOf(
                // Visible in PVS + area
                BspWorldLeafRecord(leafIndex = 0, cluster = 1, area = 2, surfaceIndices = intArrayOf(0, 1)),
                // Visible in PVS but blocked by areabits
                BspWorldLeafRecord(leafIndex = 1, cluster = 3, area = 6, surfaceIndices = intArrayOf(2)),
                // Not in PVS
                BspWorldLeafRecord(leafIndex = 2, cluster = 7, area = 2, surfaceIndices = intArrayOf(2)),
            )
        )

        val pvsBits = bitsWithSet(1, 3)
        val areaBits = bitsWithSet(2)
        val mask = computeVisibleSurfaceMask(world, pvsBits, areaBits)

        assertEquals(listOf(true, true, false), mask.toList())
    }

    @Test
    fun computeVisibleSurfaceMaskIgnoresAreaGateWhenAreaBitsEmpty() {
        val world = BspWorldRenderData(
            surfaces = listOf(surface(20, "s0")),
            leaves = listOf(
                BspWorldLeafRecord(leafIndex = 0, cluster = 4, area = 42, surfaceIndices = intArrayOf(0))
            )
        )

        val mask = computeVisibleSurfaceMask(
            worldRenderData = world,
            pvsBits = bitsWithSet(4),
            areaBits = byteArrayOf(), // no areabits payload yet
        )

        assertEquals(listOf(true), mask.toList())
    }

    private fun bitsWithSet(vararg setBits: Int): ByteArray {
        if (setBits.isEmpty()) {
            return byteArrayOf()
        }
        val maxBit = setBits.max()
        val bits = ByteArray((maxBit ushr 3) + 1)
        setBits.forEach { bit ->
            bits[bit ushr 3] = (bits[bit ushr 3].toInt() or (1 shl (bit and 7))).toByte()
        }
        return bits
    }

    private fun surface(face: Int, id: String) = BspWorldSurfaceRecord(
        faceIndex = face,
        meshPartId = id,
        textureInfoIndex = 0,
        textureName = "test",
        textureFlags = 0,
        textureAnimationNext = -1,
        lightMapStyles = byteArrayOf(0, 0, 0, 0),
        lightMapOffset = 0
    )
}
