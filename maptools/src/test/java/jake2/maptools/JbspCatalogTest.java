package jake2.maptools;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JbspCatalogTest {
    private byte[] fixture() {
        byte[] entities = ("{\"classname\" \"worldspawn\" \"sky\" \"unit1_\"}\n"
                + "{\"classname\" \"info_player_start\"}\n"
                + "{\"classname\" \"target_changelevel\" \"map\" \"*next$entry\"}\n"
                + "{\"classname\" \"target_speaker\" \"noise\" \"Ambience\\hum.wav\"}\n"
                + "{\"classname\" \"func_door\" \"model\" \"*1\"}\n"
                + "{\"classname\" \"target_speaker\" \"noise\" \"world/steam1\"}\n\0").getBytes(StandardCharsets.ISO_8859_1);
        var buffer = ByteBuffer.allocate(160 + entities.length + 76).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(0x50534249).putInt(38);
        for (int i = 0; i < 19; i++) {
            buffer.putInt(i == 5 ? 160 + entities.length : 160);
            buffer.putInt(i == 0 ? entities.length : i == 5 ? 76 : 0);
        }
        buffer.position(160);
        buffer.put(entities);
        buffer.position(160 + entities.length + 40);
        buffer.put("BASE1\\Wall".getBytes(StandardCharsets.ISO_8859_1));
        return buffer.array();
    }

    @Test void preservesTransitionsAndResolvesExplicitPathsWithoutInlineModels() {
        var report = JbspCatalog.inspect(fixture(), "example.bsp");
        assertEquals(List.of("textures/base1/wall.wal"), report.get("texturePaths"));
        assertEquals("unit1_", report.get("sky"));
        assertEquals(1L, ((Map<?, ?>) report.get("playerStarts")).get("info_player_start"));
        assertEquals(List.of(Map.of("entity", "2", "map", "*next$entry")), report.get("transitions"));
        var references = (List<?>) report.get("explicitEntityResources");
        assertEquals(2, references.size());
        assertEquals("sound/ambience/hum.wav", ((Map<?, ?>) references.getFirst()).get("path"));
        assertEquals("sound/world/steam1.wav", ((Map<?, ?>) references.get(1)).get("path"));
        assertEquals("world/steam1", ((Map<?, ?>) references.get(1)).get("value"));
    }

    @Test void rejectsWrongVersionAndOutOfBoundsLumps() {
        byte[] bytes = fixture();
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(4, 999);
        byte[] wrongVersion = bytes;
        assertThrows(IllegalStateException.class, () -> JbspCatalog.inspect(wrongVersion, "wrong.bsp"));
        bytes = fixture();
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(8, Integer.MAX_VALUE);
        byte[] invalid = bytes;
        assertThrows(IllegalArgumentException.class, () -> JbspCatalog.inspect(invalid, "invalid.bsp"));
        assertThrows(IllegalArgumentException.class, () -> JbspCatalog.inspect(new byte[12], "short.bsp"));
    }
}
