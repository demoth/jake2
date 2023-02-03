package org.demoth

abstract class State(val name: String) {

    abstract fun canEnter(): Boolean
    abstract fun canExit(): Boolean
    abstract fun enter()
    abstract fun update()
    abstract fun exit(): Boolean
}

class StateMachine(var currentState: State) {
    fun update() {
        currentState.update()
    }

    fun attemptStateChange(newState: State) {
        if (currentState.canExit() && newState.canEnter()) {
            currentState.exit()
            currentState = newState
            newState.enter()
        }
    }
}
