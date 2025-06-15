package org.demoth.cake.modelviewer

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g3d.Attributes
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.Shader
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.shaders.BaseShader
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader
import com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider
import com.badlogic.gdx.graphics.glutils.ShaderProgram

data class Md2CustomData(
    var frame1: Int,
    var frame2: Int,
    var interpolation: Float,
    val frames: Int
)

/**
 * The custom texture attribute, required to register and bind the animation texture.
 * Practically, it's just another type of texture attribute.
 */
class AnimationTextureAttribute(val texture: Texture): TextureAttribute(AnimationTexture, texture) {
    companion object {
        @property:JvmStatic val AnimationTextureAlias: String = "animationTexture"
        @property:JvmStatic val AnimationTexture: Long = register(AnimationTextureAlias)
        @JvmStatic fun init() {
            // this is weird?
            Mask = Mask or AnimationTexture
        }
    }
}

private const val md2ShaderPrefix = """
    #version 130
    #define diffuseTextureFlag
"""

/**
 * Shader for md2 vertex animations using a Vertex Animation Texture (or VAT) approach.
 *
 * This class doesn't have any custom logic apart from the registration of the required uniforms and attributes.
 * The [DefaultShader] superclass is already flexible enough to handle the rest.
 */
class Md2Shader(
    renderable: Renderable,
    config: Config,
): DefaultShader(renderable, config, md2ShaderPrefix) {
    // uniform setters (local as they are different for each object)
    private val vertexAnimationTextureSetter = object : LocalSetter() {
        override fun set(shader: BaseShader, inputID: Int, renderable: Renderable?, combinedAttributes: Attributes) {
            // identify which unit to bind to
            val textureDescription =
                (combinedAttributes.get(AnimationTextureAttribute.AnimationTexture) as TextureAttribute).textureDescription
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

    // register the uniforms
    private val u_vertexAnimationTexturePos = register(u_vertexAnimationTexture, vertexAnimationTextureSetter)
    // no setter for these two as they are set inside the setter for u_vertexAnimationTexturePos
    private val u_textureHeightPos = register(u_vertexAnimationTextureHeight)
    private val u_textureWidthPos = register(u_vertexAnimationTextureWidth)
    private val u_frame1Pos = register(u_frame1, frame1Setter)
    private val u_frame2Pos = register(u_frame2, frame2Setter)
    private val u_interpolationPos = register(u_interpolation, interpolationSetter)
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