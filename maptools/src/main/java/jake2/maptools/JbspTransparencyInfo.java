package jake2.maptools;

import jake2.qcommon.Defines;
import jake2.qcommon.filesystem.Bsp;
import jake2.qcommon.filesystem.BspFace;
import jake2.qcommon.filesystem.BspModel;
import jake2.qcommon.filesystem.BspTextureInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dumps brush-model translucency diagnostics for BSP maps.
 *
 * Intended use: compare maps where translucent brush entities differ from vanilla rendering.
 * This reports:
 * - world and inline model SURF_TRANS33 / SURF_TRANS66 face counts
 * - texture names used by translucent faces
 * - entity -> inline model mapping from entity lump (`model` = "*N")
 */
public final class JbspTransparencyInfo {

    private static final Logger logger = LoggerFactory.getLogger("JbspTransparency");

    private JbspTransparencyInfo() {
    }

    static void transparency(String[] args) {
        if (args.length < 2) {
            logger.info("usage: transparency <path/to/map.bsp> [*modelIndex]");
            return;
        }

        final String bspPath = args[1];
        final Integer modelFilter = args.length >= 3 ? parseModelIndex(args[2]) : null;
        if (args.length >= 3 && modelFilter == null) {
            logger.warn("Could not parse model filter '{}'; expected '*N' or 'N'.", args[2]);
            return;
        }

        final Bsp bsp;
        try {
            byte[] bytes = Files.readAllBytes(Path.of(bspPath));
            bsp = new Bsp(ByteBuffer.wrap(bytes));
        } catch (IOException e) {
            logger.error("Failed to read BSP file '{}': {}", bspPath, e.getMessage());
            return;
        } catch (RuntimeException e) {
            logger.error("Failed to parse BSP file '{}': {}", bspPath, e.getMessage());
            return;
        }

        logger.info("transparency report: {}", bspPath);
        logger.info("entities: {}, models: {}, faces: {}", bsp.getEntities().size(), bsp.getModels().length, bsp.getFaces().length);

        final Map<Integer, ModelSurfaceStats> modelStats = buildModelSurfaceStats(bsp);
        logModelSummary(0, modelStats.get(0), "world");

        int inlineModelCount = 0;
        for (int i = 1; i < bsp.getModels().length; i++) {
            if (modelFilter != null && i != modelFilter) {
                continue;
            }
            inlineModelCount++;
            logModelSummary(i, modelStats.get(i), "inline");
        }
        if (modelFilter != null && modelFilter > 0 && inlineModelCount == 0) {
            logger.info("no inline model {} found in this BSP", modelFilter);
        }

        logger.info("entity -> brush model mapping:");
        int mappedEntities = 0;
        List<Map<String, String>> entities = bsp.getEntities();
        for (int entityIndex = 0; entityIndex < entities.size(); entityIndex++) {
            Map<String, String> entity = entities.get(entityIndex);
            String modelValue = entity.get("model");
            Integer modelIndex = parseModelIndex(modelValue);
            if (modelIndex == null) {
                continue;
            }
            if (modelFilter != null && modelIndex.intValue() != modelFilter.intValue()) {
                continue;
            }
            if (modelIndex < 0 || modelIndex >= bsp.getModels().length) {
                logger.info("  entity #{} model={} -> invalid model index", entityIndex, modelValue);
                continue;
            }
            mappedEntities++;
            ModelSurfaceStats stats = modelStats.get(modelIndex);
            String classname = entity.getOrDefault("classname", "<no classname>");
            String targetname = entity.getOrDefault("targetname", "-");
            String renderfx = entity.get("renderfx");
            Integer renderfxValue = parseInteger(renderfx);
            boolean renderfxTranslucent = renderfxValue != null && (renderfxValue & Defines.RF_TRANSLUCENT) != 0;
            logger.info(
                "  #{} {} model={} targetname={} renderfx={} (rf_translucent={}) origin={}",
                entityIndex,
                classname,
                modelValue,
                targetname,
                renderfx == null ? "-" : renderfx,
                renderfxTranslucent,
                entity.getOrDefault("origin", "-")
            );
            if (stats != null) {
                logger.info(
                    "      faces={} trans33={} trans66={} flowing={} warp={} trans_textures={}",
                    stats.totalFaces,
                    stats.trans33Faces,
                    stats.trans66Faces,
                    stats.flowingFaces,
                    stats.warpFaces,
                    stats.translucentTextures.size()
                );
                if (!stats.translucentTextures.isEmpty()) {
                    logger.info("      translucent textures: {}", String.join(", ", stats.translucentTextures.keySet()));
                }
            }
        }

        if (mappedEntities == 0) {
            logger.info("  no brush-model entities found in entity lump for the selected filter");
        }
        logger.info("note: map entity lump may not include runtime renderfx changes applied by game code/triggers.");
    }

    private static Map<Integer, ModelSurfaceStats> buildModelSurfaceStats(Bsp bsp) {
        Map<Integer, ModelSurfaceStats> modelStats = new LinkedHashMap<>();
        BspModel[] models = bsp.getModels();
        BspFace[] faces = bsp.getFaces();
        BspTextureInfo[] textures = bsp.getTextures();

        for (int modelIndex = 0; modelIndex < models.length; modelIndex++) {
            BspModel model = models[modelIndex];
            ModelSurfaceStats stats = new ModelSurfaceStats();
            int firstFace = model.getFirstFace();
            int faceCount = model.getFaceCount();
            for (int localFace = 0; localFace < faceCount; localFace++) {
                int faceIndex = firstFace + localFace;
                if (faceIndex < 0 || faceIndex >= faces.length) {
                    continue;
                }
                BspFace face = faces[faceIndex];
                int texInfoIndex = face.getTextureInfoIndex();
                if (texInfoIndex < 0 || texInfoIndex >= textures.length) {
                    continue;
                }
                BspTextureInfo texInfo = textures[texInfoIndex];
                int flags = texInfo.getFlags();
                stats.totalFaces++;
                if ((flags & Defines.SURF_TRANS33) != 0) {
                    stats.trans33Faces++;
                    stats.translucentTextures.merge(texInfo.getName(), 1, Integer::sum);
                }
                if ((flags & Defines.SURF_TRANS66) != 0) {
                    stats.trans66Faces++;
                    stats.translucentTextures.merge(texInfo.getName(), 1, Integer::sum);
                }
                if ((flags & Defines.SURF_FLOWING) != 0) {
                    stats.flowingFaces++;
                }
                if ((flags & Defines.SURF_WARP) != 0) {
                    stats.warpFaces++;
                }
            }
            modelStats.put(modelIndex, stats);
        }
        return modelStats;
    }

    private static void logModelSummary(int modelIndex, ModelSurfaceStats stats, String kind) {
        if (stats == null) {
            return;
        }
        logger.info(
            "{} model *{}: faces={} trans33={} trans66={} flowing={} warp={} trans_textures={}",
            kind,
            modelIndex,
            stats.totalFaces,
            stats.trans33Faces,
            stats.trans66Faces,
            stats.flowingFaces,
            stats.warpFaces,
            stats.translucentTextures.size()
        );
        if (!stats.translucentTextures.isEmpty()) {
            List<String> samples = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : stats.translucentTextures.entrySet()) {
                samples.add(entry.getKey() + "(" + entry.getValue() + ")");
                if (samples.size() >= 12) {
                    break;
                }
            }
            logger.info("  textures: {}", String.join(", ", samples));
        }
    }

    private static Integer parseModelIndex(String modelToken) {
        if (modelToken == null || modelToken.isBlank()) {
            return null;
        }
        String normalized = modelToken.trim();
        if (normalized.startsWith("--model=")) {
            normalized = normalized.substring("--model=".length()).trim();
        }
        if (normalized.startsWith("*")) {
            normalized = normalized.substring(1);
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Integer parseInteger(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        try {
            if (normalized.startsWith("0x")) {
                return Integer.parseUnsignedInt(normalized.substring(2), 16);
            }
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static final class ModelSurfaceStats {
        int totalFaces;
        int trans33Faces;
        int trans66Faces;
        int flowingFaces;
        int warpFaces;
        Map<String, Integer> translucentTextures = new LinkedHashMap<>();
    }
}
