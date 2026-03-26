package org.demoth.cake.stages.console

import com.badlogic.gdx.Input
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import jake2.qcommon.Com
import jake2.qcommon.exec.Cmd
import jake2.qcommon.exec.Cvar

class ConsoleInputController(
    private val input: TextField,
    private val output: ConsoleOutputWidget,
    private val appendOutput: (Com.ConsoleLevel, String) -> Unit,
    private val submitCommand: (String) -> Unit,
    private val history: ConsoleCommandHistory = ConsoleCommandHistory(),
    private val persistHistory: (List<String>) -> Unit = {},
) : InputListener() {
    override fun keyDown(event: InputEvent, keycode: Int): Boolean {
        return when (keycode) {
            Input.Keys.ENTER -> {
                submitCurrentInput()
                true
            }
            Input.Keys.TAB -> {
                completeCurrentInput()
                true
            }
            Input.Keys.UP -> {
                navigateHistory(-1)
                true
            }
            Input.Keys.DOWN -> {
                navigateHistory(1)
                true
            }
            Input.Keys.PAGE_UP -> {
                output.scrollPage(-1)
                true
            }
            Input.Keys.PAGE_DOWN -> {
                output.scrollPage(1)
                true
            }
            else -> false
        }
    }


    private fun submitCurrentInput() {
        val command = input.text
        appendOutput(Com.ConsoleLevel.INFO, "$command\n")
        submitCommand(command)
        if (history.recordSubmitted(command)) {
            persistHistory(history.entries())
        }
        input.text = ""
        input.cursorPosition = 0
    }

    private fun completeCurrentInput() {
        val raw = input.text
        val prefixOffset = if (raw.startsWith("/") || raw.startsWith("\\")) 1 else 0
        val token = raw.substring(prefixOffset)
        if (token.isBlank() || token.any(Char::isWhitespace)) {
            return
        }

        val matches = (
            Cmd.CompleteCommand(token) +
                Cvar.getInstance().CompleteVariable(token)
            )
            .distinct()
            .sorted()

        if (matches.isEmpty()) {
            return
        }

        val leader = raw.substring(0, prefixOffset)
        if (matches.size == 1) {
            input.text = "$leader${matches.first()} "
            input.cursorPosition = input.text.length
            return
        }

        val commonPrefix = longestCommonPrefix(matches)
        if (commonPrefix.length > token.length) {
            input.text = "$leader$commonPrefix"
            input.cursorPosition = input.text.length
        }

        appendOutput(
            Com.ConsoleLevel.INFO,
            buildString {
                append("\nMatches:\n")
                // Print one candidate per line so long cvar values and descriptions stay readable.
                matches.forEach { match ->
                    append(formatMatch(match))
                    append('\n')
                }
            },
        )
    }

    private fun formatMatch(name: String): String {
        val command = Cmd.FindCommand(name)
        if (command != null) {
            val description = command.description
            return if (description.isNullOrBlank()) {
                name
            } else {
                "$name - $description"
            }
        }

        val cvar = Cvar.getInstance().FindVar(name) ?: return name
        return buildString {
            append(name)
            append(" (")
            append(cvar.string)
            append(')')
            if (!cvar.description.isNullOrBlank()) {
                append(' ')
                append(cvar.description)
            }
        }
    }

    private fun navigateHistory(direction: Int) {
        val nextText = history.navigate(direction, input.text) ?: return
        input.text = nextText
        input.cursorPosition = input.text.length
    }

    fun replaceHistory(commands: List<String>) {
        history.replaceEntries(commands)
    }

    private fun longestCommonPrefix(matches: List<String>): String {
        var prefix = matches.first()
        for (candidate in matches.drop(1)) {
            var index = 0
            val maxLength = minOf(prefix.length, candidate.length)
            while (index < maxLength && prefix[index] == candidate[index]) {
                index++
            }
            prefix = prefix.substring(0, index)
            if (prefix.isEmpty()) {
                break
            }
        }
        return prefix
    }

}
