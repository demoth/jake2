package jake2.game

import jake2.game.adapters.SuperAdapter.Companion.registerThink
import jake2.qcommon.Defines
import jake2.qcommon.util.Lib
import jake2.qcommon.util.Math3D

val train = registerThink("func_train") { self, game ->
    self.movetype = GameDefines.MOVETYPE_PUSH

    Math3D.VectorClear(self.s.angles)
    self.blocked = GameFunc.train_blocked
    if (self.spawnflags and GameFunc.TRAIN_BLOCK_STOPS != 0) {
        self.dmg = 0
    } else if (self.dmg == 0) {
        self.dmg = 100
    }
    self.solid = Defines.SOLID_BSP
    game.gameImports.setmodel(self, self.model)

    if (self.st.noise != null)
        self.moveinfo.sound_middle = game.gameImports.soundindex(self.st.noise)

    if (self.speed == 0f)
        self.speed = 100f

    self.moveinfo.speed = self.speed

    self.moveinfo.accel = self.moveinfo.speed
    self.moveinfo.decel = self.moveinfo.speed

    self.use = GameFunc.train_use

    game.gameImports.linkentity(self)

    if (self.target != null) {
        // start trains on the second frame, to make sure their targets have had a chance to spawn
        self.think.nextTime = game.level.time + Defines.FRAMETIME
        self.think.action = GameFunc.func_train_find
    } else {
        game.gameImports.dprintf("func_train without a target at ${Lib.vtos(self.absmin)}\n")
    }
    true

}
