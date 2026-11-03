package org.demoth.cake.profile

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ProfileModsTest {
    @TempDir lateinit var root: Path

    @Test
    fun `discovery is shallow includes missing selections and does not parse archives`() {
        Files.createDirectories(root.resolve("baseq2"))
        Files.createDirectories(root.resolve("catalog/nested"))
        for (path in listOf("baseq2/pak0.pak", "catalog/broken.zip", "catalog/nested/hidden.zip", "root.zip"))
            Files.writeString(root.resolve(path), "not an archive")
        val profile = CakeGameProfile("test", root.toString(), "catalog", listOf("catalog/missing.zip"))
        val found = ProfileArchives.discover(profile, false)
        assertEquals(setOf("baseq2/pak0.pak", "catalog/broken.zip", "catalog/missing.zip"), found.map { it.path }.toSet())
        assertTrue(found.single { it.required }.included)
        assertTrue(found.single { it.missing }.included)
        assertFalse(found.single { it.path.endsWith("broken.zip") }.included)
        assertThrows(IllegalArgumentException::class.java) { ProfileArchives.inspect(root.resolve("catalog/broken.zip")) }
    }

    @Test
    fun `inspection stays in its archive and enabling automount validates optional packages`() {
        Files.createDirectories(root.resolve("catalog"))
        Files.createDirectories(root.resolve("baseq2/maps"))
        Files.writeString(root.resolve("baseq2/maps/unrelated.bsp"), "base")
        ZipOutputStream(Files.newOutputStream(root.resolve("catalog/map.zip"))).use { zip ->
            for (name in listOf("docs/readme.txt", "maps/test.bsp")) {
                zip.putNextEntry(ZipEntry(name)); zip.write("content".toByteArray()); zip.closeEntry()
            }
        }
        val entries = ProfileArchives.inspect(root.resolve("catalog/map.zip"))
        assertEquals(setOf("docs/readme.txt", "maps/test.bsp"), entries.map { it.normalizedPath() }.toSet())
        assertEquals("docs/readme.txt", ProfileArchives.readme(entries)?.normalizedPath())
        val profile = CakeGameProfile("test", root.toString(), "catalog")
        ProfileArchives.validateActivation(profile, false, true)
        Files.writeString(root.resolve("catalog/broken.zip"), "invalid")
        assertThrows(IllegalArgumentException::class.java) { ProfileArchives.validateActivation(profile, false, true) }
        assertThrows(IllegalArgumentException::class.java) { ProfileArchives.resolve(profile, "other/map.zip") }
    }

    @Test
    fun `failed persistence restores the runtime and saved selection`() {
        val previous = CakeGameProfile("test", root.toString())
        val next = previous.copy(includedArchives = listOf("baseq2/map.zip"))
        var runtime = previous
        var saved = previous
        var automatic = true
        val error = assertThrows(IllegalStateException::class.java) {
            ProfileModChanges.apply(previous, next, true, false,
                applyContent = { p, a -> runtime = p; automatic = a },
                saveSelection = { saved = it },
                saveSettings = { if (!automatic) error("Cannot save config") })
        }
        assertEquals("Cannot save config", error.message)
        assertEquals(previous, runtime)
        assertEquals(previous, saved)
        assertTrue(automatic)
    }

    @Test
    fun `failed content application restores runtime without persisting new values`() {
        val previous = CakeGameProfile("test", root.toString())
        val next = previous.copy(includedArchives = listOf("baseq2/map.zip"))
        var runtime = previous
        assertThrows(IllegalStateException::class.java) {
            ProfileModChanges.apply(previous, next, false, false,
                applyContent = { p, _ -> runtime = p; if (p == next) error("Cannot load") },
                saveSelection = { fail<Unit>("Must not persist a failed selection") },
                saveSettings = { fail<Unit>("Must not persist a failed selection") })
        }
        assertEquals(previous, runtime)
    }
}
