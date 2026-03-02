package jake2.qcommon.vfs;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class VfsPathNormalizerTest {

    @Test
    public void normalizesSlashesDotsAndCaseByDefault() {
        VfsPathNormalizer normalizer = new VfsPathNormalizer(false);

        String normalized = normalizer.normalizeOrNull("./Players\\\\Male//Tris.MD2");

        assertEquals("players/male/tris.md2", normalized);
    }

    @Test
    public void preservesCaseInStrictMode() {
        VfsPathNormalizer normalizer = new VfsPathNormalizer(true);

        String normalized = normalizer.normalizeOrNull("Players/Male/Tris.MD2");

        assertEquals("Players/Male/Tris.MD2", normalized);
    }

    @Test
    public void rejectsTraversal() {
        VfsPathNormalizer normalizer = new VfsPathNormalizer(false);

        assertNull(normalizer.normalizeOrNull("models/../secret/file"));
    }

    @Test
    public void rejectsEmptyAfterNormalization() {
        VfsPathNormalizer normalizer = new VfsPathNormalizer(false);

        assertNull(normalizer.normalizeOrNull("././"));
    }
}

