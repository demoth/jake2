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
        assertEquals(168, map.vertices.size)
        assertEquals(296, map.edges.size)
        assertEquals(590, map.faceEdges.size)
        map.faces.forEach { f ->
            val edgeIndices = (f.firstEdgeIndex..<(f.firstEdgeIndex + f.numEdges)).map { edgeIndex ->
                map.faceEdges[edgeIndex]
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
    }
}