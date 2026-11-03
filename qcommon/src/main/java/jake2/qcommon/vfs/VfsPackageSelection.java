package jake2.qcommon.vfs;

import java.nio.file.Path;
import java.util.Set;

/** Discovery policy shared by profile-driven client and server filesystems.
 * Paths are exact, portable paths relative to basedir, e.g. catalog/maps.zip.
 * Explicit runtime mountPackage calls remain independent of discovery policy.
 */
public record VfsPackageSelection(boolean automount, Set<String> includedArchives) {
    public static final VfsPackageSelection AUTOMATIC = new VfsPackageSelection(true, Set.of());

    public VfsPackageSelection {
        includedArchives = Set.copyOf(includedArchives);
        for (String path : includedArchives) {
            String[] parts = path.split("/", -1);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()
                    || path.contains("\\") || path.contains(":")
                    || parts[0].equals(".") || parts[0].equals("..")
                    || parts[1].equals(".") || parts[1].equals("..")) {
                throw new IllegalArgumentException("Expected a game-directory-relative archive path: " + path);
            }
        }
    }

    public boolean includes(Path basedir, Path archive) {
        if (automount || isNumberedPak(archive.getFileName().toString())) return true;
        if (basedir == null) return false;
        Path root = basedir.toAbsolutePath().normalize();
        Path file = archive.toAbsolutePath().normalize();
        return file.startsWith(root)
                && includedArchives.contains(root.relativize(file).toString().replace('\\', '/'));
    }

    public static boolean isNumberedPak(String name) {
        return name.matches("(?i)pak[0-9]+\\.pak");
    }
}
