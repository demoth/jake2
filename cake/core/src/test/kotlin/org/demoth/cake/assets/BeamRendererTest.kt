package org.demoth.cake.assets

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.GdxNativesLoader
import jake2.qcommon.math.Vector3f
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/** Empty libGDX models exercise transforms/material requests/cache ownership without GL or game assets. */
class BeamRendererTest {
    @Test fun `repeated beams share geometry while preserving palette opacity and diameter`() {
        val materials = mutableListOf<Material>()
        var disposed = 0
        val renderer = BeamRenderer({ index -> Color(index / 255f, .5f, .25f, 1f) }, { material ->
            materials += material
            object : Model() { override fun dispose() { disposed++; super.dispose() } }
        })
        try {
            val first = renderer.prepare(Vector3f.zero, Vector3f(0f, 10f, 0f), 0xD0, 4f, .3f)!!
            assertEquals(Vector3(0f, 5f, 0f), first.transform.getTranslation(Vector3()))
            assertEquals(Vector3(2f, 10f, 2f), first.transform.getScale(Vector3()))
            repeat(100) { assertSame(first, renderer.prepare(Vector3f.zero, Vector3f(20f, 0f, 0f), 0xD0, 4f, .3f)) }
            assertEquals(1, materials.size)
            assertEquals(.3f, (materials.single().get(BlendingAttribute.Type) as BlendingAttribute).opacity)
            assertEquals(0xD0 / 255f, (materials.single().get(ColorAttribute.Diffuse) as ColorAttribute).color.r)
            assertNotSame(first, renderer.prepare(Vector3f.zero, Vector3f.unitX, 0xD1, 4f, .3f))
            renderer.prepare(Vector3f.zero, Vector3f.unitX, 0xD0, 4f, 1f)
            assertEquals(3, materials.size)
            assertFalse(materials.last().has(BlendingAttribute.Type))
        } finally { renderer.dispose() }
        assertEquals(3, disposed)
    }

    @Test fun `vertical and short beams produce finite poses and empty or invisible beams allocate no models`() {
        var created = 0
        val renderer = BeamRenderer({ Color.WHITE }, { created++; Model() })
        try {
            assertNull(renderer.prepare(Vector3f.zero, Vector3f.zero, 1, 4f, .3f))
            assertNull(renderer.prepare(Vector3f.zero, Vector3f.unitX, 1, 0f, .3f))
            assertNull(renderer.prepare(Vector3f.zero, Vector3f.unitX, 1, 4f, 0f))
            assertEquals(0, created)
            for (end in listOf(Vector3f(0f, 0f, 10f), Vector3f(0f, -10f, 0f), Vector3f(.01f, 0f, 0f))) {
                val instance = renderer.prepare(Vector3f.zero, end, 1, 4f, .3f)!!
                assertTrue(instance.transform.`val`.all { it.isFinite() })
                assertTrue(instance.transform.getTranslation(Vector3()).epsilonEquals(Vector3(end.x, end.y, end.z).scl(.5f), .001f))
            }
        } finally { renderer.dispose() }
    }

    companion object { @BeforeAll @JvmStatic fun loadMathNatives() { GdxNativesLoader.load() } }
}
