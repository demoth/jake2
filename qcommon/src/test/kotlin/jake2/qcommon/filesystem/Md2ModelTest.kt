package jake2.qcommon.filesystem

import jake2.qcommon.filesystem.qfiles.*
import org.junit.Assert.assertArrayEquals
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

        val model = Md2Model(buffer, fileName)

        assertEquals(Md2Model.ALIAS_VERSION, model.version)
        assertEquals(Md2Model.IDALIASHEADER, model.ident)
        assertArrayEquals(arrayOf("players/tekk-blade/blade.pcx", "players/tekk-blade/blograde.pcx"), model.skinNames)
        assertEquals(4145, model.glCmds.size)
        assertEquals(200, model.frames.size)
        assertEquals(429, model.num_vertices)

        val frame = model.frames.first()
        assertEquals("stand0", frame.name)
        assertEquals(model.num_vertices, frame.verts.size)
    }
}
