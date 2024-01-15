package jake2.game.character

/**
 * Responsible for checking if a certain state transition is allowed.
 * Automatic transitions (like from PAIN -> IDLE) are not subject to such checks.
 */
fun interface StateTransitionRules {
    fun transitionAllowed(from: StateType, to: StateType): Boolean
}