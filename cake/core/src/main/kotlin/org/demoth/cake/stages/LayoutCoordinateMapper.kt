package org.demoth.cake.stages

internal object LayoutCoordinateMapper {
    /**
     * Converts an IdTech2 top-left anchored image y to libGDX bottom-left y.
     */
    fun imageY(idTech2Y: Int, imageHeight: Int, screenHeight: Int): Int {
        return screenHeight - idTech2Y - imageHeight
    }

    /**
     * Converts an IdTech2 top-left text y to libGDX text baseline y.
     */
    fun textY(idTech2Y: Int, screenHeight: Int): Int {
        return screenHeight - idTech2Y
    }
}
