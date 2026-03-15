package jake2.qcommon.save;

import jake2.qcommon.Defines;
import jake2.qcommon.player_state_t;
import jake2.qcommon.pmove_state_t;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerStateSnapshotsTest {
    @Test
    void pmoveSnapshotRoundTripsAllFields() {
        pmove_state_t source = new pmove_state_t();
        source.pm_type = 2;
        source.origin[0] = 10;
        source.origin[1] = 20;
        source.origin[2] = 30;
        source.velocity[0] = 40;
        source.velocity[1] = 50;
        source.velocity[2] = 60;
        source.pm_flags = 3;
        source.pm_time = 4;
        source.gravity = 800;
        source.delta_angles[0] = 70;
        source.delta_angles[1] = 80;
        source.delta_angles[2] = 90;

        PmoveStateSnapshot snapshot = PlayerStateSnapshots.snapshot(source);
        pmove_state_t restored = new pmove_state_t();
        PlayerStateSnapshots.apply(restored, snapshot);

        assertEquals(source.pm_type, restored.pm_type);
        assertArrayEquals(source.origin, restored.origin);
        assertArrayEquals(source.velocity, restored.velocity);
        assertEquals(source.pm_flags, restored.pm_flags);
        assertEquals(source.pm_time, restored.pm_time);
        assertEquals(source.gravity, restored.gravity);
        assertArrayEquals(source.delta_angles, restored.delta_angles);
    }

    @Test
    void playerStateSnapshotRoundTripsAllFields() {
        player_state_t source = new player_state_t();
        source.pmove.pm_type = 1;
        source.pmove.origin[0] = 1;
        source.pmove.velocity[1] = 2;
        source.pmove.pm_flags = 5;
        source.pmove.pm_time = 6;
        source.pmove.gravity = 750;
        source.pmove.delta_angles[2] = 7;
        source.viewangles[0] = 11f;
        source.viewangles[1] = 12f;
        source.viewangles[2] = 13f;
        source.viewoffset[0] = 14f;
        source.viewoffset[1] = 15f;
        source.viewoffset[2] = 16f;
        source.kick_angles[0] = 17f;
        source.kick_angles[1] = 18f;
        source.kick_angles[2] = 19f;
        source.gunangles[0] = 20f;
        source.gunangles[1] = 21f;
        source.gunangles[2] = 22f;
        source.gunoffset[0] = 23f;
        source.gunoffset[1] = 24f;
        source.gunoffset[2] = 25f;
        source.gunindex = 3;
        source.gunframe = 4;
        source.blend[0] = 0.1f;
        source.blend[1] = 0.2f;
        source.blend[2] = 0.3f;
        source.blend[3] = 0.4f;
        source.fov = 95f;
        source.rdflags = 8;
        source.stats[0] = 9;
        source.stats[Defines.MAX_STATS - 1] = 10;

        PlayerStateSnapshot snapshot = PlayerStateSnapshots.snapshot(source);
        player_state_t restored = new player_state_t();
        PlayerStateSnapshots.apply(restored, snapshot);

        assertEquals(source.pmove.pm_type, restored.pmove.pm_type);
        assertArrayEquals(source.pmove.origin, restored.pmove.origin);
        assertArrayEquals(source.pmove.velocity, restored.pmove.velocity);
        assertEquals(source.pmove.pm_flags, restored.pmove.pm_flags);
        assertEquals(source.pmove.pm_time, restored.pmove.pm_time);
        assertEquals(source.pmove.gravity, restored.pmove.gravity);
        assertArrayEquals(source.pmove.delta_angles, restored.pmove.delta_angles);
        assertArrayEquals(source.viewangles, restored.viewangles);
        assertArrayEquals(source.viewoffset, restored.viewoffset);
        assertArrayEquals(source.kick_angles, restored.kick_angles);
        assertArrayEquals(source.gunangles, restored.gunangles);
        assertArrayEquals(source.gunoffset, restored.gunoffset);
        assertEquals(source.gunindex, restored.gunindex);
        assertEquals(source.gunframe, restored.gunframe);
        assertArrayEquals(source.blend, restored.blend);
        assertEquals(source.fov, restored.fov);
        assertEquals(source.rdflags, restored.rdflags);
        assertArrayEquals(source.stats, restored.stats);
    }
}
