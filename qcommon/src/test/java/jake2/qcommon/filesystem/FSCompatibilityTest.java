package jake2.qcommon.filesystem;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FSCompatibilityTest {
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
}
