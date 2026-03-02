package jake2.qcommon.vfs;

import jake2.qcommon.filesystem.VfsBackedFileSystem;
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
import static org.junit.Assert.assertTrue;

/**
 * Cross-checks read-side VFS layering and writable save-path behavior together.
 */
public class VfsReadWriteIntegrationTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void readLayeringAndWritableSavePathStayDeterministic() throws Exception {
        Path basedir = temp.newFolder("q2").toPath();
        writePak(
                basedir.resolve("baseq2/pak0.pak"),
                Map.of("maps/test.bsp", "base-pack".getBytes(StandardCharsets.US_ASCII))
        );
        Path modMap = basedir.resolve("rogue/maps/test.bsp");
        Files.createDirectories(modMap.getParent());
        Files.write(modMap, "mod-loose".getBytes(StandardCharsets.US_ASCII));

        VfsBackedFileSystem readFs = new VfsBackedFileSystem();
        readFs.configure(basedir, "baseq2", "rogue", true, false);

        assertArrayEquals("mod-loose".getBytes(StandardCharsets.US_ASCII), readFs.loadFile("maps/test.bsp"));

        Path writeRoot = temp.newFolder("write-root").toPath();
        DefaultWritableFileSystem writable = new DefaultWritableFileSystem(writeRoot);
        byte[] saveData = "autosave".getBytes(StandardCharsets.US_ASCII);

        VfsResult<VfsWritableHandle> openedWrite = writable.openWrite("save/current/server_mapcmd.ssv", VfsWriteOptions.TRUNCATE);
        assertTrue(openedWrite.success);
        try (VfsWritableHandle handle = openedWrite.value) {
            handle.outputStream().write(saveData);
        }

        assertFalse(readFs.exists("save/current/server_mapcmd.ssv"));

        VfsResult<VfsReadableHandle> openedRead = writable.openReadReal("save/current/server_mapcmd.ssv", VfsOpenOptions.DEFAULT);
        assertTrue(openedRead.success);
        try (VfsReadableHandle handle = openedRead.value) {
            assertArrayEquals(saveData, handle.inputStream().readAllBytes());
        }
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
