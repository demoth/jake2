package org.demoth

fun interface AnimationEventProcessor {
    fun process(events: Collection<String>)
}

abstract class State(
    val name: String,
    val eventProcessor: AnimationEventProcessor,
    val nextState: String? = null
) {

    open fun canEnter() = true
    open fun canExit() = true
    open fun enter() {}
    open fun update(time: Float): String? = null
    open fun exit() = true
}

open class AnimationSequenceState(
    name: String,
    val animationSequence: AnimationSequence,
    eventProcessor: AnimationEventProcessor
) : State(name, eventProcessor) {
    override fun canExit(): Boolean {
        // if it's pain animation: problem: cannot die
        return animationSequence.finished
    }

    override fun enter() {
        animationSequence.reset()
    }

    override fun update(time: Float): String? {
        val events = animationSequence.update(time)
        eventProcessor.process(events)
        if (animationSequence.finished)
            return nextState

        return null
    }
}

class StateMachine(
    states: Collection<State>,
    initialState: String = "idle"
) {
    private val stateMap: Map<String, State>
    private var currentState: State
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
    var transitionMap: Map<String, Map<String, () -> Boolean>>, // can or should????
) {
    var currentState = states.first()

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