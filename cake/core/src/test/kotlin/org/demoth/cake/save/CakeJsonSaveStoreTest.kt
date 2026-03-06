package org.demoth.cake.save

import jake2.qcommon.vfs.DefaultWritableFileSystem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import java.nio.file.Path

class CakeJsonSaveStoreTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun writeAndReadRoundTripBySlotAndProfile() {
        val root = temp
        val store = CakeJsonSaveStore(writableFactory = { profileId: String ->
            DefaultWritableFileSystem(root.resolve(profileId))
        })

        val snapshot = CakeSaveSnapshot(
            map = "base1",
            title = "Autosave in base1",
            timestampMillis = 123456789L,
            autosave = true,
        )

        val writtenPath = store.write("current", "default", snapshot)
        assertNotNull(writtenPath)
        assertEquals(root.resolve("default/save/current/cake-save.json").toString(), writtenPath)
        assertEquals(true, Files.exists(root.resolve("default/save/current/cake-save.json")))

        val loaded = store.read("current", "default")
        assertEquals(snapshot, loaded)
    }

    @Test
    fun readReturnsNullForMissingSlot() {
        val root = temp
        val store = CakeJsonSaveStore(writableFactory = { profileId: String ->
            DefaultWritableFileSystem(root.resolve(profileId))
        })

        assertNull(store.read("missing", "default"))
    }

    @Test
    fun rejectsTraversalSlotName() {
        val root = temp
        val store = CakeJsonSaveStore(writableFactory = { profileId: String ->
            DefaultWritableFileSystem(root.resolve(profileId))
        })

        assertThrows<IllegalArgumentException> {
            store.write(
                "../outside",
                "default",
                CakeSaveSnapshot("base1", "bad", 1L, autosave = false),
            )
        }
    }
}
