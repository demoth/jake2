package jake2.qcommon.math

import org.junit.Assert.assertEquals
import org.junit.Test

class Vector3fTest {

    @Test
    fun slerpTest() {
        val a = Vector3f(1f, 0f, 0f)
        val b = Vector3f(0f, 1f, 0f)

        // Test t = 0
        val result1 = a.slerp(b, 0f)
        assertEquals(a, result1)

        // Test t = 1
        val result2 = a.slerp(b, 1f)
        assertEquals(b, result2)

        // Test t = 0.5
        val expected = Vector3f(0.5f, 0.5f, 0f).normalize()
        val result3 = a.slerp(b, 0.5f)
        assertEquals(expected, result3)
    }
}
