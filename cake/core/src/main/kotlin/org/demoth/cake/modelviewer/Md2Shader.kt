package org.demoth.cake.modelviewer

import com.badlogic.gdx.graphics.g3d.Attributes
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.shaders.BaseShader
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader
import com.badlogic.gdx.graphics.glutils.ShaderProgram

class Md2Shader(
    val renderable: Renderable,
    val config: DefaultShader.Config,
    val shaderProgram: ShaderProgram,
): DefaultShader(renderable, config, shaderProgram) {
    val u_vertexAnimationTextureSetter = object : LocalSetter() {
        override fun set(shader: BaseShader, inputID: Int, renderable: Renderable?, combinedAttributes: Attributes) {
            val unit = shader.context.textureBinder.bind(((combinedAttributes.get(AnimationTextureAttribute.AnimationTexture) as TextureAttribute).textureDescription));
            shader.set(inputID, unit);
        }
    }

    val u_vertexAnimationTexturePos = register(u_vertexAnimationTexture, u_vertexAnimationTextureSetter)
    val u_textureHeightPos = register(u_textureHeight)
    val u_textureWidthPos = register(u_textureWidth)
    val u_frame1Pos = register(u_frame1)
    val u_frame2Pos = register(u_frame2)
    val u_interpolationPos = register(u_interpolation)


}
