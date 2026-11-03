package jake2.qcommon.vfs;

import java.util.Comparator;
import java.math.BigInteger;

/**
 * Deterministic pack ordering policy:
 * Load order: numbered {@code pakNN.pak} first (ascending NN), then optional archives
 * in case-insensitive alphabetical order. Later entries take precedence.
 */
public final class VfsPackOrder implements Comparator<String> {

    @Override
    public int compare(String left, String right) {
        final String a = fileName(left);
        final String b = fileName(right);

        final BigInteger aNumbered = parsePakNumber(a);
        final BigInteger bNumbered = parsePakNumber(b);

        if (aNumbered != null && bNumbered != null) {
            int numeric = aNumbered.compareTo(bNumbered);
            if (numeric != 0) {
                return numeric;
            }
            return alphabetical(a, b);
        }

        if (aNumbered != null) {
            return -1;
        }
        if (bNumbered != null) {
            return 1;
        }

        return alphabetical(a, b);
    }

    private static String fileName(String path) {
        if (path == null) {
            return "";
        }
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static int alphabetical(String a, String b) {
        int comparison = a.compareToIgnoreCase(b);
        return comparison != 0 ? comparison : a.compareTo(b);
    }

    private static BigInteger parsePakNumber(String fileName) {
        if (!VfsPackageSelection.isNumberedPak(fileName)) return null;
        return new BigInteger(fileName.substring(3, fileName.length() - 4));
    }
}
