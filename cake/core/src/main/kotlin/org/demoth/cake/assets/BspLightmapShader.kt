package org.demoth.cake.assets

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.Shader
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.shaders.BaseShader
import com.badlogic.gdx.graphics.g3d.utils.RenderContext
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.GdxRuntimeException

/**
 * Lightmap texture for BSP brush surfaces.
 *
 * The shader samples this texture via UV2 (`a_texCoord1`) and multiplies it with diffuse albedo.
 */
class BspLightmapTextureAttribute(texture: com.badlogic.gdx.graphics.Texture) : TextureAttribute(Type, texture) {
    companion object {
        @property:JvmStatic val Alias: String = "bspLightmapTexture"
        @property:JvmStatic val Type: Long = register(Alias)
        @JvmStatic fun init() {
            // Custom TextureAttribute subtypes must extend the accepted bitmask,
            // otherwise TextureAttribute constructor throws "Invalid type specified".
            Mask = Mask or Type
        }
    }
}

/**
 * Shader for BSP surfaces with baked static lightmaps.
 *
 * This is intentionally unlit (idTech2 style): output = diffuse * lightmap * material tint.
 * Runtime transparency still follows material blending/depth attributes.
 */
class BspLightmapShader : BaseShader() {
    private lateinit var shaderProgram: ShaderProgram

    private val uProjViewTrans = register(Uniform("u_projViewTrans"))
    private val uWorldTrans = register(Uniform("u_worldTrans"))
    private val uDiffuseTexture = register(Uniform("u_diffuseTexture"))
    private val uLightmapTexture = register(Uniform("u_lightmapTexture"))
    private val uDiffuseUvTransform = register(Uniform("u_diffuseUVTransform"))
    private val uTint = register(Uniform("u_tint"))
    private val uOpacity = register(Uniform("u_opacity"))

    override fun init() {
        shaderProgram = ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        if (!shaderProgram.isCompiled) {
            throw GdxRuntimeException("Failed to compile BSP lightmap shader: ${shaderProgram.log}")
        }
        super.init(shaderProgram, null)
    }

    override fun begin(camera: Camera, context: RenderContext) {
        this.camera = camera
        this.context = context
        shaderProgram.bind()
        set(uProjViewTrans, camera.combined)
    }

    override fun render(renderable: Renderable) {
        val diffuse = renderable.material.get(TextureAttribute.Diffuse) as? TextureAttribute ?: return
        // Material copies (e.g. Model -> ModelInstance) can preserve the custom type id while
        // storing it as base TextureAttribute, so cast by base type here.
        val lightmap = renderable.material.get(BspLightmapTextureAttribute.Type) as? TextureAttribute ?: return

        val blending = renderable.material.get(BlendingAttribute.Type) as? BlendingAttribute
        if (blending != null) {
            context.setBlending(true, blending.sourceFunction, blending.destFunction)
        } else {
            context.setBlending(false, GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        }

        val depth = renderable.material.get(DepthTestAttribute.Type) as? DepthTestAttribute
        if (depth != null) {
            context.setDepthTest(depth.depthFunc, depth.depthRangeNear, depth.depthRangeFar)
            context.setDepthMask(depth.depthMask)
        } else {
            context.setDepthTest(GL20.GL_LEQUAL, 0f, 1f)
            context.setDepthMask(true)
        }

        // Do not force BSP face culling here. Legacy brush winding/culling expectations differ across assets,
        // and forced BACK culling can drop entire world geometry.
        context.setCullFace(GL20.GL_NONE)

        val diffuseTextureUnit = context.textureBinder.bind(diffuse.textureDescription)
        val lightmapTextureUnit = context.textureBinder.bind(lightmap.textureDescription)
        set(uDiffuseTexture, diffuseTextureUnit)
        set(uLightmapTexture, lightmapTextureUnit)
        set(uDiffuseUvTransform, diffuse.offsetU, diffuse.offsetV, diffuse.scaleU, diffuse.scaleV)

        val tint = (renderable.material.get(ColorAttribute.Diffuse) as? ColorAttribute)?.color
        if (tint != null) {
            set(uTint, tint.r, tint.g, tint.b, tint.a)
        } else {
            set(uTint, 1f, 1f, 1f, 1f)
        }
        set(uOpacity, blending?.opacity ?: 1f)
        set(uWorldTrans, renderable.worldTransform)

        renderable.meshPart.render(shaderProgram)
    }

    override fun end() = Unit

    override fun canRender(renderable: Renderable): Boolean {
        return renderable.material.has(TextureAttribute.Diffuse) &&
            renderable.material.has(BspLightmapTextureAttribute.Type)
    }

    override fun compareTo(other: Shader): Int {
        return if (other === this) 0 else 1
    }

    override fun dispose() {
        shaderProgram.dispose()
        super.dispose()
    }

    companion object {
        private const val VERTEX_SHADER = """
attribute vec3 a_position;
attribute vec2 a_texCoord0;
attribute vec2 a_texCoord1;

uniform mat4 u_projViewTrans;
uniform mat4 u_worldTrans;
uniform vec4 u_diffuseUVTransform;

varying vec2 v_diffuseUv;
varying vec2 v_lightmapUv;

void main() {
    v_diffuseUv = a_texCoord0 * u_diffuseUVTransform.zw + u_diffuseUVTransform.xy;
    v_lightmapUv = a_texCoord1;
    gl_Position = u_projViewTrans * u_worldTrans * vec4(a_position, 1.0);
}
"""

        private const val FRAGMENT_SHADER = """
#ifdef GL_ES
precision mediump float;
#endif

uniform sampler2D u_diffuseTexture;
uniform sampler2D u_lightmapTexture;
uniform vec4 u_tint;
uniform float u_opacity;

varying vec2 v_diffuseUv;
varying vec2 v_lightmapUv;

void main() {
    vec4 albedo = texture2D(u_diffuseTexture, v_diffuseUv);
    vec3 light = texture2D(u_lightmapTexture, v_lightmapUv).rgb;
    // Safety guard: if sampled lightmap is effectively black (invalid upload/UV path),
    // fall back to unlit albedo to avoid fully disappearing world geometry.
    if (max(light.r, max(light.g, light.b)) < 0.001) {
        light = vec3(1.0);
    }
    gl_FragColor = vec4(albedo.rgb * light * u_tint.rgb, albedo.a * u_tint.a * u_opacity);
}
"""
    }
}

/**
 * Shared shader provider for MD2 VAT and BSP lightmapped brush surfaces.
 */
class Md2ShaderProvider(
    private val md2Shader: Shader,
    private val bspLightmapShader: Shader? = null,
) : com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider() {
    override fun getShader(renderable: Renderable): Shader {
        if (bspLightmapShader != null && bspLightmapShader.canRender(renderable)) {
            return bspLightmapShader
        }
        return if (renderable.userData is Md2CustomData &&
            renderable.material.has(AnimationTextureAttribute.Type)
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
