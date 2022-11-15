package jake2.game

import jake2.game.adapters.SuperAdapter.Companion.registerThink
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
    val dist: Float = absMoveDir[0] * self.size[0] 
        + absMoveDir[1] * self.size[1] 
        + absMoveDir[2] * self.size[2] - self.st.lip
    Math3D.VectorMA(self.pos1, dist, self.movedir, self.pos2)

    self.use = GameFunc.button_use
    self.s.effects = self.s.effects or Defines.EF_ANIM01

    if (self.health != 0) {
        self.max_health = self.health
        self.die = GameFunc.button_killed
        self.takedamage = Defines.DAMAGE_YES
    } else if (self.targetname == null)
        self.touch = GameFunc.button_touch

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

