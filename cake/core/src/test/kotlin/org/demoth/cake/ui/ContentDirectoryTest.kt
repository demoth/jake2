package org.demoth.cake.ui

import jake2.qcommon.vfs.VfsEntry
import jake2.qcommon.vfs.VfsLayer
import jake2.qcommon.vfs.VfsPathNormalizer
import jake2.qcommon.vfs.VfsSource
import jake2.qcommon.vfs.VfsSourceType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Path

class ContentDirectoryTest {
    @Test
    fun buildsSortedFoldersAndImmediateFileListsWithOriginalMetadata() {
        val entries = listOf("Textures/Z/B.pcx", "root.txt", "textures/a.wal", "Textures/Z/A.pcx",
            "models/m.md2", "z.cfg", "a.cfg").map { path ->
            VfsEntry(path.lowercase(), path, VfsLayer.BASE_PACK, 0,
                VfsSource(VfsSourceType.PACKAGE_ENTRY, Path.of("content.pak"), path, "pak", true, false), 123, 0)
        }

        val root = ContentDirectory.fromEntries(entries, "content.pak")

        assertEquals("content.pak", root.name)
        assertEquals("", root.path)
        assertEquals(listOf("models", "textures"), root.folders.keys.toList())
        assertEquals(listOf("a.cfg", "root.txt", "z.cfg"), root.files.map { it.normalizedPath })
        val textures = root.folders.getValue("textures")
        assertEquals(listOf("textures/a.wal"), textures.files.map { it.normalizedPath })
        val nested = textures.folders.getValue("z")
        assertEquals("Z", nested.name)
        assertEquals("textures/z", nested.path)
        assertEquals(listOf("textures/z/a.pcx", "textures/z/b.pcx"), nested.files.map { it.normalizedPath })
        assertSame(entries[3], nested.files.first())
    }

    @Test
    fun resolvesTankCommanderPathsBeforeAssigningFolderLabels() {
        val entries = listOf(
            "models/monsters/tank/tris.md2",
            "models/monsters/tank/skin.pcx",
            "models/monsters/tank/../ctank/skin.pcx",
            "models/monsters/tank/../ctank/pain.pcx",
            "./Textures//Unused/../MixedCase\\wall.wal",
        ).map { path ->
            VfsEntry(VfsPathNormalizer(false).normalizeOrNull(path)!!, path, VfsLayer.BASE_PACK, 0,
                VfsSource(VfsSourceType.PACKAGE_ENTRY, Path.of("pak0.pak"), path, "pak", true, false), 123, 0)
        }

        val root = ContentDirectory.fromEntries(entries, "pak0.pak")
        val monsters = root.folders.getValue("models").folders.getValue("monsters")
        assertEquals(listOf("ctank", "tank"), monsters.folders.keys.toList())
        assertEquals("ctank", monsters.folders.getValue("ctank").name)
        assertEquals("tank", monsters.folders.getValue("tank").name)
        assertEquals("models/monsters/ctank", monsters.folders.getValue("ctank").path)
        assertSame(entries[3], monsters.folders.getValue("ctank").files.first())
        assertEquals("models/monsters/tank/../ctank/pain.pcx",
            monsters.folders.getValue("ctank").files.first().source.entryPath)
        val textures = root.folders.getValue("textures")
        assertEquals("Textures", textures.name)
        assertEquals("MixedCase", textures.folders.getValue("mixedcase").name)
    }

    @Test
    fun emptySnapshotProducesAnEmptyRootFolder() {
        val root = ContentDirectory.fromEntries(emptyList(), "empty.pak")
        assertTrue(root.folders.isEmpty())
        assertTrue(root.files.isEmpty())
    }
}
