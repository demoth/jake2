package org.demoth.cake

import com.badlogic.gdx.math.MathUtils.degRad
import com.badlogic.gdx.math.Vector3
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
