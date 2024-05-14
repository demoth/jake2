package jake2.qcommon.filesystem

import jake2.qcommon.math.Vector3f
import java.nio.ByteBuffer

/**
 * daliasframe_t
 * A frame in the MD2 model, represents a frame in the model animation, contains coordinates and normals of all vertices
 *
 * Frame header:
 *   - 3 floats: translation(xyz)
 *   - 3 floats: scale(xyz)
 *   - 16 bytes: name
 *   - number of vertices:
 *      - 4 bytes: packed normal index + x, y, z position
 */
class Md2Frame(buffer: ByteBuffer, vertexCount: Int) {
    val points: List<Point>
    val name: String // frame name from grabbing (size 16)

    init {
        val translate = Vector3f(
            buffer.getFloat(),
            buffer.getFloat(),
            buffer.getFloat()
        )
        val scale = Vector3f(
            buffer.getFloat(),
            buffer.getFloat(),
            buffer.getFloat()
        )
        val nameBuf = ByteArray(16)
        buffer.get(nameBuf)
        name = String(nameBuf).trim { it < ' '}

        points = ArrayList(vertexCount)
        for (k in 0 until vertexCount) {
            // vertices are all 8 bit, so no swapping needed
            // 4 bytes:
            // hightest - normal index
            // x y z
            val vertexData = buffer.getInt()
            // unpack vertex data
            points.add(
                Point(
                    Vector3f(
                        scale.x * (vertexData ushr 0 and 0xFF),
                        scale.y * (vertexData ushr 8 and 0xFF),
                        scale.z * (vertexData ushr 16 and 0xFF)
                    ) + translate,
                    vertexData ushr 24 and 0xFF
                )
            )

        }
    }
}

data class Point(val position: Vector3f, val normalIndex: Int)

