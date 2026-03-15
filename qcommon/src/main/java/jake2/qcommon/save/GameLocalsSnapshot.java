package jake2.qcommon.save;

/**
 * JSON snapshot for {@code game_locals_t}.
 */
public record GameLocalsSnapshot(
        int schemaVersion,
        String helpMessage1,
        String helpMessage2,
        int helpChanged,
        String spawnPoint,
        int maxClients,
        int maxEntities,
        int serverFlags,
        boolean autoSaved
) {
}
