package jake2.qcommon.vfs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * ZIP-based package reader for pk2/pk3/pkz/zip containers.
 */
public final class ZipPackReader implements PackReader {
    private final Path packagePath;
    private final String type;
    private final List<PackEntry> entries;

    public ZipPackReader(Path packagePath, boolean caseSensitive) throws IOException {
        this.packagePath = packagePath;
        this.type = detectType(packagePath);
        this.entries = Collections.unmodifiableList(readEntries(packagePath, caseSensitive));
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public List<PackEntry> entries() {
        return entries;
    }

    @Override
    public InputStream openEntry(PackEntry entry) throws IOException {
        ZipFile zip = new ZipFile(packagePath.toFile());
        ZipEntry zipEntry = zip.getEntry(entry.displayPath);
        if (zipEntry == null || zipEntry.isDirectory()) {
            zip.close();
            throw new IOException("ZIP entry not found: " + entry.displayPath);
        }
        InputStream stream = zip.getInputStream(zipEntry);
        return new ZipFileInputStream(zip, stream);
    }

    private static List<PackEntry> readEntries(Path packagePath, boolean caseSensitive) throws IOException {
        final VfsPathNormalizer normalizer = new VfsPathNormalizer(caseSensitive);
        final List<PackEntry> result = new ArrayList<>();

        try (ZipFile zip = new ZipFile(packagePath.toFile())) {
            Enumeration<? extends ZipEntry> zipEntries = zip.entries();
            while (zipEntries.hasMoreElements()) {
                ZipEntry zipEntry = zipEntries.nextElement();
                if (zipEntry.isDirectory()) {
                    continue;
                }
                String displayPath = zipEntry.getName();
                String normalizedPath = normalizer.normalizeOrNull(displayPath);
                if (normalizedPath == null) {
                    continue;
                }

                if (containsNormalizedPath(result, normalizedPath)) {
                    continue;
                }

                long size = zipEntry.getSize();
                result.add(new PackEntry(normalizedPath, displayPath, -1, Math.max(0, size)));
            }
        }

        return result;
    }

    private static boolean containsNormalizedPath(List<PackEntry> entries, String normalizedPath) {
        for (PackEntry entry : entries) {
            if (entry.normalizedPath.equals(normalizedPath)) {
                return true;
            }
        }
        return false;
    }

    private static String detectType(Path packagePath) {
        String name = packagePath.getFileName() == null ? "" : packagePath.getFileName().toString().toLowerCase();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "zip";
        }
        return name.substring(dot + 1);
    }

    private static final class ZipFileInputStream extends InputStream {
        private final ZipFile zipFile;
        private final InputStream delegate;

        private ZipFileInputStream(ZipFile zipFile, InputStream delegate) {
            this.zipFile = zipFile;
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            return delegate.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return delegate.read(b, off, len);
        }

        @Override
        public void close() throws IOException {
            try {
                delegate.close();
            } finally {
                zipFile.close();
            }
        }
    }
}
