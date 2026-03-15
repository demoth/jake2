package jake2.game;

import jake2.game.items.GameItem;
import jake2.qcommon.Defines;
import jake2.qcommon.save.ClientPersistentSnapshot;
import jake2.qcommon.save.ClientRespawnSnapshot;
import jake2.qcommon.save.GameLocalsSnapshot;

import java.util.Arrays;
import java.util.List;

/**
 * Explicit mapping layer between live game runtime state and JSON save DTOs.
 *
 * This intentionally keeps persistence shape separate from runtime objects.
 */
public final class GameSaveSnapshots {
    public static final int GAME_LOCALS_SCHEMA_VERSION = 1;

    private GameSaveSnapshots() {
    }

    public static GameLocalsSnapshot snapshot(game_locals_t game) {
        return new GameLocalsSnapshot(
                GAME_LOCALS_SCHEMA_VERSION,
                game.helpmessage1,
                game.helpmessage2,
                game.helpchanged,
                game.spawnpoint,
                game.maxclients,
                game.maxentities,
                game.serverflags,
                game.autosaved
        );
    }

    public static void apply(game_locals_t target, GameLocalsSnapshot snapshot) {
        target.helpmessage1 = defaultString(snapshot.helpMessage1());
        target.helpmessage2 = defaultString(snapshot.helpMessage2());
        target.helpchanged = snapshot.helpChanged();
        target.spawnpoint = defaultString(snapshot.spawnPoint());
        target.maxclients = snapshot.maxClients();
        target.maxentities = snapshot.maxEntities();
        target.serverflags = snapshot.serverFlags();
        target.autosaved = snapshot.autoSaved();
    }

    public static ClientPersistentSnapshot snapshot(client_persistant_t pers) {
        return new ClientPersistentSnapshot(
                pers.userinfo,
                pers.netname,
                pers.hand,
                pers.connected,
                pers.health,
                pers.max_health,
                pers.savedFlags,
                pers.selected_item,
                Arrays.copyOf(pers.inventory, pers.inventory.length),
                pers.max_bullets,
                pers.max_shells,
                pers.max_rockets,
                pers.max_grenades,
                pers.max_cells,
                pers.max_slugs,
                itemIndex(pers.weapon),
                itemIndex(pers.lastweapon),
                pers.power_cubes,
                pers.score,
                pers.game_helpchanged,
                pers.helpchanged,
                pers.spectator
        );
    }

    public static void apply(client_persistant_t target, ClientPersistentSnapshot snapshot, List<GameItem> items) {
        target.userinfo = defaultString(snapshot.userinfo());
        target.netname = defaultString(snapshot.netname());
        target.hand = snapshot.hand();
        target.connected = snapshot.connected();
        target.health = snapshot.health();
        target.max_health = snapshot.maxHealth();
        target.savedFlags = snapshot.savedFlags();
        target.selected_item = snapshot.selectedItem();
        Arrays.fill(target.inventory, 0);
        if (snapshot.inventory() != null) {
            System.arraycopy(snapshot.inventory(), 0, target.inventory, 0, Math.min(snapshot.inventory().length, Defines.MAX_ITEMS));
        }
        target.max_bullets = snapshot.maxBullets();
        target.max_shells = snapshot.maxShells();
        target.max_rockets = snapshot.maxRockets();
        target.max_grenades = snapshot.maxGrenades();
        target.max_cells = snapshot.maxCells();
        target.max_slugs = snapshot.maxSlugs();
        target.weapon = resolveItem(snapshot.weaponIndex(), items);
        target.lastweapon = resolveItem(snapshot.lastWeaponIndex(), items);
        target.power_cubes = snapshot.powerCubes();
        target.score = snapshot.score();
        target.game_helpchanged = snapshot.gameHelpChanged();
        target.helpchanged = snapshot.helpChanged();
        target.spectator = snapshot.spectator();
    }

    public static ClientRespawnSnapshot snapshot(client_respawn_t respawn) {
        return new ClientRespawnSnapshot(
                snapshot(respawn.coop_respawn),
                respawn.enterframe,
                respawn.score,
                Arrays.copyOf(respawn.cmd_angles, respawn.cmd_angles.length),
                respawn.spectator
        );
    }

    public static void apply(client_respawn_t target, ClientRespawnSnapshot snapshot, List<GameItem> items) {
        apply(target.coop_respawn, snapshot.coopRespawn(), items);
        target.enterframe = snapshot.enterFrame();
        target.score = snapshot.score();
        Arrays.fill(target.cmd_angles, 0f);
        if (snapshot.cmdAngles() != null) {
            System.arraycopy(snapshot.cmdAngles(), 0, target.cmd_angles, 0, Math.min(snapshot.cmdAngles().length, 3));
        }
        target.spectator = snapshot.spectator();
    }

    private static Integer itemIndex(GameItem item) {
        return item == null ? null : item.index;
    }

    private static GameItem resolveItem(Integer index, List<GameItem> items) {
        if (index == null) {
            return null;
        }
        if (index < 0 || items == null || index >= items.size()) {
            return null;
        }
        return items.get(index);
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }
}
