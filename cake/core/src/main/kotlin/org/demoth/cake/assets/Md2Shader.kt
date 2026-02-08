package org.demoth.cake.assets

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g3d.Attribute
import com.badlogic.gdx.graphics.g3d.Attributes
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.Shader
import com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.shaders.BaseShader
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader
import com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider

/**
 * Small data structure to hold the custom data required for the shader.
 * Attached to every ModelInstance(userData) which should be animated using a VAT.
 */
data class Md2CustomData(
    var frame1: Int,
    var frame2: Int,
    var interpolation: Float,
    val frames: Int,
    var skinIndex: Int = 0 // entity_state_t.skinnum from the server
)

// max number of skins, usually there are only 2 (normal+pain), but the soldier monster has 6 variations
const val MAX_MD2_SKIN_TEXTURES = 12

/**
 * Stores MD2 skin textures in model skin-index order.
 * The shader uses [Md2CustomData.skinIndex] to choose which one to sample.
 */
class Md2SkinTexturesAttribute(val textures: List<Texture>) : Attribute(Type) {
    override fun copy(): Attribute = Md2SkinTexturesAttribute(textures)

    override fun compareTo(other: Attribute): Int {
        if (type != other.type) {
            return if (type < other.type) -1 else 1
        }
        val otherSkins = other as Md2SkinTexturesAttribute
        if (textures === otherSkins.textures) {
            return 0
        }
        return textures.size.compareTo(otherSkins.textures.size)
    }

    companion object {
        @property:JvmStatic val Alias: String = "md2SkinTextures"
        @property:JvmStatic val Type: Long = register(Alias)
    }
}

/**
 * The custom texture attribute, required to register and bind the animation texture.
 * Practically, it's just another type of texture attribute.
 */
class AnimationTextureAttribute(val texture: Texture): TextureAttribute(Type, texture) {
    companion object {
        @property:JvmStatic val Alias: String = "animationTexture"
        @property:JvmStatic val Type: Long = register(Alias)
        @JvmStatic fun init() {
            // this is weird?
            Mask = Mask or Type
        }
    }
}

/**
 * Shader for md2 vertex animations using a Vertex Animation Texture (or VAT) approach.
 * Support switchable skins via `skinIndex`
 *
 * This class doesn't have any custom logic apart from the registration of the required uniforms and attributes.
 * The [DefaultShader] superclass is already flexible enough to handle the rest.
 */
class Md2Shader(
    renderable: Renderable,
    config: Config,
): DefaultShader(renderable, config) {
    private val reusableTextureDescriptor = TextureDescriptor<Texture>()

    private fun resolveSkinTexture(combinedAttributes: Attributes, skinSlot: Int): Texture {
        val skinAttribute = combinedAttributes.get(Md2SkinTexturesAttribute.Type) as? Md2SkinTexturesAttribute
        if (skinAttribute != null && skinAttribute.textures.isNotEmpty()) { // todo: warning if null or empty
            val clamped = skinSlot.coerceIn(0, skinAttribute.textures.lastIndex) // todo: warning if wrong index
            return skinAttribute.textures[clamped]
        }
        val diffuse = (combinedAttributes.get(TextureAttribute.Diffuse) as? TextureAttribute)?.textureDescription?.texture
        check(diffuse != null) { "No skin textures or diffuse texture present on MD2 material" }
        return diffuse
    }

    private val skinCountSetter = object : LocalSetter() {
        override fun set(shader: BaseShader, inputID: Int, renderable: Renderable, combinedAttributes: Attributes) {
            val skinAttribute = combinedAttributes.get(Md2SkinTexturesAttribute.Type) as? Md2SkinTexturesAttribute
            val skinCount = skinAttribute?.textures?.size ?: 1
            shader.set(inputID, skinCount.coerceIn(1, MAX_MD2_SKIN_TEXTURES))
        }
    }

    private val skinIndexSetter = object : LocalSetter() {
        override fun set(shader: BaseShader, inputID: Int, renderable: Renderable, combinedAttributes: Attributes) {
            val md2CustomData = renderable.userData as Md2CustomData
            shader.set(inputID, md2CustomData.skinIndex)
        }
    }

    // one for every texture slot
    private fun skinTextureSetter(slot: Int) = object : LocalSetter() {
        override fun set(shader: BaseShader, inputID: Int, renderable: Renderable?, combinedAttributes: Attributes) {
            val texture = resolveSkinTexture(combinedAttributes, slot)
            reusableTextureDescriptor.set(texture, null, null, null, null)
            val unit = shader.context.textureBinder.bind(reusableTextureDescriptor)
            shader.set(inputID, unit)
        }
    }

    // uniform setters (local as they are different for each object)
    private val vertexAnimationTextureSetter = object : LocalSetter() {
        override fun set(shader: BaseShader, inputID: Int, renderable: Renderable?, combinedAttributes: Attributes) {
            // identify which unit to bind to
            val textureDescription =
                (combinedAttributes.get(AnimationTextureAttribute.Type) as TextureAttribute).textureDescription
            val unit = shader.context.textureBinder.bind(textureDescription)
            shader.set(inputID, unit)

            // since here we already know the size of the vat, we can also set its height and width uniforms
            shader.set(u_textureHeightPos, textureDescription.texture.height)
            shader.set(u_textureWidthPos, textureDescription.texture.width)
        }
    }
    private val frame1Setter = object : LocalSetter() {
        override fun set(shader: BaseShader, inputID: Int, renderable: Renderable, combinedAttributes: Attributes) {
            val md2CustomData = renderable.userData as Md2CustomData
            shader.set(inputID, md2CustomData.frame1)
        }
    }
    private val frame2Setter = object : LocalSetter() {
        override fun set(shader: BaseShader, inputID: Int, renderable: Renderable, combinedAttributes: Attributes) {
            val md2CustomData = renderable.userData as Md2CustomData
            shader.set(inputID, md2CustomData.frame2)
        }
    }
    private val interpolationSetter = object : LocalSetter() {
        override fun set(shader: BaseShader, inputID: Int, renderable: Renderable, combinedAttributes: Attributes) {
            val md2CustomData = renderable.userData as Md2CustomData
            shader.set(inputID, md2CustomData.interpolation)
        }
    }

    // animation related local (per renderable) uniforms
    protected val u_vertexAnimationTexture = Uniform("u_vertexAnimationTexture")
    private val u_vertexAnimationTextureHeight = Uniform("u_textureHeight")
    private val u_vertexAnimationTextureWidth = Uniform("u_textureWidth")
    private val u_frame1 = Uniform("u_frame1")
    private val u_frame2 = Uniform("u_frame2")
    private val u_interpolation = Uniform("u_interpolation")
    private val u_skinIndex = Uniform("u_skinIndex")
    private val u_skinCount = Uniform("u_skinCount")

    // register the uniforms
    private val u_vertexAnimationTexturePos = register(u_vertexAnimationTexture, vertexAnimationTextureSetter)
    // no setter for these two as they are set inside the setter for u_vertexAnimationTexturePos
    private val u_textureHeightPos = register(u_vertexAnimationTextureHeight)
    private val u_textureWidthPos = register(u_vertexAnimationTextureWidth)
    private val u_frame1Pos = register(u_frame1, frame1Setter)
    private val u_frame2Pos = register(u_frame2, frame2Setter)
    private val u_interpolationPos = register(u_interpolation, interpolationSetter)
    private val u_skinIndexPos = register(u_skinIndex, skinIndexSetter)
    private val u_skinCountPos = register(u_skinCount, skinCountSetter)
    private val u_skinTexturePositions = IntArray(MAX_MD2_SKIN_TEXTURES) { slot ->
        register(Uniform("u_skinTexture$slot"), skinTextureSetter(slot))
    }
}

/**
 * Shader provider for md2 animations, uses the user data to determine whether to use the [Md2Shader] or not.
 */
class Md2ShaderProvider(private val shader: Shader): DefaultShaderProvider() {
    override fun getShader(renderable: Renderable): Shader? {
        return if (renderable.userData is Md2CustomData) {
            shader
        } else super.getShader(renderable)
    }
}
