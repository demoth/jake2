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

/** Optional second lightstyle slot texture for a BSP face. */
class BspLightmapTexture1Attribute(texture: com.badlogic.gdx.graphics.Texture) : TextureAttribute(Type, texture) {
    companion object {
        @property:JvmStatic val Alias: String = "bspLightmapTexture1"
        @property:JvmStatic val Type: Long = register(Alias)
        @JvmStatic fun init() {
            Mask = Mask or Type
        }
    }
}

/** Optional third lightstyle slot texture for a BSP face. */
class BspLightmapTexture2Attribute(texture: com.badlogic.gdx.graphics.Texture) : TextureAttribute(Type, texture) {
    companion object {
        @property:JvmStatic val Alias: String = "bspLightmapTexture2"
        @property:JvmStatic val Type: Long = register(Alias)
        @JvmStatic fun init() {
            Mask = Mask or Type
        }
    }
}

/** Optional fourth lightstyle slot texture for a BSP face. */
class BspLightmapTexture3Attribute(texture: com.badlogic.gdx.graphics.Texture) : TextureAttribute(Type, texture) {
    companion object {
        @property:JvmStatic val Alias: String = "bspLightmapTexture3"
        @property:JvmStatic val Type: Long = register(Alias)
        @JvmStatic fun init() {
            Mask = Mask or Type
        }
    }
}

/**
 * Shader for BSP surfaces with baked static lightmaps.
 *
 * This is intentionally unlit (idTech2 style): output = diffuse * sum(lightmapStyleSlot[i] * styleWeight[i]).
 * Runtime transparency still follows material blending/depth attributes.
 *
 * Invariants:
 * - lightmap texture attributes 0..3 map to style slots 0..3.
 * - style slot weights are read from `ColorAttribute.Diffuse` channels (r/g/b/a).
 * - material copies may downcast custom lightmap attributes to base [TextureAttribute], so lookups cast to base type.
 *
 * Legacy references:
 * - `client/render/fast/Light.R_BuildLightMap` (style-slot accumulation with `Defines.MAXLIGHTMAPS`).
 * - `qcommon/Defines.MAXLIGHTMAPS` (=4).
 *
 * Ownership/lifecycle:
 * - created and initialized in [org.demoth.cake.stages.ingame.Game3dScreen],
 * - selected via [Md2ShaderProvider] for BSP world surfaces,
 * - disposed by shader provider during screen teardown.
 */
class BspLightmapShader : BaseShader() {
    private lateinit var shaderProgram: ShaderProgram

    private val uProjViewTrans = register(Uniform("u_projViewTrans"))
    private val uWorldTrans = register(Uniform("u_worldTrans"))
    private val uDiffuseTexture = register(Uniform("u_diffuseTexture"))
    private val uLightmapTexture0 = register(Uniform("u_lightmapTexture0"))
    private val uLightmapTexture1 = register(Uniform("u_lightmapTexture1"))
    private val uLightmapTexture2 = register(Uniform("u_lightmapTexture2"))
    private val uLightmapTexture3 = register(Uniform("u_lightmapTexture3"))
    private val uDiffuseUvTransform = register(Uniform("u_diffuseUVTransform"))
    private val uLightStyleWeights = register(Uniform("u_lightStyleWeights"))
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
        // Material copies (e.g. Model -> ModelInstance) can preserve custom type id while storing
        // as base TextureAttribute, so cast by base type.
        val lightmap0 = renderable.material.get(BspLightmapTextureAttribute.Type) as? TextureAttribute ?: return
        val lightmap1 = renderable.material.get(BspLightmapTexture1Attribute.Type) as? TextureAttribute
        val lightmap2 = renderable.material.get(BspLightmapTexture2Attribute.Type) as? TextureAttribute
        val lightmap3 = renderable.material.get(BspLightmapTexture3Attribute.Type) as? TextureAttribute

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

        // Legacy fast renderer keeps culling enabled globally with GL_FRONT and also does per-surface
        // planeback tests in world/inline brush passes (`Main.R_SetupGL`, `Surf.R_RecursiveWorldNode`,
        // `Surf.R_DrawInlineBModel`). Cake path currently lacks equivalent winding guarantees, so BSP
        // shader keeps culling disabled to avoid dropping valid world faces.
        context.setCullFace(GL20.GL_NONE)

        val diffuseTextureUnit = context.textureBinder.bind(diffuse.textureDescription)
        val lightmapTexture0Unit = context.textureBinder.bind(lightmap0.textureDescription)
        val lightmapTexture1Unit = context.textureBinder.bind((lightmap1 ?: lightmap0).textureDescription)
        val lightmapTexture2Unit = context.textureBinder.bind((lightmap2 ?: lightmap0).textureDescription)
        val lightmapTexture3Unit = context.textureBinder.bind((lightmap3 ?: lightmap0).textureDescription)
        set(uDiffuseTexture, diffuseTextureUnit)
        set(uLightmapTexture0, lightmapTexture0Unit)
        set(uLightmapTexture1, lightmapTexture1Unit)
        set(uLightmapTexture2, lightmapTexture2Unit)
        set(uLightmapTexture3, lightmapTexture3Unit)
        set(uDiffuseUvTransform, diffuse.offsetU, diffuse.offsetV, diffuse.scaleU, diffuse.scaleV)

        // For BSP lightmap shader we encode style weights in Diffuse color channels:
        // r/g/b/a -> lightstyle slot 0/1/2/3.
        val styleWeights = (renderable.material.get(ColorAttribute.Diffuse) as? ColorAttribute)?.color
        val w0 = styleWeights?.r ?: 1f
        val w1 = styleWeights?.g ?: 0f
        val w2 = styleWeights?.b ?: 0f
        val w3 = styleWeights?.a ?: 0f
        set(uLightStyleWeights, w0, w1, w2, w3)
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
uniform sampler2D u_lightmapTexture0;
uniform sampler2D u_lightmapTexture1;
uniform sampler2D u_lightmapTexture2;
uniform sampler2D u_lightmapTexture3;
uniform vec4 u_lightStyleWeights;
uniform float u_opacity;

varying vec2 v_diffuseUv;
varying vec2 v_lightmapUv;

void main() {
    vec4 albedo = texture2D(u_diffuseTexture, v_diffuseUv);
    vec3 light0 = texture2D(u_lightmapTexture0, v_lightmapUv).rgb * u_lightStyleWeights.x;
    vec3 light1 = texture2D(u_lightmapTexture1, v_lightmapUv).rgb * u_lightStyleWeights.y;
    vec3 light2 = texture2D(u_lightmapTexture2, v_lightmapUv).rgb * u_lightStyleWeights.z;
    vec3 light3 = texture2D(u_lightmapTexture3, v_lightmapUv).rgb * u_lightStyleWeights.w;
    vec3 light = light0 + light1 + light2 + light3;
    // Safety guard: if sampled lightmap is effectively black (invalid upload/UV path),
    // fall back to unlit albedo to avoid fully disappearing world geometry.
    if (max(light.r, max(light.g, light.b)) < 0.001) {
        light = vec3(1.0);
    }
    gl_FragColor = vec4(albedo.rgb * light, albedo.a * u_opacity);
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
