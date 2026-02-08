package org.demoth.cake.assets

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.AssetDescriptor
import com.badlogic.gdx.assets.AssetLoaderParameters
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.FileHandleResolver
import com.badlogic.gdx.assets.loaders.SynchronousAssetLoader
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GL30
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.TextureData
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.VertexAttribute.TexCoords
import com.badlogic.gdx.graphics.VertexAttributes.Usage.Generic
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Diffuse
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.GdxRuntimeException
import jake2.qcommon.filesystem.Md2Model
import jake2.qcommon.filesystem.Md2VertexData
import jake2.qcommon.filesystem.buildVertexData
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.jvm.java

/**
 * Loaded MD2 asset bundle.
 *
 * [model] is the renderable geometry using the VAT shader attributes.
 * [frames] is frame count metadata used by animation code.
 * [skins] contains resolved skin textures in MD2 skin index order when available.
 *
 * This allows future runtime skin switching (for example normal/injured monster skin)
 * without replacing model geometry.
 */
class Md2Asset(
    val model: Model,
    val frames: Int,
    val skins: List<Texture>,
) : Disposable {
    override fun dispose() {
        model.dispose()
    }
}

/**
 * Loads MD2 geometry and prepares textures required by the model material.
 *
 * Dependency policy:
 * - external skin path provided -> load only that texture.
 * - embedded skins + loadAllEmbeddedSkins=true -> preload all embedded skins.
 * - embedded skins + loadAllEmbeddedSkins=false -> load only selected skinIndex.
 *
 * Geometry is turned into a mesh with VAT index attributes and a GPU VAT texture.
 */
class Md2Loader(resolver: FileHandleResolver) : SynchronousAssetLoader<Md2Asset, Md2Loader.Parameters>(resolver) {

    /**
     * MD2 loading options.
     *
     * [externalSkinPath] overrides embedded MD2 skin names.
     * [skinIndex] selects default embedded skin (wrapped with modulo).
     * [loadAllEmbeddedSkins] preloads all embedded skins to support quick runtime switching.
     */
    data class Parameters(
        val externalSkinPath: String? = null,
        val skinIndex: Int = 0,
        val loadAllEmbeddedSkins: Boolean = true,
    ) : AssetLoaderParameters<Md2Asset>()

    override fun getDependencies(
        fileName: String,
        file: FileHandle?,
        parameter: Parameters?
    ): Array<AssetDescriptor<*>>? {
        if (file == null) {
            return null
        }
        val md2 = readMd2Model(file.readBytes())
        val skinPaths = resolveDependencySkinPaths(md2, parameter)
        if (skinPaths.isEmpty()) {
            return null
        }
        return Array<AssetDescriptor<*>>(skinPaths.size).apply {
            skinPaths.forEach { add(AssetDescriptor(it, Texture::class.java)) }
        }
    }

    override fun load(
        manager: AssetManager,
        fileName: String,
        file: FileHandle,
        parameter: Parameters?
    ): Md2Asset {
        val md2 = readMd2Model(file.readBytes())

        val selectedSkinPath = resolveSelectedSkinPath(md2, parameter)
        val dependencySkinPaths = resolveDependencySkinPaths(md2, parameter)
        val texturesByPath = dependencySkinPaths.associateWith { path ->
            manager.get(path, Texture::class.java)
        }

        val skinTexturesByIndex = when {
            parameter?.externalSkinPath?.isBlank() == false -> {
                listOf(texturesByPath.getValue(selectedSkinPath))
            }
            else -> {
                md2.skinNames.map { skinPath ->
                    val texture = texturesByPath[skinPath]
                    check(texture != null) { "Missing dependency texture for MD2 skin: $skinPath" }
                    texture
                }
            }
        }
        val diffuse = texturesByPath.getValue(selectedSkinPath)

        val vertexData = buildVertexData(md2.glCommands, md2.frames)
        val mesh = Mesh(
            true,
            vertexData.vertexAttributes.size,
            vertexData.indices.size,
            VertexAttributes(
                VertexAttribute(Generic, 1, "a_vat_index"),
                TexCoords(1) // in future, normals can also be added here
            )
        )
        mesh.setVertices(vertexData.vertexAttributes)
        mesh.setIndices(vertexData.indices)
        val material = createMd2Material(diffuse, createVat(vertexData), skinTexturesByIndex)
        return Md2Asset(
            model = createModel(mesh, material),
            frames = md2.frames.size, // :thinking: used only in the model viewer, otherwise we could have return a `Model` here
            skins = skinTexturesByIndex,
        )
    }

    /**
     * Computes texture dependency paths from MD2 skin metadata and parameters.
     */
    private fun resolveDependencySkinPaths(md2: Md2Model, parameter: Parameters?): List<String> {
        // player models provide skin path externally
        val external = parameter?.externalSkinPath?.takeIf { it.isNotBlank() }
        if (external != null) {
            return listOf(external)
        }
        if (md2.skinNames.isEmpty()) {
            return emptyList()
        }
        val embedded = md2.skinNames
        return if (parameter?.loadAllEmbeddedSkins == false) {
            listOf(embedded[normalizedSkinIndex(parameter.skinIndex, embedded.size)])
        } else {
            embedded.distinct()
        }
    }

    /**
     * Computes the skin path used as diffuse texture for the created material.
     */
    private fun resolveSelectedSkinPath(md2: Md2Model, parameter: Parameters?): String {
        val external = parameter?.externalSkinPath?.takeIf { it.isNotBlank() }
        if (external != null) {
            return external
        }

        check(md2.skinNames.isNotEmpty()) {
            "No embedded skin found in model and no external skin was provided"
        }
        val selectedIndex = normalizedSkinIndex(parameter?.skinIndex ?: 0, md2.skinNames.size)
        return md2.skinNames[selectedIndex]
    }

    private fun normalizedSkinIndex(index: Int, size: Int): Int {
        check(size > 0) { "Cannot normalize skin index for empty skin set" }
        val modulo = index % size
        return if (modulo < 0) modulo + size else modulo
    }

    private fun createMd2Material(diffuse: Texture, vat: Texture, skins: List<Texture>): Material {
        // required for registering the custom VAT texture attribute
        AnimationTextureAttribute.init()

        // create the material with diffuse + all skins + animation VAT texture
        return Material(
            TextureAttribute(Diffuse, diffuse),
            Md2SkinTexturesAttribute(skins.take(MAX_MD2_SKIN_TEXTURES)), // todo: warning if skins has more than MAX_MD2_SKIN_TEXTURES
            AnimationTextureAttribute(vat)
        )
    }

    /**
     * Builds the vertex-animation texture containing frame vertex positions.
     */
    private fun createVat(vertexData: Md2VertexData): Texture {
        return Texture(
            CustomTextureData(
                vertexData.vertices,
                vertexData.frames,
                GL30.GL_RGB16F,
                GL30.GL_RGB,
                GL20.GL_FLOAT,
                vertexData.vertexPositions.toFloatBuffer(),
            )
        )
    }
}

/**
 * Get an asset ensuring it's loaded. Blocking operation.
 */
inline fun <reified T> AssetManager.getLoaded(path: String): T {
    if (!isLoaded(path, T::class.java)) {
        load(path, T::class.java)
        finishLoadingAsset<T>(path)
    }
    return get(path, T::class.java)
}

/**
 * Get an asset ensuring it's loaded. Blocking operation.
 */
inline fun <reified T> AssetManager.getLoaded(path: String, parameter: AssetLoaderParameters<T>): T {
    if (!isLoaded(path, T::class.java)) {
        load(path, T::class.java, parameter)
        finishLoadingAsset<T>(path)
    }
    return get(path, T::class.java)
}

// Helper class to create TextureData from a vertex position data buffer
private class CustomTextureData(
    private val width: Int,
    private val height: Int,
    private val glInternalFormat: Int,
    private val glFormat: Int,
    private val glType: Int,
    private val buffer: Buffer?
) : TextureData {
    private var isPrepared = false

    override fun getType(): TextureData.TextureDataType {
        // means it doesn't rely on the pixmap format
        return TextureData.TextureDataType.Custom
    }

    override fun isPrepared(): Boolean {
        return isPrepared
    }

    override fun prepare() {
        if (isPrepared) throw GdxRuntimeException("Already prepared!")
        isPrepared = true
    }

    override fun consumePixmap(): Pixmap? {
        throw GdxRuntimeException("This TextureData does not return a Pixmap")
    }

    override fun disposePixmap(): Boolean {
        return false
    }

    override fun consumeCustomData(target: Int) {
        if (!isPrepared) throw GdxRuntimeException("Call prepare() first")

        Gdx.gl.glTexImage2D(
            /* target = */ target,
            /* level = */ 0,
            /* internalformat = */ glInternalFormat,
            /* width = */ width,
            /* height = */ height,
            /* border = */ 0,
            /* format = */ glFormat,
            /* type = */ glType,
            /* pixels = */ buffer
        )
    }

    override fun getWidth(): Int {
        return width
    }

    override fun getHeight(): Int {
        return height
    }

    override fun getFormat(): Pixmap.Format? {
        throw GdxRuntimeException("This TextureData does not use a Pixmap")
    }

    override fun useMipMaps(): Boolean {
        return false
    }

    override fun isManaged(): Boolean {
        return true // LibGDX will manage this texture
    }
}

fun createModel(mesh: Mesh, material: Material): Model {
    return ModelBuilder().apply {
        begin()
        part("part1", mesh, GL20.GL_TRIANGLES, material)
    }.end()
}

private fun FloatArray.toFloatBuffer(): FloatBuffer {
    val result = ByteBuffer
        .allocateDirect(size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
    result.put(this)
    result.flip()
    return result
}

private fun readMd2Model(modelData: ByteArray): Md2Model {
    val byteBuffer = ByteBuffer
        .wrap(modelData)
        .order(ByteOrder.LITTLE_ENDIAN)
    return Md2Model(byteBuffer)
}
