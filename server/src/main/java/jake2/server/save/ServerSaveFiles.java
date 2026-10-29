/*
Copyright (C) 1997-2001 Id Software, Inc.

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

*/

package jake2.server.save;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;

/** Save-slot file operations scoped to an explicit engine write root. */
public final class ServerSaveFiles {
    private static final List<String> SERVER_FILES = List.of(
            "server_latched_cvars.ssv.json", "server_mapcmd.ssv.json", "game.ssv.json");
    private final Path saveRoot;

    public ServerSaveFiles(Path writeRoot) {
        saveRoot = Objects.requireNonNull(writeRoot, "writeRoot").resolve("save");
    }

    /** Remove known save files, leaving unrelated files and the slot directory alone. */
    public void wipe(String slot) {
        Path directory = slotPath(slot);
        try {
            wipeDirectory(directory);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not wipe save slot: " + slot, e);
        }
    }

    /**
     * Replace a slot's save files. Level portal files are copied with their gameplay files.
     * Missing files remain optional, as for the initial autosave before a level is archived.
     * This operation is not transactional and does not validate save completeness.
     */
    public void copy(String source, String destination) {
        // Validate both slots before deleting anything.
        Path sourceDirectory = slotPath(source);
        Path destinationDirectory = slotPath(destination);
        if (sourceDirectory.equals(destinationDirectory)) return;

        try {
            wipeDirectory(destinationDirectory);
            Files.createDirectories(destinationDirectory);
            for (String name : SERVER_FILES) {
                copyIfPresent(sourceDirectory.resolve(name), destinationDirectory.resolve(name));
            }
            for (Path level : levelFiles(sourceDirectory, "*.sav.json")) {
                String name = level.getFileName().toString();
                copyIfPresent(level, destinationDirectory.resolve(name));
                String portals = name.substring(0, name.length() - ".sav.json".length()) + ".sv2.json";
                copyIfPresent(sourceDirectory.resolve(portals), destinationDirectory.resolve(portals));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not copy save slot " + source + " to " + destination, e);
        }
    }

    private Path slotPath(String slot) {
        return saveRoot.resolve(ServerSaveJsonStore.normalizeSlot(slot));
    }

    private static void wipeDirectory(Path directory) throws IOException {
        for (String name : SERVER_FILES) Files.deleteIfExists(directory.resolve(name));
        for (Path file : levelFiles(directory, "*.sav.json")) Files.deleteIfExists(file);
        for (Path file : levelFiles(directory, "*.sv2.json")) Files.deleteIfExists(file);
    }

    private static List<Path> levelFiles(Path directory, String glob) throws IOException {
        try (var files = Files.newDirectoryStream(directory, glob)) {
            var result = new java.util.ArrayList<Path>();
            for (Path file : files) result.add(file);
            return result;
        } catch (NoSuchFileException e) {
            return List.of();
        }
    }

    private static void copyIfPresent(Path source, Path destination) throws IOException {
        // Only a missing source is optional; destination and other IO errors must propagate.
        if (Files.notExists(source)) return;
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
    }
}
