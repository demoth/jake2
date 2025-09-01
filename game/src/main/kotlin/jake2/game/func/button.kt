package jake2.game.func

import jake2.game.GameBase
import jake2.game.GameDefines
import jake2.game.GameEntity
import jake2.game.GameExportsImpl
import jake2.game.GameUtil
import jake2.game.adapters.SuperAdapter.Companion.registerDie
import jake2.game.adapters.SuperAdapter.Companion.registerThink
import jake2.game.adapters.SuperAdapter.Companion.registerTouch
import jake2.game.adapters.SuperAdapter.Companion.registerUse
import jake2.game.components.MoveInfo
import jake2.game.components.addComponent
import jake2.game.components.getComponent
import jake2.qcommon.Defines
import jake2.qcommon.math.Vector3f
import jake2.qcommon.util.Math3D
import kotlin.math.abs

/*
 * QUAKED func_button (0 .5 .8) ? When a button is touched, it moves some
 * distance in the direction of it's angle, triggers all of it's targets,
 * waits some time, then returns to it's original position where it can be
 * triggered again.
 * 
 * "angle" determines the opening direction "target" all entities with a
 * matching targetname will be used "speed" override the default 40 speed
 * "wait" override the default 1 second wait (-1 = never return) "lip"
 * override the default 4 pixel lip remaining at end of move "health" if
 * set, the button must be killed instead of touched "sounds" 1) silent 2)
 * steam metal 3) wooden clunk 4) metallic click 5) in-out
 * 
 * Buttons can be activated by touching, shooting or targeting by other entity.
 */
fun funcButton(self: GameEntity, game: GameExportsImpl) {

    GameBase.G_SetMovedir(self.s.angles, self.movedir)
    self.movetype = GameDefines.MOVETYPE_STOP
    self.solid = Defines.SOLID_BSP
    game.gameImports.setmodel(self, self.model)

    if (self.speed == 0f)
        self.speed = 40f
    if (self.accel == 0f)
        self.accel = self.speed
    if (self.decel == 0f)
        self.decel = self.speed
    if (self.wait == 0f) 
        self.wait = 3f
    if (self.st.lip == 0)
        self.st.lip = 4

    Math3D.VectorCopy(self.s.origin, self.pos1)
    val absMoveDir = floatArrayOf(abs(self.movedir[0]), abs(self.movedir[1]), abs(self.movedir[2]))
    val dist: Float =
        absMoveDir[0] * self.size[0] + absMoveDir[1] * self.size[1] + absMoveDir[2] * self.size[2] - self.st.lip
    Math3D.VectorMA(self.pos1, dist, self.movedir, self.pos2)

    self.use = buttonUse
    self.s.effects = self.s.effects or Defines.EF_ANIM01

    if (self.health != 0) {
        self.max_health = self.health
        self.die = buttonKilled
        self.takedamage = Defines.DAMAGE_YES
    } else if (self.targetname == null)
        self.touch = buttonTouch

    self.addComponent(MoveInfo(
        sound_start = if (self.sounds != 1) game.gameImports.soundindex("switches/butn2.wav") else 0,
        state = MovementState.BOTTOM,
        speed = self.speed,
        accel = self.accel,
        decel = self.decel,
        wait = self.wait,
        start_origin = Vector3f(self.pos1),
        end_origin = Vector3f(self.pos2),
        start_angles = Vector3f(self.s.angles),
        end_angles = Vector3f(self.s.angles) // no rotation for button
    ))
    
    game.gameImports.linkentity(self)
}

private val buttonUse = registerUse("button_use") { self, other, activator, game ->
    self.activator = activator
    buttonFire(self, game)
}

private fun buttonFire(self: GameEntity, game: GameExportsImpl) {
    val moveInfo: MoveInfo = self.getComponent()!!
    if (moveInfo.state == MovementState.UP || moveInfo.state == MovementState.TOP)
        return
    moveInfo.state = MovementState.UP
    if (moveInfo.sound_start != 0 && self.flags and GameDefines.FL_TEAMSLAVE == 0)
        game.gameImports.sound(
            self,
            Defines.CHAN_NO_PHS_ADD + Defines.CHAN_VOICE,
            moveInfo.sound_start,
            1f,
            Defines.ATTN_STATIC.toFloat(),
            0f
        )
    startMovement(self, moveInfo.end_origin, buttonWait, game)
}

private val buttonWait = registerThink("button_wait") { self, game ->
    val moveInfo: MoveInfo = self.getComponent()!!
    moveInfo.state = MovementState.TOP

    // EF_ANIM01 -> EF_ANIM23
    self.s.effects = self.s.effects and Defines.EF_ANIM01.inv()
    self.s.effects = self.s.effects or Defines.EF_ANIM23

    GameUtil.G_UseTargets(self, self.activator, game)
    self.s.frame = 1
    if (moveInfo.wait >= 0) {
        self.think.nextTime = game.level.time + moveInfo.wait
        self.think.action = buttonReturn
    }
    true
}

private val buttonReturn = registerThink("button_return") { self, game ->
    val moveInfo: MoveInfo = self.getComponent()!!
    moveInfo.state = MovementState.DOWN

    startMovement(self, moveInfo.start_origin, buttonDone, game)

    self.s.frame = 0
    if (self.health != 0) {
        // why not max_health? - because by this time health is already reset to max_health
        self.takedamage = Defines.DAMAGE_YES
    }
    true
}

private val buttonDone = registerThink("button_done") { self, game ->
    val moveInfo: MoveInfo = self.getComponent()!!
    moveInfo.state = MovementState.BOTTOM
    // EF_ANIM23 -> EF_ANIM01
    self.s.effects = self.s.effects and Defines.EF_ANIM23.inv()
    self.s.effects = self.s.effects or Defines.EF_ANIM01
    true
}

private val buttonTouch = registerTouch("button_touch") { self, other, plane, surf, game ->
    if (other.client == null)
        return@registerTouch

    if (other.health <= 0)
        return@registerTouch

    self.activator = other
    buttonFire(self, game)
}

private val buttonKilled = registerDie("button_killed") { self, inflictor, attacker, damage, point, game ->
    self.activator = attacker
    self.health = self.max_health
    self.takedamage = Defines.DAMAGE_NO
    buttonFire(self, game)
}
