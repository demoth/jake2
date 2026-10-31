package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.GdxNativesLoader
import jake2.qcommon.math.Vector3f
import org.demoth.cake.assets.md2.Md2CustomData
import org.demoth.cake.stages.ingame.DynamicLightSystem
import org.demoth.cake.stages.ingame.RenderTuningCvars
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class ExplosionEffectTest {
    @Test fun `lights fade with animation remain stable on pause and disappear when animation ends even without models`() {
        val lights = DynamicLightSystem()
        val origin = Vector3(10f, 20f, 30f)
        val effect = ExplosionEffect(ExplosionType.MISC, 0, 100, 0, 4, origin,
            light = ExplosionLight(150f, Vector3f(.2f, .4f, .8f)))
        origin.setZero()
        fun radius(now: Int): Float? {
            effect.update(now, .01f)
            lights.beginFrame(now, .01f)
            effect.collectDynamicLights(lights)
            val result = lights.visibleLightsForShader().singleOrNull()
            if (result != null) {
                assertEquals(Vector3(10f, 20f, 30f), result.origin)
                assertEquals(.2f, result.red); assertEquals(.4f, result.green); assertEquals(.8f, result.blue)
            }
            return result?.radius
        }
        assertEquals(150f, radius(0))
        assertEquals(75f, radius(150))
        repeat(10) { assertEquals(75f, radius(150)) }
        assertNull(radius(300))
    }

    @Test fun `MD2 frames skin interpolation and stock material opacity use the same animation as the light`() {
        val model = Model()
        try {
            val instance = ModelInstance(model).apply {
                materials.add(Material())
                userData = Md2CustomData.empty()
            }
            val effect = ExplosionMd2Effect(instance, ExplosionType.MISC, 0, 100, 0, 4, Vector3(1f, 2f, 3f),
                baseSkinIndex = 2, startsTranslucent = true, light = ExplosionLight(150f, Vector3f.one))
            assertTrue(effect.update(150, .1f))
            val data = instance.userData as Md2CustomData
            assertEquals(1, data.frame1); assertEquals(2, data.frame2); assertEquals(.5f, data.interpolation)
            assertEquals(2, data.skinIndex)
            assertEquals(.5f, (instance.materials.first().get(BlendingAttribute.Type) as BlendingAttribute).opacity)
            assertEquals(Vector3(1f, 2f, 3f), instance.transform.getTranslation(Vector3()))
            val lights = DynamicLightSystem()
            lights.beginFrame(150, 0f)
            effect.collectDynamicLights(lights)
            assertEquals(75f, lights.visibleLightsForShader().single().radius)
            assertFalse(effect.update(300, .1f))
        } finally { model.dispose() }
    }

    @Test fun `opaque polygon models become translucent with the selected late skins and alpha`() {
        val model = Model()
        try {
            val instance = ModelInstance(model).apply { materials.add(Material()); userData = Md2CustomData.empty() }
            val effect = ExplosionMd2Effect(instance, ExplosionType.POLY, 0, 100, 0, 15, Vector3.Zero, fullBright = true)
            effect.update(800, 0f)
            assertFalse(instance.materials.first().has(BlendingAttribute.Type))
            assertEquals(4, (instance.userData as Md2CustomData).skinIndex)
            effect.update(1300, 0f)
            assertEquals(6, (instance.userData as Md2CustomData).skinIndex)
            assertEquals(3f / 16f, (instance.materials.first().get(BlendingAttribute.Type) as BlendingAttribute).opacity)
        } finally { model.dispose() }
    }

    companion object {
        @BeforeAll @JvmStatic fun setup() { GdxNativesLoader.load(); RenderTuningCvars.register() }
    }
}
