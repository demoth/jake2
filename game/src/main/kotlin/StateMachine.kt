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

class StateMachine(var currentState: State) {
    fun update(time: Float) {
        val nextState = currentState.update(time)
        if (nextState != null)
            attemptStateChange(nextState)
    }

    fun attemptStateChange(newState: State): Boolean {
        if (currentState.canExit() && newState.canEnter()) {
            currentState.exit()
            currentState = newState
            newState.enter()
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