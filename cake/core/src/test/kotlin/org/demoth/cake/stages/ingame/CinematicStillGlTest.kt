package org.demoth.cake.stages.ingame

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import jake2.qcommon.exec.Cvar
import org.demoth.cake.CakeCvars
import org.demoth.cake.assets.CakeFileResolver
import org.demoth.cake.assets.ObjectLoader
import org.demoth.cake.assets.PcxLoader
import org.demoth.cake.config.GameConfiguration
import org.demoth.cake.download.CakeDownloadCache
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

/** Real decoding, VFS resolution, drawing and configuration lifetime using the production loaders. */
@EnabledIfEnvironmentVariable(named = "CAKE_VIDEO_GL_TESTS", matches = "1")
class CinematicStillGlTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun `stills render through shared assets and preserve skip and stream transitions`() {
        val cvars = Cvar.getInstance()
        cvars.clear()
        CakeCvars.registerAssets(cvars)
        var failure: Throwable? = null
        try {
            Lwjgl3Application(object : ApplicationAdapter() {
                override fun create() {
                    try {
                        verifyPresentation()
                    } catch (error: Throwable) {
                        failure = error
                    } finally {
                        Gdx.app.exit()
                    }
                }
            }, Lwjgl3ApplicationConfiguration().apply {
                setInitialVisible(false)
                disableAudio(true)
                setWindowedMode(64, 64)
                setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL32, 3, 2)
            })
        } finally {
            cvars.clear()
        }
        failure?.let { throw it }
    }

    private fun verifyPresentation() {
        fun write(path: String, bytes: ByteArray) {
            val file = temp.resolve(path)
            Files.createDirectories(file.parent)
            Files.write(file, bytes)
        }
        write("baseq2/pics/ending.pcx", javaClass.getResourceAsStream("/_missing.pcx")!!.use { it.readBytes() })
        write("baseq2/pics/ending.tga", tga(rle = false))
        write("baseq2/art/rle.TGA", tga(rle = true))
        write("baseq2/pics/ending.PNG", image("png", 0x00ff00))
        write("baseq2/art/ending.jpg", image("jpg", 0xff0000))
        write("baseq2/art/ending.jpeg", image("jpg", 0xff0000))
        write("baseq2/video/ending.cin", movie())
        write("baseq2/q2palette.bin", ByteArrayOutputStream().apply {
            ObjectOutputStream(this).use { it.writeObject(IntArray(256) { 0xff0000ff.toInt() }) }
        }.toByteArray())
        // An archive in the active mod must override the base loose PNG through the shared VFS.
        write("rogue/pak0.pk3", ByteArrayOutputStream().apply {
            ZipOutputStream(this).use {
                it.putNextEntry(ZipEntry("pics/ending.PNG"))
                it.write(image("png", 0xff0000))
                it.closeEntry()
            }
        }.toByteArray())
        val resolver = CakeFileResolver(temp.toString(), "rogue",
            downloadCache = CakeDownloadCache(temp.resolve("downloads")))
        val assets = AssetManager(resolver).apply {
            PcxLoader(resolver).registerWith(this)
            setLoader(Any::class.java, ObjectLoader(resolver))
        }
        val config = GameConfiguration(assets)
        val overlappingConfig = GameConfiguration(assets)
        val controller = CinematicPresentationController(assets, config)
        val batch = SpriteBatch().apply { projectionMatrix.setToOrtho2D(0f, 0f, 64f, 64f) }
        val target = FrameBuffer(Pixmap.Format.RGBA8888, 64, 64, false)
        val textures = mutableListOf<Texture>()
        fun draw(time: Int) {
            target.begin()
            try {
                Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
                controller.render(time, batch, 64f, 64f)
                val pixels = Pixmap.createFromFrameBuffer(0, 0, 64, 64)
                try {
                    val center = pixels.getPixel(32, 32)
                    assertTrue((center ushr 24 and 255) > 240, "Decoded red image is drawn")
                    assertTrue((center ushr 16 and 255) < 15, "Mod PNG overrides green base image")
                    assertEquals(0, pixels.getPixel(32, 2).ushr(8), "Aspect ratio leaves black letterboxing")
                } finally {
                    pixels.dispose()
                }
            } finally {
                target.end()
            }
        }
        try {
            val pcxNames = listOf("pcx", "pcX", "pCx", "pCX", "Pcx", "PcX", "PCx", "PCX").map { "ending.$it" }
            for (name in pcxNames + listOf("ending.tga", "art/rle.TGA", "ending.PNG", "art/ending.jpg", "/art/ending.jpeg")) {
                val path = if ('/' in name) name.removePrefix("/") else "pics/$name"
                controller.begin(name, 0)
                assertTrue(assets.isLoaded(path, Texture::class.java), "Controller acquired $name")
                val texture = assets.get(path, Texture::class.java)
                textures += texture
                assertTrue(texture.width > 0 && texture.height > 0)
                if (!name.endsWith(".pcx", ignoreCase = true)) draw(0)
                assertNull(controller.pollSkipCommand(1000, 17, true))
                assertNull(controller.pollSkipCommand(1001, 17, false))
                assertEquals("nextserver 17", controller.pollSkipCommand(1001, 17, true)?.command)
                assertNull(controller.pollSkipCommand(1002, 17, true))
                assertSame(texture, overlappingConfig.acquireAsset<Texture>(path))
                // libGDX queues reference increments even for an already loaded texture.
                assets.finishLoading()
                controller.begin(name, 2000)
                assertEquals(2, assets.getReferenceCount(path), "Repeated begin reuses config ownership")
                controller.end()
                assertTrue(texture.textureObjectHandle != 0, "Controller borrows the configuration texture")
            }
            assets.load("q2palette.bin", Any::class.java)
            assets.finishLoading()
            controller.begin("ending.cin", 0)
            draw(0)
            draw(72)
            assertEquals("nextserver 18", controller.pollSkipCommand(72, 18, false)?.command,
                "CIN EOF auto-advances before guard")
            assertNull(controller.pollSkipCommand(72, 18, true))
            controller.begin("ending.tga", 2000)
            draw(2000)
            assertNull(controller.pollSkipCommand(2000, 19, true), "New still resets EOF and skip state")
            controller.end()
            config.unloadAssets()
            assertTrue(textures.all { it.textureObjectHandle != 0 }, "Overlapping configuration keeps images alive")
            overlappingConfig.unloadAssets()
            assertTrue(textures.all { it.textureObjectHandle == 0 }, "Last configuration releases images")
        } finally {
            controller.dispose()
            config.unloadAssets()
            overlappingConfig.unloadAssets()
            target.dispose()
            batch.dispose()
            assets.dispose()
        }
    }

    private fun image(format: String, rgb: Int): ByteArray {
        val image = BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB)
        for (y in 0..1) for (x in 0..3) image.setRGB(x, y, rgb)
        return ByteArrayOutputStream().apply { check(ImageIO.write(image, format, this)) }.toByteArray()
    }

    private fun tga(rle: Boolean): ByteArray = ByteBuffer.allocate(if (rle) 23 else 42)
        .order(ByteOrder.LITTLE_ENDIAN).apply {
            put(0).put(0).put(if (rle) 10.toByte() else 2.toByte())
            repeat(9) { put(0) }
            putShort(4).putShort(2).put(if (rle) 32.toByte() else 24.toByte()).put(0x20)
            if (rle) {
                put(0x87.toByte()).put(0).put(0).put(255.toByte()).put(255.toByte())
            } else {
                repeat(8) { put(0).put(0).put(255.toByte()) }
            }
        }.array()

    private fun movie(): ByteArray = ByteBuffer.allocate(20 + 65536 + 13 + 4)
        .order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(4).putInt(2).putInt(0).putInt(0).putInt(0)
            repeat(256) { put(1).put(1); repeat(254) { put(0) } }
            putInt(0).putInt(5).putInt(8).put(0)
            putInt(2)
        }.array()
}
