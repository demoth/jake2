package jake2.qcommon.vfs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultWritableFileSystemTest {
    @TempDir
    Path temp;

    @Test
    public void writeAndReadRoundTripInRoot() throws Exception {
        Path root = temp.resolve("write-root");
        DefaultWritableFileSystem wfs = new DefaultWritableFileSystem(root);

        byte[] written = "save-data".getBytes(StandardCharsets.UTF_8);
        VfsResult<VfsWritableHandle> write = wfs.openWrite("save/current/game.json", VfsWriteOptions.TRUNCATE);
        assertTrue(write.success());
        try (VfsWritableHandle handle = write.value()) {
            handle.outputStream().write(written);
        }

        VfsResult<VfsReadableHandle> read = wfs.openReadReal("save/current/game.json", VfsOpenOptions.DEFAULT);
        assertTrue(read.success());
        try (VfsReadableHandle handle = read.value()) {
            assertArrayEquals(written, handle.inputStream().readAllBytes());
        }
    }

    @Test
    public void appendModeAppendsContent() throws Exception {
        Path root = temp.resolve("write-root");
        DefaultWritableFileSystem wfs = new DefaultWritableFileSystem(root);

        VfsResult<VfsWritableHandle> first = wfs.openWrite("config.cfg", VfsWriteOptions.TRUNCATE);
        assertTrue(first.success());
        try (VfsWritableHandle handle = first.value()) {
            handle.outputStream().write("a".getBytes(StandardCharsets.UTF_8));
        }
        VfsResult<VfsWritableHandle> second = wfs.openWrite("config.cfg", VfsWriteOptions.APPEND);
        assertTrue(second.success());
        try (VfsWritableHandle handle = second.value()) {
            handle.outputStream().write("b".getBytes(StandardCharsets.UTF_8));
        }

        VfsResult<VfsReadableHandle> read = wfs.openReadReal("config.cfg", VfsOpenOptions.DEFAULT);
        assertTrue(read.success());
        try (VfsReadableHandle handle = read.value()) {
            assertArrayEquals("ab".getBytes(StandardCharsets.UTF_8), handle.inputStream().readAllBytes());
        }
    }

    @Test
    public void rejectsTraversalOutsideRoot() {
        Path root = temp.resolve("write-root");
        DefaultWritableFileSystem wfs = new DefaultWritableFileSystem(root);

        VfsResult<VfsWritableHandle> result = wfs.openWrite("../secrets.txt", VfsWriteOptions.TRUNCATE);

        assertFalse(result.success());
    }

    @Test
    public void homeFactoryCreatesCakeStyleRoot() {
        DefaultWritableFileSystem wfs = DefaultWritableFileSystem.forHome("cake", "xatrix");

        String writeRoot = wfs.writeRoot();
        assertNotNull(writeRoot);
        assertTrue(writeRoot.contains(".cake"));
        assertTrue(writeRoot.endsWith("xatrix"));
    }
}
