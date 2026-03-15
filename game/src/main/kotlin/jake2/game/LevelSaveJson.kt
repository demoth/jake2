package jake2.game

import jake2.game.adapters.AIAdapter
import jake2.game.adapters.EntBlockedAdapter
import jake2.game.adapters.EntDieAdapter
import jake2.game.adapters.EntDodgeAdapter
import jake2.game.adapters.EntInteractAdapter
import jake2.game.adapters.EntPainAdapter
import jake2.game.adapters.EntThinkAdapter
import jake2.game.adapters.EntTouchAdapter
import jake2.game.adapters.EntUseAdapter
import jake2.game.adapters.SuperAdapter
import jake2.game.items.GameItem
import jake2.game.monsters.mframe_t
import jake2.game.monsters.mmove_t
import jake2.game.monsters.monsterinfo_t
import jake2.qcommon.Defines
import jake2.qcommon.entity_state_t
import jake2.qcommon.save.SaveJson
import jake2.qcommon.vfs.DefaultWritableFileSystem
import jake2.qcommon.vfs.VfsOpenOptions
import jake2.qcommon.vfs.VfsReadableHandle
import jake2.qcommon.vfs.VfsResult
import jake2.qcommon.vfs.VfsWritableHandle
import jake2.qcommon.vfs.VfsWriteOptions
import jake2.qcommon.vfs.WritableFileSystem
import java.io.IOException
import java.nio.file.Path
import java.util.Arrays

data class EntityStateSnapshot(
    val index: Int,
    val origin: FloatArray,
    val angles: FloatArray,
    val oldOrigin: FloatArray,
    val modelIndex: Int,
    val modelIndex2: Int,
    val modelIndex3: Int,
    val modelIndex4: Int,
    val frame: Int,
    val skinNum: Int,
    val effects: Int,
    val renderFx: Int,
    val solid: Int,
    val sound: Int,
    val event: Int
)

data class MonsterFrameSnapshot(
    val aiAdapterId: String?,
    val dist: Float,
    val thinkAdapterId: String?
)

data class MonsterMoveSnapshot(
    val firstFrame: Int,
    val lastFrame: Int,
    val frames: List<MonsterFrameSnapshot>,
    val endFuncAdapterId: String?
)

data class MonsterInfoSnapshot(
    val currentMove: MonsterMoveSnapshot?,
    val aiFlags: Int,
    val nextFrame: Int,
    val scale: Float,
    val standAdapterId: String?,
    val idleAdapterId: String?,
    val searchAdapterId: String?,
    val walkAdapterId: String?,
    val runAdapterId: String?,
    val dodgeAdapterId: String?,
    val attackAdapterId: String?,
    val meleeAdapterId: String?,
    val sightAdapterId: String?,
    val checkAttackAdapterId: String?,
    val pauseTime: Float,
    val attackFinished: Float,
    val savedGoal: FloatArray,
    val searchTime: Float,
    val trailTime: Float,
    val lastSighting: FloatArray,
    val attackState: Int,
    val lefty: Int,
    val idleTime: Float,
    val linkCount: Int,
    val powerArmorType: Int,
    val powerArmorPower: Int
)

data class LevelLocalsSnapshot(
    val frameNum: Int,
    val time: Float,
    val levelName: String,
    val mapName: String,
    val nextMap: String,
    val intermissionTime: Float,
    val changeMap: String?,
    val exitIntermission: Boolean,
    val intermissionOrigin: FloatArray,
    val intermissionAngle: FloatArray,
    val sightClientIndex: Int?,
    val sightEntityIndex: Int?,
    val sightEntityFrameNum: Int,
    val soundEntityIndex: Int?,
    val soundEntityFrameNum: Int,
    val sound2EntityIndex: Int?,
    val sound2EntityFrameNum: Int,
    val picHealth: Int,
    val totalSecrets: Int,
    val foundSecrets: Int,
    val totalGoals: Int,
    val foundGoals: Int,
    val totalMonsters: Int,
    val killedMonsters: Int,
    val currentEntityIndex: Int?,
    val bodyQue: Int,
    val powerCubes: Int
)

data class LevelEntitySnapshot(
    val entityNumber: Int,
    val state: EntityStateSnapshot,
    val inUse: Boolean,
    val linkCount: Int,
    val numClusters: Int,
    val clusterNums: IntArray?,
    val headNode: Int,
    val areaNum: Int,
    val areaNum2: Int,
    val svFlags: Int,
    val mins: FloatArray,
    val maxs: FloatArray,
    val absMin: FloatArray,
    val absMax: FloatArray,
    val size: FloatArray,
    val solid: Int,
    val clipMask: Int,
    val moveType: Int,
    val flags: Int,
    val model: String?,
    val freeTime: Float,
    val message: String?,
    val className: String,
    val spawnFlags: Int,
    val timestamp: Float,
    val angle: Float,
    val target: String?,
    val targetName: String?,
    val killTarget: String?,
    val team: String?,
    val pathTarget: String?,
    val deathTarget: String?,
    val combatTarget: String?,
    val targetEntityIndex: Int?,
    val speed: Float,
    val accel: Float,
    val decel: Float,
    val moveDir: FloatArray,
    val pos1: FloatArray,
    val pos2: FloatArray,
    val velocity: FloatArray,
    val angularVelocity: FloatArray,
    val mass: Int,
    val airFinished: Float,
    val gravity: Float,
    val goalEntityIndex: Int?,
    val moveTargetIndex: Int?,
    val yawSpeed: Float,
    val idealYaw: Float,
    val thinkNextTime: Float,
    val thinkPrethinkId: String?,
    val thinkActionId: String?,
    val blockedAdapterId: String?,
    val touchAdapterId: String?,
    val useAdapterId: String?,
    val painAdapterId: String?,
    val dieAdapterId: String?,
    val touchDebounceTime: Float,
    val painDebounceTime: Float,
    val damageDebounceTime: Float,
    val flySoundDebounceTime: Float,
    val health: Int,
    val maxHealth: Int,
    val gibHealth: Int,
    val deadFlag: Int,
    val showHostile: Int,
    val powerArmorTime: Float,
    val map: String?,
    val viewHeight: Int,
    val takeDamage: Int,
    val damage: Int,
    val radiusDamage: Int,
    val damageRadius: Float,
    val sounds: Int,
    val count: Int,
    val chainIndex: Int?,
    val enemyIndex: Int?,
    val oldEnemyIndex: Int?,
    val activatorIndex: Int?,
    val groundEntityIndex: Int?,
    val groundEntityLinkCount: Int,
    val teamChainIndex: Int?,
    val teamMasterIndex: Int?,
    val myNoiseIndex: Int?,
    val myNoise2Index: Int?,
    val noiseIndex: Int,
    val noiseIndex2: Int,
    val volume: Float,
    val attenuation: Float,
    val wait: Float,
    val delay: Float,
    val random: Float,
    val teleportTime: Float,
    val waterType: Int,
    val waterLevel: Int,
    val moveOrigin: FloatArray,
    val moveAngles: FloatArray,
    val lightLevel: Int,
    val style: Int,
    val itemIndex: Int?,
    val monsterInfo: MonsterInfoSnapshot,
    val clientIndex: Int?,
    val ownerIndex: Int?
)

data class LevelSaveFileSnapshot(
    val schemaVersion: Int,
    val level: LevelLocalsSnapshot,
    val entities: List<LevelEntitySnapshot>
)

object LevelSaveSnapshots {
    @JvmStatic
    fun snapshot(level: level_locals_t): LevelLocalsSnapshot =
        LevelLocalsSnapshot(
            frameNum = level.framenum,
            time = level.time,
            levelName = level.level_name,
            mapName = level.mapname,
            nextMap = level.nextmap,
            intermissionTime = level.intermissiontime,
            changeMap = level.changemap,
            exitIntermission = level.exitintermission,
            intermissionOrigin = level.intermission_origin.copyOf(),
            intermissionAngle = level.intermission_angle.copyOf(),
            sightClientIndex = level.sight_client?.index,
            sightEntityIndex = level.sight_entity?.index,
            sightEntityFrameNum = level.sight_entity_framenum,
            soundEntityIndex = level.sound_entity?.index,
            soundEntityFrameNum = level.sound_entity_framenum,
            sound2EntityIndex = level.sound2_entity?.index,
            sound2EntityFrameNum = level.sound2_entity_framenum,
            picHealth = level.pic_health,
            totalSecrets = level.total_secrets,
            foundSecrets = level.found_secrets,
            totalGoals = level.total_goals,
            foundGoals = level.found_goals,
            totalMonsters = level.total_monsters,
            killedMonsters = level.killed_monsters,
            currentEntityIndex = level.current_entity?.index,
            bodyQue = level.body_que,
            powerCubes = level.power_cubes
        )

    @JvmStatic
    fun apply(target: level_locals_t, snapshot: LevelLocalsSnapshot, edicts: Array<GameEntity>) {
        target.framenum = snapshot.frameNum
        target.time = snapshot.time
        target.level_name = snapshot.levelName
        target.mapname = snapshot.mapName
        target.nextmap = snapshot.nextMap
        target.intermissiontime = snapshot.intermissionTime
        target.changemap = snapshot.changeMap
        target.exitintermission = snapshot.exitIntermission
        copy(snapshot.intermissionOrigin, target.intermission_origin, 3)
        copy(snapshot.intermissionAngle, target.intermission_angle, 3)
        target.sight_client = entityRef(snapshot.sightClientIndex, edicts)
        target.sight_entity = entityRef(snapshot.sightEntityIndex, edicts)
        target.sight_entity_framenum = snapshot.sightEntityFrameNum
        target.sound_entity = entityRef(snapshot.soundEntityIndex, edicts)
        target.sound_entity_framenum = snapshot.soundEntityFrameNum
        target.sound2_entity = entityRef(snapshot.sound2EntityIndex, edicts)
        target.sound2_entity_framenum = snapshot.sound2EntityFrameNum
        target.pic_health = snapshot.picHealth
        target.total_secrets = snapshot.totalSecrets
        target.found_secrets = snapshot.foundSecrets
        target.total_goals = snapshot.totalGoals
        target.found_goals = snapshot.foundGoals
        target.total_monsters = snapshot.totalMonsters
        target.killed_monsters = snapshot.killedMonsters
        target.current_entity = entityRef(snapshot.currentEntityIndex, edicts)
        target.body_que = snapshot.bodyQue
        target.power_cubes = snapshot.powerCubes
    }

    @JvmStatic
    fun snapshot(entityNumber: Int, entity: GameEntity): LevelEntitySnapshot =
        LevelEntitySnapshot(
            entityNumber = entityNumber,
            state = snapshot(entity.s),
            inUse = entity.inuse,
            linkCount = entity.linkcount,
            numClusters = entity.num_clusters,
            clusterNums = entity.clusternums?.copyOf(),
            headNode = entity.headnode,
            areaNum = entity.areanum,
            areaNum2 = entity.areanum2,
            svFlags = entity.svflags,
            mins = entity.mins.copyOf(),
            maxs = entity.maxs.copyOf(),
            absMin = entity.absmin.copyOf(),
            absMax = entity.absmax.copyOf(),
            size = entity.size.copyOf(),
            solid = entity.solid,
            clipMask = entity.clipmask,
            moveType = entity.movetype,
            flags = entity.flags,
            model = entity.model,
            freeTime = entity.freetime,
            message = entity.message,
            className = entity.classname,
            spawnFlags = entity.spawnflags,
            timestamp = entity.timestamp,
            angle = entity.angle,
            target = entity.target,
            targetName = entity.targetname,
            killTarget = entity.killtarget,
            team = entity.team,
            pathTarget = entity.pathtarget,
            deathTarget = entity.deathtarget,
            combatTarget = entity.combattarget,
            targetEntityIndex = entity.target_ent?.index,
            speed = entity.speed,
            accel = entity.accel,
            decel = entity.decel,
            moveDir = entity.movedir.copyOf(),
            pos1 = entity.pos1.copyOf(),
            pos2 = entity.pos2.copyOf(),
            velocity = entity.velocity.copyOf(),
            angularVelocity = entity.avelocity.copyOf(),
            mass = entity.mass,
            airFinished = entity.air_finished,
            gravity = entity.gravity,
            goalEntityIndex = entity.goalentity?.index,
            moveTargetIndex = entity.movetarget?.index,
            yawSpeed = entity.yaw_speed,
            idealYaw = entity.ideal_yaw,
            thinkNextTime = entity.think.nextTime,
            thinkPrethinkId = adapterId(entity.think.prethink),
            thinkActionId = adapterId(entity.think.action),
            blockedAdapterId = adapterId(entity.blocked),
            touchAdapterId = adapterId(entity.touch),
            useAdapterId = adapterId(entity.use),
            painAdapterId = adapterId(entity.pain),
            dieAdapterId = adapterId(entity.die),
            touchDebounceTime = entity.touch_debounce_time,
            painDebounceTime = entity.pain_debounce_time,
            damageDebounceTime = entity.damage_debounce_time,
            flySoundDebounceTime = entity.fly_sound_debounce_time,
            health = entity.health,
            maxHealth = entity.max_health,
            gibHealth = entity.gib_health,
            deadFlag = entity.deadflag,
            showHostile = entity.show_hostile,
            powerArmorTime = entity.powerarmor_time,
            map = entity.map,
            viewHeight = entity.viewheight,
            takeDamage = entity.takedamage,
            damage = entity.dmg,
            radiusDamage = entity.radius_dmg,
            damageRadius = entity.dmg_radius,
            sounds = entity.sounds,
            count = entity.count,
            chainIndex = entity.chain?.index,
            enemyIndex = entity.enemy?.index,
            oldEnemyIndex = entity.oldenemy?.index,
            activatorIndex = entity.activator?.index,
            groundEntityIndex = entity.groundentity?.index,
            groundEntityLinkCount = entity.groundentity_linkcount,
            teamChainIndex = entity.teamchain?.index,
            teamMasterIndex = entity.teammaster?.index,
            myNoiseIndex = entity.mynoise?.index,
            myNoise2Index = entity.mynoise2?.index,
            noiseIndex = entity.noise_index,
            noiseIndex2 = entity.noise_index2,
            volume = entity.volume,
            attenuation = entity.attenuation,
            wait = entity.wait,
            delay = entity.delay,
            random = entity.random,
            teleportTime = entity.teleport_time,
            waterType = entity.watertype,
            waterLevel = entity.waterlevel,
            moveOrigin = entity.move_origin.copyOf(),
            moveAngles = entity.move_angles.copyOf(),
            lightLevel = entity.light_level,
            style = entity.style,
            itemIndex = entity.item?.index,
            monsterInfo = snapshot(entity.monsterinfo),
            clientIndex = entity.client?.index,
            ownerIndex = entity.owner?.index
        )

    @JvmStatic
    fun apply(
        target: GameEntity,
        snapshot: LevelEntitySnapshot,
        items: List<GameItem>,
        edicts: Array<GameEntity>,
        clients: Array<GamePlayerInfo>
    ) {
        apply(target.s, snapshot.state)
        target.inuse = snapshot.inUse
        target.linkcount = snapshot.linkCount
        target.num_clusters = snapshot.numClusters
        target.clusternums = snapshot.clusterNums?.copyOf()
        target.headnode = snapshot.headNode
        target.areanum = snapshot.areaNum
        target.areanum2 = snapshot.areaNum2
        target.svflags = snapshot.svFlags
        copy(snapshot.mins, target.mins, 3)
        copy(snapshot.maxs, target.maxs, 3)
        copy(snapshot.absMin, target.absmin, 3)
        copy(snapshot.absMax, target.absmax, 3)
        copy(snapshot.size, target.size, 3)
        target.solid = snapshot.solid
        target.clipmask = snapshot.clipMask
        target.movetype = snapshot.moveType
        target.flags = snapshot.flags
        target.model = snapshot.model
        target.freetime = snapshot.freeTime
        target.message = snapshot.message
        target.classname = snapshot.className
        target.spawnflags = snapshot.spawnFlags
        target.timestamp = snapshot.timestamp
        target.angle = snapshot.angle
        target.target = snapshot.target
        target.targetname = snapshot.targetName
        target.killtarget = snapshot.killTarget
        target.team = snapshot.team
        target.pathtarget = snapshot.pathTarget
        target.deathtarget = snapshot.deathTarget
        target.combattarget = snapshot.combatTarget
        target.target_ent = entityRef(snapshot.targetEntityIndex, edicts)
        target.speed = snapshot.speed
        target.accel = snapshot.accel
        target.decel = snapshot.decel
        copy(snapshot.moveDir, target.movedir, 3)
        copy(snapshot.pos1, target.pos1, 3)
        copy(snapshot.pos2, target.pos2, 3)
        copy(snapshot.velocity, target.velocity, 3)
        copy(snapshot.angularVelocity, target.avelocity, 3)
        target.mass = snapshot.mass
        target.air_finished = snapshot.airFinished
        target.gravity = snapshot.gravity
        target.goalentity = entityRef(snapshot.goalEntityIndex, edicts)
        target.movetarget = entityRef(snapshot.moveTargetIndex, edicts)
        target.yaw_speed = snapshot.yawSpeed
        target.ideal_yaw = snapshot.idealYaw
        target.think.nextTime = snapshot.thinkNextTime
        target.think.prethink = thinkAdapter(snapshot.thinkPrethinkId)
        target.think.action = thinkAdapter(snapshot.thinkActionId)
        target.blocked = blockedAdapter(snapshot.blockedAdapterId)
        target.touch = touchAdapter(snapshot.touchAdapterId)
        target.use = useAdapter(snapshot.useAdapterId)
        target.pain = painAdapter(snapshot.painAdapterId)
        target.die = dieAdapter(snapshot.dieAdapterId)
        target.touch_debounce_time = snapshot.touchDebounceTime
        target.pain_debounce_time = snapshot.painDebounceTime
        target.damage_debounce_time = snapshot.damageDebounceTime
        target.fly_sound_debounce_time = snapshot.flySoundDebounceTime
        target.health = snapshot.health
        target.max_health = snapshot.maxHealth
        target.gib_health = snapshot.gibHealth
        target.deadflag = snapshot.deadFlag
        target.show_hostile = snapshot.showHostile
        target.powerarmor_time = snapshot.powerArmorTime
        target.map = snapshot.map
        target.viewheight = snapshot.viewHeight
        target.takedamage = snapshot.takeDamage
        target.dmg = snapshot.damage
        target.radius_dmg = snapshot.radiusDamage
        target.dmg_radius = snapshot.damageRadius
        target.sounds = snapshot.sounds
        target.count = snapshot.count
        target.chain = entityRef(snapshot.chainIndex, edicts)
        target.enemy = entityRef(snapshot.enemyIndex, edicts)
        target.oldenemy = entityRef(snapshot.oldEnemyIndex, edicts)
        target.activator = entityRef(snapshot.activatorIndex, edicts)
        target.groundentity = entityRef(snapshot.groundEntityIndex, edicts)
        target.groundentity_linkcount = snapshot.groundEntityLinkCount
        target.teamchain = entityRef(snapshot.teamChainIndex, edicts)
        target.teammaster = entityRef(snapshot.teamMasterIndex, edicts)
        target.mynoise = entityRef(snapshot.myNoiseIndex, edicts)
        target.mynoise2 = entityRef(snapshot.myNoise2Index, edicts)
        target.noise_index = snapshot.noiseIndex
        target.noise_index2 = snapshot.noiseIndex2
        target.volume = snapshot.volume
        target.attenuation = snapshot.attenuation
        target.wait = snapshot.wait
        target.delay = snapshot.delay
        target.random = snapshot.random
        target.teleport_time = snapshot.teleportTime
        target.watertype = snapshot.waterType
        target.waterlevel = snapshot.waterLevel
        copy(snapshot.moveOrigin, target.move_origin, 3)
        copy(snapshot.moveAngles, target.move_angles, 3)
        target.light_level = snapshot.lightLevel
        target.style = snapshot.style
        target.item = resolveItem(snapshot.itemIndex, items)
        apply(target.monsterinfo, snapshot.monsterInfo)
        target.client = snapshot.clientIndex?.let { clients.getOrNull(it) }
        target.owner = entityRef(snapshot.ownerIndex, edicts)
    }

    @JvmStatic
    fun snapshot(state: entity_state_t): EntityStateSnapshot =
        EntityStateSnapshot(
            index = state.index,
            origin = state.origin.copyOf(),
            angles = state.angles.copyOf(),
            oldOrigin = state.old_origin.copyOf(),
            modelIndex = state.modelindex,
            modelIndex2 = state.modelindex2,
            modelIndex3 = state.modelindex3,
            modelIndex4 = state.modelindex4,
            frame = state.frame,
            skinNum = state.skinnum,
            effects = state.effects,
            renderFx = state.renderfx,
            solid = state.solid,
            sound = state.sound,
            event = state.event
        )

    @JvmStatic
    fun apply(target: entity_state_t, snapshot: EntityStateSnapshot) {
        target.index = snapshot.index
        copy(snapshot.origin, target.origin, 3)
        copy(snapshot.angles, target.angles, 3)
        copy(snapshot.oldOrigin, target.old_origin, 3)
        target.modelindex = snapshot.modelIndex
        target.modelindex2 = snapshot.modelIndex2
        target.modelindex3 = snapshot.modelIndex3
        target.modelindex4 = snapshot.modelIndex4
        target.frame = snapshot.frame
        target.skinnum = snapshot.skinNum
        target.effects = snapshot.effects
        target.renderfx = snapshot.renderFx
        target.solid = snapshot.solid
        target.sound = snapshot.sound
        target.event = snapshot.event
    }

    @JvmStatic
    fun snapshot(monsterInfo: monsterinfo_t): MonsterInfoSnapshot =
        MonsterInfoSnapshot(
            currentMove = snapshot(monsterInfo.currentmove),
            aiFlags = monsterInfo.aiflags,
            nextFrame = monsterInfo.nextframe,
            scale = monsterInfo.scale,
            standAdapterId = adapterId(monsterInfo.stand),
            idleAdapterId = adapterId(monsterInfo.idle),
            searchAdapterId = adapterId(monsterInfo.search),
            walkAdapterId = adapterId(monsterInfo.walk),
            runAdapterId = adapterId(monsterInfo.run),
            dodgeAdapterId = adapterId(monsterInfo.dodge),
            attackAdapterId = adapterId(monsterInfo.attack),
            meleeAdapterId = adapterId(monsterInfo.melee),
            sightAdapterId = adapterId(monsterInfo.sight),
            checkAttackAdapterId = adapterId(monsterInfo.checkattack),
            pauseTime = monsterInfo.pausetime,
            attackFinished = monsterInfo.attack_finished,
            savedGoal = monsterInfo.saved_goal.copyOf(),
            searchTime = monsterInfo.search_time,
            trailTime = monsterInfo.trail_time,
            lastSighting = monsterInfo.last_sighting.copyOf(),
            attackState = monsterInfo.attack_state,
            lefty = monsterInfo.lefty,
            idleTime = monsterInfo.idle_time,
            linkCount = monsterInfo.linkcount,
            powerArmorType = monsterInfo.power_armor_type,
            powerArmorPower = monsterInfo.power_armor_power
        )

    @JvmStatic
    fun apply(target: monsterinfo_t, snapshot: MonsterInfoSnapshot) {
        target.currentmove = apply(snapshot.currentMove)
        target.aiflags = snapshot.aiFlags
        target.nextframe = snapshot.nextFrame
        target.scale = snapshot.scale
        target.stand = thinkAdapter(snapshot.standAdapterId)
        target.idle = thinkAdapter(snapshot.idleAdapterId)
        target.search = thinkAdapter(snapshot.searchAdapterId)
        target.walk = thinkAdapter(snapshot.walkAdapterId)
        target.run = thinkAdapter(snapshot.runAdapterId)
        target.dodge = dodgeAdapter(snapshot.dodgeAdapterId)
        target.attack = thinkAdapter(snapshot.attackAdapterId)
        target.melee = thinkAdapter(snapshot.meleeAdapterId)
        target.sight = interactAdapter(snapshot.sightAdapterId)
        target.checkattack = thinkAdapter(snapshot.checkAttackAdapterId)
        target.pausetime = snapshot.pauseTime
        target.attack_finished = snapshot.attackFinished
        copy(snapshot.savedGoal, target.saved_goal, 3)
        target.search_time = snapshot.searchTime
        target.trail_time = snapshot.trailTime
        copy(snapshot.lastSighting, target.last_sighting, 3)
        target.attack_state = snapshot.attackState
        target.lefty = snapshot.lefty
        target.idle_time = snapshot.idleTime
        target.linkcount = snapshot.linkCount
        target.power_armor_type = snapshot.powerArmorType
        target.power_armor_power = snapshot.powerArmorPower
    }

    @JvmStatic
    fun snapshot(move: mmove_t?): MonsterMoveSnapshot? =
        move?.let {
            MonsterMoveSnapshot(
                firstFrame = it.firstframe,
                lastFrame = it.lastframe,
                frames = it.frames.map(::snapshot),
                endFuncAdapterId = adapterId(it.endfunc)
            )
        }

    @JvmStatic
    fun apply(snapshot: MonsterMoveSnapshot?): mmove_t? =
        snapshot?.let {
            mmove_t(
                it.firstFrame,
                it.lastFrame,
                it.frames.map(::apply).toTypedArray(),
                thinkAdapter(it.endFuncAdapterId)
            )
        }

    @JvmStatic
    fun snapshot(frame: mframe_t): MonsterFrameSnapshot =
        MonsterFrameSnapshot(
            aiAdapterId = adapterId(frame.ai),
            dist = frame.dist,
            thinkAdapterId = adapterId(frame.think)
        )

    @JvmStatic
    fun apply(snapshot: MonsterFrameSnapshot): mframe_t =
        mframe_t(
            aiAdapter(snapshot.aiAdapterId),
            snapshot.dist,
            thinkAdapter(snapshot.thinkAdapterId)
        )

    private fun resolveItem(index: Int?, items: List<GameItem>): GameItem? {
        if (index == null || index < 0 || index >= items.size) {
            return null
        }
        return items[index]
    }

    private fun entityRef(index: Int?, edicts: Array<GameEntity>): GameEntity? {
        if (index == null || index < 0 || index >= edicts.size) {
            return null
        }
        return edicts[index]
    }

    private fun adapterId(adapter: SuperAdapter?): String? = adapter?.iD

    private fun thinkAdapter(id: String?): EntThinkAdapter? = resolveAdapter(id)
    private fun blockedAdapter(id: String?): EntBlockedAdapter? = resolveAdapter(id)
    private fun touchAdapter(id: String?): EntTouchAdapter? = resolveAdapter(id)
    private fun useAdapter(id: String?): EntUseAdapter? = resolveAdapter(id)
    private fun painAdapter(id: String?): EntPainAdapter? = resolveAdapter(id)
    private fun dieAdapter(id: String?): EntDieAdapter? = resolveAdapter(id)
    private fun dodgeAdapter(id: String?): EntDodgeAdapter? = resolveAdapter(id)
    private fun interactAdapter(id: String?): EntInteractAdapter? = resolveAdapter(id)
    private fun aiAdapter(id: String?): AIAdapter? = resolveAdapter(id)

    @Suppress("UNCHECKED_CAST")
    private fun <T : SuperAdapter> resolveAdapter(id: String?): T? {
        if (id == null) {
            return null
        }
        return SuperAdapter.getFromID(id) as? T
    }

    private fun copy(source: FloatArray?, target: FloatArray, expectedLength: Int) {
        Arrays.fill(target, 0f)
        if (source != null) {
            System.arraycopy(source, 0, target, 0, minOf(source.size, expectedLength))
        }
    }
}

class LevelSaveJsonStore(private val writable: WritableFileSystem) {
    fun read(logicalPath: String): LevelSaveFileSnapshot {
        return read(normalizePath(logicalPath), LevelSaveFileSnapshot::class.java)
    }

    fun write(logicalPath: String, snapshot: LevelSaveFileSnapshot) {
        writeInternal(normalizePath(logicalPath), snapshot)
    }

    private fun <T> read(logicalPath: String, type: Class<T>): T {
        val opened: VfsResult<VfsReadableHandle> = writable.openReadReal(logicalPath, VfsOpenOptions.DEFAULT)
        if (!opened.success() || opened.value() == null) {
            throw IOException(opened.error() ?: "Failed to open $logicalPath")
        }
        opened.value().use { handle ->
            return SaveJson.read(handle.inputStream(), type)
        }
    }

    private fun writeInternal(logicalPath: String, value: Any) {
        val opened: VfsResult<VfsWritableHandle> = writable.openWrite(logicalPath, VfsWriteOptions.TRUNCATE)
        if (!opened.success() || opened.value() == null) {
            throw IOException(opened.error() ?: "Failed to open $logicalPath")
        }
        opened.value().use { handle ->
            SaveJson.write(handle.outputStream(), value)
        }
    }

    private fun normalizePath(path: String): String {
        val candidate = Path.of(path).normalize()
        if (!candidate.isAbsolute) {
            return path
        }

        val root = Path.of(writable.writeRoot()).toAbsolutePath().normalize()
        if (!candidate.startsWith(root)) {
            throw IOException("Path is outside writable root: $path")
        }
        return root.relativize(candidate).toString().replace('\\', '/')
    }

    companion object {
        const val SCHEMA_VERSION: Int = 1

        @JvmStatic
        fun forWriteDir(writeDir: String): LevelSaveJsonStore =
            LevelSaveJsonStore(DefaultWritableFileSystem(Path.of(writeDir)))
    }
}
