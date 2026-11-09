package jake2.qcommon.filesystem

import jake2.qcommon.Defines
import jake2.qcommon.math.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BspTest {
    @Test
    fun `retains signed root node bounds while decoding the map`() {
        val map = Bsp(rootNodeFixture(qfiles.dnode_t.SIZE))
        assertEquals(BspNodeBounds(Vector3f(-2056f, -1024f, -1464f), Vector3f(2056f, 2048f, 384f)),
            map.rootNodeBounds)
    }

    @Test
    fun `missing nodes have no root bounds and partial nodes are rejected`() {
        assertNull(Bsp(rootNodeFixture(0)).rootNodeBounds)
        assertThrows(IllegalStateException::class.java) { Bsp(rootNodeFixture(qfiles.dnode_t.SIZE - 1)) }
    }

    private fun rootNodeFixture(nodeLength: Int): ByteBuffer {
        val headerSize = 8 + Defines.HEADER_LUMPS * 8
        return ByteBuffer.allocate(headerSize + qfiles.dnode_t.SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(IDBSPHEADER).putInt(Defines.BSPVERSION)
            position(8 + Defines.LUMP_NODES * 8)
            putInt(headerSize).putInt(nodeLength)
            position(headerSize + 12) // Plane index and children precede the bounds.
            for (coordinate in listOf(-2056, -1024, -1464, 2056, 2048, 384)) putShort(coordinate.toShort())
            position(0)
        }
    }

    @Test
    fun loadTestBoxBsp() {
        val map = Bsp(ByteBuffer.wrap(this.javaClass.getResourceAsStream("maps/testbox.bsp")!!.readAllBytes()))
        val expectedEntities = String(this.javaClass.getResourceAsStream("maps/testbox.ent")!!.readAllBytes())
        assertEquals(expectedEntities, map.entityString)
        assertEquals(168, map.vertices.size)
        assertEquals(296, map.edges.size)
        assertEquals(590, map.faceEdges.size)
        assertTrue(map.lighting.isNotEmpty())
        assertTrue(map.leaves.isNotEmpty())
        assertTrue(map.leafFaces.isNotEmpty())

        map.leaves.forEach { leaf ->
            assertTrue(leaf.firstLeafFace >= 0)
            assertTrue(leaf.numLeafFaces >= 0)
            assertTrue(leaf.firstLeafFace + leaf.numLeafFaces <= map.leafFaces.size)
        }
        map.leafFaces.forEach { faceIndex ->
            assertTrue(faceIndex in map.faces.indices)
        }

        map.faces.forEach { f ->
            val edgeIndices = (0..<f.numEdges).map { edgeIndex ->
                map.faceEdges[f.firstEdgeIndex + edgeIndex]
            }
            val vertices = edgeIndices.flatMap { edgeIndex ->
                if (edgeIndex > 0) {
                    val edge = map.edges[edgeIndex]
                    listOf(edge.v1, edge.v2)
                } else {
                    val edge = map.edges[-edgeIndex]
                    listOf(edge.v2, edge.v1)
                }
            }
            assert(vertices.first() == vertices.last()) {
                "Edges ($edgeIndices) of the face$f do not form a closed loop"
            }
        }
        assertEquals(17, map.textures.size)
    }
}
