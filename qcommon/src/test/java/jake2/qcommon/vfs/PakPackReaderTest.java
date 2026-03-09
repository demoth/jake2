package jake2.qcommon.vfs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PakPackReaderTest {
    @TempDir
    Path temp;

    @Test
    public void readsEntriesAndStreamsData() throws Exception {
        Path pak = temp.resolve("pak0.pak");
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("textures/wall.wal", "WALL".getBytes(StandardCharsets.US_ASCII));
        files.put("models/monsters/ogre/tris.md2", "MD2".getBytes(StandardCharsets.US_ASCII));
        writePak(pak, files);

        PakPackReader reader = new PakPackReader(pak, false);
        List<PackEntry> entries = reader.entries();

        assertEquals(2, entries.size());
        assertEquals("textures/wall.wal", entries.get(0).normalizedPath);
        assertEquals("models/monsters/ogre/tris.md2", entries.get(1).normalizedPath);

        PackEntry first = entries.get(0);
        byte[] data;
        try (InputStream stream = reader.openEntry(first)) {
            data = stream.readAllBytes();
        }
        assertArrayEquals(files.get("textures/wall.wal"), data);
    }

    @Test
    public void skipsInvalidTraversalEntries() throws Exception {
        Path pak = temp.resolve("pak1.pak");
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("../bad/file", "BAD".getBytes(StandardCharsets.US_ASCII));
        files.put("good/file.txt", "GOOD".getBytes(StandardCharsets.US_ASCII));
        writePak(pak, files);

        PakPackReader reader = new PakPackReader(pak, false);

        assertEquals(1, reader.entries().size());
        assertEquals("good/file.txt", reader.entries().get(0).normalizedPath);
    }

    @Test
    public void normalizesParentSegmentsInsideEntryPath() throws Exception {
        Path pak = temp.resolve("pak-parent.pak");
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("models/monsters/tank/../ctank/skin.pcx", "SKIN".getBytes(StandardCharsets.US_ASCII));
        writePak(pak, files);

        PakPackReader reader = new PakPackReader(pak, false);

        assertEquals(1, reader.entries().size());
        assertEquals("models/monsters/ctank/skin.pcx", reader.entries().get(0).normalizedPath);
    }

    @Test
    public void failsForInvalidHeader() throws Exception {
        Path pak = temp.resolve("broken.pak");
        Files.write(pak, "NOTPAK".getBytes(StandardCharsets.US_ASCII));
        assertThrows(IOException.class, () -> new PakPackReader(pak, false));
    }

    @Test
    public void caseSensitiveModePreservesCase() throws Exception {
        Path pak = temp.resolve("pak2.pak");
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("Players/Male/Tris.MD2", "X".getBytes(StandardCharsets.US_ASCII));
        writePak(pak, files);

        PakPackReader reader = new PakPackReader(pak, true);

        PackEntry entry = reader.entries().get(0);
        assertNotNull(entry);
        assertEquals("Players/Male/Tris.MD2", entry.normalizedPath);
        assertFalse("players/male/tris.md2".equals(entry.normalizedPath));
    }

    private static void writePak(Path target, Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        ByteArrayOutputStream directory = new ByteArrayOutputStream();

        int offset = 12; // header size

        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            byte[] content = entry.getValue();
            data.write(content);

            ByteBuffer dirEntry = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
            byte[] name = entry.getKey().getBytes(StandardCharsets.US_ASCII);
            if (name.length > 56) {
                throw new IllegalArgumentException("Entry name too long for PAK test fixture: " + entry.getKey());
            }
            dirEntry.put(name);
            dirEntry.position(56);
            dirEntry.putInt(offset);
            dirEntry.putInt(content.length);
            directory.write(dirEntry.array());

            offset += content.length;
        }

        int directoryOffset = 12 + data.size();
        int directoryLength = directory.size();

        ByteBuffer header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        header.put((byte) 'P').put((byte) 'A').put((byte) 'C').put((byte) 'K');
        header.putInt(directoryOffset);
        header.putInt(directoryLength);

        ByteArrayOutputStream full = new ByteArrayOutputStream();
        full.write(header.array());
        full.write(data.toByteArray());
        full.write(directory.toByteArray());
        Files.write(target, full.toByteArray());
    }
}
