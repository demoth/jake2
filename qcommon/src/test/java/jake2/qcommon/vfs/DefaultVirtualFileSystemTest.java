package jake2.qcommon.vfs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultVirtualFileSystemTest {
    @TempDir
    Path temp;

    @Test
    public void modLooseOverridesBaseLoose() throws Exception {
        Path basedir = Files.createDirectories(temp.resolve("q2"));
        writeFile(basedir.resolve("baseq2/textures/wall.wal"), "base");
        writeFile(basedir.resolve("rogue/textures/wall.wal"), "mod");

        DefaultVirtualFileSystem vfs = new DefaultVirtualFileSystem();
        vfs.configure(config(basedir, "rogue", false, false, Collections.emptyList()));

        VfsResult<byte[]> bytes = vfs.loadBytes("textures/wall.wal");

        assertTrue(bytes.success());
        assertArrayEquals("mod".getBytes(StandardCharsets.UTF_8), bytes.value());
        assertEquals(VfsLayer.MOD_LOOSE, vfs.resolve("textures/wall.wal").entry().layer());
    }

    @Test
    public void modPackOverridesBasePack() throws Exception {
        Path basedir = Files.createDirectories(temp.resolve("q2"));
        writePak(
                basedir.resolve("baseq2/pak0.pak"),
                Map.of("textures/wall.wal", "basePack".getBytes(StandardCharsets.US_ASCII))
        );
        writePak(
                basedir.resolve("rogue/pak0.pak"),
                Map.of("textures/wall.wal", "modPack".getBytes(StandardCharsets.US_ASCII))
        );

        DefaultVirtualFileSystem vfs = new DefaultVirtualFileSystem();
        vfs.configure(config(basedir, "rogue", false, false, Collections.emptyList()));

        VfsResult<byte[]> bytes = vfs.loadBytes("textures/wall.wal");
        assertTrue(bytes.success());
        assertArrayEquals("modPack".getBytes(StandardCharsets.US_ASCII), bytes.value());
        assertEquals(VfsLayer.MOD_PACK, vfs.resolve("textures/wall.wal").entry().layer());
    }

    @Test
    public void baseLooseOverridesBasePack() throws Exception {
        Path basedir = Files.createDirectories(temp.resolve("q2"));
        writeFile(basedir.resolve("baseq2/config.cfg"), "loose");
        writePak(
                basedir.resolve("baseq2/pak0.pak"),
                Map.of("config.cfg", "pack".getBytes(StandardCharsets.US_ASCII))
        );

        DefaultVirtualFileSystem vfs = new DefaultVirtualFileSystem();
        vfs.configure(config(basedir, null, false, false, Collections.emptyList()));

        VfsResult<byte[]> bytes = vfs.loadBytes("config.cfg");
        assertTrue(bytes.success());
        assertArrayEquals("loose".getBytes(StandardCharsets.UTF_8), bytes.value());
        assertEquals(VfsLayer.BASE_LOOSE, vfs.resolve("config.cfg").entry().layer());
    }

    @Test
    public void fallbackResolvesButIsExcludedForGameDataOnly() throws Exception {
        Path basedir = Files.createDirectories(temp.resolve("q2"));
        Path fallback = Files.createDirectories(temp.resolve("fallback"));
        writeFile(fallback.resolve("shaders/test.glsl"), "fallback");

        DefaultVirtualFileSystem vfs = new DefaultVirtualFileSystem();
        vfs.configure(config(basedir, null, false, true, Collections.singletonList(fallback)));

        assertTrue(vfs.exists("shaders/test.glsl"));
        assertFalse(vfs.exists("shaders/test.glsl", true));
    }

    @Test
    public void setGameModRebuildsLooseAndPackMounts() throws Exception {
        Path basedir = Files.createDirectories(temp.resolve("q2"));
        writeFile(basedir.resolve("baseq2/config.cfg"), "base");
        writePak(
                basedir.resolve("xatrix/pak0.pak"),
                Map.of("config.cfg", "modPack".getBytes(StandardCharsets.US_ASCII))
        );

        DefaultVirtualFileSystem vfs = new DefaultVirtualFileSystem();
        vfs.configure(config(basedir, null, false, false, Collections.emptyList()));
        assertEquals("base", new String(vfs.loadBytes("config.cfg").value(), StandardCharsets.UTF_8));

        vfs.setGameMod("xatrix");
        assertEquals("modPack", new String(vfs.loadBytes("config.cfg").value(), StandardCharsets.UTF_8));
    }

    @Test
    public void strictCaseModeRequiresExactCase() throws Exception {
        Path basedir = Files.createDirectories(temp.resolve("q2"));
        writeFile(basedir.resolve("baseq2/Players/Male/Tris.MD2"), "md2");

        DefaultVirtualFileSystem looseCaseInsensitive = new DefaultVirtualFileSystem();
        looseCaseInsensitive.configure(config(basedir, null, false, false, Collections.emptyList()));
        assertTrue(looseCaseInsensitive.exists("players/male/tris.md2"));

        DefaultVirtualFileSystem strictCaseSensitive = new DefaultVirtualFileSystem();
        strictCaseSensitive.configure(config(basedir, null, true, false, Collections.emptyList()));
        assertFalse(strictCaseSensitive.exists("players/male/tris.md2"));
        assertTrue(strictCaseSensitive.exists("Players/Male/Tris.MD2"));
    }

    @Test
    public void mountPackageAndUnmountUpdateLookup() throws Exception {
        Path basedir = Files.createDirectories(temp.resolve("q2"));
        Path runtimePak = basedir.resolve("baseq2/runtime.pak");
        writePak(
                runtimePak,
                Map.of("textures/runtime.wal", "runtime".getBytes(StandardCharsets.US_ASCII))
        );

        DefaultVirtualFileSystem vfs = new DefaultVirtualFileSystem();
        vfs.configure(config(basedir, null, false, false, Collections.emptyList()));

        assertTrue(vfs.exists("textures/runtime.wal"));
        VfsResult<String> mount = vfs.mountPackage(runtimePak.toString());
        assertFalse(mount.success(), "Already indexed startup package must fail explicit remount");

        Path latePak = basedir.resolve("downloads/new.pak");
        writePak(
                latePak,
                Map.of("textures/new.wal", "late".getBytes(StandardCharsets.US_ASCII))
        );
        VfsResult<String> mounted = vfs.mountPackage(latePak.toString());
        assertTrue(mounted.success());
        assertTrue(vfs.exists("textures/new.wal"));

        VfsResult<Void> unmounted = vfs.unmount(mounted.value());
        assertTrue(unmounted.success());
        assertFalse(vfs.exists("textures/new.wal"));
    }

    @Test
    public void baseZipPackIsIndexed() throws Exception {
        Path basedir = Files.createDirectories(temp.resolve("q2"));
        writeZip(
                basedir.resolve("baseq2/textures.pk3"),
                Map.of("textures/zip.wal", "zip".getBytes(StandardCharsets.US_ASCII))
        );

        DefaultVirtualFileSystem vfs = new DefaultVirtualFileSystem();
        vfs.configure(config(basedir, null, false, false, Collections.emptyList()));

        VfsResult<byte[]> bytes = vfs.loadBytes("textures/zip.wal");
        assertTrue(bytes.success());
        assertArrayEquals("zip".getBytes(StandardCharsets.US_ASCII), bytes.value());
        assertEquals(VfsLayer.BASE_PACK, vfs.resolve("textures/zip.wal").entry().layer());
    }

    @Test
    public void listsResolvedWinnersAsImmutableSnapshot() throws Exception {
        Path basedir = temp.resolve("q2");
        writeFile(basedir.resolve("baseq2/shared.cfg"), "base");
        writeFile(basedir.resolve("baseq2/z.cfg"), "z");
        writeFile(basedir.resolve("rogue/Shared.cfg"), "modded");
        writeFile(basedir.resolve("rogue/A.cfg"), "a");
        VirtualFileSystem vfs = new DefaultVirtualFileSystem();
        vfs.configure(config(basedir, "rogue", false, false, List.of()));

        List<VfsEntry> snapshot = vfs.listEntries();

        assertEquals(List.of("a.cfg", "shared.cfg", "z.cfg"), snapshot.stream().map(VfsEntry::normalizedPath).toList());
        VfsEntry shared = snapshot.get(1);
        assertEquals(vfs.resolve("shared.cfg").entry(), shared);
        assertEquals("Shared.cfg", shared.displayPath());
        assertEquals(6, shared.size());
        assertEquals(VfsSourceType.DIRECTORY, shared.source().type());
        assertEquals(basedir.resolve("rogue"), shared.source().containerPath());
        assertThrows(UnsupportedOperationException.class, snapshot::clear);

        vfs.setGameMod(null);

        assertEquals(List.of("shared.cfg", "z.cfg"), vfs.listEntries().stream().map(VfsEntry::normalizedPath).toList());
        assertEquals(3, snapshot.size());
        assertEquals(VfsLayer.MOD_LOOSE, snapshot.get(1).layer());
    }

    @Test
    public void listsOnlyExplicitlyMountedArchiveEntries() throws Exception {
        Path pak = temp.resolve("archive/content.pak");
        writePak(pak, Map.of("nested/Z.txt", "zip".getBytes(StandardCharsets.US_ASCII),
                "a.txt", "a".getBytes(StandardCharsets.US_ASCII)));
        writeFile(pak.resolveSibling("outside.cfg"), "outside");
        VirtualFileSystem vfs = new DefaultVirtualFileSystem();
        vfs.configure(config(null, null, false, false, List.of()));
        VfsResult<String> mount = vfs.mountPackage(pak.toString());
        assertTrue(mount.success());

        List<VfsEntry> snapshot = vfs.listEntries();

        assertEquals(List.of("a.txt", "nested/z.txt"), snapshot.stream().map(VfsEntry::normalizedPath).toList());
        VfsEntry nested = snapshot.get(1);
        assertEquals("nested/Z.txt", nested.displayPath());
        assertEquals(3, nested.size());
        assertEquals(VfsSourceType.PACKAGE_ENTRY, nested.source().type());
        assertEquals(pak, nested.source().containerPath());
        assertTrue(vfs.unmount(mount.value()).success());
        assertTrue(vfs.listEntries().isEmpty());
        assertEquals(2, snapshot.size());
    }

    private VfsConfig config(
            Path basedir,
            String gameMod,
            boolean caseSensitive,
            boolean enableFallback,
            java.util.List<Path> extraRoots
    ) {
        return new VfsConfig(
                basedir,
                "baseq2",
                gameMod,
                false,
                enableFallback,
                caseSensitive,
                extraRoots,
                Set.of("pak", "pk2", "pk3", "pkz", "zip")
        );
    }

    private static void writeFile(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void writePak(Path target, Map<String, byte[]> entries) throws IOException {
        Files.createDirectories(target.getParent());

        ByteArrayOutputStream data = new ByteArrayOutputStream();
        ByteArrayOutputStream directory = new ByteArrayOutputStream();

        int offset = 12; // header size
        Map<String, byte[]> ordered = new LinkedHashMap<>(entries);
        for (Map.Entry<String, byte[]> entry : ordered.entrySet()) {
            byte[] content = entry.getValue();
            data.write(content);

            ByteBuffer dirEntry = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
            byte[] name = entry.getKey().getBytes(StandardCharsets.US_ASCII);
            if (name.length > 56) {
                throw new IllegalArgumentException("Entry name too long for PAK fixture: " + entry.getKey());
            }
            dirEntry.put(name);
            dirEntry.position(56);
            dirEntry.putInt(offset);
            dirEntry.putInt(content.length);
            directory.write(dirEntry.array());

            offset += content.length;
        }

        int directoryOffset = 12 + data.size();
        int directoryLength = directory.size();

        ByteBuffer header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        header.put((byte) 'P').put((byte) 'A').put((byte) 'C').put((byte) 'K');
        header.putInt(directoryOffset);
        header.putInt(directoryLength);

        ByteArrayOutputStream full = new ByteArrayOutputStream();
        full.write(header.array());
        full.write(data.toByteArray());
        full.write(directory.toByteArray());
        Files.write(target, full.toByteArray());
    }

    private static void writeZip(Path target, Map<String, byte[]> entries) throws IOException {
        Files.createDirectories(target.getParent());
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(target))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                out.putNextEntry(zipEntry);
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
    }
}
