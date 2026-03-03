package jake2.game

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SnapToEightsTest {

    private val testData = mapOf(
        0f to 0f,
        0.02f to 0f,
        0.05f to 0f,
        0.07f to 0.125f,
        0.18f to 0.125f,
        0.19f to 0.25f,
        -0.18f to -0.125f,
        -0.19f to -0.25f
    )

    @Test
    fun testSnapToEights() {
        testData.entries.forEach {
            assertEquals(it.value, it.key.snapToEights(), "${it.key}.snapToEights() -> ${it.value}")
        }


    }
}
