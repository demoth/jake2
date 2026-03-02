package jake2.qcommon.vfs;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/**
 * Readable handle returned by VFS open operations.
 */
public interface VfsReadableHandle extends Closeable {
    InputStream inputStream() throws IOException;

    long size();

    @Override
    void close() throws IOException;
}

