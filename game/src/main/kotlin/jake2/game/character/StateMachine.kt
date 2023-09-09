package jake2.game.character

class StateMachine(
    states: Collection<State>,
    private val transitionRules: StateTransitionRules,
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
            attemptStateChange(nextState, true)
    }

    fun attemptStateChange(nextStateName: String, force: Boolean = false): Boolean {
        if (currentState.name == nextStateName)
            return true

        val nextState = stateMap[nextStateName] ?: throw IllegalStateException("state $nextStateName is not found!")
        if (force || transitionRules.transitionAllowed(currentState.type, nextState.type)) {
            currentState.exit()
            currentState = nextState
            nextState.enter()
            return true
        }
        return false
    }
}

