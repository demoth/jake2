package jake2.game.func

import jake2.game.GameDefines
import jake2.game.GameEntity
import jake2.game.GameExportsImpl
import jake2.game.adapters.EntThinkAdapter
import jake2.game.adapters.SuperAdapter
import jake2.game.adapters.SuperAdapter.Companion.registerThink
import jake2.game.components.MoveInfo
import jake2.game.components.getComponent
import jake2.qcommon.Defines
import jake2.qcommon.math.Vector3f
import jake2.qcommon.math.toVector3f
import jake2.qcommon.util.Math3D
import kotlin.math.floor

enum class MovementState {
    TOP,
    UP,
    DOWN,
    BOTTOM
}

/**
 * Move_Calc
 */
fun startMovement(self: GameEntity, destination: Vector3f, endFunction: EntThinkAdapter?, game: GameExportsImpl) {
    Math3D.VectorClear(self.velocity)

    val moveInfo: MoveInfo = self.getComponent()!!

    val delta = destination - self.s.origin.toVector3f()
    moveInfo.remaining_distance = delta.length()
    moveInfo.dir = delta.normalize()
    moveInfo.endfunc = endFunction

    if (moveInfo.speed == moveInfo.accel && moveInfo.speed == moveInfo.decel) {
        //  steady movement, reached final speed
        val teamMaster = self.flags and GameDefines.FL_TEAMSLAVE == 0
        if (game.level.current_entity === (if (teamMaster) self else self.teammaster)) {
            moveBegin.think(self, game)
        } else {
            self.think.nextTime = game.level.time + Defines.FRAMETIME
            self.think.action = moveBegin
        }
    } else {
        // accelerate
        moveInfo.current_speed = 0f
        self.think.action = accelMove
        self.think.nextTime = game.level.time + Defines.FRAMETIME
    }
}

private val moveBegin = registerThink("move_begin") { self, game ->
    val moveInfo: MoveInfo = self.getComponent()!!
    if (moveInfo.speed * Defines.FRAMETIME >= moveInfo.remaining_distance) {
        moveFinal.think(self, game)
        return@registerThink true
    }
    self.velocity = (moveInfo.dir * moveInfo.speed).toArray()
    val frames = floor(moveInfo.remaining_distance / moveInfo.speed / Defines.FRAMETIME)
    moveInfo.remaining_distance -= frames * moveInfo.speed * Defines.FRAMETIME
    self.think.nextTime = game.level.time + frames * Defines.FRAMETIME
    self.think.action = moveFinal
    true

}

private val moveFinal = registerThink("move_final") { self, game ->
    val moveInfo: MoveInfo = self.getComponent()!!
    if (moveInfo.remaining_distance == 0f) {
        moveDone.think(self, game)
        return@registerThink true
    }

    self.velocity = (moveInfo.dir * moveInfo.remaining_distance / Defines.FRAMETIME).toArray()

    self.think.action = moveDone
    self.think.nextTime = game.level.time + Defines.FRAMETIME
    true

}

private val moveDone = registerThink("move_done") { self, game ->
    val moveInfo: MoveInfo = self.getComponent()!!
    Math3D.VectorClear(self.velocity)
    moveInfo.endfunc?.think(self, game)
    true
}

/**
 * Think_AccelMove
 *
 * The team has completed a frame of movement, so change the speed for the next frame.
 */
private val accelMove: EntThinkAdapter = registerThink("thinc_accelmove") { self, game ->
    val moveInfo: MoveInfo = self.getComponent()!!
    moveInfo.remaining_distance -= moveInfo.current_speed

    if (moveInfo.current_speed == 0f) // starting or blocked
        moveInfo.platCalculateAcceleratedMove()

    moveInfo.platAccelerate()

    // will the entire move complete on next frame?
    if (moveInfo.remaining_distance <= moveInfo.current_speed) {
        moveFinal.think(self, game)
        return@registerThink true
    }

    self.velocity = (moveInfo.dir * moveInfo.current_speed * 10f).toArray()
    self.think.nextTime = game.level.time + Defines.FRAMETIME
    self.think.action = SuperAdapter.think("thinc_accelmove") // hack to have a pointer while initializing the function
    true
}


