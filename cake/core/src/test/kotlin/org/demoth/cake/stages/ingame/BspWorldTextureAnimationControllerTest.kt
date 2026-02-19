package org.demoth.cake.stages.ingame

import org.demoth.cake.assets.BspWorldTextureInfoRecord
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BspWorldTextureAnimationControllerTest {

    @Test
    fun resolveTextureAnimationChainFollowsNextLinksUntilCycle() {
        val textureInfos = mapOf(
            10 to textureInfo(10, "comp_0", 11),
            11 to textureInfo(11, "comp_1", 12),
            12 to textureInfo(12, "comp_2", 10),
        )

        val chain = resolveTextureAnimationChain(10, textureInfos)

        assertArrayEquals(intArrayOf(10, 11, 12), chain)
    }

    @Test
    fun resolveTextureAnimationChainStopsOnNonPositiveOrMissingNext() {
        val textureInfos = mapOf(
            2 to textureInfo(2, "button", 0),
            5 to textureInfo(5, "panel", 99),
        )

        assertArrayEquals(intArrayOf(2), resolveTextureAnimationChain(2, textureInfos))
        assertArrayEquals(intArrayOf(5), resolveTextureAnimationChain(5, textureInfos))
        assertArrayEquals(intArrayOf(), resolveTextureAnimationChain(42, textureInfos))
    }

    @Test
    fun selectTextureAnimationTexInfoUsesLegacyTwoHertzCadence() {
        val chain = intArrayOf(20, 21, 22)

        assertEquals(20, selectTextureAnimationTexInfo(chain, 0))
        assertEquals(20, selectTextureAnimationTexInfo(chain, 499))
        assertEquals(21, selectTextureAnimationTexInfo(chain, 500))
        assertEquals(22, selectTextureAnimationTexInfo(chain, 1000))
        assertEquals(20, selectTextureAnimationTexInfo(chain, 1500))
    }

    private fun textureInfo(index: Int, name: String, next: Int) = BspWorldTextureInfoRecord(
        textureInfoIndex = index,
        textureName = name,
        textureFlags = 0,
        textureAnimationNext = next
    )
}
