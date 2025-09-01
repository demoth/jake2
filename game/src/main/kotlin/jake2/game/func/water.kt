package jake2.game.func

import jake2.game.GameBase
import jake2.game.GameDefines
import jake2.game.GameExportsImpl
import jake2.game.GameEntity
import jake2.game.components.MoveInfo
import jake2.game.components.addComponent
import jake2.game.hasSpawnFlag
import jake2.game.setSpawnFlag
import jake2.qcommon.Defines
import jake2.qcommon.math.Vector3f
import jake2.qcommon.util.Math3D
import kotlin.math.abs

/**
 * QUAKED func_water (0 .5 .8) ?
 * START_OPEN
 * func_water is a movable water brush.
 * It must be targeted to operate. Use a non-water texture at your own risk.
 *
 * START_OPEN causes the water to move to its destination when spawned and operate in reverse.
 *
 * "angle" determines the opening direction (up or down only)
 * "speed" movement speed (25 default)
 * "wait" wait before returning (-1 default, -1 = TOGGLE)
 * "lip" lip remaining at end of move (0 default)
 * "sounds" (yes, these need to be changed) 0) no sound 1) water 2) lava
 *
 * Note: func_water has a classname `func_door`
 */
fun funcWater(self: GameEntity, game: GameExportsImpl) {
    GameBase.G_SetMovedir(self.s.angles, self.movedir)
    self.movetype = GameDefines.MOVETYPE_PUSH
    self.solid = Defines.SOLID_BSP
    game.gameImports.setmodel(self, self.model)

    // calculate second position
    Math3D.VectorCopy(self.s.origin, self.pos1)
    val abs_movedir = floatArrayOf(abs(self.movedir[0]), abs(self.movedir[1]), abs(self.movedir[2]))
    val distance = abs_movedir[0] * self.size[0] + abs_movedir[1] * self.size[1] + abs_movedir[2] * self.size[2] - self.st.lip
    Math3D.VectorMA(self.pos1, distance, self.movedir, self.pos2)

    // if it starts open, switch the positions
    if (self.hasSpawnFlag(DOOR_START_OPEN)) {
        Math3D.VectorCopy(self.pos2, self.s.origin)
        Math3D.VectorCopy(self.pos1, self.pos2)
        Math3D.VectorCopy(self.s.origin, self.pos1)
    }
    if (self.speed == 0f)
        self.speed = 25f
    if (self.wait == 0f)
        self.wait = -1f

    self.addComponent(MoveInfo(
        sound_start = if (self.sounds != 0) game.gameImports.soundindex("world/mov_watr.wav") else 0,
        sound_end = if (self.sounds != 0) game.gameImports.soundindex("world/stp_watr.wav") else 0,
        distance = distance,
        start_origin = Vector3f(self.pos1),
        start_angles = Vector3f(self.s.angles),
        end_origin = Vector3f(self.pos2),
        end_angles = Vector3f(self.s.angles),
        state = MovementState.BOTTOM,
        accel = self.speed,
        decel = self.speed,
        speed = self.speed,
        wait = self.wait,
    ))
    
    self.use = doorOpenUse

    if (self.wait == -1f)
        self.setSpawnFlag(DOOR_TOGGLE)

    self.classname = "func_door"

    game.gameImports.linkentity(self)
}
