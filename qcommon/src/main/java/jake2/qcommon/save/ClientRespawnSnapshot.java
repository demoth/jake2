package jake2.qcommon.save;

/**
 * JSON snapshot for {@code client_respawn_t}.
 */
public record ClientRespawnSnapshot(
        ClientPersistentSnapshot coopRespawn,
        int enterFrame,
        int score,
        float[] cmdAngles,
        boolean spectator
) {
}
