package jake2.game
import jake2.game.items.GameItem
import jake2.qcommon.save.ClientPersistentSnapshot
import jake2.qcommon.save.ClientRespawnSnapshot
import jake2.qcommon.save.GameLocalsSnapshot
import jake2.qcommon.save.PlayerStateSnapshot
import jake2.qcommon.save.PlayerStateSnapshots
import jake2.qcommon.save.PmoveStateSnapshot
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

data class GamePlayerSnapshot(
    val playerState: PlayerStateSnapshot,
    val ping: Int,
    val persistent: ClientPersistentSnapshot,
    val respawn: ClientRespawnSnapshot,
    val oldPmove: PmoveStateSnapshot,
    val showScores: Boolean,
    val showInventory: Boolean,
    val showHelp: Boolean,
    val showHelpIcon: Boolean,
    val ammoIndex: Int,
    val buttons: Int,
    val oldButtons: Int,
    val latchedButtons: Int,
    val weaponThunk: Boolean,
    val newWeaponIndex: Int?,
    val damageArmor: Int,
    val damageParmor: Int,
    val damageBlood: Int,
    val damageKnockback: Int,
    val damageFrom: FloatArray,
    val killerYaw: Float,
    val weaponState: Int,
    val kickAngles: FloatArray,
    val kickOrigin: FloatArray,
    val viewDamageRoll: Float,
    val viewDamagePitch: Float,
    val viewDamageTime: Float,
    val fallTime: Float,
    val fallValue: Float,
    val damageAlpha: Float,
    val bonusAlpha: Float,
    val damageBlend: FloatArray,
    val viewAngle: FloatArray,
    val bobTime: Float,
    val oldViewAngles: FloatArray,
    val oldVelocity: FloatArray,
    val nextDrownTime: Float,
    val oldWaterLevel: Int,
    val breatherSound: Int,
    val machinegunShots: Int,
    val animEnd: Int,
    val animPriority: Int,
    val animDuck: Boolean,
    val animRun: Boolean,
    val quadFramenum: Float,
    val invincibleFramenum: Float,
    val breatherFramenum: Float,
    val enviroFramenum: Float,
    val grenadeBlewUp: Boolean,
    val grenadeTime: Float,
    val silencerShots: Int,
    val weaponSound: Int,
    val pickupMsgTime: Float,
    val floodLockTill: Float,
    val floodWhen: FloatArray,
    val floodWhenHead: Int,
    val respawnTime: Float,
    val chaseTargetIndex: Int?,
    val updateChase: Boolean
)

data class GameSaveFileSnapshot(
    val schemaVersion: Int,
    val game: GameLocalsSnapshot,
    val clients: List<GamePlayerSnapshot>
)

object GamePlayerSnapshots {
    @JvmStatic
    fun snapshot(client: GamePlayerInfo): GamePlayerSnapshot =
        GamePlayerSnapshot(
            playerState = PlayerStateSnapshots.snapshot(client.playerState),
            ping = client.ping,
            persistent = GameSaveSnapshots.snapshot(client.pers),
            respawn = GameSaveSnapshots.snapshot(client.resp),
            oldPmove = PlayerStateSnapshots.snapshot(client.old_pmove),
            showScores = client.showscores,
            showInventory = client.showinventory,
            showHelp = client.showhelp,
            showHelpIcon = client.isShowHelpIcon,
            ammoIndex = client.ammo_index,
            buttons = client.buttons,
            oldButtons = client.oldbuttons,
            latchedButtons = client.latched_buttons,
            weaponThunk = client.weapon_thunk,
            newWeaponIndex = itemIndex(client.newweapon),
            damageArmor = client.damage_armor,
            damageParmor = client.damage_parmor,
            damageBlood = client.damage_blood,
            damageKnockback = client.damage_knockback,
            damageFrom = client.damage_from.copyOf(),
            killerYaw = client.killer_yaw,
            weaponState = client.weaponstate.intValue,
            kickAngles = client.kick_angles.copyOf(),
            kickOrigin = client.kick_origin.copyOf(),
            viewDamageRoll = client.v_dmg_roll,
            viewDamagePitch = client.v_dmg_pitch,
            viewDamageTime = client.v_dmg_time,
            fallTime = client.fall_time,
            fallValue = client.fall_value,
            damageAlpha = client.damage_alpha,
            bonusAlpha = client.bonus_alpha,
            damageBlend = client.damage_blend.copyOf(),
            viewAngle = client.v_angle.copyOf(),
            bobTime = client.bobtime,
            oldViewAngles = client.oldviewangles.copyOf(),
            oldVelocity = client.oldvelocity.copyOf(),
            nextDrownTime = client.next_drown_time,
            oldWaterLevel = client.old_waterlevel,
            breatherSound = client.breather_sound,
            machinegunShots = client.machinegun_shots,
            animEnd = client.anim_end,
            animPriority = client.anim_priority,
            animDuck = client.anim_duck,
            animRun = client.anim_run,
            quadFramenum = client.quad_framenum,
            invincibleFramenum = client.invincible_framenum,
            breatherFramenum = client.breather_framenum,
            enviroFramenum = client.enviro_framenum,
            grenadeBlewUp = client.grenade_blew_up,
            grenadeTime = client.grenade_time,
            silencerShots = client.silencer_shots,
            weaponSound = client.weapon_sound,
            pickupMsgTime = client.pickup_msg_time,
            floodLockTill = client.flood_locktill,
            floodWhen = client.flood_when.copyOf(),
            floodWhenHead = client.flood_whenhead,
            respawnTime = client.respawn_time,
            chaseTargetIndex = client.chase_target?.index,
            updateChase = client.update_chase
        )

    @JvmStatic
    fun apply(target: GamePlayerInfo, snapshot: GamePlayerSnapshot, items: List<GameItem>, edicts: Array<GameEntity>) {
        PlayerStateSnapshots.apply(target.playerState, snapshot.playerState)
        target.ping = snapshot.ping
        GameSaveSnapshots.apply(target.pers, snapshot.persistent, items)
        GameSaveSnapshots.apply(target.resp, snapshot.respawn, items)
        PlayerStateSnapshots.apply(target.old_pmove, snapshot.oldPmove)
        target.showscores = snapshot.showScores
        target.showinventory = snapshot.showInventory
        target.showhelp = snapshot.showHelp
        target.setShowHelpIcon(snapshot.showHelpIcon)
        target.ammo_index = snapshot.ammoIndex
        target.buttons = snapshot.buttons
        target.oldbuttons = snapshot.oldButtons
        target.latched_buttons = snapshot.latchedButtons
        target.weapon_thunk = snapshot.weaponThunk
        target.newweapon = resolveItem(snapshot.newWeaponIndex, items)
        target.damage_armor = snapshot.damageArmor
        target.damage_parmor = snapshot.damageParmor
        target.damage_blood = snapshot.damageBlood
        target.damage_knockback = snapshot.damageKnockback
        copy(snapshot.damageFrom, target.damage_from, 3)
        target.killer_yaw = snapshot.killerYaw
        target.weaponstate = WeaponStates.fromInt(snapshot.weaponState)
        copy(snapshot.kickAngles, target.kick_angles, 3)
        copy(snapshot.kickOrigin, target.kick_origin, 3)
        target.v_dmg_roll = snapshot.viewDamageRoll
        target.v_dmg_pitch = snapshot.viewDamagePitch
        target.v_dmg_time = snapshot.viewDamageTime
        target.fall_time = snapshot.fallTime
        target.fall_value = snapshot.fallValue
        target.damage_alpha = snapshot.damageAlpha
        target.bonus_alpha = snapshot.bonusAlpha
        copy(snapshot.damageBlend, target.damage_blend, 3)
        copy(snapshot.viewAngle, target.v_angle, 3)
        target.bobtime = snapshot.bobTime
        copy(snapshot.oldViewAngles, target.oldviewangles, 3)
        copy(snapshot.oldVelocity, target.oldvelocity, 3)
        target.next_drown_time = snapshot.nextDrownTime
        target.old_waterlevel = snapshot.oldWaterLevel
        target.breather_sound = snapshot.breatherSound
        target.machinegun_shots = snapshot.machinegunShots
        target.anim_end = snapshot.animEnd
        target.anim_priority = snapshot.animPriority
        target.anim_duck = snapshot.animDuck
        target.anim_run = snapshot.animRun
        target.quad_framenum = snapshot.quadFramenum
        target.invincible_framenum = snapshot.invincibleFramenum
        target.breather_framenum = snapshot.breatherFramenum
        target.enviro_framenum = snapshot.enviroFramenum
        target.grenade_blew_up = snapshot.grenadeBlewUp
        target.grenade_time = snapshot.grenadeTime
        target.silencer_shots = snapshot.silencerShots
        target.weapon_sound = snapshot.weaponSound
        target.pickup_msg_time = snapshot.pickupMsgTime
        target.flood_locktill = snapshot.floodLockTill
        copy(snapshot.floodWhen, target.flood_when, 10)
        target.flood_whenhead = snapshot.floodWhenHead
        target.respawn_time = snapshot.respawnTime
        target.chase_target = snapshot.chaseTargetIndex?.let { edicts.getOrNull(it) }
        target.update_chase = snapshot.updateChase
    }

    private fun itemIndex(item: GameItem?): Int? = item?.index

    private fun resolveItem(index: Int?, items: List<GameItem>): GameItem? {
        if (index == null || index < 0 || index >= items.size) {
            return null
        }
        return items[index]
    }

    private fun copy(source: FloatArray?, target: FloatArray, expectedLength: Int) {
        Arrays.fill(target, 0f)
        if (source != null) {
            System.arraycopy(source, 0, target, 0, minOf(source.size, expectedLength))
        }
    }
}

class GameSaveJsonStore(private val writable: WritableFileSystem) {
    fun read(logicalPath: String): GameSaveFileSnapshot {
        return read(normalizePath(logicalPath), GameSaveFileSnapshot::class.java)
    }

    fun write(logicalPath: String, snapshot: GameSaveFileSnapshot) {
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
        fun forWriteDir(writeDir: String): GameSaveJsonStore =
            GameSaveJsonStore(DefaultWritableFileSystem(Path.of(writeDir)))
    }
}
