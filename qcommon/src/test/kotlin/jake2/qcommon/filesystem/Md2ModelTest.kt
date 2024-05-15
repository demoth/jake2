package jake2.qcommon.filesystem

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Md2ModelTest {

    @Test
    fun testLoadModel() {
        val fileName = "models/blade/tris.md2"
        val bytes: ByteArray = this::class.java.getResourceAsStream(fileName)?.readAllBytes()
            ?: throw AssertionError("Could not load $fileName")
        val buffer = ByteBuffer.wrap(bytes)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        val model = Md2Model(buffer)

        assertEquals(listOf("players/tekk-blade/blade.pcx", "players/tekk-blade/blograde.pcx"), model.skinNames)
        assertEquals(268, model.glCommands.size)
        assertEquals(200, model.frames.size)

        val frame = model.frames.first()
        assertEquals("stand0", frame.name)
        assertEquals(model.verticesCount, frame.points.size)
    }
}
