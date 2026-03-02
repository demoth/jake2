package jake2.qcommon.filesystem;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
        assertTrue(fs.loadMappedFile("does/not/exist.txt") == null);
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

    @Test
    public void loadMappedFileMapsLooseFile() throws Exception {
        Path basedir = temp.newFolder("q2").toPath();
        Path file = basedir.resolve("baseq2/video/test.cin");
        Files.createDirectories(file.getParent());
        Files.write(file, "cin".getBytes(StandardCharsets.UTF_8));

        VfsBackedFileSystem fs = new VfsBackedFileSystem();
        fs.configure(basedir, "baseq2", null, true, false);

        ByteBuffer buffer = fs.loadMappedFile("video/test.cin");
        assertNotNull(buffer);
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        assertArrayEquals("cin".getBytes(StandardCharsets.UTF_8), bytes);
    }

    @Test
    public void loadMappedFileReadsPakEntryAsReadonlyBuffer() throws Exception {
        Path basedir = temp.newFolder("q2").toPath();
        Path pak = basedir.resolve("baseq2/pak0.pak");
        writePak(pak, Map.of("video/test.cin", "pakcin".getBytes(StandardCharsets.US_ASCII)));

        VfsBackedFileSystem fs = new VfsBackedFileSystem();
        fs.configure(basedir, "baseq2", null, true, false);

        ByteBuffer buffer = fs.loadMappedFile("video/test.cin");
        assertNotNull(buffer);
        assertTrue(buffer.isReadOnly());
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        assertArrayEquals("pakcin".getBytes(StandardCharsets.US_ASCII), bytes);
    }

    private static void writePak(Path target, Map<String, byte[]> entries) throws IOException {
        Files.createDirectories(target.getParent());
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        ByteArrayOutputStream directory = new ByteArrayOutputStream();

        int offset = 12;
        Map<String, byte[]> ordered = new LinkedHashMap<>(entries);
        for (Map.Entry<String, byte[]> entry : ordered.entrySet()) {
            byte[] content = entry.getValue();
            data.write(content);

            ByteBuffer dirEntry = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
            byte[] name = entry.getKey().getBytes(StandardCharsets.US_ASCII);
            if (name.length > 56) {
                throw new IllegalArgumentException("Entry name too long: " + entry.getKey());
            }
            dirEntry.put(name);
            dirEntry.position(56);
            dirEntry.putInt(offset);
            dirEntry.putInt(content.length);
            directory.write(dirEntry.array());
            offset += content.length;
        }

        ByteBuffer header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        header.put((byte) 'P').put((byte) 'A').put((byte) 'C').put((byte) 'K');
        header.putInt(12 + data.size());
        header.putInt(directory.size());

        ByteArrayOutputStream full = new ByteArrayOutputStream();
        full.write(header.array());
        full.write(data.toByteArray());
        full.write(directory.toByteArray());
        Files.write(target, full.toByteArray());
    }
}
