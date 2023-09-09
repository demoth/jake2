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
    open fun update(time: Float): String? = null
    open fun exit() = true
}