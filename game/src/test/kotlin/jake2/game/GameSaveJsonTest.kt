package jake2.game

import jake2.game.items.GameItem
import jake2.qcommon.vfs.DefaultWritableFileSystem
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class GameSaveJsonTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun gamePlayerSnapshotRoundTripsRepresentativeFields() {
        val source = GamePlayerInfo(1)
        source.playerState.pmove.pm_type = 2
        source.playerState.gunindex = 4
        source.ping = 55
        source.pers.weapon = item(2, "weapon_shotgun")
        source.resp.coop_respawn.weapon = item(3, "weapon_railgun")
        source.old_pmove.pm_type = 3
        source.showscores = true
        source.showinventory = true
        source.showhelp = true
        source.setShowHelpIcon(true)
        source.ammo_index = 7
        source.newweapon = item(1, "weapon_blaster")
        source.damage_armor = 11
        source.damage_parmor = 12
        source.damage_blood = 13
        source.damage_knockback = 14
        source.damage_from[0] = 1f
        source.damage_from[1] = 2f
        source.damage_from[2] = 3f
        source.killer_yaw = 15f
        source.weaponstate = WeaponStates.WEAPON_FIRING
        source.kick_angles[0] = 4f
        source.kick_origin[1] = 5f
        source.damage_alpha = 0.4f
        source.bonus_alpha = 0.5f
        source.v_angle[2] = 6f
        source.oldviewangles[1] = 7f
        source.oldvelocity[2] = 8f
        source.old_waterlevel = 2
        source.machinegun_shots = 9
        source.anim_end = 10
        source.anim_priority = 11
        source.anim_duck = true
        source.anim_run = true
        source.grenade_blew_up = true
        source.silencer_shots = 12
        source.flood_when[0] = 9f
        source.flood_whenhead = 3
        source.respawn_time = 22f
        source.chase_target = GameEntity(2)
        source.update_chase = true

        val snapshot = GamePlayerSnapshots.snapshot(source)
        val restored = GamePlayerInfo(1)
        val items = listOf(
            item(0, "item0"),
            item(1, "weapon_blaster"),
            item(2, "weapon_shotgun"),
            item(3, "weapon_railgun")
        )
        val edicts = Array(4) { GameEntity(it) }

        GamePlayerSnapshots.apply(restored, snapshot, items, edicts)

        assertEquals(source.playerState.pmove.pm_type, restored.playerState.pmove.pm_type)
        assertEquals(source.playerState.gunindex, restored.playerState.gunindex)
        assertEquals(source.ping, restored.ping)
        assertEquals(2, restored.pers.weapon.index)
        assertEquals(3, restored.resp.coop_respawn.weapon.index)
        assertEquals(source.old_pmove.pm_type, restored.old_pmove.pm_type)
        assertTrue(restored.showscores)
        assertTrue(restored.showinventory)
        assertTrue(restored.showhelp)
        assertTrue(restored.isShowHelpIcon)
        assertEquals(source.ammo_index, restored.ammo_index)
        assertEquals(1, restored.newweapon.index)
        assertEquals(source.damage_armor, restored.damage_armor)
        assertEquals(source.damage_parmor, restored.damage_parmor)
        assertEquals(source.damage_blood, restored.damage_blood)
        assertEquals(source.damage_knockback, restored.damage_knockback)
        assertArrayEquals(source.damage_from, restored.damage_from, 0f)
        assertEquals(source.killer_yaw, restored.killer_yaw)
        assertEquals(source.weaponstate, restored.weaponstate)
        assertArrayEquals(source.kick_angles, restored.kick_angles, 0f)
        assertArrayEquals(source.kick_origin, restored.kick_origin, 0f)
        assertEquals(source.damage_alpha, restored.damage_alpha)
        assertEquals(source.bonus_alpha, restored.bonus_alpha)
        assertArrayEquals(source.v_angle, restored.v_angle, 0f)
        assertArrayEquals(source.oldviewangles, restored.oldviewangles, 0f)
        assertArrayEquals(source.oldvelocity, restored.oldvelocity, 0f)
        assertEquals(source.old_waterlevel, restored.old_waterlevel)
        assertEquals(source.machinegun_shots, restored.machinegun_shots)
        assertEquals(source.anim_end, restored.anim_end)
        assertEquals(source.anim_priority, restored.anim_priority)
        assertEquals(source.anim_duck, restored.anim_duck)
        assertEquals(source.anim_run, restored.anim_run)
        assertEquals(source.grenade_blew_up, restored.grenade_blew_up)
        assertEquals(source.silencer_shots, restored.silencer_shots)
        assertArrayEquals(source.flood_when, restored.flood_when, 0f)
        assertEquals(source.flood_whenhead, restored.flood_whenhead)
        assertEquals(source.respawn_time, restored.respawn_time)
        assertEquals(2, restored.chase_target.index)
        assertTrue(restored.update_chase)
    }

    @Test
    fun missingReferencesRestoreAsNull() {
        val source = GamePlayerInfo(1)
        source.newweapon = item(9, "weapon_bfg")
        source.chase_target = GameEntity(9)

        val snapshot = GamePlayerSnapshots.snapshot(source)
        val restored = GamePlayerInfo(1)

        GamePlayerSnapshots.apply(restored, snapshot, listOf(item(0, "item0")), Array(1) { GameEntity(it) })

        assertNull(restored.newweapon)
        assertNull(restored.chase_target)
    }

    @Test
    fun gameSaveJsonStoreRoundTripsGameSnapshot() {
        val store = GameSaveJsonStore(DefaultWritableFileSystem(tempDir))
        val clients = listOf(GamePlayerSnapshots.snapshot(GamePlayerInfo(0)))
        val snapshot = GameSaveFileSnapshot(
            schemaVersion = GameSaveJsonStore.SCHEMA_VERSION,
            game = GameSaveSnapshots.snapshot(game_locals_t().apply {
                helpmessage1 = "help"
                maxclients = 1
                autosaved = true
            }),
            clients = clients
        )

        store.write("save/current/game.ssv", snapshot)
        val restored = store.read("save/current/game.ssv")

        assertEquals(snapshot.schemaVersion, restored.schemaVersion)
        assertEquals(snapshot.game.helpMessage1(), restored.game.helpMessage1())
        assertEquals(snapshot.game.maxClients(), restored.game.maxClients())
        assertEquals(1, restored.clients.size)
    }

    @Test
    fun gameSaveJsonStoreAcceptsAbsolutePathsUnderWriteRoot() {
        val store = GameSaveJsonStore(DefaultWritableFileSystem(tempDir))
        val absolutePath = tempDir.resolve("save/current/game.ssv").toString()
        val snapshot = GameSaveFileSnapshot(
            schemaVersion = GameSaveJsonStore.SCHEMA_VERSION,
            game = GameSaveSnapshots.snapshot(game_locals_t().apply { maxclients = 1 }),
            clients = listOf(GamePlayerSnapshots.snapshot(GamePlayerInfo(0)))
        )

        store.write(absolutePath, snapshot)
        val restored = store.read(absolutePath)

        assertEquals(1, restored.game.maxClients())
        assertTrue(tempDir.resolve("save/current/game.ssv").toFile().isFile)
    }

    private fun item(index: Int, classname: String): GameItem =
        GameItem(
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
        )
}
