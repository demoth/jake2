package org.demoth.cake.stages.ingame

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.FileHandleResolver
import com.badlogic.gdx.files.FileHandle
import org.demoth.cake.config.GameConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class CinematicPresentationControllerTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun `missing stills use picture paths and preserve guarded one-shot skip without loading fallback art`() {
        val paths = mutableListOf<String>()
        val assets = AssetManager(FileHandleResolver { path ->
            paths += path
            // A resolver may return a non-existent handle, as the default libGDX resolvers do.
            FileHandle(temp.resolve(path).toFile())
        })
        val config = GameConfiguration(assets)
        val controller = CinematicPresentationController(assets, config)
        try {
            for (name in listOf("end.pcx", "end.tga", "end.PNG", "end.jpg", "end.jpeg", "/art/end.png")) {
                controller.begin(name, 100)
                assertEquals(if ('/' in name) "art/end.png" else "pics/$name", paths.last())
                assertEquals(0, assets.loadedAssets, "Missing pictures must not load substitute art")
                assertNull(controller.pollSkipCommand(1100, 7, true), "Skip guard includes its boundary")
                assertNull(controller.pollSkipCommand(1101, 7, false), "Stills do not automatically advance")
                assertEquals("nextserver 7", controller.pollSkipCommand(1101, 7, true)?.command)
                assertNull(controller.pollSkipCommand(1102, 7, true))
                controller.end()
                assertNull(controller.pollSkipCommand(5000, 7, true))
            }
            controller.begin("absent.cin", 0)
            assertEquals("video/absent.cin", paths.last())
            assertEquals("nextserver 8", controller.pollSkipCommand(0, 8, false)?.command,
                "Missing streams still auto-advance")
            assertNull(controller.pollSkipCommand(0, 8, false))
        } finally {
            controller.dispose()
            config.unloadAssets()
            assets.dispose()
        }
    }
}
