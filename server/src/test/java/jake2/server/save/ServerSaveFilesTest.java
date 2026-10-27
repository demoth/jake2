package jake2.server.save;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ServerSaveFilesTest {
    @TempDir Path temp;

    @Test
    void copiesSaveFilesAndLevelPairsReplacingStaleFiles() throws Exception {
        Path root = temp.resolve("profile");
        write(root, "current", "server_mapcmd.ssv.json", "map");
        write(root, "current", "server_latched_cvars.ssv.json", "settings");
        write(root, "current", "game.ssv.json", "game");
        write(root, "current", "base1.sav.json", "level");
        write(root, "current", "base1.sv2.json", "portals");
        write(root, "current", "orphan.sv2.json", "no matching level");
        write(root, "current", "notes.txt", "not a save file");
        write(root, "current", "preview.png", "working preview must not be copied");
        write(root, "manual", "game.ssv.json", "longer old game contents");
        write(root, "manual", "old.sav.json", "old level");
        write(root, "manual", "old.sv2.json", "old portals");
        write(root, "manual", "notes.txt", "keep me");
        write(root, "manual", "preview.png", "stale image");

        new ServerSaveFiles(root).copy("current", "manual");

        for (String file : new String[]{"server_mapcmd.ssv.json", "server_latched_cvars.ssv.json",
                "game.ssv.json", "base1.sav.json", "base1.sv2.json"}) {
            assertEquals(Files.readString(root.resolve("save/current/" + file)),
                    Files.readString(root.resolve("save/manual/" + file)));
        }
        assertFalse(Files.exists(root.resolve("save/manual/old.sav.json")));
        assertFalse(Files.exists(root.resolve("save/manual/old.sv2.json")));
        assertFalse(Files.exists(root.resolve("save/manual/orphan.sv2.json")));
        assertFalse(Files.exists(root.resolve("save/manual/notes.txt")));
        assertFalse(Files.exists(root.resolve("save/manual/preview.png")));
        assertEquals("not a save file", Files.readString(root.resolve("save/current/notes.txt")));
    }

    @Test
    void storesKeepIndependentRootsWhenOperationsAreInterleaved() throws Exception {
        Path first = temp.resolve("first");
        Path second = temp.resolve("second");
        ServerSaveFiles firstFiles = new ServerSaveFiles(first);
        ServerSaveFiles secondFiles = new ServerSaveFiles(second);
        write(first, "current", "game.ssv.json", "first game");
        write(second, "current", "game.ssv.json", "second game");

        firstFiles.copy("current", "shared");
        secondFiles.copy("current", "shared");
        firstFiles.wipe("shared");

        assertFalse(Files.exists(first.resolve("save/shared/game.ssv.json")));
        assertEquals("second game", Files.readString(second.resolve("save/shared/game.ssv.json")));
        assertEquals("first game", Files.readString(first.resolve("save/current/game.ssv.json")));
    }

    @Test
    void initialAutosaveDoesNotRequireArchivedLevels() throws Exception {
        write(temp, "current", "server_mapcmd.ssv.json", "map");
        ServerSaveFiles files = new ServerSaveFiles(temp);
        files.copy("current", "autosave");
        files.wipe("missing");
        assertEquals("map", Files.readString(temp.resolve("save/autosave/server_mapcmd.ssv.json")));
    }

    @Test
    void rejectsInvalidSlotsBeforeChangingDestinationAndSelfCopyIsHarmless() throws Exception {
        Path saved = write(temp, "manual", "game.ssv.json", "keep");
        ServerSaveFiles files = new ServerSaveFiles(temp);
        assertThrows(IllegalArgumentException.class, () -> files.copy("../outside", "manual"));
        assertThrows(IllegalArgumentException.class, () -> files.copy("manual", "../outside"));
        assertThrows(IllegalArgumentException.class, () -> files.wipe("../outside"));
        files.copy(" manual ", "manual");
        assertEquals("keep", Files.readString(saved));
    }

    @Test
    void filesystemFailuresAreReportedInsteadOfSilentlyContinuing() throws Exception {
        write(temp, "current", "game.ssv.json", "game");
        Path blocked = temp.resolve("save/manual/game.ssv.json");
        Files.createDirectories(blocked);
        Files.writeString(blocked.resolve("child"), "prevents deletion");
        ServerSaveFiles files = new ServerSaveFiles(temp);
        assertThrows(UncheckedIOException.class, () -> files.wipe("manual"));
        Files.delete(blocked.resolve("child"));
        Files.delete(blocked);
        Files.delete(blocked.getParent());
        Files.writeString(blocked.getParent(), "not a checkpoint directory");
        assertThrows(IllegalArgumentException.class, () -> files.copy("current", "manual"));
    }

    private Path write(Path root, String slot, String name, String content) throws Exception {
        Path file = root.resolve("save").resolve(slot).resolve(name);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }
}
