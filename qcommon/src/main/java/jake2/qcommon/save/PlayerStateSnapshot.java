package jake2.qcommon.save;

/**
 * JSON snapshot for {@code player_state_t}.
 */
public record PlayerStateSnapshot(
        PmoveStateSnapshot pmove,
        float[] viewAngles,
        float[] viewOffset,
        float[] kickAngles,
        float[] gunAngles,
        float[] gunOffset,
        int gunIndex,
        int gunFrame,
        float[] blend,
        float fov,
        int rdFlags,
        short[] stats
) {
}
