package org.demoth.cake.stages.console

import jake2.qcommon.Com
import jake2.qcommon.Globals
import jake2.qcommon.MainCommon
import jake2.qcommon.exec.Cvar
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
        val previousLogLevel = Globals.logLevel
        val sink = Com.ConsoleSink { level, text -> output += ConsoleEntry(level, text) }
        Globals.logLevel = MainCommon.registerLogLevel(Cvar()).also { it.set(Com.ConsoleLevel.DEBUG) }
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
            Globals.logLevel = previousLogLevel
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
