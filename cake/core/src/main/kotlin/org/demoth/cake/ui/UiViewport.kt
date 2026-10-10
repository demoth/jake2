package org.demoth.cake.ui

import com.badlogic.gdx.utils.viewport.ExtendViewport

/** Shared UI units: a 4:3 reference area, extended to cover the window without stretching. */
class UiViewport : ExtendViewport(960f, 720f) {
    fun setReferenceHeight(requestedHeight: Float): Boolean {
        // Keep enough room for the existing forms. Invalid values use the default reference.
        val height = if (requestedHeight.isFinite()) requestedHeight.coerceAtLeast(640f) else 720f
        if (height == minWorldHeight) return false
        minWorldWidth = height * 4f / 3f
        minWorldHeight = height
        return true
    }
}
