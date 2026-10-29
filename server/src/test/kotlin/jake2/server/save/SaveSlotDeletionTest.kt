package jake2.server.save

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class SaveSlotDeletionTest {
    @TempDir lateinit var root: Path

    @Test
    fun `deletion removes the entire selected checkpoint and preserves siblings and working state`() {
        for (slot in listOf("manual", "other", "current")) {
            Files.createDirectories(root.resolve("save/$slot"))
            Files.writeString(root.resolve("save/$slot/game.ssv.json"), slot)
        }
        Files.createDirectories(root.resolve("save/manual/sidecars"))
        Files.writeString(root.resolve("save/manual/sidecars/preview.png"), "preview")
        assertThrows(IllegalArgumentException::class.java) { deleteSaveSlot(root, " manual ") }
        assertTrue(Files.exists(root.resolve("save/manual/game.ssv.json")))
        deleteSaveSlot(root, "manual")
        assertFalse(Files.exists(root.resolve("save/manual")))
        assertEquals("other", Files.readString(root.resolve("save/other/game.ssv.json")))
        assertEquals("current", Files.readString(root.resolve("save/current/game.ssv.json")))
    }

    @Test
    fun `invalid internal missing and linked slots are rejected without changing outside files`() {
        Files.createDirectories(root.resolve("save/current"))
        val outside = Files.createDirectory(root.resolve("outside"))
        Files.writeString(outside.resolve("sentinel"), "keep")
        Files.createSymbolicLink(root.resolve("save/linked"), outside)
        for (slot in listOf("current", "../outside", "linked", "missing", "")) {
            assertThrows(IllegalArgumentException::class.java) { deleteSaveSlot(root, slot) }
        }
        assertEquals("keep", Files.readString(outside.resolve("sentinel")))
        assertTrue(Files.isDirectory(root.resolve("save/current")))
    }

    @Test
    fun `nested links are deleted without following them`() {
        val outside = Files.createDirectory(root.resolve("outside"))
        Files.writeString(outside.resolve("sentinel"), "keep")
        Files.createDirectories(root.resolve("save/manual"))
        Files.createSymbolicLink(root.resolve("save/manual/nested"), outside)
        deleteSaveSlot(root, "manual")
        assertFalse(Files.exists(root.resolve("save/manual")))
        assertEquals("keep", Files.readString(outside.resolve("sentinel")))
    }
}
