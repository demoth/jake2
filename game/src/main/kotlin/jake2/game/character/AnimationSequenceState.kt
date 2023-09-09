package jake2.game.character

open class AnimationSequenceState(
    name: String,
    private val animationSequence: AnimationSequence,
    eventProcessor: AnimationEventProcessor,
    nextState: String?,
    type: StateType
) : State(name, eventProcessor, nextState, type) {

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