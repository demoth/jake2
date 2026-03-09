package jake2.qcommon.vfs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Normalizes logical paths before indexing and lookup.
 */
public final class VfsPathNormalizer {
    private final boolean caseSensitive;

    public VfsPathNormalizer(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    /**
     * Returns normalized logical path or {@code null} if the path is invalid.
     */
    public String normalizeOrNull(String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) {
            return null;
        }

        final String slashesNormalized = rawPath.replace('\\', '/');
        final String[] parts = slashesNormalized.split("/");
        final List<String> normalizedParts = new ArrayList<>();

        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                if (normalizedParts.isEmpty()) {
                    return null;
                }
                normalizedParts.removeLast();
                continue;
            }
            normalizedParts.add(part);
        }

        if (normalizedParts.isEmpty()) {
            return null;
        }

        final String joined = String.join("/", normalizedParts);
        return caseSensitive ? joined : joined.toLowerCase(Locale.ROOT);
    }
}
