package org.demoth

import java.lang.Integer.min

private const val FRAME_TIME = 0.1f

/**
 * Describes a certain action or movement
 */
data class AnimationSequence(

    val name: String,

    /*
    List of frame indices, which current animation sequence loops through.
    Frames are separated with 0.1s time
    */
    val frames: List<Int>,

    // frame -> event name
    val events: Map<Int, String>,
    val loop: Boolean,
) {

    var currentSpeed: Float = 1f // time multiplier

    var finished = false // for non looped sequences
    private var currentFrameIndex = 0
    private var interFrameTime = 0f // for inter frame interpolation between 0, and 0.1 (FRAME_TIME)

    val currentFrame: Int
        get() = frames[currentFrameIndex]


    fun reset() {
        currentSpeed = 1f
        currentFrameIndex = 0
        interFrameTime = 0f
        finished = false
    }


    /**
     * @param time - elapsed time, can cover multiple frames. In this case return all events associated with skipped frames
     * @return events, emitted during the update
     */
    fun update(time: Float): Collection<String>  {
        if (finished)
            return emptyList()

        var eventsThisUpdate: MutableList<String>? = null

        val updateTime = interFrameTime + time * currentSpeed // Adjust time for animation speed

        // Determine how many frames we move given the elapsed time
        val framesToMove = (updateTime / FRAME_TIME).toInt()

        for (i in 1..framesToMove) {
            // Get next frame index, loop if needed
            val nextFrameIndex = (currentFrameIndex + i) % frames.size

            // Avoid eager list creation because mostly result will be empty
            events[nextFrameIndex]?.let {
                if (eventsThisUpdate == null) {
                    eventsThisUpdate = mutableListOf(it)
                } else {
                    // If we have an event for the next frame, add it to the collection.
                    eventsThisUpdate!!.add(it)
                }
            }

            // If the sequence is not looping, and we reach the end, mark it as finished
            if (nextFrameIndex == frames.size - 1 && !loop) {
                finished = true
                break
            }
        }

        // Adjust the inter frame time and current frame index
        interFrameTime = updateTime % FRAME_TIME
        currentFrameIndex = if (loop)
                (currentFrameIndex + framesToMove) % frames.size
        else
            min(currentFrameIndex + framesToMove, frames.size - 1) // for non loop should be the last

        return eventsThisUpdate ?: emptyList()
    }
}