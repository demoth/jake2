package jake2.qcommon.save;

/**
 * JSON snapshot for {@code pmove_state_t}.
 */
public record PmoveStateSnapshot(
        int pmType,
        short[] origin,
        short[] velocity,
        byte pmFlags,
        byte pmTime,
        short gravity,
        short[] deltaAngles
) {
}
