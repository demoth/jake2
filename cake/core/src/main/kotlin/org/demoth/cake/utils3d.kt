package org.demoth.cake

import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.MathUtils.degRad
import com.badlogic.gdx.math.Vector3
import org.demoth.cake.assets.AnimationTextureAttribute
import org.demoth.cake.assets.Md2CustomData
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
 * `CL_input.ClampPitch()` and `PMove.PM_ClampAngles()`
 */
fun clampPitch(value: Float): Float {
    return wrapSignedAngle(value).coerceIn(-89f, 89f)
}
