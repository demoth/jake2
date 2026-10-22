package org.demoth.cake.viewer

import com.badlogic.gdx.assets.AssetDescriptor
import com.badlogic.gdx.assets.AssetLoaderParameters
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.FileHandleResolver
import com.badlogic.gdx.assets.loaders.SynchronousAssetLoader
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.Array as GdxArray
import com.badlogic.gdx.utils.Disposable
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PreviewResourcesTest {
    private class Resource(val name: String, val disposed: MutableList<String>) : Disposable {
        override fun dispose() { disposed += name }
    }

    private fun manager(disposed: MutableList<String>) = AssetManager(FileHandleResolver(::FileHandle), false).apply {
        setLoader(Resource::class.java, object : SynchronousAssetLoader<Resource, AssetLoaderParameters<Resource>>(FileHandleResolver(::FileHandle)) {
            override fun load(manager: AssetManager, fileName: String, file: FileHandle, parameter: AssetLoaderParameters<Resource>?): Resource {
                check(fileName != "broken") { "Failed root load" }
                return Resource(fileName, disposed)
            }

            override fun getDependencies(fileName: String, file: FileHandle, parameter: AssetLoaderParameters<Resource>?): GdxArray<AssetDescriptor<*>>? =
                if (fileName == "shared") null else GdxArray<AssetDescriptor<*>>().apply {
                    add(AssetDescriptor("shared", Resource::class.java))
                }
        })
    }

    @Test
    fun closesOwnedObjectsBeforeRootAndPreservesAnotherConsumersSharedDependency() {
        val disposed = mutableListOf<String>()
        val assets = manager(disposed)
        try {
            assets.load("shared", Resource::class.java)
            assets.finishLoading()
            val preview = PreviewResources(assets)
            preview.load("model", Resource::class.java)
            preview.own(Disposable {
                assertTrue(assets.isLoaded("model"))
                disposed += "renderer"
            })
            preview.close()
            preview.close()
            assertEquals(listOf("renderer", "model"), disposed)
            assertFalse(assets.isLoaded("model"))
            assertEquals(1, assets.getReferenceCount("shared"))
        } finally { assets.dispose() }
    }

    @Test
    fun acquiresItsOwnReferenceToCachedRootAndCanBeReusedAfterClosing() {
        val assets = manager(mutableListOf())
        try {
            assets.load("model", Resource::class.java)
            assets.finishLoading()
            val preview = PreviewResources(assets)
            assertSame(assets.get("model", Resource::class.java), preview.load("model", Resource::class.java))
            assertEquals(2, assets.getReferenceCount("model"))
            assertThrows(IllegalStateException::class.java) { preview.load("other", Resource::class.java) }
            preview.close()
            assertEquals(1, assets.getReferenceCount("model"))
            preview.load("other", Resource::class.java)
            preview.close()
            assertFalse(assets.isLoaded("other"))
            assertTrue(assets.isLoaded("model"))
        } finally { assets.dispose() }
    }

    @Test
    fun failedLoadCanBeClosedAndReusedAfterManagerRecovery() {
        val assets = manager(mutableListOf())
        try {
            val preview = PreviewResources(assets)
            assertThrows(RuntimeException::class.java) { preview.load("broken", Resource::class.java) }
            preview.close()
            assets.clear()
            preview.load("model", Resource::class.java)
            preview.close()
            assertEquals(0, assets.loadedAssets)
        } finally { assets.dispose() }
    }
}
