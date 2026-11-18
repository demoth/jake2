package org.demoth.cake.stages.console

import jake2.qcommon.Com
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConsoleBufferTest {
    @Test
    fun fragmentsCoalesceOnlyWithinTheSameLevel() {
        val buffer = ConsoleBuffer()
        buffer.append(Com.ConsoleLevel.DEBUG, "first ")
        buffer.append(Com.ConsoleLevel.DEBUG, "continued\n")
        buffer.append(Com.ConsoleLevel.DEBUG, "another")
        buffer.append(Com.ConsoleLevel.PRINT, "command result\n")

        assertEquals(
            listOf(
                ConsoleEntry(Com.ConsoleLevel.DEBUG, "first continued\n"),
                ConsoleEntry(Com.ConsoleLevel.DEBUG, "another"),
                ConsoleEntry(Com.ConsoleLevel.PRINT, "command result\n"),
            ),
            buffer.entries(),
        )
    }

    @Test
    fun javaEntryPointsAcceptKotlinMessages() {
        val output = mutableListOf<ConsoleEntry>()
        val previousDeveloper = jake2.qcommon.Globals.developer
        val sink = Com.ConsoleSink { level, text -> output += ConsoleEntry(level, text) }
        jake2.qcommon.Globals.developer = jake2.qcommon.exec.Cvar().register(
            "developer", jake2.qcommon.exec.CvarTypes.BOOLEAN, true,
        )
        Com.SetConsoleSink(sink)
        try {
            Com.Print("fragment", false)
            Com.Info("initialized")
            Com.Debug("details")
            Com.Warn("recoverable")
            Com.Error("failed")
            assertEquals(
                listOf("fragment", "initialized\n", "details\n", "recoverable\n", "failed\n"),
                output.map { it.text },
            )
        } finally {
            Com.ClearConsoleSink(sink)
            jake2.qcommon.Globals.developer = previousDeveloper
        }
    }

    @Test
    fun carriageReturnTerminatesAnEntryJustLikeNewline() {
        val buffer = ConsoleBuffer()
        buffer.append(Com.ConsoleLevel.PRINT, "first\r")
        buffer.append(Com.ConsoleLevel.PRINT, "second\r\n")
        buffer.append(Com.ConsoleLevel.PRINT, "third\n")
        assertEquals(listOf("first\r", "second\r\n", "third\n"), buffer.entries().map { it.text })
    }
}
