package jake2.qcommon

fun lerpI(a: Int, b: Int, t: Float): Int {
    return (a.toFloat() + (b.toFloat() - a.toFloat()) * t).toInt()
}
