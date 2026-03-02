package jake2.qcommon.vfs;

import java.util.Comparator;
import java.util.Locale;

/**
 * Deterministic pack ordering policy:
 * numbered {@code pakNN.*} first (ascending NN), then case-insensitive alphabetical.
 */
public final class VfsPackOrder implements Comparator<String> {

    @Override
    public int compare(String left, String right) {
        final String a = fileName(left).toLowerCase(Locale.ROOT);
        final String b = fileName(right).toLowerCase(Locale.ROOT);

        final Integer aNumbered = parsePakNumber(a);
        final Integer bNumbered = parsePakNumber(b);

        if (aNumbered != null && bNumbered != null) {
            int numeric = Integer.compare(aNumbered, bNumbered);
            if (numeric != 0) {
                return numeric;
            }
            return a.compareToIgnoreCase(b);
        }

        if (aNumbered != null) {
            return -1;
        }
        if (bNumbered != null) {
            return 1;
        }

        return a.compareToIgnoreCase(b);
    }

    private static String fileName(String path) {
        if (path == null) {
            return "";
        }
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static Integer parsePakNumber(String fileName) {
        if (!fileName.startsWith("pak")) {
            return null;
        }

        int index = 3;
        if (index >= fileName.length() || !Character.isDigit(fileName.charAt(index))) {
            return null;
        }
        while (index < fileName.length() && Character.isDigit(fileName.charAt(index))) {
            index++;
        }
        if (index < fileName.length() && fileName.charAt(index) != '.') {
            return null;
        }
        return Integer.parseInt(fileName.substring(3, index));
    }
}

