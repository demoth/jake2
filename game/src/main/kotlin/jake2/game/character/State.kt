package jake2.game.character

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