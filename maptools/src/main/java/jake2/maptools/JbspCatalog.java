package jake2.maptools;

import com.fasterxml.jackson.databind.ObjectMapper;
import jake2.qcommon.filesystem.Bsp;
import jake2.qcommon.filesystem.BspHeader;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Static catalog evidence. Entity classes can load additional assets at runtime. */
public final class JbspCatalog {
    private JbspCatalog() {}

    public static void main(String[] args) {
        try {
            var reports = new ArrayList<Map<String, Object>>();
            for (String arg : args) {
                reports.add(inspect(Files.readAllBytes(Path.of(arg)), arg));
            }
            System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(reports));
        } catch (Exception e) {
            throw new IllegalArgumentException("BSP catalog inspection failed: " + e.getMessage(), e);
        }
    }

    static Map<String, Object> inspect(byte[] bytes, String source) {
        if (bytes.length < 160) throw new IllegalArgumentException("Truncated BSP header: " + source);
        var header = new BspHeader(ByteBuffer.wrap(bytes));
        for (var lump : header.lumps) {
            if (lump.offset < 0 || lump.length < 0 || (long) lump.offset + lump.length > bytes.length) {
                throw new IllegalArgumentException("BSP lump outside file: " + source);
            }
        }
        var bsp = new Bsp(ByteBuffer.wrap(bytes));
        return describe(bsp, source);
    }

    static Map<String, Object> describe(Bsp bsp, String source) {
        var entities = bsp.getEntities();
        var world = entities.stream().filter(e -> "worldspawn".equals(e.get("classname")))
                .findFirst().orElse(Map.of());
        var report = new LinkedHashMap<String, Object>();
        report.put("source", source);
        report.put("bspVersion", bsp.getHeader().version);
        report.put("title", world.get("message"));
        report.put("sky", world.get("sky"));
        report.put("texturePaths", Arrays.stream(bsp.getTextures())
                .map(t -> "textures/" + normalize(t.getName()) + ".wal").distinct().sorted().toList());
        report.put("entityClasses", entities.stream().map(e -> e.getOrDefault("classname", ""))
                .distinct().sorted().toList());
        var starts = new LinkedHashMap<String, Long>();
        for (String type : List.of("info_player_start", "info_player_coop", "info_player_deathmatch")) {
            starts.put(type, entities.stream().filter(e -> type.equals(e.get("classname"))).count());
        }
        report.put("playerStarts", starts);
        var transitions = new ArrayList<Map<String, String>>();
        var references = new ArrayList<Map<String, String>>();
        for (int index = 0; index < entities.size(); index++) {
            var entity = entities.get(index);
            var type = entity.getOrDefault("classname", "");
            if (type.equals("target_changelevel")) {
                // Keep *unit and $spawnpoint syntax intact; grouping needs the original destination.
                transitions.add(Map.of("entity", Integer.toString(index), "map", entity.getOrDefault("map", "")));
            }
            for (String key : entity.keySet().stream().sorted().toList()) {
                var value = entity.get(key);
                if (!(key.equals("model") || key.startsWith("noise") || key.equals("sound"))) continue;
                if (value.isBlank() || value.startsWith("*") || value.matches("[0-9]+")) continue;
                var path = normalize(value);
                if (!key.equals("model") && !path.startsWith("sound/")) path = "sound/" + path;
                references.add(Map.of("entity", Integer.toString(index), "classname", type,
                        "key", key, "value", value, "path", path));
            }
        }
        report.put("transitions", transitions);
        report.put("explicitEntityResources", references);
        report.put("entities", entities);
        report.put("limitations", "Static references only. Gameplay classes load implicit models, sounds and other assets; "
                + "readmes and runtime testing are required. Paths are normalized for comparison; BSP bytes are unchanged.");
        return report;
    }

    private static String normalize(String value) {
        return value.replace('\\', '/').toLowerCase(Locale.ROOT);
    }
}
