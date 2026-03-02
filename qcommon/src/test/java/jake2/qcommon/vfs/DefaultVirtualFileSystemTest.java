package jake2.qcommon.vfs;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DefaultVirtualFileSystemTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void modLooseOverridesBaseLoose() throws Exception {
        Path basedir = temp.newFolder("q2").toPath();
        writeFile(basedir.resolve("baseq2/textures/wall.wal"), "base");
        writeFile(basedir.resolve("rogue/textures/wall.wal"), "mod");

        DefaultVirtualFileSystem vfs = new DefaultVirtualFileSystem();
        vfs.configure(config(basedir, "rogue", false, false, Collections.emptyList()));

        VfsResult<byte[]> bytes = vfs.loadBytes("textures/wall.wal", VfsLookupOptions.DEFAULT);

        assertTrue(bytes.success);
        assertArrayEquals("mod".getBytes(StandardCharsets.UTF_8), bytes.value);
        assertEquals(VfsLayer.MOD_LOOSE, vfs.resolve("textures/wall.wal", VfsLookupOptions.DEFAULT).entry.layer);
    }

    @Test
    public void fallbackResolvesButIsExcludedForGameDataOnly() throws Exception {
        Path basedir = temp.newFolder("q2").toPath();
        Path fallback = temp.newFolder("fallback").toPath();
        writeFile(fallback.resolve("shaders/test.glsl"), "fallback");

        DefaultVirtualFileSystem vfs = new DefaultVirtualFileSystem();
        vfs.configure(config(basedir, null, false, true, Collections.singletonList(fallback)));

        assertTrue(vfs.exists("shaders/test.glsl", VfsLookupOptions.DEFAULT));
        assertFalse(vfs.exists("shaders/test.glsl", new VfsLookupOptions(true)));
    }

    @Test
    public void setGameModRebuildsLooseMounts() throws Exception {
        Path basedir = temp.newFolder("q2").toPath();
        writeFile(basedir.resolve("baseq2/config.cfg"), "base");
        writeFile(basedir.resolve("xatrix/config.cfg"), "mod");

        DefaultVirtualFileSystem vfs = new DefaultVirtualFileSystem();
        vfs.configure(config(basedir, null, false, false, Collections.emptyList()));
        assertEquals("base", new String(vfs.loadBytes("config.cfg", VfsLookupOptions.DEFAULT).value, StandardCharsets.UTF_8));

        vfs.setGameMod("xatrix");
        assertEquals("mod", new String(vfs.loadBytes("config.cfg", VfsLookupOptions.DEFAULT).value, StandardCharsets.UTF_8));
    }

    @Test
    public void strictCaseModeRequiresExactCase() throws Exception {
        Path basedir = temp.newFolder("q2").toPath();
        writeFile(basedir.resolve("baseq2/Players/Male/Tris.MD2"), "md2");

        DefaultVirtualFileSystem looseCaseInsensitive = new DefaultVirtualFileSystem();
        looseCaseInsensitive.configure(config(basedir, null, false, false, Collections.emptyList()));
        assertTrue(looseCaseInsensitive.exists("players/male/tris.md2", VfsLookupOptions.DEFAULT));

        DefaultVirtualFileSystem strictCaseSensitive = new DefaultVirtualFileSystem();
        strictCaseSensitive.configure(config(basedir, null, true, false, Collections.emptyList()));
        assertFalse(strictCaseSensitive.exists("players/male/tris.md2", VfsLookupOptions.DEFAULT));
        assertTrue(strictCaseSensitive.exists("Players/Male/Tris.MD2", VfsLookupOptions.DEFAULT));
    }

    @Test
    public void mountPackageIsExplicitlyUnsupportedInThisStep() throws Exception {
        Path basedir = temp.newFolder("q2").toPath();

        DefaultVirtualFileSystem vfs = new DefaultVirtualFileSystem();
        vfs.configure(config(basedir, null, false, false, Collections.emptyList()));

        VfsResult<String> result = vfs.mountPackage("baseq2/pak0.pak");
        assertFalse(result.success);
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
}

