package jake2.qcommon.vfs;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
                "pak10.pak",
                "abc.pak",
                "pak2.pk3",
                "textures.zip",
                "zzz.pkz"
        ), packs);
    }

    @Test
    public void caseTiesAndLargePakNumbersAreDeterministic() {
        List<String> packs = new ArrayList<>(List.of("a.zip", "A.zip",
                "pak999999999999999999999.pak", "pak2.pak"));
        packs.sort(new VfsPackOrder());
        assertEquals(List.of("pak2.pak", "pak999999999999999999999.pak", "A.zip", "a.zip"), packs);
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

