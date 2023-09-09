package jake2.game.character

/**
 * Responsible for receiving and processing events, emitted from an animation sequence
 */
fun interface AnimationEventProcessor {
    fun process(events: Collection<String>)
}