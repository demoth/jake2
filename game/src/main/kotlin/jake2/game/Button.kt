package jake2.game

import jake2.game.adapters.SuperAdapter.Companion.registerDie
import jake2.game.adapters.SuperAdapter.Companion.registerThink
import jake2.game.adapters.SuperAdapter.Companion.registerTouch
import jake2.game.adapters.SuperAdapter.Companion.registerUse
import jake2.qcommon.Defines
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
val button = registerThink("func_button") { self, game ->

    GameBase.G_SetMovedir(self.s.angles, self.movedir)
    self.movetype = GameDefines.MOVETYPE_STOP
    self.solid = Defines.SOLID_BSP
    game.gameImports.setmodel(self, self.model)

    if (self.sounds != 1)
        self.moveinfo.sound_start = game.gameImports.soundindex("switches/butn2.wav")

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

    self.moveinfo.state = GameFunc.STATE_BOTTOM

    self.moveinfo.speed = self.speed
    self.moveinfo.accel = self.accel
    self.moveinfo.decel = self.decel
    self.moveinfo.wait = self.wait

    Math3D.VectorCopy(self.pos1, self.moveinfo.start_origin)
    Math3D.VectorCopy(self.s.angles, self.moveinfo.start_angles)
    Math3D.VectorCopy(self.pos2, self.moveinfo.end_origin)
    Math3D.VectorCopy(self.s.angles, self.moveinfo.end_angles)

    game.gameImports.linkentity(self)
    true
}

private val buttonUse = registerUse("button_use") { self, other, activator, game ->
    self.activator = activator
    buttonFire(self, game)
}

private fun buttonFire(self: SubgameEntity, game: GameExportsImpl): Boolean {
    if (self.moveinfo.state == GameFunc.STATE_UP || self.moveinfo.state == GameFunc.STATE_TOP)
        return true
    self.moveinfo.state = GameFunc.STATE_UP
    if (self.moveinfo.sound_start != 0 && self.flags and GameDefines.FL_TEAMSLAVE == 0)
        game.gameImports.sound(
        self, Defines.CHAN_NO_PHS_ADD
                + Defines.CHAN_VOICE, self.moveinfo.sound_start, 1f,
        Defines.ATTN_STATIC.toFloat(), 0f
    )
    GameFunc.Move_Calc(self, self.moveinfo.end_origin, buttonWait, game)
    return true
}

private val buttonWait = registerThink("button_wait") { self, game ->
    self.moveinfo.state = GameFunc.STATE_TOP

    // EF_ANIM01 -> EF_ANIM23
    self.s.effects = self.s.effects and Defines.EF_ANIM01.inv()
    self.s.effects = self.s.effects or Defines.EF_ANIM23

    GameUtil.G_UseTargets(self, self.activator, game)
    self.s.frame = 1
    if (self.moveinfo.wait >= 0) {
        self.think.nextTime = game.level.time + self.moveinfo.wait
        self.think.action = buttonReturn
    }
    true
}

private val buttonReturn = registerThink("button_return") { self, game ->
    self.moveinfo.state = GameFunc.STATE_DOWN

    GameFunc.Move_Calc(self, self.moveinfo.start_origin, buttonDone, game)

    self.s.frame = 0

    if (self.health != 0) {
        // why not max_health? - because by this time health is already reset to max_health
        self.takedamage = Defines.DAMAGE_YES
    }
    true
}

private val buttonDone = registerThink("button_done") { self, game ->
    self.moveinfo.state = GameFunc.STATE_BOTTOM
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
