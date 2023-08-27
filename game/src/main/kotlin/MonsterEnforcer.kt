package org.demoth

import jake2.qcommon.math.Vector3f
import kotlin.random.Random


fun createSequences(name: String): Collection<AnimationSequence> {
    // hardcoded or parsed from json file or something
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

    // other properties follow

    private val stateMachine = StateMachine(
        createSequences(name).map {
            // other possible states?
            AnimationSequenceState(it.name, it, this)
            // "stand" state should have a random chance to transition into the "fidget" state
        }
    )

    override fun process(events: Collection<String>) {
        events.forEach {
            when (it) {
                "fire" -> {
                    // GameLogic.createProjectile(...)
                }
                "fart" -> {
                    // make funny sound
                }
                "try-fidget" -> {
                    if (Random.nextFloat() < 0.15f)
                        stateMachine.attemptStateChange("fidget")
                }
                else -> {
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