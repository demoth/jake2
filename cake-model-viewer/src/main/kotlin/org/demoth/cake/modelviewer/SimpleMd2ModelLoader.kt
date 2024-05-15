package org.demoth.cake.modelviewer

import com.badlogic.gdx.assets.loaders.FileHandleResolver
import com.badlogic.gdx.assets.loaders.ModelLoader
import com.badlogic.gdx.assets.loaders.ModelLoader.ModelParameters
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.g3d.model.data.ModelData

// Load quake 2 static models
class SimpleMd2ModelLoader(resolver: FileHandleResolver): ModelLoader<Md2ModelParameters>(resolver) {
    override fun loadModelData(fileHandle: FileHandle?, parameters: Md2ModelParameters?): ModelData {
        TODO("Not yet implemented")
    }
}

class Md2ModelParameters: ModelParameters() {

}
