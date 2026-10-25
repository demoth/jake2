package org.demoth.cake.ui.menu

import jake2.qcommon.Defines
import jake2.qcommon.filesystem.IDBSPHEADER
import org.demoth.cake.profile.CakeGameProfile
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class HostMapsTest {
    @TempDir lateinit var root: Path

    @Test
    fun `catalog includes loose and packed maps and uses winning mod metadata`() {
        write("baseq2/maps/shared.bsp", bsp("Base title"))
        write("mod/maps/shared.bsp", bsp("Mod title"))
        write("baseq2/maps/custom/untitled.bsp", bsp(null))
        write("baseq2/maps/not-a-map.txt", byteArrayOf())
        ZipOutputStream(Files.newOutputStream(root.resolve("baseq2/maps.pk3"))).use {
            it.putNextEntry(ZipEntry("maps/packed.bsp"))
            it.write(bsp("Packed\nArena"))
            it.closeEntry()
        }
        val maps = loadHostMaps(CakeGameProfile("test", root.toString(), "mod"))
        assertEquals(listOf(HostMap("custom/untitled"), HostMap("packed", "Packed Arena"),
            HostMap("shared", "Mod title")), maps)
        assertEquals("packed — Packed Arena", maps[1].toString())
        assertEquals("custom/untitled", maps[0].toString())
        assertEquals("Base title", loadHostMaps(CakeGameProfile("base", root.toString()))
            .first { it.name == "shared" }.title)
    }

    @Test
    fun `unreadable or invalid metadata falls back to map names`() {
        write("baseq2/maps/broken.bsp", byteArrayOf(1, 2, 3))
        val badOffset = bsp("Ignored").also {
            ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(8, Int.MAX_VALUE)
        }
        write("baseq2/maps/offset.bsp", badOffset)
        write("baseq2/maps/empty.bsp", bsp("  "))
        assertEquals(listOf(HostMap("broken"), HostMap("empty"), HostMap("offset")),
            loadHostMaps(CakeGameProfile("test", root.toString())))
    }

    @Test
    fun `missing profile or empty content produces no choices`() {
        assertTrue(loadHostMaps(null).isEmpty())
        assertTrue(loadHostMaps(CakeGameProfile("empty", root.toString())).isEmpty())
    }

    private fun write(path: String, bytes: ByteArray) {
        val target = root.resolve(path)
        Files.createDirectories(target.parent)
        Files.write(target, bytes)
    }

    private fun bsp(title: String?): ByteArray {
        val message = title?.let { "\"message\" \"$it\"" }.orEmpty()
        val entities = "{\"classname\" \"worldspawn\" $message}\u0000".toByteArray(Charsets.ISO_8859_1)
        val size = 8 + Defines.HEADER_LUMPS * 8
        return ByteBuffer.allocate(size + entities.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(IDBSPHEADER); putInt(38)
            putInt(size); putInt(entities.size)
            position(size); put(entities)
        }.array()
    }
}
