package jake2.qcommon.vfs;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Quake PAK package reader.
 */
public final class PakPackReader implements PackReader {
    private static final int HEADER_SIZE = 12;
    private static final int DIRECTORY_ENTRY_SIZE = 64;
    private static final int NAME_SIZE = 56;
    private static final int PAK_IDENT = (('K' << 24) + ('C' << 16) + ('A' << 8) + 'P');

    private final Path packagePath;
    private final List<PackEntry> entries;

    public PakPackReader(Path packagePath, boolean caseSensitive) throws IOException {
        this.packagePath = packagePath;
        this.entries = Collections.unmodifiableList(readEntries(packagePath, caseSensitive));
    }

    @Override
    public String type() {
        return "pak";
    }

    @Override
    public List<PackEntry> entries() {
        return entries;
    }

    @Override
    public InputStream openEntry(PackEntry entry) throws IOException {
        final InputStream stream = Files.newInputStream(packagePath);
        skipExactly(stream, entry.offset);
        return new BoundedInputStream(stream, entry.length);
    }

    private static List<PackEntry> readEntries(Path packagePath, boolean caseSensitive) throws IOException {
        final VfsPathNormalizer normalizer = new VfsPathNormalizer(caseSensitive);
        final List<PackEntry> result = new ArrayList<>();

        try (RandomAccessFile file = new RandomAccessFile(packagePath.toFile(), "r")) {
            if (file.length() < HEADER_SIZE) {
                throw new IOException("PAK header is too short: " + packagePath);
            }

            ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
            file.getChannel().read(header, 0);
            header.flip();

            int ident = header.getInt();
            int directoryOffset = header.getInt();
            int directoryLength = header.getInt();

            if (ident != PAK_IDENT) {
                throw new IOException("Not a PAK file: " + packagePath);
            }
            if (directoryLength < 0 || directoryLength % DIRECTORY_ENTRY_SIZE != 0) {
                throw new IOException("Invalid PAK directory length: " + packagePath);
            }
            if (directoryOffset < 0 || directoryOffset + (long) directoryLength > file.length()) {
                throw new IOException("Invalid PAK directory offset: " + packagePath);
            }

            final int numEntries = directoryLength / DIRECTORY_ENTRY_SIZE;
            final ByteBuffer entryBuffer = ByteBuffer.allocate(DIRECTORY_ENTRY_SIZE).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < numEntries; i++) {
                entryBuffer.clear();
                file.getChannel().read(entryBuffer, directoryOffset + (long) i * DIRECTORY_ENTRY_SIZE);
                entryBuffer.flip();

                byte[] rawName = new byte[NAME_SIZE];
                entryBuffer.get(rawName);
                String displayPath = zeroTerminatedAscii(rawName);
                int filePos = entryBuffer.getInt();
                int fileLen = entryBuffer.getInt();

                if (filePos < 0 || fileLen < 0 || filePos + (long) fileLen > file.length()) {
                    continue;
                }

                String normalizedPath = normalizer.normalizeOrNull(displayPath);
                if (normalizedPath == null) {
                    continue;
                }

                result.add(new PackEntry(normalizedPath, displayPath, filePos, fileLen));
            }
        }

        return result;
    }

    private static String zeroTerminatedAscii(byte[] bytes) {
        int length = 0;
        while (length < bytes.length && bytes[length] != 0) {
            length++;
        }
        return new String(bytes, 0, length, StandardCharsets.US_ASCII);
    }

    private static void skipExactly(InputStream stream, long bytesToSkip) throws IOException {
        long remaining = bytesToSkip;
        while (remaining > 0) {
            long skipped = stream.skip(remaining);
            if (skipped == 0) {
                if (stream.read() == -1) {
                    throw new IOException("Unexpected EOF while seeking package entry");
                }
                skipped = 1;
            }
            remaining -= skipped;
        }
    }

    private static final class BoundedInputStream extends InputStream {
        private final InputStream delegate;
        private long remaining;

        private BoundedInputStream(InputStream delegate, long remaining) {
            this.delegate = delegate;
            this.remaining = remaining;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int value = delegate.read();
            if (value != -1) {
                remaining--;
            }
            return value;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int toRead = (int) Math.min(len, remaining);
            int read = delegate.read(b, off, toRead);
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}

