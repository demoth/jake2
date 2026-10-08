package org.demoth.cake.assets

import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.Shader
import org.demoth.cake.assets.md2.AnimationNormalTextureAttribute
import org.demoth.cake.assets.md2.AnimationTextureAttribute
import org.demoth.cake.assets.md2.Md2CustomData

/**
 * Selects MD2 VAT, BSP lightmap, or default libGDX shaders for each renderable.
 */
class CakeShaderProvider(
    private val md2Shader: Shader,
    private val bspLightmapShader: Shader? = null,
) : com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider() {
    override fun getShader(renderable: Renderable): Shader {
        if (bspLightmapShader != null && bspLightmapShader.canRender(renderable)) {
            return bspLightmapShader
        }
        return if (renderable.userData is Md2CustomData &&
            renderable.material.has(AnimationTextureAttribute.Type) &&
            renderable.material.has(AnimationNormalTextureAttribute.Type)
        ) {
            md2Shader
        } else {
            super.getShader(renderable)
        }
    }

    override fun dispose() {
        super.dispose()
        md2Shader.dispose()
        bspLightmapShader?.dispose()
    }
}
