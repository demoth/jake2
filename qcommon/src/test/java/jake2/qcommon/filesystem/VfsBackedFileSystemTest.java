package jake2.qcommon.filesystem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VfsBackedFileSystemTest {
    @TempDir
    Path temp;

    @Test
    public void resolvesBaseFilesAfterConfigure() throws Exception {
        Path basedir = Files.createDirectories(temp.resolve("q2"));
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
        Path basedir = Files.createDirectories(temp.resolve("q2"));
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
        Path basedir = Files.createDirectories(temp.resolve("q2"));
        VfsBackedFileSystem fs = new VfsBackedFileSystem();
        fs.configure(basedir, "baseq2", null, true, false);

        assertFalse(fs.exists("does/not/exist.txt"));
        assertTrue(fs.loadFile("does/not/exist.txt") == null);
        assertTrue(fs.loadMappedFile("does/not/exist.txt") == null);
    }

    @Test
    public void caseSensitiveModeCanBeToggled() throws Exception {
        Path basedir = Files.createDirectories(temp.resolve("q2"));
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
        Path basedir = Files.createDirectories(temp.resolve("q2"));
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
        Path basedir = Files.createDirectories(temp.resolve("q2"));
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

    @Test
    public void openFileOpensLooseEntries() throws Exception {
        Path basedir = Files.createDirectories(temp.resolve("q2"));
        Path file = basedir.resolve("baseq2/maps/test.bsp");
        Files.createDirectories(file.getParent());
        Files.write(file, "bsp".getBytes(StandardCharsets.UTF_8));

        VfsBackedFileSystem fs = new VfsBackedFileSystem();
        fs.configure(basedir, "baseq2", null, true, false);

        QuakeFile opened = fs.openFile("maps/test.bsp");
        assertNotNull(opened);
        assertFalse(opened.fromPack);
        byte[] bytes = opened.toBytes();
        assertArrayEquals("bsp".getBytes(StandardCharsets.UTF_8), bytes);
    }

    @Test
    public void openFileOpensPakPackageEntries() throws Exception {
        Path basedir = Files.createDirectories(temp.resolve("q2"));
        Path pak = basedir.resolve("baseq2/pak0.pak");
        writePak(pak, Map.of("maps/test.bsp", "packbsp".getBytes(StandardCharsets.US_ASCII)));

        VfsBackedFileSystem fs = new VfsBackedFileSystem();
        fs.configure(basedir, "baseq2", null, true, false);

        QuakeFile opened = fs.openFile("maps/test.bsp");
        assertNotNull(opened);
        assertTrue(opened.fromPack);
        byte[] bytes = opened.toBytes();
        assertArrayEquals("packbsp".getBytes(StandardCharsets.US_ASCII), bytes);
    }

    @Test
    public void openFileOpensZipEntriesThroughTempBridge() throws Exception {
        Path basedir = Files.createDirectories(temp.resolve("q2"));
        Path zip = basedir.resolve("baseq2/assets.pk3");
        writeZip(zip, Map.of("pics/colormap.pcx", "zip".getBytes(StandardCharsets.US_ASCII)));

        VfsBackedFileSystem fs = new VfsBackedFileSystem();
        fs.configure(basedir, "baseq2", null, true, false);

        QuakeFile opened = fs.openFile("pics/colormap.pcx");
        assertNotNull(opened);
        assertTrue(opened.fromPack);
        byte[] bytes = opened.toBytes();
        assertArrayEquals("zip".getBytes(StandardCharsets.US_ASCII), bytes);
    }

    @Test
    public void isFromPackReflectsWinningSource() throws Exception {
        Path basedir = Files.createDirectories(temp.resolve("q2"));
        writePak(basedir.resolve("baseq2/pak0.pak"), Map.of("maps/test.bsp", "pak".getBytes(StandardCharsets.US_ASCII)));
        Files.createDirectories(basedir.resolve("rogue/maps"));
        Files.write(basedir.resolve("rogue/maps/test.bsp"), "loose".getBytes(StandardCharsets.US_ASCII));

        VfsBackedFileSystem fs = new VfsBackedFileSystem();
        fs.configure(basedir, "baseq2", null, true, false);
        assertTrue(fs.isFromPack("maps/test.bsp"));

        fs.setGameMod("rogue");
        assertFalse(fs.isFromPack("maps/test.bsp"));
    }

    @Test
    public void debugViewsExposeFilesMountsAndOverrides() throws Exception {
        Path basedir = Files.createDirectories(temp.resolve("q2"));
        Files.createDirectories(basedir.resolve("baseq2/textures"));
        Files.write(basedir.resolve("baseq2/textures/wall.wal"), "loose".getBytes(StandardCharsets.US_ASCII));
        writePak(basedir.resolve("baseq2/pak0.pak"), Map.of("textures/wall.wal", "pak".getBytes(StandardCharsets.US_ASCII)));
        writeZip(basedir.resolve("baseq2/assets.pk3"), Map.of("models/items/ammo/tris.md2", "zip".getBytes(StandardCharsets.US_ASCII)));

        VfsBackedFileSystem fs = new VfsBackedFileSystem();
        fs.configure(basedir, "baseq2", null, true, false);

        List<String> files = fs.debugResolvedFiles();
        List<String> mounts = fs.debugMounts();
        List<String> overrides = fs.debugOverrides();

        assertTrue(files.contains("textures/wall.wal"));
        assertTrue(files.contains("models/items/ammo/tris.md2"));
        assertEquals(files, files.stream().sorted().toList());

        assertTrue(mounts.stream().anyMatch(line -> line.contains("[BASE_LOOSE]")));
        assertTrue(mounts.stream().anyMatch(line -> line.contains("[BASE_PACK]") && line.contains("pak0.pak")));
        assertTrue(mounts.stream().anyMatch(line -> line.contains("[BASE_PACK]") && line.contains("assets.pk3")));

        assertTrue(overrides.stream().anyMatch(line -> line.startsWith("textures/wall.wal ->")));
    }

    @Test
    public void debugLooseRootsAndWildcardListingUseVfsOrder() throws Exception {
        Path basedir = Files.createDirectories(temp.resolve("q2"));
        Files.createDirectories(basedir.resolve("baseq2/players/male"));
        Files.createDirectories(basedir.resolve("rogue/players/female"));
        Files.write(basedir.resolve("baseq2/players/male/tris.md2"), "base".getBytes(StandardCharsets.US_ASCII));
        Files.write(basedir.resolve("rogue/players/female/tris.md2"), "mod".getBytes(StandardCharsets.US_ASCII));

        VfsBackedFileSystem fs = new VfsBackedFileSystem();
        fs.configure(basedir, "baseq2", "rogue", true, false);

        List<String> roots = fs.debugLooseMountRoots();
        assertEquals(2, roots.size());
        assertTrue(roots.get(0).replace('\\', '/').endsWith("/rogue"));
        assertTrue(roots.get(1).replace('\\', '/').endsWith("/baseq2"));

        List<String> matches = fs.debugFilesMatching("players/*/tris.md2");
        assertTrue(matches.contains("players/female/tris.md2"));
        assertTrue(matches.contains("players/male/tris.md2"));

        List<String> slashMatches = fs.debugFilesMatching("players\\*\\tris.md2");
        assertEquals(matches, slashMatches);
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

    private static void writeZip(Path target, Map<String, byte[]> entries) throws IOException {
        Files.createDirectories(target.getParent());
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(target))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                out.putNextEntry(zipEntry);
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
    }
}
