package org.demoth.cake.testing

import com.badlogic.gdx.Gdx
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

@EnabledIfEnvironmentVariable(named = "CAKE_VIDEO_GL_TESTS", matches = "1")
class HiddenGlTestGlTest {
    @TempDir lateinit var temp: Path

    @Test fun resumesAfterRenderingAndPreservesFailureCleanupAndHomeIsolation() {
        val home = System.getProperty("user.home")
        val expected = AssertionError("Scenario assertion")
        val cleanupFailure = IllegalStateException("Cleanup failure")
        val cleanups = mutableListOf<String>()
        var rendering = false
        var renders = 0
        val result = CompletableFuture<Unit>()
        val actual = assertThrows(AssertionError::class.java) {
            HiddenGlTest.run(home = temp) {
                onCleanup {
                    cleanups += "first"
                    assertEquals(temp.toString(), System.getProperty("user.home"))
                }
                onCleanup { cleanups += "second"; throw cleanupFailure }
                renderWith {
                    rendering = true
                    renders++
                    result.complete(Unit)
                    rendering = false
                }
                await(result)
                assertFalse(rendering, "Assertions resume after the render callback returns")
                assertEquals(1, renders)
                throw expected
            }
        }
        assertSame(expected, actual)
        assertTrue(actual.suppressed.contains(cleanupFailure))
        assertEquals(listOf("second", "first"), cleanups)
        assertEquals(home, System.getProperty("user.home"))
    }

    @Test fun unfinishedSequencesTimeOutAndCancelPendingWork() {
        val result = CompletableFuture<Unit>()
        var cleaned = false
        val failure = assertThrows(IllegalStateException::class.java) {
            HiddenGlTest.run(timeoutSeconds = 1) {
                onCleanup { cleaned = true }
                await(result)
            }
        }
        assertTrue(failure.message!!.contains("timed out"))
        assertTrue(result.isCancelled)
        assertTrue(cleaned)
    }

    @Test fun applicationExitCannotReportAnUnfinishedScenarioAsSuccess() {
        val result = CompletableFuture<Unit>()
        val failure = assertThrows(IllegalStateException::class.java) {
            HiddenGlTest.run {
                renderWith { Gdx.app.exit() }
                await(result)
            }
        }
        assertTrue(failure.message!!.contains("before the scenario completed"))
        assertTrue(result.isCancelled)
    }
}
