package org.demoth.cake

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader
import com.badlogic.gdx.math.MathUtils.degRad
import com.badlogic.gdx.math.Vector3
import org.demoth.cake.assets.AnimationTextureAttribute
import org.demoth.cake.assets.Md2Asset
import org.demoth.cake.assets.Md2CustomData
import org.demoth.cake.assets.Md2Loader
import org.demoth.cake.assets.Md2Shader
import org.demoth.cake.assets.getLoaded
import kotlin.math.cos
import kotlin.math.sin

fun toForwardUp(pitchDeg: Float, yawDeg: Float, rollDeg: Float): Pair<Vector3, Vector3> {
    val pitch = pitchDeg * degRad
    val yaw = yawDeg * degRad
    val roll = rollDeg * degRad

    val cp = cos(pitch)
    val sp = sin(pitch)
    val cy = cos(yaw)
    val sy = sin(yaw)
    val cr = cos(roll)
    val sr = sin(roll)

    // Matches Math3D.AngleVectors from the original client code.
    val forward = Vector3(
        cp * cy,
        cp * sy,
        -sp
    )
    val up = Vector3(
        cr * sp * cy + sr * sy,
        cr * sp * sy - sr * cy,
        cr * cp
    )

    return forward to up
}

fun lerpAngle(from: Float, to: Float, fraction: Float): Float {
    var delta = to - from
    if (delta > 180) delta -= 360
    if (delta < -180) delta += 360
    return from + delta * fraction
}

// attach custom data to animated md2 models
fun createModelInstance(model: Model): ModelInstance {
    return ModelInstance(model).apply {
        if (model.materials.any { it.has(AnimationTextureAttribute.Type) }) {
            userData = Md2CustomData.empty()
        }
    }
}

/**
 * Applies per-instance opacity for model-backed entities/effects.
 *
 * Opaque path removes blending and enables depth writes.
 * Translucent path enables standard alpha blending and disables depth writes.
 *
 * Legacy counterpart:
 * `client/CL_ents.AddPacketEntities` + `client/ref_gl` translucent entity rendering.
 */
fun applyModelOpacity(instance: ModelInstance, opacity: Float, forceTranslucent: Boolean = false) {
    val clampedOpacity = opacity.coerceIn(0f, 1f)
    val translucent = forceTranslucent || clampedOpacity < 1f
    instance.materials.forEach { material ->
        val depth = material.get(DepthTestAttribute.Type) as? DepthTestAttribute
        if (translucent) {
            val blending = material.get(BlendingAttribute.Type) as? BlendingAttribute
            if (blending == null) {
                material.set(BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, clampedOpacity))
            } else {
                blending.sourceFunction = GL20.GL_SRC_ALPHA
                blending.destFunction = GL20.GL_ONE_MINUS_SRC_ALPHA
                blending.opacity = clampedOpacity
            }
            if (depth == null) {
                material.set(DepthTestAttribute(GL20.GL_LEQUAL, 0f, 1f, false))
            } else {
                depth.depthMask = false
            }
        } else {
            material.remove(BlendingAttribute.Type)
            if (depth != null) {
                depth.depthMask = true
            }
        }
    }
}

/**
 * Normalizes an angle to the signed range (-180, 180].
 *
 * This representation is preferred in input/prediction code because difference/rebase operations
 * remain intuitive when crossing the 0/360 seam.
 *
 * `CL_input.ClampPitch()`
 */
fun wrapSignedAngle(value: Float): Float {
    var angle = value
    while (angle <= -180f) angle += 360f
    while (angle > 180f) angle -= 360f
    return angle
}

/**
 * Applies Quake-style view pitch limits after signed normalization.
 *
 * Pitch is clamped to +/-89 to match PMove constraints and avoid producing commands outside
 * server-accepted vertical look range.
 *
 * `CL_input.ClampPitch()` and `pmove_t.clampAngles()`
 */
fun clampPitch(value: Float): Float {
    return wrapSignedAngle(value).coerceIn(-89f, 89f)
}

// helper function to ensure begin/end call is made
fun ModelBatch.use(camera: Camera, action: (ModelBatch) -> Unit) {
    begin(camera)
    action(this)
    end()
}

// bootstrap md2 shader with an embedded model
fun initializeMd2Shader(assetManager: AssetManager): Md2Shader {
    val bootstrapMd2Path = "jake2/qcommon/filesystem/models/blade/tris.md2"
    val loadedForShaderInit = !assetManager.isLoaded(bootstrapMd2Path, Md2Asset::class.java)
    val md2Asset = assetManager.getLoaded<Md2Asset>(
        bootstrapMd2Path,
        Md2Loader.Parameters(
            loadEmbeddedSkins = false,
            useDefaultSkinIfMissing = true,
        )
    )
    try {
        val md2Instance = createModelInstance(md2Asset.model)
        val tempRenderable = Renderable()
        val md2Shader = Md2Shader(
            // required for shader initialization; renderable is not reused
            md2Instance.getRenderable(tempRenderable),
            DefaultShader.Config(
                assetManager.get(md2VatShader),
                assetManager.get(md2FragmentShader),
            )
        )
        md2Shader.init()
        return md2Shader
    } finally {
        if (loadedForShaderInit) {
            assetManager.unload(bootstrapMd2Path)
        }
    }
}
