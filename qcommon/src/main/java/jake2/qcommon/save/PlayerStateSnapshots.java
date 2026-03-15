package jake2.qcommon.save;

import jake2.qcommon.Defines;
import jake2.qcommon.player_state_t;
import jake2.qcommon.pmove_state_t;

import java.util.Arrays;

/**
 * Explicit mapping layer between shared player state runtime objects and JSON DTOs.
 */
public final class PlayerStateSnapshots {
    private PlayerStateSnapshots() {
    }

    public static PmoveStateSnapshot snapshot(pmove_state_t pmove) {
        return new PmoveStateSnapshot(
                pmove.pm_type,
                Arrays.copyOf(pmove.origin, pmove.origin.length),
                Arrays.copyOf(pmove.velocity, pmove.velocity.length),
                pmove.pm_flags,
                pmove.pm_time,
                pmove.gravity,
                Arrays.copyOf(pmove.delta_angles, pmove.delta_angles.length)
        );
    }

    public static void apply(pmove_state_t target, PmoveStateSnapshot snapshot) {
        target.pm_type = snapshot.pmType();
        copy(snapshot.origin(), target.origin, 3);
        copy(snapshot.velocity(), target.velocity, 3);
        target.pm_flags = snapshot.pmFlags();
        target.pm_time = snapshot.pmTime();
        target.gravity = snapshot.gravity();
        copy(snapshot.deltaAngles(), target.delta_angles, 3);
    }

    public static PlayerStateSnapshot snapshot(player_state_t state) {
        return new PlayerStateSnapshot(
                snapshot(state.pmove),
                Arrays.copyOf(state.viewangles, state.viewangles.length),
                Arrays.copyOf(state.viewoffset, state.viewoffset.length),
                Arrays.copyOf(state.kick_angles, state.kick_angles.length),
                Arrays.copyOf(state.gunangles, state.gunangles.length),
                Arrays.copyOf(state.gunoffset, state.gunoffset.length),
                state.gunindex,
                state.gunframe,
                Arrays.copyOf(state.blend, state.blend.length),
                state.fov,
                state.rdflags,
                Arrays.copyOf(state.stats, state.stats.length)
        );
    }

    public static void apply(player_state_t target, PlayerStateSnapshot snapshot) {
        apply(target.pmove, snapshot.pmove());
        copy(snapshot.viewAngles(), target.viewangles, 3);
        copy(snapshot.viewOffset(), target.viewoffset, 3);
        copy(snapshot.kickAngles(), target.kick_angles, 3);
        copy(snapshot.gunAngles(), target.gunangles, 3);
        copy(snapshot.gunOffset(), target.gunoffset, 3);
        target.gunindex = snapshot.gunIndex();
        target.gunframe = snapshot.gunFrame();
        copy(snapshot.blend(), target.blend, 4);
        target.fov = snapshot.fov();
        target.rdflags = snapshot.rdFlags();
        copy(snapshot.stats(), target.stats, Defines.MAX_STATS);
    }

    private static void copy(short[] source, short[] target, int expectedLength) {
        Arrays.fill(target, (short) 0);
        if (source != null) {
            System.arraycopy(source, 0, target, 0, Math.min(source.length, expectedLength));
        }
    }

    private static void copy(float[] source, float[] target, int expectedLength) {
        Arrays.fill(target, 0f);
        if (source != null) {
            System.arraycopy(source, 0, target, 0, Math.min(source.length, expectedLength));
        }
    }
}
