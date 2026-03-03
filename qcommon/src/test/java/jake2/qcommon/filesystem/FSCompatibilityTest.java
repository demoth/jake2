package jake2.qcommon.filesystem;

import jake2.qcommon.exec.Cmd;
import org.junit.Rule;
import org.junit.Test;
import org.junit.BeforeClass;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class FSCompatibilityTest {
    @BeforeClass
    public static void initFilesystemCommands() {
        FS.InitFilesystem();
    }

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void openWriteAndOpenReadRoundTripAbsolutePath() throws Exception {
        File target = new File(temp.getRoot(), "save/current/server_mapcmd.ssv");
        String absolutePath = target.getAbsolutePath();

        try (QuakeFile out = FS.OpenWriteFile(absolutePath)) {
            out.writeString("Autosave in base1");
            out.writeString("base1");
        }

        assertTrue(target.exists());

        try (QuakeFile in = FS.OpenReadFile(absolutePath)) {
            assertEquals("Autosave in base1", in.readString());
            assertEquals("base1", in.readString());
        }
    }

    @Test(expected = FileNotFoundException.class)
    public void openReadThrowsForMissingAbsolutePath() throws Exception {
        File missing = new File(temp.getRoot(), "save/current/missing.ssv");
        FS.OpenReadFile(missing.getAbsolutePath());
    }

    @Test
    public void openWriteCreatesParentDirectories() throws Exception {
        File target = new File(temp.getRoot(), "deep/path/to/config.cfg");
        assertTrue(!target.getParentFile().exists());

        try (QuakeFile out = FS.OpenWriteFile(target.getAbsolutePath())) {
            out.write("bind w +forward".getBytes(StandardCharsets.US_ASCII));
        }

        assertTrue(target.getParentFile().exists());
        assertTrue(target.exists());
    }

    @Test
    public void loadMappedFileReadsAbsolutePath() throws Exception {
        File target = new File(temp.getRoot(), "video/test.cin");
        FS.CreatePath(target.getAbsolutePath());
        byte[] payload = "cin-data".getBytes(StandardCharsets.US_ASCII);
        try (QuakeFile out = FS.OpenWriteFile(target.getAbsolutePath())) {
            out.write(payload);
        }

        ByteBuffer mapped = FS.LoadMappedFile(target.getAbsolutePath());
        assertTrue(mapped != null);
        byte[] read = new byte[mapped.remaining()];
        mapped.get(read);
        assertEquals("cin-data", new String(read, StandardCharsets.US_ASCII));
    }

    @Test
    public void fileExistsResolvesFsLinks() throws Exception {
        Path mountRoot = temp.newFolder("link-root").toPath();
        Files.createDirectories(mountRoot.resolve("maps"));
        Files.writeString(mountRoot.resolve("maps/test.bsp"), "dummy", StandardCharsets.US_ASCII);

        String linkPrefix = "compat-link-" + System.nanoTime();
        Cmd.ExecuteString("link " + linkPrefix + " " + mountRoot.toAbsolutePath());

        assertTrue(FS.FileExists(linkPrefix + "/maps/test.bsp"));
    }

    @Test
    public void loadFileResolvesFsLinks() throws Exception {
        Path mountRoot = temp.newFolder("link-root-load").toPath();
        Files.createDirectories(mountRoot.resolve("pics"));
        Files.writeString(mountRoot.resolve("pics/colormap.pcx"), "pcx-payload", StandardCharsets.US_ASCII);

        String linkPrefix = "compat-load-" + System.nanoTime();
        Cmd.ExecuteString("link " + linkPrefix + " " + mountRoot.toAbsolutePath());

        byte[] data = FS.LoadFile(linkPrefix + "/pics/colormap.pcx");
        assertNotNull(data);
        assertEquals("pcx-payload", new String(data, StandardCharsets.US_ASCII));
    }

    @Test
    public void loadMappedFileResolvesFsLinks() throws Exception {
        Path mountRoot = temp.newFolder("link-root-mapped").toPath();
        Files.createDirectories(mountRoot.resolve("video"));
        Files.writeString(mountRoot.resolve("video/intro.cin"), "mapped-link-data", StandardCharsets.US_ASCII);

        String linkPrefix = "compat-mapped-" + System.nanoTime();
        Cmd.ExecuteString("link " + linkPrefix + " " + mountRoot.toAbsolutePath());

        ByteBuffer mapped = FS.LoadMappedFile(linkPrefix + "/video/intro.cin");
        assertNotNull(mapped);
        byte[] read = new byte[mapped.remaining()];
        mapped.get(read);
        assertEquals("mapped-link-data", new String(read, StandardCharsets.US_ASCII));
    }
}
