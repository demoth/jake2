package org.demoth.cake.automation

import jake2.qcommon.Globals
import jake2.qcommon.exec.Cbuf
import jake2.qcommon.exec.Cmd
import org.demoth.cake.CakeStartupBootstrap
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class AutomationRunnerTest {
    @TempDir lateinit var temp: Path
    private class Host : AutomationHost {
        override val automationUi = AutomationUi({ null }, {})
        override val automationCaptures = AutomationCaptures()
        override var presentedAutomationScreen: String? = null
        override var isGamePresentationReady = false
        var sampleMode: Boolean? = null
        override fun openAutomationSample(game: Boolean?) { sampleMode = game }
    }
    private lateinit var host: Host
    private lateinit var runner: AutomationRunner
    private var clock = 0L
    private var exits = 0
    private var developer = false
    private val observed = mutableListOf<String>()

    @BeforeEach fun setup() {
        CakeStartupBootstrap.ensureInitialized()
        developer = Globals.developer.typedValue
        Globals.developer.forceSet(true)
        host = Host()
        runner = AutomationRunner(host, exit = { exits++ }, nanoTime = { clock })
        runner.syncCommands()
        Cmd.AddCommand("runner_probe") { observed += it[1] }
    }

    @AfterEach fun cleanup() {
        runner.dispose()
        host.automationCaptures.cancelAll("Test finished")
        Cmd.RemoveCommand("runner_probe")
        Cmd.RemoveAlias("runner_testalias")
        Cmd.RemoveAlias("runner_loop")
        Globals.developer.forceSet(developer)
    }

    @Test fun frameWaitsDoNotHoldGlobalCommandsOrAdvanceOnRepeatedCommandPasses() {
        val result = runner.runText("runner_probe first; automation_frames 2; runner_probe second")
        runner.update()
        assertEquals(listOf("first"), observed)
        Cbuf.AddText("runner_probe global")
        repeat(20) { Cbuf.Execute(); runner.update() }
        assertEquals(listOf("first", "global"), observed)
        runner.frameRendered()
        runner.update()
        assertFalse(result.isDone)
        runner.frameRendered()
        runner.update()
        assertTrue(result.isDone)
        result.join()
        runner.update()
        assertEquals(listOf("first", "global", "second"), observed)
        assertEquals(0, exits)
    }

    @Test fun nestedScriptsAliasesMacrosQuotesAndCommentsStayInTheAutomationQueue() {
        Files.writeString(temp.resolve("nested child.cfg"), """
            runner_probe "child; // value"
            automation_frames 1
            runner_probe child-done
        """.trimIndent())
        val root = temp.resolve("root.cfg")
        Files.writeString(root, """
            // comment; runner_probe unexpected
            set runner_testvalue 7
            alias runner_testalias "runner_probe alias-start; automation_frames 1; runner_probe alias-end"
            runner_testalias
            exec "nested child.cfg"
            runner_probe ${'$'}runner_testvalue
        """.trimIndent())
        Cmd.ExecuteString("automation_run \"$root\"")
        val result = runner.lastResult!!
        runner.update()
        assertEquals(listOf("alias-start"), observed)
        assertEquals("", Cbuf.contents())
        runner.frameRendered()
        runner.update()
        assertEquals(listOf("alias-start", "alias-end", "child; // value"), observed)
        assertEquals("", Cbuf.contents())
        runner.frameRendered()
        runner.update()
        assertTrue(result.isDone)
        result.join()
        assertEquals(listOf("alias-start", "alias-end", "child; // value", "child-done", "7"), observed)
    }

    @Test fun runtimeReadinessAndKotlinPredicatesCanStopForAssertionsWithoutCapturing() {
        val result = runner.runText("automation_open ui_sample game; automation_await screen ui_sample; runner_probe ready")
        runner.update()
        assertEquals(true, host.sampleMode)
        runner.update()
        assertFalse(result.isDone)
        host.presentedAutomationScreen = "ui_sample"
        runner.update()
        assertTrue(result.isDone)
        result.join()
        assertEquals(listOf("ready"), observed)

        var health = 0
        val assertionPoint = runner.await("health restored") { health == 41 }
        runner.update()
        health = 41
        runner.update()
        assertTrue(assertionPoint.isDone)
        assertionPoint.join()
        assertEquals(41, health)
    }

    @Test fun timeoutFailsTheSequenceAndDoesNotExecuteFollowingActions() {
        val result = runner.runText("automation_await game_ready 0.001\nrunner_probe unexpected", source = "timeout.cfg")
        runner.update()
        clock = 2_000_000
        val failure = assertThrows(IllegalStateException::class.java) { runner.update() }
        assertTrue(failure.message!!.contains("timeout.cfg:1"))
        assertTrue(failure.message!!.contains("Timed out"))
        assertTrue(result.isCompletedExceptionally)
        assertFalse(runner.isRunning)
        assertTrue(observed.isEmpty())
    }

    @Test fun captureIsABarrierAndTimesOutWithoutRenderedFrames() {
        val file = temp.resolve("not-rendered.png")
        val result = runner.runText("automation_capture \"$file\" presentation 0.001\nrunner_probe unexpected")
        runner.update()
        repeat(10) { Cbuf.Execute(); runner.update() }
        assertFalse(result.isDone)
        assertTrue(observed.isEmpty())
        clock = 2_000_000
        assertThrows(IllegalStateException::class.java) { runner.update() }
        assertTrue(result.isCompletedExceptionally)
        assertFalse(Files.exists(file))
    }

    @Test fun invalidCommandsAndRecursiveExpansionFailWithSourceLocations() {
        var result = runner.runText("automation_typo", source = "typo.cfg")
        assertTrue(assertThrows(IllegalStateException::class.java) { runner.update() }.message!!.contains("typo.cfg:1"))
        assertTrue(result.isCompletedExceptionally)
        result = runner.runText("wait", source = "wait.cfg")
        assertTrue(assertThrows(IllegalStateException::class.java) { runner.update() }.message!!.contains("automation_frames"))
        assertTrue(result.isCompletedExceptionally)
        result = runner.runText("alias runner_loop runner_loop\nrunner_loop")
        assertTrue(assertThrows(IllegalStateException::class.java) { runner.update() }.message!!.contains("32 levels"))
        assertTrue(result.isCompletedExceptionally)
        assertEquals("", Cbuf.contents())
    }

    @Test fun developerModeControlsRegistrationAndCancelsLiveWork() {
        assertNotNull(Cmd.FindCommand("automation_run"))
        val result = runner.await("never ready") { false }
        runner.update()
        Globals.developer.forceSet(false)
        runner.syncCommands()
        assertNull(Cmd.FindCommand("automation_run"))
        assertNull(Cmd.FindCommand("automation_capture"))
        assertTrue(result.isCancelled)
        assertThrows(IllegalStateException::class.java) { runner.runText("runner_probe forbidden") }
        Globals.developer.forceSet(true)
        runner.syncCommands()
        Cmd.ExecuteString("automation_frames 1")
        val interactive = runner.lastResult!!
        runner.update()
        runner.frameRendered()
        runner.update()
        assertTrue(interactive.isDone)
        interactive.join()
    }

    @Test fun batchExitFollowsCompletionAndCancellationIsAnError() {
        val result = runner.runText("automation_frames 1", exitOnComplete = true)
        runner.update()
        assertEquals(0, exits)
        runner.frameRendered()
        runner.update()
        assertTrue(result.isDone)
        assertEquals(1, exits)
        val cancelled = runner.runText("automation_frames 1", exitOnComplete = true)
        assertThrows(IllegalStateException::class.java) { runner.cancel("Test cancelled") }
        assertTrue(cancelled.isCancelled)
        assertFalse(runner.isRunning)
    }
}
