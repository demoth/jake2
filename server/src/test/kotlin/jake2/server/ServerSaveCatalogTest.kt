package jake2.server

import jake2.server.save.ServerSaveJsonStore
import jake2.server.save.ServerSaveJsonStore.LatchedCvarSnapshot
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

class ServerSaveCatalogTest {
    @TempDir lateinit var root: Path

    private fun metadata(slot: String, time: Long, coop: Boolean = false, deathmatch: Boolean = false) {
        val store = ServerSaveJsonStore.forWriteDir(root.toString())
        store.writeMapCommand(slot, "Checkpoint $slot", "*testbox")
        store.writeLatchedCvars(slot, listOf(
            LatchedCvarSnapshot("maxclients", if (coop || deathmatch) "4" else "1"),
            LatchedCvarSnapshot("coop", if (coop) "1" else "0"),
            LatchedCvarSnapshot("deathmatch", if (deathmatch) "1" else "0"),
        ))
        Files.writeString(root.resolve("save/$slot/game.ssv.json"), "{}")
        Files.setLastModifiedTime(root.resolve("save/$slot/server_mapcmd.ssv.json"), FileTime.fromMillis(time))
    }

    @Test
    fun `missing save directory is empty and listing does not create it`() {
        assertEquals(emptyList<SavedGameEntry>(), ServerSaveCatalog(root).list())
        assertFalse(Files.exists(root.resolve("save")))
    }

    @Test
    fun `catalog sorts checkpoints retains autosave and isolates malformed entries`() {
        metadata("current", 5000)
        metadata("console checkpoint", 1000)
        metadata("save0", 3000)
        metadata("coop", 2000, coop = true)
        metadata("deathmatch", 2000, deathmatch = true)
        metadata("broken", 4000)
        Files.writeString(root.resolve("save/broken/server_mapcmd.ssv.json"), "{broken")
        Files.createDirectories(root.resolve("save/incomplete"))
        Files.writeString(root.resolve("save/readme.txt"), "not a slot")
        val entries = ServerSaveCatalog(root).list()
        assertFalse(entries.any { it.slot == "current" || it.slot == "readme.txt" })
        assertEquals(listOf("save0", "coop", "deathmatch", "console checkpoint"),
            entries.filter { it.problem == null }.map { it.slot })
        assertEquals("testbox", entries.first { it.slot == "save0" }.mapName)
        assertEquals(SavedGameMode.COOP, entries.first { it.slot == "coop" }.mode)
        assertEquals(SavedGameMode.DEATHMATCH, entries.first { it.slot == "deathmatch" }.mode)
        assertNotNull(entries.first { it.slot == "broken" }.problem)
        assertNotNull(entries.first { it.slot == "incomplete" }.problem)
    }

    @Test
    fun `bad versions settings and missing gameplay file do not appear usable`() {
        metadata("version", 1)
        Files.writeString(root.resolve("save/version/server_mapcmd.ssv.json"),
            """{"schemaVersion":99,"comment":"old","mapCommand":"testbox"}""")
        metadata("settings", 2)
        Files.writeString(root.resolve("save/settings/server_latched_cvars.ssv.json"), "{}")
        metadata("missing", 3)
        Files.delete(root.resolve("save/missing/game.ssv.json"))
        assertTrue(ServerSaveCatalog(root).list().all { it.problem != null })
        assertNotNull(ServerSaveCatalog(root).inspect("../outside").problem)
    }

    @Test
    fun `slot links are not browsed and save root failures are not mistaken for empty catalogs`() {
        val outside = Files.createDirectory(root.resolve("outside"))
        Files.createDirectory(root.resolve("save"))
        Files.createSymbolicLink(root.resolve("save/link"), outside)
        assertTrue(ServerSaveCatalog(root).list().isEmpty())
        assertNotNull(ServerSaveCatalog(root).inspect("link").problem)
        Files.delete(root.resolve("save/link"))
        Files.delete(root.resolve("save"))
        Files.writeString(root.resolve("save"), "not a directory")
        assertThrows(java.io.IOException::class.java) { ServerSaveCatalog(root).list() }
    }
}
