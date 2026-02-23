package jake2.qcommon.filesystem

import jake2.qcommon.math.Vector3f
import junit.framework.TestCase.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Test

/**
 * Create a simple geometry to test transformation of md2 packed data into shader-suitable format.
 * In this example there is a single square composed of two halves:
 *  - bottom (triangle strip, grass texture)
 *  - top (triangle fan, wood texture).
 * The actual texture has the opposite layout: the grass is on the top, and the wood is on the bottom.
 * This makes an interesting case because the same vertices (same in the sense of the positions)
 * have different texture coordinates when are part of different triangles.
 *
 *
 * This is how I image the model (vertex indices are inside, vertex positions are outside)
 *     0,1                       1,1
 *       ┌──────────────────────┐
 *       │2                    3│
 *       │     wood texture     │
 *    Y  │1                    4│
 *  0,0.5┼──────────────────────┼1,0.5
 *       │      grass texture   │
 *       │0                    5│
 *       └──────────────────────┘
 *      0,0         X            1,0
 *
 * This is how the texture image looks
 *     0,1┌──────────┐1,1
 *        │  grass   │
 *        │  texture │
 *   0,0.5┼──────────┼1,0.5
 *     Y  │  wood    │
 *        │  texture │
 *        └──────────┘
 *     0,0     X      1,0
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
 * Vertices 1 and 4 are shared and have different uv in different quads, so need to create new ones instead.
 *
 * 6(prev 1*)0.0     0.0     *
 * 7(prev 4*)1.0     0.0     *
 *
 * Vertices are re-indexed as they are used in the gl commands.
 * Re-indexed vertices will look like:
 * new index, old index, u  v
 *  0       0        0.0     0.5
 *  1       5        1.0     0.5
 *  2       4        1.0     1.0
 *  3       1        0.0     1.0
 *  4       1*       0.0     0.0
 *  5       4*       1.0     0.0
 *  6       3        1.0     0.5
 *  7       2        0.0     0.5
 *
 *  New index is just going incrementally (we always draw all vertices in a model),
 *  old index is used to locate the vertex position in the frame data (and VAT).
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

        val actual = buildVertexData(testGlCmds, testFrames)

        // these values are taken from the example in the Javadoc (strip and fan unpacked)
        val expectedVertexAttributes = floatArrayOf(
            0.0f, 0.0f, 0.5f,
            4.0f, 1.0f, 1.0f,
            5.0f, 1.0f, 0.5f,

            4.0f, 1.0f, 1.0f,
            1.0f, 0.0f, 1.0f,
            5.0f, 1.0f, 0.5f,

            1.0f, 0.0f, 0.0f,
            3.0f, 1.0f, 0.5f,
            4.0f, 1.0f, 0.0f,

            1.0f, 0.0f, 0.0f,
            2.0f, 0.0f, 0.5f,
            3.0f, 1.0f, 0.5f,
        )
        assertArrayEquals(expectedVertexAttributes, actual.vertexAttributes, 0.0001f)

        // todo: add proper test for texture coordinates
        assertEquals(testFrames.size * testFrames.first().points.size * 3, actual.vertexPositions.size)
        assertEquals(testFrames.size * testFrames.first().points.size * 3, actual.vertexNormals.size)
    }
}
