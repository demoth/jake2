package jake2.qcommon.save;

/**
 * JSON snapshot for {@code client_persistant_t}.
 *
 * Item references stay as item indices to preserve explicit, data-only save state.
 */
public record ClientPersistentSnapshot(
        String userinfo,
        String netname,
        int hand,
        boolean connected,
        int health,
        int maxHealth,
        int savedFlags,
        int selectedItem,
        int[] inventory,
        int maxBullets,
        int maxShells,
        int maxRockets,
        int maxGrenades,
        int maxCells,
        int maxSlugs,
        Integer weaponIndex,
        Integer lastWeaponIndex,
        int powerCubes,
        int score,
        int gameHelpChanged,
        int helpChanged,
        boolean spectator
) {
}
