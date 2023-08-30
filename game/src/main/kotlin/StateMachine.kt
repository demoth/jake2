package org.demoth

import kotlin.random.Random

fun interface AnimationEventProcessor {
    fun process(events: Collection<String>)
}

abstract class State(
    val name: String,
    val eventProcessor: AnimationEventProcessor,
    val nextState: String? = null
) {

    abstract val currentFrame: Int // meh.. need to rethink the applicability of OOP here

    open fun canEnter() = true
    open fun canExit() = true
    open fun enter() {}
    open fun update(time: Float): String? = null
    open fun exit() = true
}

open class AnimationSequenceState(
    name: String,
    val animationSequence: AnimationSequence,
    eventProcessor: AnimationEventProcessor,
    nextState: String? = null
) : State(name, eventProcessor, nextState) {

    override fun canExit() = true

    override fun enter() = animationSequence.reset()

    override fun update(time: Float): String? {
        val events = animationSequence.update(time)
        eventProcessor.process(events)
        if (animationSequence.finished)
            return nextState

        return null
    }

    override val currentFrame: Int
        get() = animationSequence.currentFrame
}

class StateMachine(
    states: Collection<State>,
    initialState: String = "stand"
) {
    private val stateMap: Map<String, State>
    var currentState: State
    init {
        assert(states.isNotEmpty())
        stateMap = states.associateBy { it.name }
        currentState = stateMap[initialState] ?: throw IllegalArgumentException("initial state $initialState is not found!")
    }


    fun update(time: Float) {
        val nextState = currentState.update(time)
        if (nextState != null)
            attemptStateChange(nextState)
    }

    fun attemptStateChange(nextStateName: String): Boolean {
        if (currentState.name == nextStateName)
            return true

        val nextState = stateMap[nextStateName] ?: throw IllegalStateException("state $nextStateName is not found!")
        if (currentState.canExit() && nextState.canEnter()) {
            currentState.exit()
            currentState = nextState
            nextState.enter()
            return true
        }
        return false
    }
}

class StateMachine2(
    var states: Collection<State>,
    // from, to, check
    var transitionMap: MutableMap<String, Map<String, () -> Boolean>>, // should
) {
    var currentState = states.first()

    init {
        transitionMap["stand"] = mapOf(
            "fidget" to { Random.nextFloat() < 0.15f } // cooldown, reset on enter stand
        )
    }

    fun attemptChange(name: String) {
        // check rules
    }
    fun update(time: Float) {
        currentState.update(time)
        val targetStates = transitionMap[currentState.name] ?: return
        targetStates.forEach { (stateName, func) ->
            if (func.invoke()) {
                attemptChange(stateName)
                return@forEach
            }
        }
    }
}
// ai wants to walk, currently stunned
// ai wants to walk, currently idle