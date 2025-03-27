package org.demoth.cake.modelviewer

import com.badlogic.gdx.graphics.GL20.GL_TRIANGLES
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import jake2.qcommon.filesystem.PCX
import org.demoth.cake.ResourceLocator

/**
 * This class is responsible for building the skybox geometry and assigning proper textures
 */
class SkyLoader(private val resourceLocator: ResourceLocator) {

    private var parts= listOf("rt", "bk", "lf", "ft", "up", "dn")
    private val s = 2048f // size

    /**
     * [name] the name of the unit or set of the skybox images, usually ends with an underscore
     */
    fun load(name: String): ModelInstance {
        val textures = parts.associateWith {
            resourceLocator.findSky("$name$it")
        }

        val modelBuilder = ModelBuilder()
        modelBuilder.begin()

        // Top side (z = s, normal = (0, 0, -1))
        modelBuilder.skyPart(textures, "up").rect(
            s, s, s,
            s, -s, s,
            -s, -s, s,
            -s, s, s,
            0f, 0f, -1f
        )
        // Front side (y = s, normal = (0, -1, 0))
        modelBuilder.skyPart(textures, "ft").rect(
            s, -s, -s,
            -s, -s, -s,
            -s, -s, s,
            s, -s, s,
            0f, -1f, 0f
        )
        // Right side (x = s, normal = (-1, 0, 0))
        modelBuilder.skyPart(textures, "rt").rect(
            s, s, -s,
            s, -s, -s,
            s, -s, s,
            s, s, s,
            -1f, 0f, 0f
        )
        // Back side (y = -s, normal = (0, 1, 0))
        modelBuilder.skyPart(textures, "bk").rect(
            -s, s, -s,
            s, s, -s,
            s, s, s,
            -s, s, s,
            0f, 1f, 0f
        )
        // Left side (x = -s, normal = (1, 0, 0))
        modelBuilder.skyPart(textures, "lf").rect(
            -s, -s, -s,
            -s, s, -s,
            -s, s, s,
            -s, -s, s,
            1f, 0f, 0f
        )
        // Bottom side (z = -s, normal = (0, 0, 1))
        modelBuilder.skyPart(textures, "dn").rect(
            -s, -s, -s,
            s, -s, -s,
            s, s, -s,
            -s, s, -s,
            0f, 0f, 1f
        )
        val model = modelBuilder.end()
        return ModelInstance(model)
    }

}

private fun ModelBuilder.skyPart(
    textures: Map<String, ByteArray>,
    name: String
): MeshPartBuilder = part(
    name,
    GL_TRIANGLES,
    (VertexAttributes.Usage.Position + VertexAttributes.Usage.TextureCoordinates).toLong(),
    Material(
        TextureAttribute(
            TextureAttribute.Diffuse,
            Texture(PCXTextureData(fromPCX(PCX(textures[name]!!)))),
        )
    )
)
