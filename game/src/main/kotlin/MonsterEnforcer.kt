package org.demoth

import jake2.qcommon.math.Vector3f


fun readSequences(): Collection<AnimationSequence> {
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
class GameCharacter : AnimationEventProcessor {

    private var health = 100f
    // other properties follow

    private val stateMachine: StateMachine = StateMachine(
        readSequences().map {
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
        } else if (amount / health > 0.5f) {
            // pain state will automatically transition to the "stand" state in the end of animation
            stateMachine.attemptStateChange("pain")
        }
    }

    //
    // these commands are called either by AI or a Player
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
            // transitions to "stand" once hit the ground
        }
    }

    fun attack() {
        stateMachine.attemptStateChange("attack")
    }

}