package jake2.qcommon.vfs;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Filesystem-backed writable handle.
 */
public final class PathWritableHandle implements VfsWritableHandle {
    private final OutputStream stream;

    public PathWritableHandle(OutputStream stream) {
        this.stream = stream;
    }

    @Override
    public OutputStream outputStream() {
        return stream;
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }
}

