package org.demoth.cake.modelviewer

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g3d.Attributes
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.shaders.BaseShader
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader
import com.badlogic.gdx.graphics.glutils.ShaderProgram

data class Md2CustomData(
    var frame1: Int,
    var frame2: Int,
    var interpolation: Float,
    val frames: Int
)

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

class Md2Shader(
    renderable: Renderable,
    config: Config,
    shaderProgram: ShaderProgram,
): DefaultShader(renderable, config, shaderProgram) {
    // uniform setters
    private val vertexAnimationTextureSetter = object : LocalSetter() {
        override fun set(shader: BaseShader, inputID: Int, renderable: Renderable?, combinedAttributes: Attributes) {
            // identify which unit to bind to
            val textureDescription =
                (combinedAttributes.get(AnimationTextureAttribute.AnimationTexture) as TextureAttribute).textureDescription
            val unit = shader.context.textureBinder.bind(textureDescription)
            shader.set(inputID, unit)
            // also set the animation texture height and width
            shader.set(u_textureHeightPos, textureDescription.texture.height)
            shader.set(u_textureWidthPos, textureDescription.texture.width)
        }
    }

    // local setters (unique values for each object)
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
    private val u_vertexAnimationTexture = Uniform("u_vertexAnimationTexture")
    private val u_vertexAnimationTextureHeight = Uniform("u_textureHeight")
    private val u_vertexAnimationTextureWidth = Uniform("u_textureWidth")
    private val u_frame1 = Uniform("u_frame1")
    private val u_frame2 = Uniform("u_frame2")
    private val u_interpolation = Uniform("u_interpolation")

    // register additional uniforms
    private val u_vertexAnimationTexturePos = register(u_vertexAnimationTexture, vertexAnimationTextureSetter)
    private val u_textureHeightPos = register(u_vertexAnimationTextureHeight)
    private val u_textureWidthPos = register(u_vertexAnimationTextureWidth)
    private val u_frame1Pos = register(u_frame1, frame1Setter)
    private val u_frame2Pos = register(u_frame2, frame2Setter)
    private val u_interpolationPos = register(u_interpolation, interpolationSetter)


}
