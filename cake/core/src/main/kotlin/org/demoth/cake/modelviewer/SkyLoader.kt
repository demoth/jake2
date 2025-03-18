package org.demoth.cake.modelviewer

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import jake2.qcommon.filesystem.PCX
import org.demoth.cake.ResourceLocator

class SkyLoader(private val resourceLocator: ResourceLocator) {

    var parts= listOf("rt", "bk", "lf", "ft", "up", "dn")
    private val s = 1000f // size

    fun load(name: String): ModelInstance {
        val textures = parts.associateWith {
            resourceLocator.findSky("$name$it")
        }
        // todo: create a sky box with 6 sides facing inwards, assign a texture to each side

        // fixme: temp one face
        val modelBuilder = ModelBuilder()
        // temporary: create one side on the top facing downwards
        val model = modelBuilder.createRect(
            s, s, s,
            s, -s, s,
            -s, -s, s,
            -s, s, s,
            0f, 0f, -1f,
            Material(
                TextureAttribute(
                    TextureAttribute.Diffuse,
                    Texture(PCXTextureData(fromPCX(PCX(textures["up"]!!)))),
                )
            ),
            (VertexAttributes.Usage.Position + VertexAttributes.Usage.TextureCoordinates).toLong()
        )
        return ModelInstance(model)
    }
}