package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array as GdxArray
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.Pool
import org.demoth.cake.assets.md2.Md2CustomData
import org.demoth.cake.createModelInstance

/**
 * Model-instance submission for beam geometry, using libGDX pools and the existing ModelBatch pass.
 * Models belong to the asset catalog. Only instances are pooled here; clearing/disposal never unloads models.
 */
internal class BeamModelRenderer(private val modelProvider: (String) -> Model?) : BeamSegmentSink, Disposable {
    private class Instances(val model: Model) {
        val active = GdxArray<ModelInstance>()
        val pool = object : Pool<ModelInstance>(16, 4096) {
            override fun newObject(): ModelInstance = createModelInstance(model)
        }
    }
    private val instances = mutableMapOf<BeamKind, Instances>()

    fun beginFrame() {
        instances.values.forEach {
            it.pool.freeAll(it.active)
            it.active.clear()
        }
    }

    override fun submit(kind: BeamKind, x: Float, y: Float, z: Float, pitch: Float, yaw: Float, roll: Float) {
        val model = modelProvider(kind.modelPath) ?: return // Expansion assets remain optional in base-game profiles.
        val group = instances[kind]?.takeIf { it.model === model } ?: Instances(model).also { replacement ->
            instances.remove(kind)?.let { it.active.clear(); it.pool.clear() }
            instances[kind] = replacement
        }
        val instance = group.pool.obtain()
        instance.transform.idt().rotate(Vector3.Z, yaw).rotate(Vector3.Y, pitch).rotate(Vector3.X, -roll)
            .setTranslation(x, y, z)
        (instance.userData as? Md2CustomData)?.let {
            it.frame1 = 0; it.frame2 = 0; it.interpolation = 0f
            if (kind == BeamKind.LIGHTNING) {
                it.lightRed = 1f; it.lightGreen = 1f; it.lightBlue = 1f
                it.shadeVectorX = 0f; it.shadeVectorY = 0f; it.shadeVectorZ = 0f
            }
        }
        group.active.add(instance)
    }

    fun forEachInstance(submit: (ModelInstance) -> Unit) {
        instances.values.forEach { group -> group.active.forEach(submit) }
    }

    fun render(modelBatch: ModelBatch) = forEachInstance { modelBatch.render(it) }

    override fun dispose() {
        instances.values.forEach { it.active.clear(); it.pool.clear() }
        instances.clear()
    }
}
