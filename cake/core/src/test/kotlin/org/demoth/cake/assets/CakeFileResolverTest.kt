package org.demoth.cake.assets

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.LinkedHashMap

class CakeFileResolverTest {

    @TempDir
    lateinit var temp: Path

    @Test
    fun resolvesExactCaseInGamemod() {
        val basedir = temp.toString()
        val file = createFile("rogue/sound/Alert.wav")
        val resolver = CakeFileResolver(basedir = basedir, gamemod = "rogue")

        val resolved = resolver.resolve("sound/Alert.wav")

        assertNotNull(resolved)
        assertEquals(file.absolutePath, resolved!!.file().absolutePath)
    }

    @Test
    fun resolvesCaseInsensitiveInGamemod() {
        val basedir = temp.toString()
        val file = createFile("rogue/berserk/RUN.wav")
        val resolver = CakeFileResolver(basedir = basedir, gamemod = "rogue")

        val resolved = resolver.resolve("berserk/run.wav")

        assertNotNull(resolved)
        assertTrue(resolved!!.exists())
        assertTrue(resolved.file().name.equals(file.name, ignoreCase = true))
    }

    @Test
    fun gamemodOverridesBasemod() {
        val basedir = temp.toString()
        createFile("baseq2/textures/wall.tga")
        val file = createFile("rogue/textures/wall.tga")
        val resolver = CakeFileResolver(basedir = basedir, gamemod = "rogue")

        val resolved = resolver.resolve("textures/wall.tga")

        assertNotNull(resolved)
        assertEquals(file.absolutePath, resolved!!.file().absolutePath)
        assertTrue(resolved.file().absolutePath.contains("${File.separator}rogue${File.separator}"))
    }

    @Test
    fun resolvesFromBasePakWhenLooseFileIsMissing() {
        val basedir = temp.toString()
        writePak(
            Path.of(basedir, "baseq2", "pak0.pak"),
            linkedMapOf("sound/alert.wav" to "pak-base".toByteArray(StandardCharsets.US_ASCII)),
        )
        val resolver = CakeFileResolver(basedir = basedir, gamemod = null)

        val resolved = resolver.resolve("sound/alert.wav")

        assertNotNull(resolved)
        assertTrue(resolved!!.exists())
        assertEquals("pak-base", String(resolved.readBytes(), StandardCharsets.US_ASCII))
    }

    @Test
    fun gamemodPakOverridesBaseLoose() {
        val basedir = temp.toString()
        createFile("baseq2/textures/wall.tga", "base-loose".toByteArray(StandardCharsets.US_ASCII))
        writePak(
            Path.of(basedir, "rogue", "pak0.pak"),
            linkedMapOf("textures/wall.tga" to "mod-pack".toByteArray(StandardCharsets.US_ASCII)),
        )
        val resolver = CakeFileResolver(basedir = basedir, gamemod = "rogue")

        val resolved = resolver.resolve("textures/wall.tga")

        assertNotNull(resolved)
        assertEquals("mod-pack", String(resolved!!.readBytes(), StandardCharsets.US_ASCII))
    }

    @Test
    fun baseLooseOverridesBasePak() {
        val basedir = temp.toString()
        createFile("baseq2/config.cfg", "base-loose".toByteArray(StandardCharsets.US_ASCII))
        writePak(
            Path.of(basedir, "baseq2", "pak0.pak"),
            linkedMapOf("config.cfg" to "base-pack".toByteArray(StandardCharsets.US_ASCII)),
        )
        val resolver = CakeFileResolver(basedir = basedir, gamemod = null)

        val resolved = resolver.resolve("config.cfg")

        assertNotNull(resolved)
        assertEquals("base-loose", String(resolved!!.readBytes(), StandardCharsets.US_ASCII))
    }

    @Test
    fun resolvesFromZipPackage() {
        val basedir = temp.toString()
        writeZip(
            Path.of(basedir, "baseq2", "assets.pk3"),
            linkedMapOf("models/items/ammo/tris.md2" to "zip-md2".toByteArray(StandardCharsets.US_ASCII)),
        )
        val resolver = CakeFileResolver(basedir = basedir, gamemod = null)

        val resolved = resolver.resolve("models/items/ammo/tris.md2")

        assertNotNull(resolved)
        assertEquals("zip-md2", String(resolved!!.readBytes(), StandardCharsets.US_ASCII))
    }

    @Test
    fun changingGamemodReconfiguresVfsLookup() {
        val basedir = temp.toString()
        createFile("baseq2/config.cfg", "base".toByteArray(StandardCharsets.US_ASCII))
        createFile("rogue/config.cfg", "rogue".toByteArray(StandardCharsets.US_ASCII))
        val resolver = CakeFileResolver(basedir = basedir, gamemod = null)

        val baseResolved = resolver.resolve("config.cfg")
        assertNotNull(baseResolved)
        assertEquals("base", String(baseResolved!!.readBytes(), StandardCharsets.US_ASCII))

        resolver.gamemod = "rogue"
        val modResolved = resolver.resolve("config.cfg")
        assertNotNull(modResolved)
        assertEquals("rogue", String(modResolved!!.readBytes(), StandardCharsets.US_ASCII))
    }

    @Test
    fun resolvesModelPathWithSkinPrefixVariantKey() {
        val basedir = temp.toString()
        val file = createFile("rogue/players/male/tris.md2")
        val resolver = CakeFileResolver(basedir = basedir, gamemod = "rogue")

        val resolved = resolver.resolve("players/male/grunt.pcx|players/male/tris.md2")

        assertNotNull(resolved)
        assertEquals(file.absolutePath, resolved!!.file().absolutePath)
    }

    @Test
    fun resolvesPathContainingParentSegmentsInsideGameRoot() {
        val basedir = temp.toString()
        val file = createFile("baseq2/models/monsters/ctank/pain.pcx")
        val resolver = CakeFileResolver(basedir = basedir, gamemod = null)

        val resolved = resolver.resolve("models/monsters/tank/../ctank/pain.pcx")

        assertNotNull(resolved)
        assertEquals(file.absolutePath, resolved!!.file().absolutePath)
    }

    @Test
    fun resolvesPakEntryStoredWithParentSegments() {
        val basedir = temp.toString()
        writePak(
            Path.of(basedir, "baseq2", "pak0.pak"),
            linkedMapOf("models/monsters/tank/../ctank/skin.pcx" to "ctank-skin".toByteArray(StandardCharsets.US_ASCII)),
        )
        val resolver = CakeFileResolver(basedir = basedir, gamemod = null)

        val resolved = resolver.resolve("models/monsters/ctank/skin.pcx")

        assertNotNull(resolved)
        assertEquals("ctank-skin", String(resolved!!.readBytes(), StandardCharsets.US_ASCII))
    }

    @Test
    fun rejectsPathTraversalBeyondGameRootBeginning() {
        val basedir = temp.toString()
        val resolver = CakeFileResolver(basedir = basedir, gamemod = null)

        val resolved = resolver.resolve("../outside.pcx")

        assertNull(resolved)
    }
    @Test

    fun rejectsPathTraversalBeyondGameRootMiddle() {
        val basedir = temp.toString()
        val resolver = CakeFileResolver(basedir = basedir, gamemod = null)

        val resolved = resolver.resolve("models/../../outside.pcx")

        assertNull(resolved)
    }

    @Test
    fun resolvesVirtualSkyAssetWithoutPhysicalFile() {
        val resolver = CakeFileResolver()

        val resolved = resolver.resolve("sky/desert_.sky")

        assertNotNull(resolved)
        assertTrue(resolved!!.exists())
    }

    @Test
    fun missingAssetReturnsNullWithoutThrowing() {
        val resolver = CakeFileResolver()

        val resolved = resolver.resolve("models/missing/file.md2")

        assertNull(resolved)
    }

    private fun createFile(relativePath: String, bytes: ByteArray = byteArrayOf()): File {
        val file = temp.resolve(relativePath).toFile()
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
