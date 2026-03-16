package org.demoth.cake.modelviewer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ModelViewerStartupHintsTest {

    @TempDir
    lateinit var temp: Path

    @Test
    fun detectsBasedirFromCurrentFolder() {
        val installRoot = Files.createDirectories(temp.resolve("Quake2"))
        Files.createDirectories(installRoot.resolve("baseq2"))
        val openedFile = Files.createDirectories(temp.resolve("viewer")).resolve("tris.md2")
        Files.writeString(openedFile, "")

        val hints = detectModelViewerStartupHints(openedFile.toString(), installRoot)

        assertEquals(installRoot.toString(), hints.basedir)
        assertNull(hints.gamemod)
    }

    @Test
    fun detectsBasedirAndGamemodFromOpenedFilePath() {
        val installRoot = Files.createDirectories(temp.resolve("Quake2"))
        Files.createDirectories(installRoot.resolve("baseq2"))
        val openedFile = Files.createDirectories(installRoot.resolve("rogue/models/objects")).resolve("tris.md2")
        Files.writeString(openedFile, "")

        val hints = detectModelViewerStartupHints(openedFile.toString(), temp)

        assertEquals(installRoot.toString(), hints.basedir)
        assertEquals("rogue", hints.gamemod)
    }

    @Test
    fun returnsEmptyHintsWhenNoBaseq2ContextExists() {
        val openedFile = Files.createDirectories(temp.resolve("viewer")).resolve("tris.md2")
        Files.writeString(openedFile, "")

        val hints = detectModelViewerStartupHints(openedFile.toString(), temp)

        assertNull(hints.basedir)
        assertNull(hints.gamemod)
    }
}
