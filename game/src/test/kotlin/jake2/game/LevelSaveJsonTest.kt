package jake2.game

import jake2.game.adapters.AIAdapter
import jake2.game.adapters.EntThinkAdapter
import jake2.game.adapters.EntUseAdapter
import jake2.game.adapters.SuperAdapter
import jake2.game.items.GameItem
import jake2.game.monsters.mframe_t
import jake2.game.monsters.mmove_t
import jake2.qcommon.vfs.DefaultWritableFileSystem
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class LevelSaveJsonTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun levelLocalsAndEntitySnapshotsRoundTripRepresentativeState() {
        val think = SuperAdapter.registerThink("test.level.think") { _, _ -> true }
        val use = SuperAdapter.registerUse("test.level.use") { _, _, _, _ -> }
        val ai = object : AIAdapter() {
            override fun ai(self: GameEntity, dist: Float, gameExports: GameExportsImpl) = Unit
            override val iD: String = "test.level.ai"
        }
        SuperAdapter.register(ai.iD, ai)

        val level = level_locals_t().apply {
            framenum = 12
            time = 1.5f
            level_name = "Outer Base"
            mapname = "base1"
            nextmap = "base2"
            intermissiontime = 8f
            changemap = "boss1"
            exitintermission = true
            intermission_origin[0] = 1f
            intermission_angle[1] = 2f
            pic_health = 42
            total_secrets = 5
            found_secrets = 3
            total_goals = 6
            found_goals = 4
            total_monsters = 7
            killed_monsters = 2
            body_que = 9
            power_cubes = 11
        }

        val edicts = Array(6) { GameEntity(it) }
        val clients = arrayOf(GamePlayerInfo(0), GamePlayerInfo(1))
        val items = listOf(item(0, "item0"), item(1, "item1"), item(2, "item_armor"))

        val entity = edicts[3]
        entity.inuse = true
        entity.s.index = 3
        entity.s.origin[0] = 10f
        entity.s.angles[1] = 20f
        entity.s.old_origin[2] = 30f
        entity.s.modelindex = 4
        entity.classname = "monster_test"
        entity.target = "door1"
        entity.target_ent = edicts[4]
        entity.goalentity = edicts[5]
        entity.movetarget = edicts[2]
        entity.chain = edicts[1]
        entity.enemy = edicts[4]
        entity.groundentity = edicts[2]
        entity.teamchain = edicts[5]
        entity.mynoise = edicts[1]
        entity.mynoise2 = edicts[2]
        entity.setOwner(edicts[1])
        entity.setClient(clients[1])
        entity.movetype = 6
        entity.flags = 7
        entity.model = "models/monsters/test.md2"
        entity.message = "wake up"
        entity.spawnflags = 8
        entity.timestamp = 9f
        entity.speed = 10f
        entity.accel = 11f
        entity.decel = 12f
        entity.velocity[0] = 13f
        entity.avelocity[1] = 14f
        entity.mass = 15
        entity.gravity = 0.75f
        entity.yaw_speed = 16f
        entity.ideal_yaw = 17f
        entity.think.nextTime = 18f
        entity.think.prethink = think
        entity.think.action = think
        entity.use = use
        entity.health = 100
        entity.max_health = 120
        entity.deadflag = 1
        entity.map = "base1"
        entity.count = 2
        entity.groundentity_linkcount = 22
        entity.noise_index = 23
        entity.noise_index2 = 24
        entity.teleport_time = 25f
        entity.watertype = 26
        entity.waterlevel = 2
        entity.light_level = 27
        entity.style = 28
        entity.item = items[2]
        entity.monsterinfo.currentmove = mmove_t(1, 2, arrayOf(mframe_t(ai, 5f, think)), think)
        entity.monsterinfo.stand = think
        entity.monsterinfo.search = think
        val sight = object : jake2.game.adapters.EntInteractAdapter() {
            override fun interact(self: GameEntity, other: GameEntity?, gameExports: GameExportsImpl): Boolean = true
            override val iD: String = "test.level.sight"
        }
        SuperAdapter.register(sight.iD, sight)
        entity.monsterinfo.sight = sight
        entity.monsterinfo.saved_goal[0] = 6f
        entity.monsterinfo.last_sighting[1] = 7f

        level.sight_client = edicts[3]
        level.sight_entity = edicts[4]
        level.sound_entity = edicts[5]
        level.sound2_entity = edicts[1]
        level.current_entity = edicts[2]

        val levelSnapshot = LevelSaveSnapshots.snapshot(level)
        val entitySnapshot = LevelSaveSnapshots.snapshot(3, entity)

        val restoredLevel = level_locals_t()
        val restoredEdicts = Array(6) { GameEntity(it) }
        LevelSaveSnapshots.apply(restoredLevel, levelSnapshot, restoredEdicts)

        val restoredEntity = restoredEdicts[3]
        LevelSaveSnapshots.apply(restoredEntity, entitySnapshot, items, restoredEdicts, clients)

        assertEquals(level.framenum, restoredLevel.framenum)
        assertEquals(level.mapname, restoredLevel.mapname)
        assertSame(restoredEdicts[3], restoredLevel.sight_client)
        assertSame(restoredEdicts[4], restoredLevel.sight_entity)
        assertSame(restoredEdicts[5], restoredLevel.sound_entity)
        assertSame(restoredEdicts[1], restoredLevel.sound2_entity)
        assertSame(restoredEdicts[2], restoredLevel.current_entity)

        assertTrue(restoredEntity.inuse)
        assertEquals(3, restoredEntity.s.index)
        assertArrayEquals(entity.s.origin, restoredEntity.s.origin, 0f)
        assertArrayEquals(entity.s.angles, restoredEntity.s.angles, 0f)
        assertArrayEquals(entity.s.old_origin, restoredEntity.s.old_origin, 0f)
        assertEquals(entity.classname, restoredEntity.classname)
        assertSame(restoredEdicts[4], restoredEntity.target_ent)
        assertSame(restoredEdicts[5], restoredEntity.goalentity)
        assertSame(restoredEdicts[2], restoredEntity.movetarget)
        assertSame(restoredEdicts[1], restoredEntity.chain)
        assertSame(restoredEdicts[4], restoredEntity.enemy)
        assertSame(restoredEdicts[2], restoredEntity.groundentity)
        assertSame(restoredEdicts[5], restoredEntity.teamchain)
        assertSame(restoredEdicts[1], restoredEntity.getOwner())
        assertSame(clients[1], restoredEntity.getClient())
        assertSame(think, restoredEntity.think.prethink)
        assertSame(think, restoredEntity.think.action)
        assertSame(use, restoredEntity.use)
        assertEquals(items[2].index, restoredEntity.item.index)
        assertNotNull(restoredEntity.monsterinfo.currentmove)
        assertEquals(1, restoredEntity.monsterinfo.currentmove.firstframe)
        assertEquals(2, restoredEntity.monsterinfo.currentmove.lastframe)
        assertSame(ai, restoredEntity.monsterinfo.currentmove.frames[0].ai)
        assertSame(think, restoredEntity.monsterinfo.currentmove.frames[0].think)
        assertSame(think, restoredEntity.monsterinfo.currentmove.endfunc)
        assertSame(think, restoredEntity.monsterinfo.stand)
        assertSame(think, restoredEntity.monsterinfo.search)
        assertEquals("test.level.sight", restoredEntity.monsterinfo.sight.iD)
    }

    @Test
    fun missingRefsRestoreAsNull() {
        val entity = GameEntity(1).apply {
            inuse = true
            target_ent = GameEntity(10)
            setOwner(GameEntity(11))
            item = item(9, "weapon_bfg")
        }

        val snapshot = LevelSaveSnapshots.snapshot(1, entity)
        val restored = GameEntity(1)

        LevelSaveSnapshots.apply(restored, snapshot, listOf(item(0, "item0")), Array(2) { GameEntity(it) }, arrayOf(GamePlayerInfo(0)))

        assertNull(restored.target_ent)
        assertNull(restored.getOwner())
        assertNull(restored.item)
    }

    @Test
    fun levelSaveJsonStoreRoundTripsSnapshot() {
        val store = LevelSaveJsonStore(DefaultWritableFileSystem(tempDir))
        val level = level_locals_t().apply {
            mapname = "testmap"
            framenum = 3
        }
        val entity = GameEntity(2).apply {
            inuse = true
            classname = "target_test"
        }
        val snapshot = LevelSaveFileSnapshot(
            schemaVersion = LevelSaveJsonStore.SCHEMA_VERSION,
            level = LevelSaveSnapshots.snapshot(level),
            entities = listOf(LevelSaveSnapshots.snapshot(2, entity))
        )

        store.write("save/current/level.sav", snapshot)
        val restored = store.read("save/current/level.sav")

        assertEquals(LevelSaveJsonStore.SCHEMA_VERSION, restored.schemaVersion)
        assertEquals("testmap", restored.level.mapName)
        assertEquals(1, restored.entities.size)
        assertEquals(2, restored.entities[0].entityNumber)
        assertEquals("target_test", restored.entities[0].className)
    }

    @Test
    fun levelSaveJsonStoreAcceptsAbsolutePathsUnderWriteRoot() {
        val store = LevelSaveJsonStore(DefaultWritableFileSystem(tempDir))
        val absolutePath = tempDir.resolve("save/current/base1.sav").toString()
        val snapshot = LevelSaveFileSnapshot(
            schemaVersion = LevelSaveJsonStore.SCHEMA_VERSION,
            level = LevelSaveSnapshots.snapshot(level_locals_t().apply { mapname = "base1" }),
            entities = emptyList()
        )

        store.write(absolutePath, snapshot)
        val restored = store.read(absolutePath)

        assertEquals("base1", restored.level.mapName)
        assertTrue(tempDir.resolve("save/current/base1.sav").toFile().isFile)
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
