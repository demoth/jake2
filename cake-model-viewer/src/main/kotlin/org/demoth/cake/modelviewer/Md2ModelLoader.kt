package org.demoth.cake.modelviewer

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20.GL_TRIANGLES
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import java.lang.Float.intBitsToFloat
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Md2ModelLoader {
    fun loadMd2Model(modelPath: String, texturePath: String): ModelInstance {
        val md2Model: Md2Model = readMd2Model(modelPath)

        // put all vertices of the 1st frame into the buffer
        val (vertexIndices, vertexBuffer) = createVertexIndices(md2Model, 1)

        val modelBuilder = ModelBuilder()
        modelBuilder.begin()
        val meshBuilder = modelBuilder.part(
            "part1",
            GL_TRIANGLES,
            VertexAttributes(VertexAttribute.Position(), VertexAttribute.TexCoords(0)),
            Material(
                TextureAttribute(TextureAttribute.Diffuse, Texture(Gdx.files.internal(texturePath)))
            )
        )
        meshBuilder.addMesh(vertexBuffer, vertexIndices)
        val model = modelBuilder.end()
        return ModelInstance(model)
    }

    internal data class VertexInfo(val positionIndex: Int, val s: Float, val t: Float)
    internal data class VertexFullInfo(val x: Float, val y: Float, val z: Float, val s: Float, val t: Float)

    private fun convert(info: VertexInfo, positions: Array<Point>, frame: Md2Frame): VertexFullInfo {
        val position = positions[info.positionIndex]
        return VertexFullInfo(
            x = position.x * frame.scale[0],
            y = position.y * frame.scale[1],
            z = position.z * frame.scale[2],
            s = info.s,
            t = info.t
        )

    }

    private fun createVertexIndices(model: Md2Model, frameIndex: Int): Pair<ShortArray, FloatArray> {
        var glCmdIndex = 0 // todo: use queue to pop elements instead of using mutable index?

        val frame = model.frames[frameIndex]!!
        val vertexPositions = frame.points

        val result = mutableListOf<VertexFullInfo>()

        while (true) {
            val numOfPoints = model.glCmds[glCmdIndex]
            glCmdIndex++
            glCmdIndex += if (numOfPoints == 0) {
                break
            } else if (numOfPoints >= 0) {
                // triangle strip
                val vertices = mutableListOf<VertexInfo>()
                for (i in glCmdIndex until (glCmdIndex + numOfPoints * 3) step 3) {
                    val s = intBitsToFloat(model.glCmds[i + 0])
                    val t = intBitsToFloat(model.glCmds[i + 1])
                    val vertexIndex = model.glCmds[i + 2]
                    vertices.add(VertexInfo(vertexIndex, s ,t))
                }
                // converting strips into separate triangles
                var clockwise = false // when converting a triangle strip into a set of separate triangles, need to alternate the winding direction
                vertices.windowed(3).forEach {
                    if (clockwise) {
                        result.add(convert(it[0], vertexPositions, frame))
                        result.add(convert(it[1], vertexPositions, frame))
                        result.add(convert(it[2], vertexPositions, frame))
                    } else {
                        result.add(convert(it[2], vertexPositions, frame))
                        result.add(convert(it[1], vertexPositions, frame))
                        result.add(convert(it[0], vertexPositions, frame))
                    }
                    clockwise = !clockwise
                }
                numOfPoints * 3
            } else {
                // triangle fan
                val vertices = mutableListOf<VertexInfo>()
                for (i in glCmdIndex until (glCmdIndex - numOfPoints * 3) step 3) {
                    val s = intBitsToFloat(model.glCmds[i + 0])
                    val t = intBitsToFloat(model.glCmds[i + 1])
                    val vertexIndex = model.glCmds[i + 2]
                    vertices.add(VertexInfo(vertexIndex, s, t))
                }
                convertStripToTriangles(vertices).windowed(3).forEach {
                    result.add(convert(it[2], vertexPositions, frame))
                    result.add(convert(it[1], vertexPositions, frame))
                    result.add(convert(it[0], vertexPositions, frame))
                }
                (-numOfPoints * 3)
            }

        }

        return result.indices.map { it.toShort() }.toShortArray() to result.flatMap { listOf(it.x, it.y, it.z, it.s, it.t) }.toFloatArray()
    }

    private fun convertStripToTriangles(vertices: List<VertexInfo>): List<VertexInfo> {
        val result = mutableListOf<VertexInfo>()
        vertices.drop(1).windowed(2).forEach {
            result.add(vertices.first())
            result.add(it.first())
            result.add(it.last())
        }
        return result
    }

    fun readMd2Model(modelPath: String): Md2Model {
        val byteBuffer = ByteBuffer
            .wrap(Gdx.files.internal(modelPath).readBytes())
            .order(ByteOrder.LITTLE_ENDIAN)
        return Md2Model(byteBuffer, "model1")
    }

}
