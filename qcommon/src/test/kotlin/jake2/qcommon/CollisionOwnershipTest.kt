package jake2.qcommon

import jake2.qcommon.filesystem.ResourceFormatException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Collision worlds report load and lookup failures directly and keep query scratch per world. */
class CollisionOwnershipTest {
    @Test
    fun `truncated map data fails with a format error`() {
        val failure = assertThrows(ResourceFormatException::class.java) {
            CM().CM_LoadMapFile(byteArrayOf(0), "broken.bsp", IntArray(1))
        }
        assertEquals("Truncated BSP header", failure.message)
    }

    @Test
    fun `optional and required lookups agree on which inline models exist`() {
        val cm = CM().apply { numcmodels = 2 }
        assertSame(cm.map_cmodels[1], cm.findInlineModel("*1"))
        assertSame(cm.map_cmodels[1], cm.InlineModel("*1"))
        for (reference in listOf(null, "", "maps/test.bsp", "*0", "*2")) {
            assertNull(cm.findInlineModel(reference))
            assertThrows(CollisionStateException::class.java) { cm.InlineModel(reference) }
        }
    }

    @Test
    fun `independent collision instances do not share box query scratch`() {
        val worlds = List(2) {
            CM().apply {
                map_nodes[0].plane = cplane_t().apply { normal[0] = 1f; type = 0 }
                map_nodes[0].children[0] = -1
                map_nodes[0].children[1] = -2
                map_cmodels[0].headnode = 0
            }
        }
        val start = CyclicBarrier(2)
        Executors.newFixedThreadPool(2).use { executor ->
            val tasks = worlds.mapIndexed { index, cm -> executor.submit {
                val box = floatArrayOf(if (index == 0) 10f else -10f, 0f, 0f)
                val leaves = IntArray(2)
                val top = IntArray(1)
                start.await(5, TimeUnit.SECONDS)
                repeat(10000) {
                    assertEquals(1, cm.CM_BoxLeafnums(box, box, leaves, leaves.size, top))
                    assertEquals(index, leaves[0])
                    assertEquals(-1, top[0])
                }
            } }
            tasks.forEach { it.get(10, TimeUnit.SECONDS) }
        }
    }
}
