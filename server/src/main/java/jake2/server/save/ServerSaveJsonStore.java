package jake2.server.save;

import jake2.qcommon.save.SaveJson;
import jake2.qcommon.vfs.DefaultWritableFileSystem;
import jake2.qcommon.vfs.VfsOpenOptions;
import jake2.qcommon.vfs.VfsReadableHandle;
import jake2.qcommon.vfs.VfsResult;
import jake2.qcommon.vfs.VfsWritableHandle;
import jake2.qcommon.vfs.VfsWriteOptions;
import jake2.qcommon.vfs.WritableFileSystem;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * JSON-backed persistence for server-owned save metadata.
 *
 * Legacy filenames are preserved so save copy/wipe flows do not need to change
 * while the content format moves away from QuakeFile binary storage.
 */
public final class ServerSaveJsonStore {
    public static final int SCHEMA_VERSION = 1;

    private final WritableFileSystem writable;

    public ServerSaveJsonStore(WritableFileSystem writable) {
        this.writable = Objects.requireNonNull(writable, "writable");
    }

    public static ServerSaveJsonStore forWriteDir(String writeDir) {
        return new ServerSaveJsonStore(new DefaultWritableFileSystem(Path.of(writeDir)));
    }

    public boolean hasMapCommand(String slot) {
        VfsResult<VfsReadableHandle> opened = writable.openReadReal(mapCommandPath(slot), VfsOpenOptions.DEFAULT);
        if (!opened.success() || opened.value() == null) {
            return false;
        }
        try (VfsReadableHandle ignored = opened.value()) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public ServerMapCommandSnapshot readMapCommand(String slot) throws IOException {
        return read(mapCommandPath(slot), ServerMapCommandSnapshot.class);
    }

    public void writeMapCommand(String slot, String comment, String mapCommand) throws IOException {
        write(mapCommandPath(slot), new ServerMapCommandSnapshot(SCHEMA_VERSION, comment, mapCommand));
    }

    public ServerLatchedCvarsSnapshot readLatchedCvars(String slot) throws IOException {
        return read(latchedCvarsPath(slot), ServerLatchedCvarsSnapshot.class);
    }

    public void writeLatchedCvars(String slot, List<LatchedCvarSnapshot> cvars) throws IOException {
        write(latchedCvarsPath(slot), new ServerLatchedCvarsSnapshot(SCHEMA_VERSION, List.copyOf(cvars)));
    }

    private <T> T read(String logicalPath, Class<T> type) throws IOException {
        VfsResult<VfsReadableHandle> opened = writable.openReadReal(logicalPath, VfsOpenOptions.DEFAULT);
        if (!opened.success() || opened.value() == null) {
            throw new IOException(opened.error() == null ? "Failed to open " + logicalPath : opened.error());
        }

        try (VfsReadableHandle handle = opened.value()) {
            return SaveJson.read(handle.inputStream(), type);
        }
    }

    private void write(String logicalPath, Object value) throws IOException {
        VfsResult<VfsWritableHandle> opened = writable.openWrite(logicalPath, VfsWriteOptions.TRUNCATE);
        if (!opened.success() || opened.value() == null) {
            throw new IOException(opened.error() == null ? "Failed to open " + logicalPath : opened.error());
        }

        try (VfsWritableHandle handle = opened.value()) {
            SaveJson.write(handle.outputStream(), value);
        }
    }

    private static String mapCommandPath(String slot) {
        return "save/" + normalizeSlot(slot) + "/server_mapcmd.ssv";
    }

    private static String latchedCvarsPath(String slot) {
        return "save/" + normalizeSlot(slot) + "/server_latched_cvars.ssv";
    }

    private static String normalizeSlot(String slot) {
        if (slot == null) {
            throw new IllegalArgumentException("slot must not be null");
        }
        String normalized = slot.trim().replace('\\', '/');
        if (normalized.isEmpty() || normalized.contains("..") || normalized.contains("/") || normalized.contains(":")) {
            throw new IllegalArgumentException("Invalid save slot: " + slot);
        }
        return normalized;
    }

    public record ServerMapCommandSnapshot(
            int schemaVersion,
            String comment,
            String mapCommand
    ) {
    }

    public record LatchedCvarSnapshot(
            String name,
            String value
    ) {
    }

    public record ServerLatchedCvarsSnapshot(
            int schemaVersion,
            List<LatchedCvarSnapshot> cvars
    ) {
    }
}
