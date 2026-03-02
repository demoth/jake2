package jake2.qcommon.vfs;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class VfsPackOrderTest {

    @Test
    public void sortsNumberedPakFirstThenAlphabetically() {
        List<String> packs = new ArrayList<>(Arrays.asList(
                "abc.pak",
                "pak10.pak",
                "pak2.pk3",
                "zzz.pkz",
                "pak0.pak",
                "textures.zip"
        ));

        packs.sort(new VfsPackOrder());

        assertEquals(Arrays.asList(
                "pak0.pak",
                "pak2.pk3",
                "pak10.pak",
                "abc.pak",
                "textures.zip",
                "zzz.pkz"
        ), packs);
    }

    @Test
    public void doesNotTreatPakPrefixWithoutNumericSuffixAsNumbered() {
        List<String> packs = new ArrayList<>(Arrays.asList(
                "pakx.pak",
                "pak1.pak"
        ));

        packs.sort(new VfsPackOrder());

        assertEquals(Arrays.asList("pak1.pak", "pakx.pak"), packs);
    }
}

