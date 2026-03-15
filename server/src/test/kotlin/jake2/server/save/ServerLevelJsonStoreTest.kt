package jake2.server.save

import jake2.qcommon.CM
import jake2.qcommon.Defines
import jake2.qcommon.vfs.DefaultWritableFileSystem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ServerLevelJsonStoreTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun roundTripsLevelStateSnapshot() {
        val store = ServerLevelJsonStore(DefaultWritableFileSystem(temp))
        val configstrings = arrayOfNulls<String>(Defines.MAX_CONFIGSTRINGS)
        configstrings[1] = "maps/base1.bsp"
        configstrings[2] = "sound/world/ambience.wav"
        val portalOpen = BooleanArray(Defines.MAX_MAP_AREAPORTALS)
        portalOpen[3] = true
        portalOpen[7] = true

        store.writeLevelState("current", "base1", configstrings, portalOpen)

        val snapshot = store.readLevelState("current", "base1")
        assertEquals(ServerLevelJsonStore.SCHEMA_VERSION, snapshot.schemaVersion)
        assertEquals("maps/base1.bsp", snapshot.configstrings[1])
        assertEquals("sound/world/ambience.wav", snapshot.configstrings[2])
        assertTrue(snapshot.portalOpen[3])
        assertTrue(snapshot.portalOpen[7])
        assertFalse(snapshot.portalOpen[1])
    }

    @Test
    fun applyLevelStateRestoresConfigstringsAndPortalFlags() {
        val store = ServerLevelJsonStore(DefaultWritableFileSystem(temp))
        val configstrings = arrayOfNulls<String>(Defines.MAX_CONFIGSTRINGS)
        configstrings[5] = "models/items/armor/tris.md2"
        val portalOpen = BooleanArray(Defines.MAX_MAP_AREAPORTALS)
        portalOpen[4] = true
        store.writeLevelState("current", "base1", configstrings, portalOpen)

        val cm = CM()
        cm.numareas = 1
        cm.portalopen[4] = false
        val restoredConfigstrings = Array<String?>(Defines.MAX_CONFIGSTRINGS) { "stale" }

        store.applyLevelState(store.readLevelState("current", "base1"), restoredConfigstrings, cm)

        assertEquals("models/items/armor/tris.md2", restoredConfigstrings[5])
        assertEquals(null, restoredConfigstrings[0])
        assertTrue(cm.portalopen[4])
    }

    @Test
    fun keepsLegacySv2FilenameWhileWritingJsonContent() {
        val store = ServerLevelJsonStore(DefaultWritableFileSystem(temp))

        store.writeLevelState("current", "base1", arrayOfNulls(Defines.MAX_CONFIGSTRINGS), BooleanArray(Defines.MAX_MAP_AREAPORTALS))

        assertTrue(Files.isRegularFile(temp.resolve("save/current/base1.sv2")))
    }
}
