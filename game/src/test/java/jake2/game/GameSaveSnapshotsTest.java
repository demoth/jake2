package jake2.game;

import jake2.game.items.GameItem;
import jake2.qcommon.save.ClientPersistentSnapshot;
import jake2.qcommon.save.ClientRespawnSnapshot;
import jake2.qcommon.save.GameLocalsSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameSaveSnapshotsTest {
    @Test
    void gameLocalsSnapshotRoundTripsCoreFields() {
        game_locals_t source = new game_locals_t();
        source.helpmessage1 = "help1";
        source.helpmessage2 = "help2";
        source.helpchanged = 2;
        source.spawnpoint = "spawn";
        source.maxclients = 4;
        source.maxentities = 512;
        source.serverflags = 7;
        source.autosaved = true;

        GameLocalsSnapshot snapshot = GameSaveSnapshots.snapshot(source);
        game_locals_t restored = new game_locals_t();
        GameSaveSnapshots.apply(restored, snapshot);

        assertEquals(GameSaveSnapshots.GAME_LOCALS_SCHEMA_VERSION, snapshot.schemaVersion());
        assertEquals(source.helpmessage1, restored.helpmessage1);
        assertEquals(source.helpmessage2, restored.helpmessage2);
        assertEquals(source.helpchanged, restored.helpchanged);
        assertEquals(source.spawnpoint, restored.spawnpoint);
        assertEquals(source.maxclients, restored.maxclients);
        assertEquals(source.maxentities, restored.maxentities);
        assertEquals(source.serverflags, restored.serverflags);
        assertEquals(source.autosaved, restored.autosaved);
    }

    @Test
    void clientPersistentSnapshotRoundTripsPrimitiveAndItemFields() {
        client_persistant_t source = new client_persistant_t();
        source.userinfo = "\\name\\ranger";
        source.netname = "Ranger";
        source.hand = 1;
        source.connected = true;
        source.health = 83;
        source.max_health = 100;
        source.savedFlags = 9;
        source.selected_item = 2;
        source.inventory[0] = 3;
        source.inventory[4] = 7;
        source.max_bullets = 200;
        source.max_shells = 100;
        source.max_rockets = 50;
        source.max_grenades = 25;
        source.max_cells = 300;
        source.max_slugs = 40;
        source.weapon = item(5, "weapon_railgun");
        source.lastweapon = item(3, "weapon_shotgun");
        source.power_cubes = 2;
        source.score = 17;
        source.game_helpchanged = 4;
        source.helpchanged = 1;
        source.spectator = false;

        ClientPersistentSnapshot snapshot = GameSaveSnapshots.snapshot(source);
        client_persistant_t restored = new client_persistant_t();
        List<GameItem> items = List.of(
                item(0, "item0"),
                item(1, "item1"),
                item(2, "item2"),
                item(3, "weapon_shotgun"),
                item(4, "item4"),
                item(5, "weapon_railgun")
        );

        GameSaveSnapshots.apply(restored, snapshot, items);

        assertEquals("\\name\\ranger", restored.userinfo);
        assertEquals("Ranger", restored.netname);
        assertEquals(1, restored.hand);
        assertTrue(restored.connected);
        assertEquals(83, restored.health);
        assertEquals(100, restored.max_health);
        assertEquals(9, restored.savedFlags);
        assertEquals(2, restored.selected_item);
        assertArrayEquals(source.inventory, restored.inventory);
        assertEquals(200, restored.max_bullets);
        assertEquals(100, restored.max_shells);
        assertEquals(50, restored.max_rockets);
        assertEquals(25, restored.max_grenades);
        assertEquals(300, restored.max_cells);
        assertEquals(40, restored.max_slugs);
        assertEquals(5, restored.weapon.index);
        assertEquals(3, restored.lastweapon.index);
        assertEquals(2, restored.power_cubes);
        assertEquals(17, restored.score);
        assertEquals(4, restored.game_helpchanged);
        assertEquals(1, restored.helpchanged);
    }

    @Test
    void clientRespawnSnapshotRoundTripsFields() {
        client_respawn_t source = new client_respawn_t();
        source.coop_respawn.weapon = item(1, "weapon_blaster");
        source.enterframe = 12;
        source.score = 34;
        source.cmd_angles[0] = 1f;
        source.cmd_angles[1] = 2f;
        source.cmd_angles[2] = 3f;
        source.spectator = true;

        ClientRespawnSnapshot snapshot = GameSaveSnapshots.snapshot(source);
        client_respawn_t restored = new client_respawn_t();
        List<GameItem> items = List.of(item(0, "item0"), item(1, "weapon_blaster"));

        GameSaveSnapshots.apply(restored, snapshot, items);

        assertEquals(12, restored.enterframe);
        assertEquals(34, restored.score);
        assertArrayEquals(new float[]{1f, 2f, 3f}, restored.cmd_angles);
        assertTrue(restored.spectator);
        assertEquals(1, restored.coop_respawn.weapon.index);
    }

    @Test
    void missingItemIndexRestoresAsNull() {
        client_persistant_t source = new client_persistant_t();
        source.weapon = item(9, "weapon_bfg");

        ClientPersistentSnapshot snapshot = GameSaveSnapshots.snapshot(source);
        client_persistant_t restored = new client_persistant_t();
        List<GameItem> items = List.of(item(0, "item0"));

        GameSaveSnapshots.apply(restored, snapshot, items);

        assertNull(restored.weapon);
    }

    private static GameItem item(int index, String classname) {
        return new GameItem(
                classname,
                null,
                null,
                null,
                null,
                "",
                null,
                0,
                null,
                "",
                classname,
                0,
                0,
                null,
                0,
                0,
                null,
                "",
                index
        );
    }
}
