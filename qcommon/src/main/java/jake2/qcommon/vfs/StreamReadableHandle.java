package jake2.qcommon.vfs;

import java.io.IOException;
import java.io.InputStream;

/**
 * Generic stream-backed readable handle.
 */
public final class StreamReadableHandle implements VfsReadableHandle {
    private final InputStream stream;
    private final long size;

    public StreamReadableHandle(InputStream stream, long size) {
        this.stream = stream;
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
