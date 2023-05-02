package jake2.qcommon.math

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class Vector3f(val x: Float, val y: Float, val z: Float) {
    constructor(a: FloatArray): this(a[0], a[1], a[2])

    companion object {
        val zero = Vector3f(0f, 0f, 0f)
        val one = Vector3f(1f, 1f, 1f)
        val unitX = Vector3f(1f, 0f, 0f)
        val unitY = Vector3f(0f, 1f, 0f)
        val unitZ = Vector3f(0f, 0f, 1f)
    }

    operator fun plus(other: Vector3f) = Vector3f(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3f) = Vector3f(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Float) = Vector3f(x * scalar, y * scalar, z * scalar)
    operator fun div(scalar: Float) = Vector3f(x / scalar, y / scalar, z / scalar)
    operator fun unaryMinus() = Vector3f(-x, -y, -z)

    infix fun dot(other: Vector3f) = x * other.x + y * other.y + z * other.z
    infix fun cross(other: Vector3f) = Vector3f(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )

    fun length() = sqrt(x * x + y * y + z * z)
    fun normalize() = this / length()
    fun distance(other: Vector3f) = (this - other).length()
    fun angle(other: Vector3f) = acos((this dot other) / (this.length() * other.length()))
    
    fun abs() = Vector3f(abs(x), abs(y), abs(z))

    /**
     * Linear interpolation
     */
    fun lerp(other: Vector3f, t: Float) = this + (other - this) * t

    /**
     * Performs spherical linear interpolation (slerp) between this vector and the
     * specified vector.
     *
     * @param other the other vector
     * @param t the interpolation factor, in the range [0, 1]
     * @return the interpolated vector
     */
    fun slerp(other: Vector3f, t: Float): Vector3f {
        // Normalize both vectors
        val a = this.normalize()
        val b = other.normalize()

        // Compute the cosine of the angle between the vectors
        val dot = a dot b

        // If the dot product is close to 1, then the vectors are close together,
        // and we can use simple linear interpolation
        if (dot > 0.9995) {
            return a.lerp(b, t)
        }

        // Clamp the dot product to the range [-1, 1] to avoid errors due to
        // floating-point imprecision
        val theta = acos(max(-1.0f, min(1.0f, dot)))

        // Compute the interpolated vector using spherical linear interpolation
        val s = 1f / sin(theta)
        return (a * sin((1 - t) * theta)) + (b * sin(t * theta)) / s
    }

    fun toArray() = floatArrayOf(x, y ,z)

    fun toAngles(): FloatArray {
        var yaw: Float
        var pitch: Float
        if (y == 0f && x == 0f) {
            yaw = 0f
            pitch = if (z > 0) 90f else 270f
        } else {
            yaw = if (x != 0f) (atan2(y, x) * 180 / Math.PI).toInt().toFloat() // todo: implement proper rounding
            else if (y > 0) 90f
            else -90f

            if (yaw < 0)
                yaw += 360f
            val forward = sqrt((x * x + y * y))
            pitch = (atan2(z, forward) * 180 / Math.PI).toInt().toFloat() // todo: implement proper rounding
            if (pitch < 0)
                pitch += 360f
        }
        return floatArrayOf(-pitch, yaw, 0f)
    }
}

fun FloatArray.toVector3f() = Vector3f(this[0], this[1], this[2])
