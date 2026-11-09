package org.demoth.cake.stages.ingame

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WorldCameraRangeTest {
    @Test
    fun `Q2PRO thresholds use the largest root node dimension`() {
        for ((size, expected) in listOf(
            1024 to 4096f, 2048 to 4096f, 2049 to 8192f,
            4096 to 8192f, 4097 to 16384f, 30000 to 16384f,
        )) {
            for (axis in 0..2) {
                val min = Vector3(-1000f, -1000f, -1000f)
                val max = min.cpy().add(64f, 64f, 64f)
                when (axis) {
                    0 -> max.x = min.x + size
                    1 -> max.y = min.y + size
                    2 -> max.z = min.z + size
                }
                assertEquals(expected, worldCameraFar(BoundingBox(min, max)), "size=$size axis=$axis")
            }
        }
    }

    @Test
    fun `maps without nodes use the minimum distance`() {
        assertEquals(4096f, worldCameraFar(null))
    }
}
