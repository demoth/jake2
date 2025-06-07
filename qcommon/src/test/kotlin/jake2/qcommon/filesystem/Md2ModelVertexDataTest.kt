package jake2.qcommon.filesystem

import jake2.qcommon.math.Vector3f
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Create a simple geometry to test transformation of md2 packed data into shader suitable format.
 * In this example there is a sinble square composed of two halfs:
 *  - bottom (triangle strip, grass texture)
 *  - top (triangle fan, wood texture).
 * The actual texture has the opposite layout: the grass is on the top and the wood is on the bottom.
 * This makes an interesting case, because the same vertices (same in a sense of the postitions)
 * have different texture coordinates when are part of different triangles.
 *
 * Initially we have 6 vertices in the md2 format. But since the vertices,
 * which are shared by different quads have different texture coordinates, we need to make new vertices instead.
 * In our test case, vertex attributes will look like this:
 *
 * Initial vertex attributes
 * index    u       v       shared
 * 0        0.0     0.5
 * 1        0.0     1.0     *
 * 2        0.0     0.5
 * 3        1.0     0.5
 * 4        1.0     1.0     *
 * 5        1.0     0.5
 *
 * vertices 1 and 4 are shared and have different uv in different quads, so need to create new ones instead
 *
 * 6(prev 1)0.0     0.0     *
 * 7(prev 4)1.0     0.0     *
 */
class Md2ModelVertexDataTest {

    fun createTestGlCmd(): List<Md2GlCmd> = listOf(
        // bottom quad - grass
        Md2GlCmd(
            Md2GlCmdType.TRIANGLE_STRIP,
            listOf(
                Md2VertexInfo(0, 0.0f, 0.5f),
                Md2VertexInfo(5, 1.0f, 0.5f),
                Md2VertexInfo(4, 1.0f, 1.0f),
                Md2VertexInfo(1, 0.0f, 1.0f),
            )
        ),
        // top quad - wood
        Md2GlCmd(
            Md2GlCmdType.TRIANGLE_FAN,
            listOf(
                Md2VertexInfo(1, 0.0f, 0.0f),
                Md2VertexInfo(4, 1.0f, 0.0f),
                Md2VertexInfo(3, 1.0f, 0.5f),
                Md2VertexInfo(2, 0.0f, 0.5f),
            )
        ),
    )

    // animate between square and hex shape
    fun createTestFrames(): List<Md2Frame> = listOf(
        Md2Frame(
            "square",
            listOf(
                Md2Point(Vector3f(0.0f, 0.0f, 0.0f), 0),
                Md2Point(Vector3f(0.0f, 0.5f, 0.0f), 0),
                Md2Point(Vector3f(0.0f, 1f, 0.0f), 0),
                Md2Point(Vector3f(1f, 1f, 0.0f), 0),
                Md2Point(Vector3f(1f, 0.5f, 0.0f), 0),
                Md2Point(Vector3f(1f, 0f, 0.0f), 0),
            )
        ),
        Md2Frame(
            "hex",
            listOf(
                Md2Point(Vector3f(0.25f, 0.0f, 0.0f), 0),
                Md2Point(Vector3f(0.0f, 0.5f, 0.0f), 0),
                Md2Point(Vector3f(0.25f, 1f, 0.0f), 0),
                Md2Point(Vector3f(0.75f, 1f, 0.0f), 0),
                Md2Point(Vector3f(1f, 0.5f, 0.0f), 0),
                Md2Point(Vector3f(0.75f, 0f, 0.0f), 0),
            )
        ),
    )

    @Test
    fun testSimpleVertexData() {
        val testGlCmds: List<Md2GlCmd> = createTestGlCmd()
        val testFrames: List<Md2Frame>  = createTestFrames()

        val actual = getVertexData(testGlCmds, testFrames)
        val expected = TODO()

        assertEquals(expected, actual)
    }
}
