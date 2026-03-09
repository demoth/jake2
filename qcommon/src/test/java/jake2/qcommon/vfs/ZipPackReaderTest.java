package jake2.qcommon.vfs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ZipPackReaderTest {
    @TempDir
    Path temp;

    @Test
    public void readsEntriesAndStreamsData() throws Exception {
        Path pk3 = temp.resolve("pak2.pk3");
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("textures/wall.wal", "WALL".getBytes(StandardCharsets.US_ASCII));
        files.put("models/monsters/ogre/tris.md2", "MD2".getBytes(StandardCharsets.US_ASCII));
        writeZip(pk3, files);

        ZipPackReader reader = new ZipPackReader(pk3, false);
        List<PackEntry> entries = reader.entries();

        assertEquals(2, entries.size());
        assertEquals("textures/wall.wal", entries.get(0).normalizedPath);
        assertEquals("models/monsters/ogre/tris.md2", entries.get(1).normalizedPath);
        assertEquals("pk3", reader.type());

        PackEntry first = entries.get(0);
        byte[] data;
        try (InputStream stream = reader.openEntry(first)) {
            data = stream.readAllBytes();
        }
        assertArrayEquals(files.get("textures/wall.wal"), data);
    }

    @Test
    public void skipsInvalidTraversalEntries() throws Exception {
        Path zip = temp.resolve("mod.zip");
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("../bad/file", "BAD".getBytes(StandardCharsets.US_ASCII));
        files.put("good/file.txt", "GOOD".getBytes(StandardCharsets.US_ASCII));
        writeZip(zip, files);

        ZipPackReader reader = new ZipPackReader(zip, false);

        assertEquals(1, reader.entries().size());
        assertEquals("good/file.txt", reader.entries().get(0).normalizedPath);
    }

    @Test
    public void normalizesParentSegmentsInsideEntryPath() throws Exception {
        Path zip = temp.resolve("mod-parent.zip");
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("models/monsters/tank/../ctank/skin.pcx", "SKIN".getBytes(StandardCharsets.US_ASCII));
        writeZip(zip, files);

        ZipPackReader reader = new ZipPackReader(zip, false);

        assertEquals(1, reader.entries().size());
        assertEquals("models/monsters/ctank/skin.pcx", reader.entries().get(0).normalizedPath);
    }

    @Test
    public void failsForInvalidZip() throws Exception {
        Path broken = temp.resolve("broken.pk3");
        Files.write(broken, "NOTZIP".getBytes(StandardCharsets.US_ASCII));
        assertThrows(IOException.class, () -> new ZipPackReader(broken, false));
    }

    @Test
    public void caseSensitiveModePreservesCase() throws Exception {
        Path zip = temp.resolve("pak3.pk3");
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("Players/Male/Tris.MD2", "X".getBytes(StandardCharsets.US_ASCII));
        writeZip(zip, files);

        ZipPackReader reader = new ZipPackReader(zip, true);

        PackEntry entry = reader.entries().get(0);
        assertNotNull(entry);
        assertEquals("Players/Male/Tris.MD2", entry.normalizedPath);
        assertFalse("players/male/tris.md2".equals(entry.normalizedPath));
    }

    private static void writeZip(Path target, Map<String, byte[]> entries) throws IOException {
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
