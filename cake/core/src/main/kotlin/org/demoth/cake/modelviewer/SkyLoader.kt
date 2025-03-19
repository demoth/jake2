package org.demoth.cake.modelviewer

import com.badlogic.gdx.graphics.GL20.GL_TRIANGLES
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import jake2.qcommon.filesystem.PCX
import org.demoth.cake.ResourceLocator
import org.demoth.cake.modelviewer.PCXTextureData
import org.demoth.cake.modelviewer.fromPCX

class SkyLoader(private val resourceLocator: ResourceLocator) {

    var parts= listOf("rt", "bk", "lf", "ft", "up", "dn")
    private val s = 1000f // size

    fun load(name: String): ModelInstance {
        val textures = parts.associateWith {
            resourceLocator.findSky("$name$it")
        }

        val modelBuilder = ModelBuilder()
        modelBuilder.begin()

        // Top side (z = s, normal = (0, 0, -1))
        modelBuilder.part(
            "top",
            GL_TRIANGLES,
            (VertexAttributes.Usage.Position + VertexAttributes.Usage.TextureCoordinates).toLong(),
            Material(
                TextureAttribute(
                    TextureAttribute.Diffuse,
                    Texture(PCXTextureData(fromPCX(PCX(textures["up"]!!)))),
                )
            )
        ).rect(
            s, s, s,
            s, -s, s,
            -s, -s, s,
            -s, s, s,
            0f, 0f, -1f
        )

        // Bottom side (z = -s, normal = (0, 0, 1))
        modelBuilder.part(
            "bottom",
            GL_TRIANGLES,
            (VertexAttributes.Usage.Position + VertexAttributes.Usage.TextureCoordinates).toLong(),
            Material(
                TextureAttribute(
                    TextureAttribute.Diffuse,
                    Texture(PCXTextureData(fromPCX(PCX(textures["dn"]!!)))),
                )
            )
        ).rect(
            -s, -s, -s,
            s, -s, -s,
            s, s, -s,
            -s, s, -s,
            0f, 0f, 1f
        )

        // Front side (y = s, normal = (0, -1, 0))
        modelBuilder.part(
            "front",
            GL_TRIANGLES,
            (VertexAttributes.Usage.Position + VertexAttributes.Usage.TextureCoordinates).toLong(),
            Material(
                TextureAttribute(
                    TextureAttribute.Diffuse,
                    Texture(PCXTextureData(fromPCX(PCX(textures["ft"]!!)))),
                )
            )
        ).rect(
            -s, s, -s,
            s, s, -s,
            s, s, s,
            -s, s, s,
            0f, -1f, 0f
        )

        // Back side (y = -s, normal = (0, 1, 0))
        modelBuilder.part(
            "back",
            GL_TRIANGLES,
            (VertexAttributes.Usage.Position + VertexAttributes.Usage.TextureCoordinates).toLong(),
            Material(
                TextureAttribute(
                    TextureAttribute.Diffuse,
                    Texture(PCXTextureData(fromPCX(PCX(textures["bk"]!!)))),
                )
            )
        ).rect(
            s, -s, -s,
            -s, -s, -s,
            -s, -s, s,
            s, -s, s,
            0f, 1f, 0f
        )

        // Right side (x = s, normal = (-1, 0, 0))
        modelBuilder.part(
            "right",
            GL_TRIANGLES,
            (VertexAttributes.Usage.Position + VertexAttributes.Usage.TextureCoordinates).toLong(),
            Material(
                TextureAttribute(
                    TextureAttribute.Diffuse,
                    Texture(PCXTextureData(fromPCX(PCX(textures["rt"]!!)))),
                )
            )
        ).rect(
            s, -s, -s,
            s, -s, s,
            s, s, s,
            s, s, -s,
            -1f, 0f, 0f
        )

        // Left side (x = -s, normal = (1, 0, 0))
        modelBuilder.part(
            "left",
            GL_TRIANGLES,
            (VertexAttributes.Usage.Position + VertexAttributes.Usage.TextureCoordinates).toLong(),
            Material(
                TextureAttribute(
                    TextureAttribute.Diffuse,
                    Texture(PCXTextureData(fromPCX(PCX(textures["lf"]!!)))),
                )
            )
        ).rect(
            -s, s, -s,
            -s, s, s,
            -s, -s, s,
            -s, -s, -s,
            1f, 0f, 0f
        )

        val model = modelBuilder.end()
        return ModelInstance(model)
    }
}
