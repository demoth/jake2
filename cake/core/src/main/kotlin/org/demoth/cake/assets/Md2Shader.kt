package org.demoth.cake.assets

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g3d.Attribute
import com.badlogic.gdx.graphics.g3d.Attributes
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.shaders.BaseShader
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader
import org.demoth.cake.stages.ingame.RenderTuningCvars

/**
 * Small data structure to hold the custom data required for the shader.
 * Attached to every ModelInstance(userData) which should be animated using a VAT.
 */
data class Md2CustomData(
    var frame1: Int,
    var frame2: Int,
    var interpolation: Float, // in range (0,1)
    var skinIndex: Int, // entity_state_t.skinnum from the server
    var lightRed: Float,
    var lightGreen: Float,
    var lightBlue: Float,
    var shadeVectorX: Float,
    var shadeVectorY: Float,
    var shadeVectorZ: Float,
) {
    companion object {
        /**
         * Initial values before frame data arrives from the server.
         */
        fun empty() = Md2CustomData(0, 0, 0f, 0, 1f, 1f, 1f, 0f, 0f, 1f)
    }
}

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
 * Per-frame MD2 normal VAT texture.
 *
 * Legacy counterpart:
 * MD2 alias rendering resolves `lightnormalindex` against `anorms` table per frame.
 * Cake stores those resolved vectors in a second VAT texture and interpolates in shader.
 */
class AnimationNormalTextureAttribute(val texture: Texture): TextureAttribute(Type, texture) {
    companion object {
        @property:JvmStatic val Alias: String = "animationNormalTexture"
        @property:JvmStatic val Type: Long = register(Alias)
        @JvmStatic fun init() {
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
 *
 * Legacy counterpart:
 * - `client/render/fast/Mesh.R_DrawAliasModel`
 * - `../yquake2/src/client/refresh/gl3/gl3_mesh.c`
 *
 * Difference:
 * legacy aliases use quantized `anormtab` lookup (`shadedots`). Cake uses continuous dot product
 * with interpolated per-frame normals from normal VAT by default.
 * Optional strict parity mode (`r_md2_legacy_shadedots`) switches to quantized shadedot response.
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
    private val normalAnimationTextureSetter = object : LocalSetter() {
        override fun set(shader: BaseShader, inputID: Int, renderable: Renderable?, combinedAttributes: Attributes) {
            val textureDescription =
                (combinedAttributes.get(AnimationNormalTextureAttribute.Type) as TextureAttribute).textureDescription
            val unit = shader.context.textureBinder.bind(textureDescription)
            shader.set(inputID, unit)

            shader.set(u_normalTextureHeightPos, textureDescription.texture.height)
            shader.set(u_normalTextureWidthPos, textureDescription.texture.width)
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
    // The custom MD2 fragment shader samples skin textures directly, so we explicitly pass opacity from
    // BlendingAttribute instead of relying on DefaultShader's built-in diffuse path.
    private val opacitySetter = object : LocalSetter() {
        override fun set(shader: BaseShader, inputID: Int, renderable: Renderable?, combinedAttributes: Attributes) {
            val blending = combinedAttributes.get(BlendingAttribute.Type) as? BlendingAttribute
            shader.set(inputID, blending?.opacity ?: 1f)
        }
    }
    private val entityLightColorSetter = object : LocalSetter() {
        override fun set(shader: BaseShader, inputID: Int, renderable: Renderable, combinedAttributes: Attributes) {
            val md2CustomData = renderable.userData as Md2CustomData
            shader.set(inputID, md2CustomData.lightRed, md2CustomData.lightGreen, md2CustomData.lightBlue)
        }
    }
    private val shadeVectorSetter = object : LocalSetter() {
        override fun set(shader: BaseShader, inputID: Int, renderable: Renderable, combinedAttributes: Attributes) {
            val md2CustomData = renderable.userData as Md2CustomData
            shader.set(inputID, md2CustomData.shadeVectorX, md2CustomData.shadeVectorY, md2CustomData.shadeVectorZ)
        }
    }
    private val gammaExponentSetter = object : LocalSetter() {
        override fun set(shader: BaseShader, inputID: Int, renderable: Renderable?, combinedAttributes: Attributes) {
            shader.set(inputID, RenderTuningCvars.gammaExponent())
        }
    }
    private val intensitySetter = object : LocalSetter() {
        override fun set(shader: BaseShader, inputID: Int, renderable: Renderable?, combinedAttributes: Attributes) {
            shader.set(inputID, RenderTuningCvars.intensity())
        }
    }
    private val legacyShadedotsSetter = object : LocalSetter() {
        override fun set(shader: BaseShader, inputID: Int, renderable: Renderable?, combinedAttributes: Attributes) {
            shader.set(inputID, if (RenderTuningCvars.legacyMd2ShadedotsEnabled()) 1f else 0f)
        }
    }

    // animation related local (per renderable) uniforms
    protected val u_vertexAnimationTexture = Uniform("u_vertexAnimationTexture")
    protected val u_vertexNormalTexture = Uniform("u_vertexNormalTexture")
    private val u_vertexAnimationTextureHeight = Uniform("u_textureHeight")
    private val u_vertexAnimationTextureWidth = Uniform("u_textureWidth")
    private val u_vertexNormalTextureHeight = Uniform("u_normalTextureHeight")
    private val u_vertexNormalTextureWidth = Uniform("u_normalTextureWidth")
    private val u_frame1 = Uniform("u_frame1")
    private val u_frame2 = Uniform("u_frame2")
    private val u_interpolation = Uniform("u_interpolation")
    private val u_skinIndex = Uniform("u_skinIndex")
    private val u_skinCount = Uniform("u_skinCount")
    // Keep this aligned with `assets/shaders/md2.frag`.
    private val u_md2Opacity = Uniform("u_opacity")
    private val u_entityLightColor = Uniform("u_entityLightColor")
    private val u_shadeVector = Uniform("u_shadeVector")
    private val u_gammaExponent = Uniform("u_gammaExponent")
    private val u_intensity = Uniform("u_intensity")
    private val u_useLegacyShadedots = Uniform("u_useLegacyShadedots")

    // register the uniforms
    private val u_vertexAnimationTexturePos = register(u_vertexAnimationTexture, vertexAnimationTextureSetter)
    private val u_vertexNormalTexturePos = register(u_vertexNormalTexture, normalAnimationTextureSetter)
    // no setter for these two as they are set inside the setter for u_vertexAnimationTexturePos
    private val u_textureHeightPos = register(u_vertexAnimationTextureHeight)
    private val u_textureWidthPos = register(u_vertexAnimationTextureWidth)
    private val u_normalTextureHeightPos = register(u_vertexNormalTextureHeight)
    private val u_normalTextureWidthPos = register(u_vertexNormalTextureWidth)
    private val u_frame1Pos = register(u_frame1, frame1Setter)
    private val u_frame2Pos = register(u_frame2, frame2Setter)
    private val u_interpolationPos = register(u_interpolation, interpolationSetter)
    private val u_skinIndexPos = register(u_skinIndex, skinIndexSetter)
    private val u_skinCountPos = register(u_skinCount, skinCountSetter)
    private val u_md2OpacityPos = register(u_md2Opacity, opacitySetter)
    private val u_entityLightColorPos = register(u_entityLightColor, entityLightColorSetter)
    private val u_shadeVectorPos = register(u_shadeVector, shadeVectorSetter)
    private val u_gammaExponentPos = register(u_gammaExponent, gammaExponentSetter)
    private val u_intensityPos = register(u_intensity, intensitySetter)
    private val u_useLegacyShadedotsPos = register(u_useLegacyShadedots, legacyShadedotsSetter)
    private val u_skinTexturePositions = IntArray(MAX_MD2_SKIN_TEXTURES) { slot ->
        register(Uniform("u_skinTexture$slot"), skinTextureSetter(slot))
    }
}
