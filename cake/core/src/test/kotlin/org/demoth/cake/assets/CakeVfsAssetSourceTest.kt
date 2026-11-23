package org.demoth.cake.assets

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Covers selected-archive visibility, base PAK precedence, and extracted-file lifetime. */
class CakeVfsAssetSourceTest {
    @TempDir lateinit var temp: Path

    @Test
    fun archivePreviewUsesOrderedBasePaksWithoutListingOrMountingOtherContent() {
        val root = temp.resolve("game")
        writePak(root.resolve("baseq2/pak0.pak"), mapOf(
            "textures/base.wal" to "base0", "textures/shared.wal" to "base"))
        writePak(root.resolve("baseq2/pak2.pak"), mapOf("textures/base.wal" to "base2"))
        writePak(root.resolve("baseq2/pak10.pak"), mapOf("textures/base.wal" to "base10"))
        Files.writeString(root.resolve("baseq2/loose.txt"), "loose")
        Files.createDirectories(root.resolve("baseq2/textures"))
        Files.writeString(root.resolve("baseq2/textures/base.wal"), "loose override")
        writeZip(root.resolve("baseq2/optional.zip"), mapOf("optional.txt" to "optional"))
        writePak(root.resolve("catalog/pak0.pak"), mapOf("mod.txt" to "other mod"))
        val selected = root.resolve("catalog/selected.zip")
        writeZip(selected, mapOf("maps/test.bsp" to "map", "readme.txt" to "selected readme",
            "textures/shared.wal" to "selected"))
        val source = CakeVfsAssetSource()
        try {
            val entries = source.configureArchive(selected.toString(), root.toString())
            assertEquals(setOf("maps/test.bsp", "readme.txt", "textures/shared.wal"),
                entries.map { it.normalizedPath }.toSet())
            assertTrue(entries.all { it.source.containerPath == selected })
            assertEquals("base10", source.resolve("textures/base.wal")!!.readString())
            assertEquals("selected", source.resolve("textures/shared.wal")!!.readString())
            assertTrue(source.listEntries().any { it.normalizedPath == "textures/base.wal" },
                "Texture candidate lookup includes baseline assets without adding them to the browser")
            assertNull(source.resolve("loose.txt"))
            assertNull(source.resolve("optional.txt"))
            assertNull(source.resolve("mod.txt"))
        } finally { source.configure(null, null, false) }
    }

    @Test
    fun selectingNumberedBasePakMountsItOnceAndLetsItsOwnFilesWin() {
        val root = temp.resolve("game")
        val selected = root.resolve("baseq2/pak0.pak")
        writePak(selected, mapOf("textures/shared.wal" to "selected"))
        writePak(root.resolve("baseq2/pak1.pak"), mapOf(
            "textures/shared.wal" to "patch", "textures/patch.wal" to "patch-only"))
        val source = CakeVfsAssetSource()
        try {
            val entries = source.configureArchive(selected.toString(), root.toString())
            assertEquals(listOf("textures/shared.wal"), entries.map { it.normalizedPath })
            assertEquals("selected", source.resolve("textures/shared.wal")!!.readString())
            assertEquals("patch-only", source.resolve("textures/patch.wal")!!.readString())
        } finally { source.configure(null, null, false) }
    }

    @Test
    fun switchingArchiveBaselinesReleasesExtractsAndCanReturnToArchiveOnly() {
        val first = temp.resolve("first")
        val second = temp.resolve("second")
        writePak(first.resolve("baseq2/pak0.pak"), mapOf("textures/base.wal" to "first"))
        writePak(second.resolve("baseq2/pak0.pak"), mapOf("textures/base.wal" to "second"))
        val selected = temp.resolve("selected.zip")
        writeZip(selected, mapOf("maps/test.bsp" to "map"))
        val source = CakeVfsAssetSource()
        try {
            source.configureArchive(selected.toString(), first.toString())
            val oldExtract = source.resolve("textures/base.wal")!!
            assertEquals("first", oldExtract.readString())
            source.configureArchive(selected.toString(), second.toString())
            assertFalse(oldExtract.exists())
            val currentExtract = source.resolve("textures/base.wal")!!
            assertEquals("second", currentExtract.readString())
            source.configureArchive(selected.toString())
            assertFalse(currentExtract.exists())
            assertNull(source.resolve("textures/base.wal"), "Archive-only callers retain strict isolation")
            assertNotNull(source.resolve("maps/test.bsp"))
        } finally { source.configure(null, null, false) }
    }

    private fun writeZip(path: Path, entries: Map<String, String>) {
        Files.createDirectories(path.parent)
        ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray()); zip.closeEntry()
            }
        }
    }

    private fun writePak(path: Path, entries: Map<String, String>) {
        Files.createDirectories(path.parent)
        val data = ByteArrayOutputStream()
        val directory = ByteArrayOutputStream()
        var offset = 12
        entries.forEach { (name, content) ->
            val bytes = content.toByteArray()
            data.write(bytes)
            val entry = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
            entry.put(name.toByteArray())
            entry.position(56); entry.putInt(offset); entry.putInt(bytes.size)
            directory.write(entry.array())
            offset += bytes.size
        }
        val header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            .put(byteArrayOf(80, 65, 67, 75)).putInt(offset).putInt(directory.size()).array()
        Files.write(path, header + data.toByteArray() + directory.toByteArray())
    }
}
