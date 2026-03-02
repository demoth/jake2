package jake2.qcommon.filesystem;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VfsBackedFileSystemTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void resolvesBaseFilesAfterConfigure() throws Exception {
        Path basedir = temp.newFolder("q2").toPath();
        Path file = basedir.resolve("baseq2/config.cfg");
        Files.createDirectories(file.getParent());
        Files.write(file, "cfg".getBytes(StandardCharsets.UTF_8));

        VfsBackedFileSystem fs = new VfsBackedFileSystem();
        fs.configure(basedir, "baseq2", null, true, false);

        assertTrue(fs.exists("config.cfg"));
        assertArrayEquals("cfg".getBytes(StandardCharsets.UTF_8), fs.loadFile("config.cfg"));
    }

    @Test
    public void setGameModSwitchesResolution() throws Exception {
        Path basedir = temp.newFolder("q2").toPath();
        Path base = basedir.resolve("baseq2/config.cfg");
        Path mod = basedir.resolve("rogue/config.cfg");
        Files.createDirectories(base.getParent());
        Files.createDirectories(mod.getParent());
        Files.write(base, "base".getBytes(StandardCharsets.UTF_8));
        Files.write(mod, "mod".getBytes(StandardCharsets.UTF_8));

        VfsBackedFileSystem fs = new VfsBackedFileSystem();
        fs.configure(basedir, "baseq2", null, true, false);
        assertArrayEquals("base".getBytes(StandardCharsets.UTF_8), fs.loadFile("config.cfg"));

        fs.setGameMod("rogue");
        assertArrayEquals("mod".getBytes(StandardCharsets.UTF_8), fs.loadFile("config.cfg"));
    }

    @Test
    public void missingEntriesReturnNullOrFalse() throws Exception {
        Path basedir = temp.newFolder("q2").toPath();
        VfsBackedFileSystem fs = new VfsBackedFileSystem();
        fs.configure(basedir, "baseq2", null, true, false);

        assertFalse(fs.exists("does/not/exist.txt"));
        assertTrue(fs.loadFile("does/not/exist.txt") == null);
    }

    @Test
    public void caseSensitiveModeCanBeToggled() throws Exception {
        Path basedir = temp.newFolder("q2").toPath();
        Path model = basedir.resolve("baseq2/Players/Male/Tris.MD2");
        Files.createDirectories(model.getParent());
        Files.write(model, "md2".getBytes(StandardCharsets.UTF_8));

        VfsBackedFileSystem fs = new VfsBackedFileSystem();
        fs.configure(basedir, "baseq2", null, true, false);
        assertTrue(fs.exists("players/male/tris.md2"));

        fs.setCaseSensitive(true);
        assertFalse(fs.exists("players/male/tris.md2"));
        assertTrue(fs.exists("Players/Male/Tris.MD2"));
    }
}
