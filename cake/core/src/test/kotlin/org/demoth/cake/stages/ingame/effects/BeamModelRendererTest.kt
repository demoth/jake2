package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.GdxNativesLoader
import org.demoth.cake.assets.md2.Md2CustomData
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/** Uses empty libGDX models and matrices, without meshes, textures, GL, windows or installed content. */
class BeamModelRendererTest {
    private fun instances(renderer: BeamModelRenderer): List<ModelInstance> = buildList { renderer.forEachInstance { add(it) } }

    @Test fun `frame rebuilds reuse instances instead of accumulating segments`() {
        val model = Model()
        val renderer = BeamModelRenderer { model }
        try {
            renderer.submit(BeamKind.CABLE, 1f, 2f, 3f, 0f, 90f, 0f)
            renderer.submit(BeamKind.CABLE, 4f, 5f, 6f, 0f, 0f, 0f)
            val initial = instances(renderer).toSet()
            assertEquals(2, initial.size)
            repeat(100) {
                renderer.beginFrame()
                renderer.submit(BeamKind.CABLE, 7f, 8f, 9f, 0f, 90f, 0f)
                renderer.submit(BeamKind.CABLE, 10f, 11f, 12f, 0f, 0f, 0f)
                assertEquals(initial, instances(renderer).toSet())
            }
            val first = instances(renderer).first()
            assertEquals(Vector3(7f, 8f, 9f), first.transform.getTranslation(Vector3()))
            val forward = Vector3.X.cpy().rot(first.transform)
            assertTrue(forward.epsilonEquals(Vector3.Y, .0001f))
            renderer.beginFrame()
            assertTrue(instances(renderer).isEmpty())
        } finally { renderer.dispose(); model.dispose() }
    }

    @Test fun `missing optional assets are skipped and later model replacements discard stale instances`() {
        val firstModel = Model()
        val secondModel = Model()
        var available: Model? = null
        val renderer = BeamModelRenderer { available }
        try {
            renderer.submit(BeamKind.LIGHTNING, 0f, 0f, 0f, 0f, 0f, 0f)
            assertTrue(instances(renderer).isEmpty())
            available = firstModel
            renderer.submit(BeamKind.LIGHTNING, 0f, 0f, 0f, 0f, 0f, 0f)
            val original = instances(renderer).single()
            renderer.beginFrame()
            available = secondModel
            renderer.submit(BeamKind.LIGHTNING, 0f, 0f, 0f, 0f, 0f, 0f)
            assertNotSame(original, instances(renderer).single())
            assertSame(secondModel, instances(renderer).single().model)
        } finally { renderer.dispose(); firstModel.dispose(); secondModel.dispose() }
    }

    @Test fun `lightning restores full-bright frame state when a model instance is reused`() {
        val model = Model()
        val renderer = BeamModelRenderer { model }
        try {
            renderer.submit(BeamKind.LIGHTNING, 0f, 0f, 0f, 0f, 0f, 0f)
            val instance = instances(renderer).single()
            instance.userData = Md2CustomData.empty().apply {
                frame1 = 9; frame2 = 10; interpolation = .5f
                lightRed = 0f; lightGreen = 0f; lightBlue = 0f
                shadeVectorX = 1f; shadeVectorY = 1f; shadeVectorZ = 1f
            }
            renderer.beginFrame()
            renderer.submit(BeamKind.LIGHTNING, 0f, 0f, 0f, 0f, 0f, 0f)
            assertSame(instance, instances(renderer).single())
            val data = instance.userData as Md2CustomData
            assertEquals(0, data.frame1); assertEquals(0, data.frame2); assertEquals(0f, data.interpolation)
            assertEquals(1f, data.lightRed); assertEquals(1f, data.lightGreen); assertEquals(1f, data.lightBlue)
            assertEquals(0f, data.shadeVectorX); assertEquals(0f, data.shadeVectorY); assertEquals(0f, data.shadeVectorZ)
        } finally { renderer.dispose(); model.dispose() }
    }

    @Test fun `disposal releases instances and leaves shared model lifetime to the asset owner`() {
        var disposed = false
        val model = object : Model() {
            override fun dispose() { disposed = true; super.dispose() }
        }
        val renderer = BeamModelRenderer { model }
        renderer.submit(BeamKind.GRAPPLE, 0f, 0f, 0f, 0f, 0f, 0f)
        renderer.dispose()
        assertTrue(instances(renderer).isEmpty())
        assertFalse(disposed)
        model.dispose()
        assertTrue(disposed)
    }

    @Test fun `heatbeam model instances select their player or remote frame and full-bright lighting`() {
        for ((kind, frame) in listOf(BeamKind.HEATBEAM_PLAYER to 1, BeamKind.HEATBEAM_MONSTER to 2)) {
            val model = Model()
            val renderer = BeamModelRenderer { model }
            try {
                renderer.submit(kind, 0f, 0f, 0f, 0f, 0f, 0f)
                val instance = instances(renderer).single()
                instance.userData = Md2CustomData.empty().apply { lightRed = 0f; frame1 = 99 }
                renderer.beginFrame()
                renderer.submit(kind, 0f, 0f, 0f, 0f, 0f, 30f)
                val data = instance.userData as Md2CustomData
                assertEquals(frame, data.frame1)
                assertEquals(frame, data.frame2)
                assertEquals(0f, data.interpolation)
                assertEquals(1f, data.lightRed)
                assertSame(instance, instances(renderer).single())
            } finally { renderer.dispose(); model.dispose() }
        }
    }

    companion object {
        @BeforeAll @JvmStatic fun loadMathNatives() { GdxNativesLoader.load() }
    }
}
