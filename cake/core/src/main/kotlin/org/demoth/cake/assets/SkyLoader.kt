package org.demoth.cake.assets

import com.badlogic.gdx.assets.AssetDescriptor
import com.badlogic.gdx.assets.AssetLoaderParameters
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.FileHandleResolver
import com.badlogic.gdx.assets.loaders.SynchronousAssetLoader
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.GL20.GL_TRIANGLES
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.utils.Array

/**
 * Loads a skybox model and declares all six sky textures as dependencies.
 */
class SkyLoader(resolver: FileHandleResolver) : SynchronousAssetLoader<Model, SkyLoader.Parameters>(resolver) {

    class Parameters : AssetLoaderParameters<Model>() {
        var skyName: String? = null
    }

    override fun load(
        manager: AssetManager,
        fileName: String,
        file: FileHandle,
        parameter: Parameters?
    ): Model {
        val skyName = requireSkyName(parameter)
        val textures = PARTS.associateWith { part ->
            manager.get(toTexturePath(skyName, part), Texture::class.java)
        }
        return buildSkyModel(textures)
    }

    override fun getDependencies(
        fileName: String,
        file: FileHandle?,
        parameter: Parameters?
    ): Array<AssetDescriptor<*>> {
        val skyName = requireSkyName(parameter)
        return Array<AssetDescriptor<*>>(PARTS.size).apply {
            PARTS.forEach { part ->
                add(AssetDescriptor(toTexturePath(skyName, part), Texture::class.java))
            }
        }
    }

    private fun buildSkyModel(textures: Map<String, Texture>): Model {
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
        return model
    }

    private fun requireSkyName(parameter: Parameters?): String {
        return parameter?.skyName?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("SkyLoader requires a non-empty skyName parameter")
    }

    companion object {
        private val PARTS = listOf("rt", "bk", "lf", "ft", "up", "dn")
        private const val s = 2048f
        private const val SKY_ASSET_PATH = "sky/skybox.sky"

        fun assetPath(): String = SKY_ASSET_PATH

        private fun toTexturePath(skyName: String, part: String): String = "env/$skyName$part.pcx"
    }
}

private fun ModelBuilder.skyPart(
    textures: Map<String, Texture>,
    name: String
): MeshPartBuilder = part(
    name,
    GL_TRIANGLES,
    (VertexAttributes.Usage.Position + VertexAttributes.Usage.TextureCoordinates).toLong(),
    Material(
        TextureAttribute(
            TextureAttribute.Diffuse,
            textures[name]!!,
        )
    )
)
