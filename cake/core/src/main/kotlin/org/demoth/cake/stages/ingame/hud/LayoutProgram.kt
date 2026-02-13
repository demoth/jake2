package org.demoth.cake.stages.ingame.hud

/**
 * Pre-parsed IdTech2 layout script.
 *
 * This model stores command structure only. Dynamic values (stats, server frame, screen size,
 * config lookups) are resolved later during per-frame evaluation.
 */
data class LayoutProgram(
    val ops: List<LayoutOp>,
)

enum class LayoutXAnchor {
    LEFT,
    RIGHT,
    VIEW,
}

enum class LayoutYAnchor {
    TOP,
    BOTTOM,
    VIEW,
}

sealed interface LayoutOp {
    data class SetX(val anchor: LayoutXAnchor, val value: Int) : LayoutOp
    data class SetY(val anchor: LayoutYAnchor, val value: Int) : LayoutOp

    data class Pic(val statIndex: Int) : LayoutOp
    data class Picn(val picName: String) : LayoutOp
    data class Num(val width: Int, val statIndex: Int) : LayoutOp
    data class StatString(val statIndex: Int) : LayoutOp

    data class Client(
        val xOffset: Int,
        val yOffset: Int,
        val clientIndex: Int,
        val score: Int,
        val ping: Int,
        val time: Int,
    ) : LayoutOp

    data class Ctf(
        val xOffset: Int,
        val yOffset: Int,
        val clientIndex: Int,
        val score: Int,
        val ping: Int,
    ) : LayoutOp

    data class Text(
        val text: String,
        val alt: Boolean,
        val centered: Boolean,
    ) : LayoutOp

    data object HNum : LayoutOp
    data object ANum : LayoutOp
    data object RNum : LayoutOp

    data class IfStat(val statIndex: Int, val body: List<LayoutOp>) : LayoutOp
}
