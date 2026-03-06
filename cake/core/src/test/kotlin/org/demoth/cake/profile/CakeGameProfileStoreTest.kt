package org.demoth.cake.profile

import jake2.qcommon.vfs.DefaultWritableFileSystem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class CakeGameProfileStoreTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun upsertAndReadSelectedRoundTrip() {
        val basedir = Files.createDirectories(temp.resolve("quake2"))
        val store = CakeGameProfileStore(
            writableFactory = { DefaultWritableFileSystem(temp) },
        )

        val writtenPath = store.upsertProfile(
            CakeGameProfile(
                id = "Default01",
                basedir = basedir.toString(),
                gamemod = "rogue",
            ),
        )

        assertEquals(temp.resolve("profiles.json").toString(), writtenPath)
        assertEquals(
            CakeGameProfile(id = "Default01", basedir = basedir.toString(), gamemod = "rogue"),
            store.readSelected(),
        )
    }

    @Test
    fun bootstrapDefaultCreatesSelectedProfileWhenMissing() {
        val basedir = Files.createDirectories(temp.resolve("quake2"))
        val store = CakeGameProfileStore(
            writableFactory = { DefaultWritableFileSystem(temp) },
        )

        val selected = store.bootstrapDefault(
            CakeGameProfile(
                id = CakeGameProfileStore.DEFAULT_PROFILE_ID,
                basedir = basedir.toString(),
            ),
        )

        assertEquals(CakeGameProfileStore.DEFAULT_PROFILE_ID, selected.id)
        assertEquals(selected, store.readSelected())
    }

    @Test
    fun clearRemovesProfilesConfig() {
        val basedir = Files.createDirectories(temp.resolve("quake2"))
        val store = CakeGameProfileStore(
            writableFactory = { DefaultWritableFileSystem(temp) },
        )

        store.upsertProfile(CakeGameProfile(id = "main", basedir = basedir.toString(), gamemod = "rogue"))
        store.clear()

        assertNull(store.readSelected())
    }

    @Test
    fun rejectsInvalidProfileValues() {
        val basedir = Files.createDirectories(temp.resolve("quake2"))
        val store = CakeGameProfileStore(
            writableFactory = { DefaultWritableFileSystem(temp) },
        )

        assertThrows(IllegalArgumentException::class.java) {
            store.upsertProfile(CakeGameProfile(id = "not-valid", basedir = basedir.toString()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.upsertProfile(CakeGameProfile(id = "Valid01", basedir = temp.resolve("missing").toString()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.upsertProfile(CakeGameProfile(id = "Valid02", basedir = basedir.toString(), gamemod = "../xatrix"))
        }

        assertNull(store.readSelected())
    }
}
