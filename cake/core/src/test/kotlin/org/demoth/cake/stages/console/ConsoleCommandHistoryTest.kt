package org.demoth.cake.stages.console

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConsoleCommandHistoryTest {
    @Test
    fun upAndDownNavigateHistoryAndRestoreDraft() {
        val history = ConsoleCommandHistory(maxSize = 10)

        history.recordSubmitted("connect localhost")
        history.recordSubmitted("map demo1")

        assertEquals("map demo1", history.navigate(direction = -1, currentInput = "dr") )
        assertEquals("connect localhost", history.navigate(direction = -1, currentInput = "ignored"))
        assertEquals("map demo1", history.navigate(direction = 1, currentInput = "ignored"))
        assertEquals("dr", history.navigate(direction = 1, currentInput = "ignored"))
    }

    @Test
    fun commandsStartingWithSpaceAreNotPersisted() {
        val history = ConsoleCommandHistory(maxSize = 10)

        assertFalse(history.recordSubmitted(" secret"))
        assertFalse(history.recordSubmitted("   "))
        assertTrue(history.recordSubmitted("status"))

        assertEquals(listOf("status"), history.entries())
    }

    @Test
    fun replaceEntriesKeepsLastFiveHundredPersistableCommands() {
        val history = ConsoleCommandHistory()

        history.replaceEntries(
            listOf(" hidden", "")
                .plus((1..505).map { "cmd-$it" }),
        )

        assertEquals(500, history.entries().size)
        assertEquals("cmd-6", history.entries().first())
        assertEquals("cmd-505", history.entries().last())
    }

    @Test
    fun replaceEntriesResetsNavigationToNewHistory() {
        val history = ConsoleCommandHistory(maxSize = 10)

        history.recordSubmitted("first")
        assertEquals("first", history.navigate(direction = -1, currentInput = "draft"))

        history.replaceEntries(listOf("second"))

        assertEquals("second", history.navigate(direction = -1, currentInput = "fresh"))
        assertEquals("fresh", history.navigate(direction = 1, currentInput = "ignored"))
    }

    @Test
    fun navigateReturnsNullWhenHistoryIsEmpty() {
        val history = ConsoleCommandHistory()

        assertNull(history.navigate(direction = -1, currentInput = "draft"))
    }
}
