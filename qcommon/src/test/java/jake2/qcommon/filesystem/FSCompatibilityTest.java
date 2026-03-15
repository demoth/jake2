package jake2.qcommon.filesystem;

import jake2.qcommon.vfs.EngineFilesystemLifecycle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FSCompatibilityTest {
    @BeforeAll
    public static void initFilesystemCommands() {
        EngineFilesystemLifecycle.init();
    }

    @TempDir
    Path temp;

    @Test
    public void openReadThrowsForExistingAbsolutePathOutsideVfs() throws Exception {
        File target = temp.resolve("save/current/server_mapcmd.ssv.json").toFile();
        Files.createDirectories(target.toPath().getParent());
        Files.writeString(target.toPath(), "payload", StandardCharsets.US_ASCII);

        assertThrows(FileNotFoundException.class, () ->
                FS.OpenReadFile(target.getAbsolutePath()));
    }

    @Test
    public void openReadThrowsForMissingAbsolutePath() throws Exception {
        File missing = temp.resolve("save/current/missing.ssv").toFile();
        assertThrows(FileNotFoundException.class, () ->
                FS.OpenReadFile(missing.getAbsolutePath()));
    }

    @Test
    public void openWriteCreatesParentDirectories() throws Exception {
        File target = temp.resolve("deep/path/to/config.cfg").toFile();
        assertTrue(!target.getParentFile().exists());

        try (QuakeFile out = FS.OpenWriteFile(target.getAbsolutePath())) {
            out.write("bind w +forward".getBytes(StandardCharsets.US_ASCII));
        }

        assertTrue(target.getParentFile().exists());
        assertTrue(target.exists());
    }

    @Test
    public void loadMappedFileDoesNotReadAbsolutePath() throws Exception {
        File target = temp.resolve("video/test.cin").toFile();
        FS.CreatePath(target.getAbsolutePath());
        byte[] payload = "cin-data".getBytes(StandardCharsets.US_ASCII);
        try (QuakeFile out = FS.OpenWriteFile(target.getAbsolutePath())) {
            out.write(payload);
        }

        ByteBuffer mapped = FS.LoadMappedFile(target.getAbsolutePath());
        assertNull(mapped);
    }

    @Test
    public void fileExistsDoesNotTreatAbsolutePathAsReadableGameAsset() throws Exception {
        File target = temp.resolve("maps/test.bsp").toFile();
        Files.createDirectories(target.toPath().getParent());
        Files.writeString(target.toPath(), "dummy", StandardCharsets.US_ASCII);

        assertFalse(FS.FileExists(target.getAbsolutePath()));
    }

    @Test
    public void loadFileDoesNotReadAbsolutePath() throws Exception {
        File target = temp.resolve("pics/colormap.pcx").toFile();
        Files.createDirectories(target.toPath().getParent());
        Files.writeString(target.toPath(), "pcx-payload", StandardCharsets.US_ASCII);

        assertNull(FS.LoadFile(target.getAbsolutePath()));
    }

    @Test
    public void fileExistsDoesNotResolveUnmountedRelativePrefix() {
        assertFalse(FS.FileExists("compat-link/maps/test.bsp"));
    }
}
