package jake2.game.character

open class AnimationSequenceState(
    name: String,
    private val animationSequence: AnimationSequence,
    eventProcessor: AnimationEventProcessor,
    nextState: String?,
    type: StateType
) : State(name, eventProcessor, nextState, type) {

    override fun enter() {
        val zeroEvent = animationSequence.reset() ?: return
        eventProcessor.process(listOf(zeroEvent))
    }

    override fun update(time: Float): Pair<String?, Collection<String>> {
        val events = animationSequence.update(time)
        eventProcessor.process(events)
        if (animationSequence.finished)
            return nextState to events

        return null to emptySet()
    }

    override val currentFrame: Int
        get() = animationSequence.currentFrame
}