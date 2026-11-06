package org.demoth.cake.automation

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.Disposable
import jake2.qcommon.Globals
import jake2.qcommon.exec.Cbuf
import jake2.qcommon.exec.Cmd
import org.demoth.cake.ui.menu.MenuScreen
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture

/** A frame-driven executor for the existing command language, independent of global Cbuf. */
class AutomationRunner(
    private val host: AutomationHost,
    private val exit: () -> Unit = { Gdx.app.exit() },
    private val nanoTime: () -> Long = System::nanoTime,
) : Disposable {
    private data class Step(val description: String, val execute: () -> Unit)
    private data class Wait(val description: String, val deadline: Long, val ready: () -> Boolean)
    private class Run(val exitOnComplete: Boolean) {
        val steps = ArrayDeque<Step>()
        val result = CompletableFuture<Unit>()
        var waiting: Wait? = null
        var capture: CompletableFuture<FileHandle>? = null
        var location = "<automation>"
    }

    private var active: Run? = null
    private var registered = false
    private var executing = false
    private var renderedFrames = 0L
    private var commandDirectory = Path.of(".").toAbsolutePath().normalize()
    private val operations = listOf("automation_open", "automation_click", "automation_await",
        "automation_frames", "automation_capture")
    private val commands = operations + listOf("automation_run", "automation_cancel")
    val isRunning: Boolean get() = active != null
    var lastResult: CompletableFuture<Unit>? = null
        private set

    /** Call before startup commands and at frame boundaries. Handlers also check developer live. */
    fun syncCommands() {
        if (Globals.developer?.typedValue == true) {
            if (registered) return
            operations.forEach { name ->
                Cmd.AddCommand(name, "Developer automation; see automation/README.md") { args ->
                    requireDeveloper()
                    if (executing) perform(args) else {
                        val run = start(false)
                        run.steps.addLast(Step(name) { perform(args) })
                    }
                }
            }
            Cmd.AddCommand("automation_run", "Run a local command script: automation_run <file> [exit]") { args ->
                require(args.size in 2..3 && (args.size == 2 || args[2] == "exit")) {
                    "Usage: automation_run <file> [exit]"
                }
                runScript(Path.of(args[1]), exitOnComplete = args.size == 3)
            }
            Cmd.AddCommand("automation_cancel", "Cancel the active automation sequence") { args ->
                requireDeveloper()
                require(args.size == 1) { "Usage: automation_cancel" }
                cancel("Cancelled by automation_cancel")
            }
            registered = true
        } else {
            unregister()
            cancel("Automation disabled: developer = 0")
        }
    }

    fun runScript(path: Path, exitOnComplete: Boolean = false): CompletableFuture<Unit> {
        requireDeveloper()
        check(active == null) { "Automation is already running; cancel it first" }
        val absolute = path.toAbsolutePath().normalize()
        return runText(Files.readString(absolute), absolute.toString(), absolute.parent, exitOnComplete)
    }

    fun runText(text: String, source: String = "<inline>", directory: Path = Path.of("."),
                exitOnComplete: Boolean = false): CompletableFuture<Unit> {
        val run = start(exitOnComplete)
        run.steps.addAll(parse(text, source, directory.toAbsolutePath().normalize(), 0))
        return run.result
    }

    /** Test-specific readiness without inventing a runtime expression/assertion language. */
    fun await(description: String, timeoutSeconds: Double = 10.0, ready: () -> Boolean): CompletableFuture<Unit> {
        val run = start(false)
        run.steps.addLast(Step(description) { waitFor(description, timeoutSeconds, ready) })
        return run.result
    }

    /** Once per frame, before the menu pump. Cbuf may execute any number of times independently. */
    fun update() {
        syncCommands()
        val run = active ?: return
        if (run.result.isCancelled) {
            cancel("Automation result cancelled")
            return
        }
        try {
            val waiting = run.waiting
            if (waiting != null) {
                if (!waiting.ready()) {
                    check(nanoTime() < waiting.deadline) { "Timed out waiting for ${waiting.description}" }
                    return
                }
                run.waiting = null
                run.capture = null
            }
            var budget = 256
            while (active === run && run.steps.isNotEmpty() && budget-- > 0) {
                val step = run.steps.removeFirst()
                run.location = step.description
                executing = true
                try { step.execute() } finally { executing = false }
                if (Globals.developer?.typedValue != true) syncCommands()
                if (run.waiting != null) return
            }
            if (active === run && run.steps.isEmpty()) {
                active = null
                run.result.complete(Unit)
                if (run.exitOnComplete) exit()
            }
        } catch (error: Exception) {
            val failure = IllegalStateException("Automation failed at ${run.location}: ${error.message}", error)
            fail(failure)
            throw failure
        }
    }

    /** Call only after a nonzero framebuffer's complete draw, not after command-buffer passes. */
    fun frameRendered() { renderedFrames++ }

    fun fail(error: Throwable) {
        val run = active ?: return
        active = null
        run.capture?.cancel(false)
        run.result.completeExceptionally(error)
    }

    fun cancel(reason: String) {
        val run = active ?: return
        fail(CancellationException(reason))
        if (run.exitOnComplete) error("Automation cancelled: $reason")
    }

    override fun dispose() {
        unregister()
        fail(CancellationException("Application disposed"))
    }

    private fun start(exitOnComplete: Boolean): Run {
        requireDeveloper()
        check(active == null) { "Automation is already running; cancel it first" }
        return Run(exitOnComplete).also { active = it; lastResult = it.result }
    }

    private fun parse(text: String, source: String, directory: Path, depth: Int): List<Step> {
        check(depth <= 32) { "Script/alias expansion exceeded 32 levels" }
        return text.lineSequence().flatMapIndexed { index, line ->
            Cbuf.splitScriptCommandLine(line).asSequence().map { command ->
                Step("$source:${index + 1} ($command)") { dispatch(command, directory, depth) }
            }
        }.toList()
    }

    private fun prepend(text: String, source: String, directory: Path, depth: Int) {
        val steps = parse(text, source, directory, depth)
        steps.asReversed().forEach { active!!.steps.addFirst(it) }
    }

    private fun dispatch(command: String, directory: Path, depth: Int) {
        val args = Cmd.TokenizeString(command, true)
        check(args.isNotEmpty()) { "Command could not be tokenized" }
        when (args[0]) {
            "exec" -> {
                require(args.size == 2) { "Usage: exec <file>" }
                val path = directory.resolve(args[1]).normalize()
                prepend(Files.readString(path), path.toString(), path.parent, depth + 1)
            }
            "wait" -> error("Use automation_frames for rendered-frame waits")
            else -> {
                val alias = if (Cmd.FindCommand(args[0]) == null) Cmd.AliasValue(args[0]) else null
                if (alias != null) prepend(alias, "alias ${args[0]}", directory, depth + 1)
                else {
                    check(!args[0].startsWith("automation_") || Cmd.FindCommand(args[0]) != null) {
                        "Unknown automation command '${args[0]}'"
                    }
                    val previous = commandDirectory
                    commandDirectory = directory
                    try { Cmd.ExecuteString(command) } finally { commandDirectory = previous }
                }
            }
        }
    }

    private fun perform(args: List<String>) {
        when (args[0]) {
            "automation_open" -> {
                require(args.size in 2..3) { "Usage: automation_open <screen> [engine|game|section]" }
                if (args[1] == "ui_sample") host.openAutomationSample(when (args.getOrNull(2)) {
                    null -> null
                    "engine" -> false
                    "game" -> true
                    else -> error("Expected engine or game")
                }) else {
                    val screen = MenuScreen.valueOf(args[1].uppercase(Locale.ROOT))
                    require(args.size == 2 || screen == MenuScreen.OPTIONS_SECTION) { "Screen takes no argument" }
                    host.automationUi.open(screen, args.getOrNull(2))
                }
            }
            "automation_click" -> {
                require(args.size == 2) { "Usage: automation_click <id>" }
                host.automationUi.click(args[1])
            }
            "automation_frames" -> {
                require(args.size in 2..3) { "Usage: automation_frames <count> [timeoutSeconds]" }
                val count = args[1].toLong()
                require(count > 0) { "Frame count must be positive" }
                val target = Math.addExact(renderedFrames, count)
                waitFor("$count rendered frames", timeout(args.getOrNull(2))) { renderedFrames >= target }
            }
            "automation_await" -> condition(args)
            "automation_capture" -> {
                require(args.size in 2..4) { "Usage: automation_capture <file> [presentation|final] [timeoutSeconds]" }
                val layer = CaptureLayer.valueOf((args.getOrNull(2) ?: "presentation").uppercase(Locale.ROOT))
                val duration = timeout(args.getOrNull(3))
                val capture = host.automationCaptures.request(FileHandle(commandDirectory.resolve(args[1]).normalize().toFile()), layer)
                active!!.capture = capture
                waitFor("capture ${args[1]}", duration) {
                    if (!capture.isDone) false else { capture.join(); true }
                }
            }
            else -> error("Unknown automation operation ${args[0]}")
        }
    }

    private fun condition(args: List<String>) {
        when (args.getOrNull(1)) {
            "game_ready" -> {
                require(args.size in 2..3) { "Usage: automation_await game_ready [timeoutSeconds]" }
                waitFor("game presentation", timeout(args.getOrNull(2))) { host.isGamePresentationReady }
            }
            "screen" -> {
                require(args.size in 3..4) { "Usage: automation_await screen <screen> [timeoutSeconds]" }
                require(args[2] in MenuScreen.entries.map { it.name.lowercase(Locale.ROOT) } + listOf("ui_sample", "console")) {
                    "Unknown screen '${args[2]}'"
                }
                waitFor("screen ${args[2]}", timeout(args.getOrNull(3))) { host.presentedAutomationScreen == args[2] }
            }
            "element" -> {
                require(args.size in 4..5 && args[3] in listOf("visible", "enabled")) {
                    "Usage: automation_await element <id> <visible|enabled> [timeoutSeconds]"
                }
                waitFor("element ${args[2]} ${args[3]}", timeout(args.getOrNull(4))) {
                    if (args[3] == "visible") host.automationUi.isVisible(args[2]) else host.automationUi.isEnabled(args[2])
                }
            }
            else -> error("Expected screen, element or game_ready condition")
        }
    }

    private fun timeout(value: String?): Double = (value?.toDouble() ?: 10.0).also {
        require(it.isFinite() && it > 0 && it <= 86400) { "Timeout must be positive and at most 86400 seconds" }
    }

    private fun waitFor(description: String, seconds: Double, ready: () -> Boolean) {
        timeout(seconds.toString())
        active!!.waiting = Wait(description, nanoTime() + (seconds * 1_000_000_000L).toLong(), ready)
    }

    private fun requireDeveloper() {
        check(Globals.developer?.typedValue == true) { "Automation requires developer = 1" }
    }

    private fun unregister() {
        if (registered) commands.forEach(Cmd::RemoveCommand)
        registered = false
    }
}
