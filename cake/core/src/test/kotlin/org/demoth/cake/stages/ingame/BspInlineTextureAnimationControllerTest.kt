package org.demoth.cake.stages.ingame

import org.junit.Assert.assertEquals
import org.junit.Test

class BspInlineTextureAnimationControllerTest {

    @Test
    fun selectTextureAnimationTexInfoByEntityFrameCyclesByFrameModulo() {
        val chain = intArrayOf(11, 12, 13)

        assertEquals(11, selectTextureAnimationTexInfoByEntityFrame(chain, 0))
        assertEquals(12, selectTextureAnimationTexInfoByEntityFrame(chain, 1))
        assertEquals(13, selectTextureAnimationTexInfoByEntityFrame(chain, 2))
        assertEquals(11, selectTextureAnimationTexInfoByEntityFrame(chain, 3))
    }

    @Test
    fun selectTextureAnimationTexInfoByEntityFrameHandlesNegativeFrames() {
        val chain = intArrayOf(20, 21, 22, 23)

        assertEquals(23, selectTextureAnimationTexInfoByEntityFrame(chain, -1))
        assertEquals(22, selectTextureAnimationTexInfoByEntityFrame(chain, -2))
        assertEquals(20, selectTextureAnimationTexInfoByEntityFrame(chain, -4))
    }

    @Test
    fun selectTextureAnimationTexInfoByEntityFrameReturnsNullForEmptyChain() {
        assertEquals(null, selectTextureAnimationTexInfoByEntityFrame(intArrayOf(), 7))
    }
}
