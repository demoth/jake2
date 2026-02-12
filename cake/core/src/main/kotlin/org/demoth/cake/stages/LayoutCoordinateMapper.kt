package org.demoth.cake.stages

internal object LayoutCoordinateMapper {
    /**
     * Converts a Quake top-left anchored image y to libGDX bottom-left y.
     */
    fun imageY(quakeY: Int, imageHeight: Int, screenHeight: Int): Int {
        return screenHeight - quakeY - imageHeight
    }

    /**
     * Converts a Quake top-left text y to libGDX text baseline y.
     */
    fun textY(quakeY: Int, screenHeight: Int): Int {
        return screenHeight - quakeY
    }
}
