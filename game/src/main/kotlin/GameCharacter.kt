package org.demoth

import jake2.game.GameDefines
import jake2.game.GameExportsImpl
import jake2.game.SubgameEntity
import jake2.game.adapters.SuperAdapter.Companion.registerThink
import jake2.game.components.ThinkComponent
import jake2.qcommon.Defines
import jake2.qcommon.math.Vector3f
import jake2.qcommon.util.Math3D
import kotlin.random.Random


fun createSequences(name: String): Collection<AnimationSequence> {
    // hardcoded or parsed from json file or something
    if (name == "enforcer")
        return listOf(
            AnimationSequence(
                name = "stand",
                frames = (50..71).toList(),
                events = mapOf(1 to "try-fidget"),
                loop = true
            ),
            AnimationSequence(
                name = "fidget",
                frames = (1..49).toList(),
                events = emptyMap(),
                loop = false,
                nextState = "stand"
            )
        )
    TODO("Not yet implemented")
}

/*
Enforcer states:

stand
fidget
walk
run
pain1
pain2
duck
death1
death2
death3
attack1
attack2

 */
class GameCharacter(name: String) : AnimationEventProcessor {

    private var health = 100f
    private var stunThreshold = 0.5f
    private var stunTime = 1f
    val currentFrame: Int
        get() = stateMachine.currentState.currentFrame


    // other properties follow

    private val stateMachine = StateMachine(
        createSequences(name).map {
            // other possible states?
            AnimationSequenceState(it.name, it, this, it.nextState)
        }
    )

    override fun process(events: Collection<String>) {
        events.forEach {
            println("processing event: $it")
            when (it) {
                "fire" -> {
                    // GameLogic.createProjectile(...)
                }

                "fart" -> {
                    // make funny sound
                }

                "try-fidget" -> {
                    if (Random.nextFloat() < 0.5f)
                        stateMachine.attemptStateChange("fidget")
                }

                else -> {
                    println("unexpected event: $it")
                    // whatever
                }
            }
        }
    }

    fun update(time: Float) = stateMachine.update(time)

    // called by the GameLogic
    fun applyDamage(amount: Float) {
        health -= amount
        if (health < 0f) {
            stateMachine.attemptStateChange("dead") // hesdeadjim
            // GameLogic.die(..)
        } else {
            if (amount / health > stunThreshold) {
                // pain state will automatically transition to the "stand" state in the end of animation.
                // fixme: what if we want pain to take less time (adjust stun time)
                stateMachine.attemptStateChange("pain")
            }
        }
    }

    //
    // these commands are called either by AI or a Player.
    // could be called continuously
    //
    fun aim(to: Vector3f) {
        TODO()
    }

    fun walk() {
        if (stateMachine.attemptStateChange("walk")) {
            // GameLogic.moveCharacter(...)
            // fixme: if called continuously, continue the same animation sequence
        }
    }

    fun jump() {
        if (stateMachine.attemptStateChange("jump")) {
            // GameLogic.tossCharacter(...)
            // transitions to "stand" once hit the ground // todo: where is this code?
        }
    }

    fun attack() {
        stateMachine.attemptStateChange("attack")
    }

}


fun spawnNewMonster(self: SubgameEntity, game: GameExportsImpl) {

    val entity = game.G_Spawn()
    self.movetype = GameDefines.MOVETYPE_STEP
    self.solid = Defines.SOLID_BBOX
    self.s.modelindex = game.gameImports.modelindex("models/monsters/infantry/tris.md2")
    self.svflags = self.svflags or Defines.SVF_MONSTER
    self.s.renderfx = self.s.renderfx or Defines.RF_FRAMELERP
    self.clipmask = Defines.MASK_MONSTERSOLID
    self.s.skinnum = 0
    self.deadflag = GameDefines.DEAD_NO
    self.svflags = self.svflags and Defines.SVF_DEADMONSTER.inv()

    Math3D.VectorSet(self.mins, -16f, -16f, -24f)
    Math3D.VectorSet(self.maxs, 16f, 16f, 32f)

    self.character = GameCharacter("enforcer") // new stuff!!

    self.think = ThinkComponent().apply {
        nextTime = game.level.time + Defines.FRAMETIME
        action = registerThink("") { self, game ->
            self.character.update(Defines.FRAMETIME) // YES!
            self.s.frame = self.character.currentFrame
            self.think.nextTime = game.level.time + Defines.FRAMETIME
            true
        }
    }

    game.gameImports.linkentity(entity)

}