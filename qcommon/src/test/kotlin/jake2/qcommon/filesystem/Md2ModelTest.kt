package jake2.qcommon.filesystem

import jake2.qcommon.math.Vector3f
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

    @Test
    fun testTriangleFan() {
        val points = arrayListOf(
            Md2Point(Vector3f(0.1f, 1.1f, 2.1f), 1),
            Md2Point(Vector3f(0.2f, 1.2f, 2.2f), 2),
            Md2Point(Vector3f(0.3f, 1.3f, 2.3f), 3),
            Md2Point(Vector3f(0.4f, 1.4f, 2.4f), 4),
        )

        val vertices = arrayListOf(
            Md2VertexInfo(0, 3.1f, 4.1f),
            Md2VertexInfo(1, 3.2f, 4.2f),
            Md2VertexInfo(2, 3.3f, 4.3f),
            Md2VertexInfo(3, 3.4f, 4.4f),
        )
        val command = Md2GlCmd(Md2GlCmdType.TRIANGLE_FAN, vertices)
        val actual = command.toFloats(points)
        val expected = listOf(
            0.3f, 1.3f, 2.3f, 3.3f, 4.3f,
            0.2f, 1.2f, 2.2f, 3.2f, 4.2f,
            0.1f, 1.1f, 2.1f, 3.1f, 4.1f,
            0.4f, 1.4f, 2.4f, 3.4f, 4.4f,
            0.3f, 1.3f, 2.3f, 3.3f, 4.3f,
            0.1f, 1.1f, 2.1f, 3.1f, 4.1f
        )
        assertEquals(expected, actual)
    }

    @Test
    fun testTriangleStrip() {
        val points = arrayListOf(
            Md2Point(Vector3f(0.1f, 1.1f, 2.1f), 1),
            Md2Point(Vector3f(0.2f, 1.2f, 2.2f), 2),
            Md2Point(Vector3f(0.3f, 1.3f, 2.3f), 3),
            Md2Point(Vector3f(0.4f, 1.4f, 2.4f), 4),
        )

        val vertices = arrayListOf(
            Md2VertexInfo(0, 3.1f, 4.1f),
            Md2VertexInfo(1, 3.2f, 4.2f),
            Md2VertexInfo(2, 3.3f, 4.3f),
            Md2VertexInfo(3, 3.4f, 4.4f),
        )
        val command = Md2GlCmd(Md2GlCmdType.TRIANGLE_STRIP, vertices)
        val actual = command.toFloats(points)
        val expected = listOf(
            0.3f, 1.3f, 2.3f, 3.3f, 4.3f,
            0.2f, 1.2f, 2.2f, 3.2f, 4.2f,
            0.1f, 1.1f, 2.1f, 3.1f, 4.1f,
            0.2f, 1.2f, 2.2f, 3.2f, 4.2f,
            0.3f, 1.3f, 2.3f, 3.3f, 4.3f,
            0.4f, 1.4f, 2.4f, 3.4f, 4.4f)
        assertEquals(expected, actual)
    }
}
