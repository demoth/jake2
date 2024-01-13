package jake2.game.character


enum class StateType {
    IDLE,
    MOVEMENT,
    ATTACK,
    PAIN,
    DEAD,
}

abstract class State(
    val name: String,
    val eventProcessor: AnimationEventProcessor,
    val nextState: String? = null,
    val type: StateType
) {

    abstract val currentFrame: Int // meh.. need to rethink the applicability of OOP here

    open fun enter() {}

    /**
     * returns a pair of next state name and the set of events (passed to the BT)
     */
    open fun update(time: Float): Pair<String?, Collection<String>> = null to emptySet()

    open fun exit() = true
}