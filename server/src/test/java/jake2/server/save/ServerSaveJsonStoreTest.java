package jake2.server.save;

import jake2.qcommon.vfs.DefaultWritableFileSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerSaveJsonStoreTest {
    @TempDir
    Path temp;

    @Test
    void roundTripsServerMapCommandSnapshot() throws Exception {
        ServerSaveJsonStore store = new ServerSaveJsonStore(new DefaultWritableFileSystem(temp));

        store.writeMapCommand("current", "Autosave in base1", "gamemap \"*base1\"");

        assertTrue(store.hasMapCommand("current"));
        ServerSaveJsonStore.ServerMapCommandSnapshot snapshot = store.readMapCommand("current");
        assertEquals(ServerSaveJsonStore.SCHEMA_VERSION, snapshot.schemaVersion());
        assertEquals("Autosave in base1", snapshot.comment());
        assertEquals("gamemap \"*base1\"", snapshot.mapCommand());
    }

    @Test
    void roundTripsLatchedCvarsSnapshot() throws Exception {
        ServerSaveJsonStore store = new ServerSaveJsonStore(new DefaultWritableFileSystem(temp));

        store.writeLatchedCvars(
                "current",
                List.of(
                        new ServerSaveJsonStore.LatchedCvarSnapshot("deathmatch", "0"),
                        new ServerSaveJsonStore.LatchedCvarSnapshot("skill", "2")
                )
        );

        ServerSaveJsonStore.ServerLatchedCvarsSnapshot snapshot = store.readLatchedCvars("current");
        assertEquals(ServerSaveJsonStore.SCHEMA_VERSION, snapshot.schemaVersion());
        assertEquals(2, snapshot.cvars().size());
        assertEquals("deathmatch", snapshot.cvars().get(0).name());
        assertEquals("2", snapshot.cvars().get(1).value());
    }

    @Test
    void missingMapCommandReturnsFalse() {
        ServerSaveJsonStore store = new ServerSaveJsonStore(new DefaultWritableFileSystem(temp));

        assertFalse(store.hasMapCommand("missing"));
    }

    @Test
    void keepsLegacySaveFilenamesWhileWritingJsonContent() throws Exception {
        ServerSaveJsonStore store = new ServerSaveJsonStore(new DefaultWritableFileSystem(temp));

        store.writeMapCommand("current", "comment", "mapcmd");

        assertTrue(Files.isRegularFile(temp.resolve("save/current/server_mapcmd.ssv")));
    }
}
