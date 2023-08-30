package jake2.game.character

fun interface AnimationEventProcessor {
    fun process(events: Collection<String>)
}