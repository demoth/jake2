package jake2.qcommon.vfs;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Writable handle returned by writable VFS operations.
 */
public interface VfsWritableHandle extends Closeable {
    OutputStream outputStream() throws IOException;

    @Override
    void close() throws IOException;
}

