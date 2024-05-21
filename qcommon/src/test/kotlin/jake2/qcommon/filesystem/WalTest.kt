package jake2.qcommon.filesystem

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WalTest {
    @Test
    fun loadWal() {
        val wal = WAL(
            ByteBuffer.wrap(this::class.java.getResourceAsStream("dc1a.wal")!!.readBytes())
                .also { it.order(ByteOrder.LITTLE_ENDIAN) })

        assertEquals("e7bm",wal.name)
        assertEquals(128,wal.width)
        assertEquals(32,wal.height)
        assertArrayEquals(wal.offsets, intArrayOf(100, 4196, 5220, 5476))
        assertEquals(0, wal.flags)
        assertEquals(0, wal.contents)
        assertEquals(0, wal.value)
        assertEquals(128 * 32, wal.imageData.size)
    }
}