package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.assets.AssetDescriptor
import com.badlogic.gdx.assets.AssetLoaderParameters
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.FileHandleResolver
import com.badlogic.gdx.assets.loaders.SynchronousAssetLoader
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.Array as GdxArray
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

class EffectAssetCatalogTest {
    @Test fun `overlapping screens and config owners retain independent references to shared assets`() {
        val path = "sound/weapons/lashit.wav"
        var loaded = 0
        var disposed = 0
        val resolver = FileHandleResolver { if (it == path) FileHandle(it) else null }
        val assets = AssetManager(resolver, false)
        assets.setLoader(Sound::class.java, object : SynchronousAssetLoader<Sound, AssetLoaderParameters<Sound>>(resolver) {
            override fun load(manager: AssetManager, fileName: String, file: FileHandle, parameter: AssetLoaderParameters<Sound>?): Sound {
                loaded++
                return Proxy.newProxyInstance(Sound::class.java.classLoader, arrayOf(Sound::class.java)) { _, method, _ ->
                    if (method.name == "dispose") disposed++
                    null
                } as Sound
            }
            override fun getDependencies(fileName: String, file: FileHandle, parameter: AssetLoaderParameters<Sound>?): GdxArray<AssetDescriptor<*>>? = null
        })
        val oldScreen = EffectAssetCatalog(assets)
        val newScreen = EffectAssetCatalog(assets)
        try {
            // Configstrings already own this sound when both screens precache it.
            assets.load(path, Sound::class.java)
            assets.finishLoading()
            oldScreen.precache()
            newScreen.precache()
            newScreen.precache()
            assertEquals(1, loaded)
            assertEquals(3, assets.getReferenceCount(path))
            assertSame(oldScreen.getSound(path), newScreen.getSound(path))
            oldScreen.dispose()
            oldScreen.dispose()
            assertEquals(2, assets.getReferenceCount(path))
            assets.unload(path)
            assertNotNull(newScreen.getSound(path))
            assertEquals(0, disposed)
            newScreen.dispose()
            assertFalse(assets.isLoaded(path))
            assertEquals(1, disposed)
        } finally {
            oldScreen.dispose()
            newScreen.dispose()
            assets.dispose()
        }
    }
}
