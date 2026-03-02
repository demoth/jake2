package jake2.qcommon.vfs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Filesystem-backed readable handle.
 */
public final class PathReadableHandle implements VfsReadableHandle {
    private final InputStream stream;
    private final long size;

    public PathReadableHandle(Path path, long size) throws IOException {
        this.stream = Files.newInputStream(path);
        this.size = size;
    }

    @Override
    public InputStream inputStream() {
        return stream;
    }

    @Override
    public long size() {
        return size;
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }
}

