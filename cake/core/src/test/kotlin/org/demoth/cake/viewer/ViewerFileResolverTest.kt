package org.demoth.cake.viewer

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.LinkedHashMap

class ViewerFileResolverTest {

    @TempDir
    lateinit var temp: Path

    @Test
    fun resolvesFromOpenedFileDirectory() {
        val openedFile = createFile("viewer/tris.md2")
        val skin = createFile("viewer/skin.pcx")
        val resolver = ViewerFileResolver(openedFile.absolutePath)

        val resolved = resolver.resolve("skin.pcx")

        assertNotNull(resolved)
        assertEquals(skin.absolutePath, resolved!!.file().absolutePath)
    }

    @Test
    fun resolvesCaseInsensitiveFromOpenedFileDirectory() {
        val openedFile = createFile("viewer/tris.md2")
        val skin = createFile("viewer/Skin.PCX")
        val resolver = ViewerFileResolver(openedFile.absolutePath)

        val resolved = resolver.resolve("skin.pcx")

        assertNotNull(resolved)
        assertTrue(resolved!!.exists())
        assertTrue(resolved.file().name.equals(skin.name, ignoreCase = true))
    }

    @Test
    fun resolvesByFileNameWhenDependencyUsesGameRelativePath() {
        val openedFile = createFile("viewer/tris.md2")
        val skin = createFile("viewer/grunt.pcx")
        val resolver = ViewerFileResolver(openedFile.absolutePath)

        val resolved = resolver.resolve("players/male/grunt.pcx")

        assertNotNull(resolved)
        assertEquals(skin.absolutePath, resolved!!.file().absolutePath)
    }

    @Test
    fun fallsBackToBasedirBaseq2WhenProvided() {
        val openedFile = createFile("viewer/tris.md2")
        val baseSkin = createFile("baseq2/models/monsters/berserk/skin.pcx")
        val resolver = ViewerFileResolver(
            openedFilePath = openedFile.absolutePath,
            basedir = temp.toString(),
        )

        val resolved = resolver.resolve("models/monsters/berserk/skin.pcx")

        assertNotNull(resolved)
        assertEquals(baseSkin.absolutePath, resolved!!.file().absolutePath)
    }

    @Test
    fun fallsBackToBasedirPakWhenProvided() {
        val openedFile = createFile("viewer/tris.md2")
        writePak(
            temp.resolve("baseq2").resolve("pak0.pak"),
            linkedMapOf("models/monsters/berserk/skin.pcx" to "pak-skin".toByteArray(StandardCharsets.US_ASCII)),
        )
        val resolver = ViewerFileResolver(
            openedFilePath = openedFile.absolutePath,
            basedir = temp.toString(),
        )

        val resolved = resolver.resolve("models/monsters/berserk/skin.pcx")

        assertNotNull(resolved)
        assertEquals("pak-skin", String(resolved!!.readBytes(), StandardCharsets.US_ASCII))
    }

    @Test
    fun fallsBackToBasedirZipWhenProvided() {
        val openedFile = createFile("viewer/tris.md2")
        writeZip(
            temp.resolve("baseq2").resolve("assets.pk3"),
            linkedMapOf("textures/e1u1/wall.wal" to "zip-wall".toByteArray(StandardCharsets.US_ASCII)),
        )
        val resolver = ViewerFileResolver(
            openedFilePath = openedFile.absolutePath,
            basedir = temp.toString(),
        )

        val resolved = resolver.resolve("textures/e1u1/wall.wal")

        assertNotNull(resolved)
        assertEquals("zip-wall", String(resolved!!.readBytes(), StandardCharsets.US_ASCII))
    }

    @Test
    fun resolvesMapTextureFromParentFolder() {
        val openedMap = createFile("baseq2/maps/base1.bsp")
        val texture = createFile("baseq2/textures/e1u1/wall.wal")
        val resolver = ViewerFileResolver(openedMap.absolutePath)

        val resolved = resolver.resolve("textures/e1u1/wall.wal")

        assertNotNull(resolved)
        assertEquals(texture.absolutePath, resolved!!.file().absolutePath)
    }

    @Test
    fun returnsNullForMissingAsset() {
        val openedFile = createFile("viewer/tris.md2")
        val resolver = ViewerFileResolver(openedFile.absolutePath)

        val resolved = resolver.resolve("missing/asset/file.pcx")

        assertNull(resolved)
    }

    @Test
    fun reconfiguresLocalRootsAndAdjacentFileFallback() {
        val first = createFile("first/models/tris.md2")
        val second = createFile("second/models/tris.md2")
        val firstTexture = createFile("first/textures/shared.wal")
        val secondTexture = createFile("second/textures/shared.wal")
        val firstSkin = createFile("first/models/skin.pcx")
        val secondSkin = createFile("second/models/skin.pcx")
        createFile("first/textures/old-only.wal")
        val resolver = ViewerFileResolver(first.absolutePath)

        assertEquals(firstTexture.absolutePath, resolver.resolve("textures/shared.wal")!!.file().absolutePath)
        assertEquals(firstSkin.absolutePath, resolver.resolve("players/male/skin.pcx")!!.file().absolutePath)

        resolver.configure(second.absolutePath)

        assertEquals(secondTexture.absolutePath, resolver.resolve("textures/shared.wal")!!.file().absolutePath)
        assertEquals(secondSkin.absolutePath, resolver.resolve("players/male/skin.pcx")!!.file().absolutePath)
        assertNull(resolver.resolve("textures/old-only.wal"))
    }

    @Test
    fun reconfiguresGameDataAndReleasesPreviousArchiveExtracts() {
        val openedFile = createFile("viewer/models/tris.md2")
        val path = "textures/shared.wal"
        writePak(temp.resolve("first/baseq2/pak0.pak"), linkedMapOf(path to "first".toByteArray()))
        writePak(temp.resolve("second/custom/pak0.pak"), linkedMapOf(path to "second".toByteArray()))
        val resolver = ViewerFileResolver(openedFile.absolutePath, basedir = temp.resolve("first").toString())
        val firstExtract = resolver.resolve(path)!!
        assertEquals("first", firstExtract.readString())

        resolver.configure(openedFile.absolutePath, basedir = temp.resolve("second").toString(), gamemod = "custom")

        assertFalse(firstExtract.exists())
        val secondExtract = resolver.resolve(path)!!
        assertEquals("second", secondExtract.readString())

        resolver.configure(openedFile.absolutePath)

        assertFalse(secondExtract.exists())
        assertNull(resolver.resolve(path))
    }

    private fun createFile(relativePath: String): File {
        val file = temp.resolve(relativePath).toFile()
        file.parentFile.mkdirs()
        file.writeText("")
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

        @BeforeAll
        @JvmStatic
        fun setupGdx() {
            if (Gdx.app == null) {
                app = HeadlessApplication(EmptyListener(), HeadlessApplicationConfiguration())
            }
        }

        @AfterAll
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
