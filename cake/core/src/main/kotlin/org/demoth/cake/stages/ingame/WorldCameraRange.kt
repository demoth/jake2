package org.demoth.cake.stages.ingame

import com.badlogic.gdx.math.collision.BoundingBox

/** Q2PRO's set_world_size / GL_Frustum policy, evaluated once when loading a map. */
internal fun worldCameraFar(rootNodeBounds: BoundingBox?): Float {
    if (rootNodeBounds == null) return 4096f
    val size = maxOf(rootNodeBounds.width, rootNodeBounds.height, rootNodeBounds.depth)
    return when {
        size > 4096 -> 16384f
        size > 2048 -> 8192f
        else -> 4096f
    }
}
