package jake2.qcommon.vfs;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Read-only package backend abstraction.
 */
public interface PackReader {
    String type();

    List<PackEntry> entries();

    InputStream openEntry(PackEntry entry) throws IOException;
}

