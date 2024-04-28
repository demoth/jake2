package org.demoth.cake.stages

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import ktx.app.KtxScreen


class Game3dScreen(val cam: Camera) : KtxScreen {
    val instance: ModelInstance
    val modelBatch: ModelBatch

    init {
        // create camera
        cam.update()

        // create
        val modelBuilder = ModelBuilder()
        val model = modelBuilder.createBox(
            5f, 5f, 5f,
            Material(ColorAttribute.createDiffuse(Color.GREEN)),
            (Usage.Position or Usage.Normal).toLong()
        )
        instance = ModelInstance(model)
        modelBatch = ModelBatch()
    }

    override fun render(delta: Float) {
        modelBatch.begin(cam);
        modelBatch.render(instance);
        modelBatch.end();
    }

    override fun dispose() {
        modelBatch.dispose()

    }
}