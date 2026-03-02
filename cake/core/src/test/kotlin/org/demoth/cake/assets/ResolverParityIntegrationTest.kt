package org.demoth.cake.assets

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration
import org.demoth.cake.modelviewer.ModelViewerFileResolver
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.LinkedHashMap

class ResolverParityIntegrationTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun cakeAndModelViewerResolveSameWinnerForModLooseVsBasePack() {
        val basedir = temp.root.absolutePath
        val openedFile = createFile("viewer/tris.md2")
        createFile("rogue/textures/wall.wal", "mod-loose".toByteArray(StandardCharsets.US_ASCII))
        writePak(
            Path.of(basedir, "baseq2", "pak0.pak"),
            linkedMapOf("textures/wall.wal" to "base-pack".toByteArray(StandardCharsets.US_ASCII)),
        )

        val cakeResolver = CakeFileResolver(basedir = basedir, gamemod = "rogue")
        val viewerResolver = ModelViewerFileResolver(
            openedFilePath = openedFile.absolutePath,
            basedir = basedir,
            gamemod = "rogue",
        )

        val cakeHandle = cakeResolver.resolve("textures/wall.wal")
        val viewerHandle = viewerResolver.resolve("textures/wall.wal")

        assertNotNull(cakeHandle)
        assertNotNull(viewerHandle)
        assertEquals("mod-loose", String(cakeHandle!!.readBytes(), StandardCharsets.US_ASCII))
        assertEquals("mod-loose", String(viewerHandle!!.readBytes(), StandardCharsets.US_ASCII))
    }

    @Test
    fun cakeAndModelViewerResolveSameWinnerForBaseZip() {
        val basedir = temp.root.absolutePath
        val openedFile = createFile("viewer/tris.md2")
        writeZip(
            Path.of(basedir, "baseq2", "assets.pk3"),
            linkedMapOf("models/items/ammo/tris.md2" to "zip-md2".toByteArray(StandardCharsets.US_ASCII)),
        )

        val cakeResolver = CakeFileResolver(basedir = basedir, gamemod = null)
        val viewerResolver = ModelViewerFileResolver(
            openedFilePath = openedFile.absolutePath,
            basedir = basedir,
            gamemod = null,
        )

        val cakeHandle = cakeResolver.resolve("models/items/ammo/tris.md2")
        val viewerHandle = viewerResolver.resolve("models/items/ammo/tris.md2")

        assertNotNull(cakeHandle)
        assertNotNull(viewerHandle)
        assertEquals("zip-md2", String(cakeHandle!!.readBytes(), StandardCharsets.US_ASCII))
        assertEquals("zip-md2", String(viewerHandle!!.readBytes(), StandardCharsets.US_ASCII))
    }

    @Test
    fun cakeAndModelViewerBothReturnNullForMissingAsset() {
        val basedir = temp.root.absolutePath
        val openedFile = createFile("viewer/tris.md2")

        val cakeResolver = CakeFileResolver(basedir = basedir, gamemod = "rogue")
        val viewerResolver = ModelViewerFileResolver(
            openedFilePath = openedFile.absolutePath,
            basedir = basedir,
            gamemod = "rogue",
        )

        assertNull(cakeResolver.resolve("missing/file.pcx"))
        assertNull(viewerResolver.resolve("missing/file.pcx"))
    }

    private fun createFile(relativePath: String, bytes: ByteArray = byteArrayOf()): File {
        val file = File(temp.root, relativePath)
        file.parentFile.mkdirs()
        file.writeBytes(bytes)
        return file
    }

    private fun writePak(target: Path, entries: LinkedHashMap<String, ByteArray>) {
        Files.createDirectories(target.parent)
        val data = ByteArrayOutputStream()
        val directory = ByteArrayOutputStream()

        var offset = 12
        entries.forEach { (name, content) ->
            data.write(content)

            val dirEntry = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
            val nameBytes = name.toByteArray(StandardCharsets.US_ASCII)
            require(nameBytes.size <= 56) { "Entry name too long for pak fixture: $name" }
            dirEntry.put(nameBytes)
            dirEntry.position(56)
            dirEntry.putInt(offset)
            dirEntry.putInt(content.size)
            directory.write(dirEntry.array())
            offset += content.size
        }

        val header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        header.put('P'.code.toByte()).put('A'.code.toByte()).put('C'.code.toByte()).put('K'.code.toByte())
        header.putInt(12 + data.size())
        header.putInt(directory.size())

        val full = ByteArrayOutputStream()
        full.write(header.array())
        full.write(data.toByteArray())
        full.write(directory.toByteArray())
        Files.write(target, full.toByteArray())
    }

    private fun writeZip(target: Path, entries: LinkedHashMap<String, ByteArray>) {
        Files.createDirectories(target.parent)
        java.util.zip.ZipOutputStream(Files.newOutputStream(target)).use { out ->
            entries.forEach { (name, content) ->
                out.putNextEntry(java.util.zip.ZipEntry(name))
                out.write(content)
                out.closeEntry()
            }
        }
    }

    companion object {
        private var app: HeadlessApplication? = null

        @BeforeClass
        @JvmStatic
        fun setupGdx() {
            if (Gdx.app == null) {
                app = HeadlessApplication(EmptyListener(), HeadlessApplicationConfiguration())
            }
        }

        @AfterClass
        @JvmStatic
        fun teardownGdx() {
            app?.exit()
            app = null
        }
    }

    private class EmptyListener : ApplicationListener {
        override fun create() = Unit
        override fun resize(width: Int, height: Int) = Unit
        override fun render() = Unit
        override fun pause() = Unit
        override fun resume() = Unit
        override fun dispose() = Unit
    }
}
