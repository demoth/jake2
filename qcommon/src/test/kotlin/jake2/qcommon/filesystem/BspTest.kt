package jake2.qcommon.filesystem

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

class BspTest {
    @Test
    fun loadTestBoxBsp() {
        val map = Bsp(ByteBuffer.wrap(this.javaClass.getResourceAsStream("maps/testbox.bsp")!!.readAllBytes()))
        val expectedEntities = String(this.javaClass.getResourceAsStream("maps/testbox.ent")!!.readAllBytes())
        assertEquals(expectedEntities, map.entities)
    }
}