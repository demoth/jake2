package org.demoth.cake.profile

import jake2.qcommon.vfs.DefaultWritableFileSystem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class CakeConsoleHistoryStoreTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun readHistoryReturnsEmptyListWhenMissing() {
        val store = CakeConsoleHistoryStore(
            writableFactory = { profileId -> DefaultWritableFileSystem(temp.resolve(profileId)) },
        )

        assertEquals(emptyList<String>(), store.readHistory("default"))
    }

    @Test
    fun writeHistoryRoundTripsPerProfile() {
        val store = CakeConsoleHistoryStore(
            writableFactory = { profileId -> DefaultWritableFileSystem(temp.resolve(profileId)) },
        )

        val alphaPath = store.writeHistory("alpha", listOf("status", "connect localhost"))
        store.writeHistory("beta", listOf("map demo1"))

        assertEquals(temp.resolve("alpha").resolve(CakeConsoleHistoryStore.HISTORY_FILE_NAME).toString(), alphaPath)
        assertEquals(listOf("status", "connect localhost"), store.readHistory("alpha"))
        assertEquals(listOf("map demo1"), store.readHistory("beta"))
    }
}
