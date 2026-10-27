package jake2.qcommon

import jake2.qcommon.exec.Cmd
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CollisionOwnershipTest {
    @Test
    fun `owner policy propagates its exception`() {
        val failure = IllegalArgumentException("owner failure")
        val cm = CM { severity, message ->
            assertEquals(Defines.ERR_DROP, severity)
            assertTrue(message.contains("Couldn't load"))
            failure
        }
        assertSame(failure, assertThrows(IllegalArgumentException::class.java) {
            cm.CM_LoadMapFile(null, "missing", intArrayOf(0))
        })
    }

    @Test
    fun `default policy preserves legacy drop dispatch`() {
        val names = listOf("sv_shutdown", "cl_drop")
        val previous = names.associateWith { Cmd.FindCommand(it) }
        val called = mutableListOf<String>()
        try {
            names.forEach { name -> Cmd.AddCommand(name, true) { called.add(name) } }
            assertThrows(longjmpException::class.java) { CM().InlineModel("invalid") }
            assertEquals(names, called)
        } finally {
            names.forEach { name ->
                Cmd.RemoveCommand(name)
                previous[name]?.let { Cmd.AddCommand(name, it.function) }
            }
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
