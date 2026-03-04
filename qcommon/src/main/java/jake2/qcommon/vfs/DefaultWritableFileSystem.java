package jake2.qcommon.vfs;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple writable filesystem rooted at one directory.
 *
 * This is intentionally separate from read mount layering:
 * writes always target one root (for example {@code $HOME/.cake/<mod>}).
 */
public final class DefaultWritableFileSystem implements WritableFileSystem {
    private final Path root;

    public DefaultWritableFileSystem(Path root) {
        this.root = root.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize writable root: " + this.root, e);
        }
    }

    public static DefaultWritableFileSystem forHome(String appDirectoryName, String modName) {
        final String effectiveApp = (appDirectoryName == null || appDirectoryName.isBlank())
                ? ".cake"
                : "." + appDirectoryName.replace("\\", "").replace("/", "");
        final String effectiveMod = (modName == null || modName.isBlank()) ? "baseq2" : modName;
        final Path home = Path.of(System.getProperty("user.home"));
        return new DefaultWritableFileSystem(home.resolve(effectiveApp).resolve(effectiveMod));
    }

    @Override
    public VfsResult<VfsWritableHandle> openWrite(String logicalPath, VfsWriteOptions options) {
        Path path = resolveRelativeWritePath(logicalPath);
        if (path == null) {
            return VfsResult.fail("Invalid write path: " + logicalPath);
        }

        VfsWriteOptions effective = options == null ? VfsWriteOptions.TRUNCATE : options;
        try {
            Files.createDirectories(path.getParent());
            final OutputStream stream;
            if (effective.append()) {
                stream = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
            } else if (effective.truncateExisting()) {
                stream = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            } else {
                stream = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            }
            return VfsResult.ok(new PathWritableHandle(stream));
        } catch (IOException e) {
            return VfsResult.fail("Failed to open writable path: " + e.getMessage());
        }
    }

    @Override
    public VfsResult<VfsReadableHandle> openReadReal(String logicalPath, VfsOpenOptions options) {
        Path path = resolveRelativeWritePath(logicalPath);
        if (path == null) {
            return VfsResult.fail("Invalid read path: " + logicalPath);
        }
        if (!Files.exists(path)) {
            return VfsResult.fail("File not found: " + logicalPath);
        }

        try {
            return VfsResult.ok(new PathReadableHandle(path, Files.size(path)));
        } catch (IOException e) {
            return VfsResult.fail("Failed to open read path: " + e.getMessage());
        }
    }

    @Override
    public String resolveWritePath(String logicalPath) {
        Path resolved = resolveRelativeWritePath(logicalPath);
        return resolved == null ? null : resolved.toString();
    }

    @Override
    public String writeRoot() {
        return root.toString();
    }

    private Path resolveRelativeWritePath(String logicalPath) {
        if (logicalPath == null || logicalPath.isBlank()) {
            return null;
        }

        final String slashPath = logicalPath.replace('\\', '/');
        final String[] parts = slashPath.split("/");
        final List<String> safeParts = new ArrayList<>();
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                return null;
            }
            safeParts.add(part);
        }
        if (safeParts.isEmpty()) {
            return null;
        }

        Path resolved = root.resolve(String.join("/", safeParts)).toAbsolutePath().normalize();
        if (!resolved.startsWith(root)) {
            return null;
        }
        return resolved;
    }
}

