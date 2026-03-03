package org.demoth.cake.save

import jake2.qcommon.vfs.DefaultWritableFileSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files

class CakeJsonSaveStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun writeAndReadRoundTripBySlotAndMod() {
        val root = temp.root.toPath()
        val store = CakeJsonSaveStore(writableFactory = { gameMod: String? ->
            DefaultWritableFileSystem(root.resolve(gameMod ?: "baseq2"))
        })

        val snapshot = CakeSaveSnapshot(
            map = "base1",
            title = "Autosave in base1",
            timestampMillis = 123456789L,
            autosave = true,
        )

        val writtenPath = store.write("current", "rogue", snapshot)
        assertNotNull(writtenPath)
        assertEquals(root.resolve("rogue/save/current/cake-save.json").toString(), writtenPath)
        assertEquals(true, Files.exists(root.resolve("rogue/save/current/cake-save.json")))

        val loaded = store.read("current", "rogue")
        assertEquals(snapshot, loaded)
    }

    @Test
    fun readReturnsNullForMissingSlot() {
        val root = temp.root.toPath()
        val store = CakeJsonSaveStore(writableFactory = { gameMod: String? ->
            DefaultWritableFileSystem(root.resolve(gameMod ?: "baseq2"))
        })

        assertNull(store.read("missing", "rogue"))
    }

    @Test
    fun rejectsTraversalSlotName() {
        val root = temp.root.toPath()
        val store = CakeJsonSaveStore(writableFactory = { gameMod: String? ->
            DefaultWritableFileSystem(root.resolve(gameMod ?: "baseq2"))
        })

        assertThrows<IllegalArgumentException> {
            store.write(
                "../outside",
                "rogue",
                CakeSaveSnapshot("base1", "bad", 1L, autosave = false),
            )
        }
    }
}
