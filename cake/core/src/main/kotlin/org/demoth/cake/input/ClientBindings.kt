package org.demoth.cake.input

import com.badlogic.gdx.Input
import jake2.qcommon.Com
import jake2.qcommon.exec.Cbuf
import jake2.qcommon.exec.Cmd

/**
 * Runtime key binding table and input event dispatcher for Cake.
 *
 * Supports keyboard keys, mouse buttons and wheel pseudo-keys.
 */
class ClientBindings(
    private val executeImmediate: (String) -> Unit = Cmd::ExecuteString,
    private val queueCommand: (String) -> Unit = { command -> Cbuf.AddText("$command\n") },
) {
    companion object {
        private const val MOUSE1 = 10_001
        private const val MOUSE2 = 10_002
        private const val MOUSE3 = 10_003
        private const val MWHEELUP = 10_101
        private const val MWHEELDOWN = 10_102

        private val keyAliases: Map<String, Int> by lazy {
            buildMap {
                put("uparrow", Input.Keys.UP)
                put("downarrow", Input.Keys.DOWN)
                put("leftarrow", Input.Keys.LEFT)
                put("rightarrow", Input.Keys.RIGHT)
                put("ctrl", Input.Keys.CONTROL_LEFT)
                put("control", Input.Keys.CONTROL_LEFT)
                put("enter", Input.Keys.ENTER)
                put("mouse1", MOUSE1)
                put("mouse2", MOUSE2)
                put("mouse3", MOUSE3)
                put("mwheelup", MWHEELUP)
                put("mwheeldown", MWHEELDOWN)
            }
        }

        private val keyboardNameToCode: Map<String, Int> by lazy {
            buildMap {
                for (keycode in 0..255) {
                    val keyName = Input.Keys.toString(keycode) ?: continue
                    if (keyName.isNotBlank() && !keyName.startsWith("Unknown", ignoreCase = true)) {
                        put(normalizeKeyName(keyName), keycode)
                    }
                }
            }
        }

        private fun normalizeKeyName(keyName: String): String {
            return keyName.trim().lowercase()
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "")
        }

        private fun keyCodeToDisplayName(keyCode: Int): String {
            return when (keyCode) {
                MOUSE1 -> "MOUSE1"
                MOUSE2 -> "MOUSE2"
                MOUSE3 -> "MOUSE3"
                MWHEELUP -> "MWHEELUP"
                MWHEELDOWN -> "MWHEELDOWN"
                Input.Keys.UP -> "UPARROW"
                Input.Keys.DOWN -> "DOWNARROW"
                Input.Keys.LEFT -> "LEFTARROW"
                Input.Keys.RIGHT -> "RIGHTARROW"
                Input.Keys.CONTROL_LEFT -> "CTRL"
                else -> (Input.Keys.toString(keyCode) ?: keyCode.toString()).uppercase()
            }
        }
    }

    private val bindings = mutableMapOf<Int, String>()
    private val heldInputs = mutableSetOf<Int>()
    private val activeButtonsByInput = mutableMapOf<Int, MutableList<String>>()

    init {
        registerBindingCommands()
        installDefaultBindings()
    }

    fun handleKeyDown(keycode: Int): Boolean = onInputPressed(keycode)

    fun handleKeyUp(keycode: Int): Boolean = onInputReleased(keycode)

    fun handleMouseButtonDown(button: Int): Boolean {
        val keyCode = when (button) {
            Input.Buttons.LEFT -> MOUSE1
            Input.Buttons.RIGHT -> MOUSE2
            Input.Buttons.MIDDLE -> MOUSE3
            else -> return false
        }
        return onInputPressed(keyCode)
    }

    fun handleMouseButtonUp(button: Int): Boolean {
        val keyCode = when (button) {
            Input.Buttons.LEFT -> MOUSE1
            Input.Buttons.RIGHT -> MOUSE2
            Input.Buttons.MIDDLE -> MOUSE3
            else -> return false
        }
        return onInputReleased(keyCode)
    }

    fun handleScroll(amountX: Float, amountY: Float): Boolean {
        var handled = false

        if (amountY < 0f) {
            handled = dispatchWheel(MWHEELUP) || handled
        } else if (amountY > 0f) {
            handled = dispatchWheel(MWHEELDOWN) || handled
        }

        if (amountX < 0f) {
            handled = dispatchWheel(MWHEELUP) || handled
        } else if (amountX > 0f) {
            handled = dispatchWheel(MWHEELDOWN) || handled
        }

        return handled
    }

    fun setBindingByName(keyName: String, binding: String): Boolean {
        val keyCode = parseKeyName(keyName) ?: return false
        setBinding(keyCode, binding)
        return true
    }

    fun getBindingByName(keyName: String): String? {
        val keyCode = parseKeyName(keyName) ?: return null
        return bindings[keyCode]
    }

    fun clearBindings() {
        bindings.clear()
        releaseAllActiveButtons()
    }

    fun releaseAllActiveButtons() {
        val active = activeButtonsByInput.values.flatMap { it }
        activeButtonsByInput.clear()
        heldInputs.clear()
        for (commandName in active) {
            executeImmediate("-$commandName")
        }
    }

    fun listBindings(): List<Pair<String, String>> {
        return bindings.entries
            .sortedBy { keyCodeToDisplayName(it.key) }
            .map { keyCodeToDisplayName(it.key) to it.value }
    }

    private fun setBinding(keyCode: Int, binding: String?) {
        if (binding == null || binding.isBlank()) {
            bindings.remove(keyCode)
        } else {
            bindings[keyCode] = binding.trim()
        }
    }

    private fun installDefaultBindings() {
        setBindingByName("w", "+forward")
        setBindingByName("s", "+back")
        setBindingByName("a", "+moveleft")
        setBindingByName("d", "+moveright")
        setBindingByName("space", "+moveup")
        setBindingByName("c", "+movedown")
        setBindingByName("leftarrow", "+left")
        setBindingByName("rightarrow", "+right")
        setBindingByName("uparrow", "+lookup")
        setBindingByName("downarrow", "+lookdown")
        setBindingByName("ctrl", "+attack")
        setBindingByName("mouse1", "+attack")

        setBindingByName("mwheelup", "weapnext")
        setBindingByName("mwheeldown", "weapprev")

        setBindingByName("1", "use blaster")
        setBindingByName("2", "use shotgun")
        setBindingByName("3", "use super shotgun")
        setBindingByName("4", "use machinegun")
        setBindingByName("5", "use chaingun")
        setBindingByName("6", "use grenade launcher")
        setBindingByName("7", "use rocket launcher")
        setBindingByName("8", "use hyperblaster")
        setBindingByName("9", "use railgun")
        setBindingByName("0", "use bfg10k")
        setBindingByName("g", "use grenades")
        setBindingByName("tab", "inven")
        setBindingByName("enter", "invuse")
        setBindingByName("]", "invnext")
        setBindingByName("[", "invprev")
        setBindingByName("f2", "cmd help")
    }

    private fun onInputPressed(keyCode: Int): Boolean {
        val binding = bindings[keyCode] ?: return false

        if (!heldInputs.add(keyCode)) {
            return true
        }

        executeBindingPress(keyCode, binding, transient = false)
        return true
    }

    private fun onInputReleased(keyCode: Int): Boolean {
        heldInputs.remove(keyCode)

        val activeButtons = activeButtonsByInput.remove(keyCode)
        if (activeButtons != null) {
            for (commandName in activeButtons) {
                executeImmediate("-$commandName")
            }
        }

        return bindings.containsKey(keyCode) || activeButtons != null
    }

    private fun dispatchWheel(keyCode: Int): Boolean {
        val binding = bindings[keyCode] ?: return false
        executeBindingPress(keyCode, binding, transient = true)
        return true
    }

    private fun executeBindingPress(keyCode: Int, binding: String, transient: Boolean) {
        val commands = splitBoundCommands(binding)
        val activeButtons = mutableListOf<String>()

        for (command in commands) {
            val args = Cmd.TokenizeString(command, false)
            if (args.isEmpty()) {
                continue
            }

            val name = args[0]
            if (name.startsWith("+") && name.length > 1) {
                executeImmediate(command)
                val buttonCommand = name.substring(1)
                if (transient) {
                    executeImmediate("-$buttonCommand")
                } else {
                    activeButtons += buttonCommand
                }
            } else {
                queueCommand(command)
            }
        }

        if (!transient && activeButtons.isNotEmpty()) {
            activeButtonsByInput[keyCode] = activeButtons
        }
    }

    private fun splitBoundCommands(binding: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        for (char in binding) {
            when {
                char == '"' -> {
                    inQuotes = !inQuotes
                    current.append(char)
                }
                char == ';' && !inQuotes -> {
                    val command = current.toString().trim()
                    if (command.isNotEmpty()) {
                        result += command
                    }
                    current.setLength(0)
                }
                else -> current.append(char)
            }
        }

        val tail = current.toString().trim()
        if (tail.isNotEmpty()) {
            result += tail
        }

        return result
    }

    private fun parseKeyName(keyName: String): Int? {
        val normalized = normalizeKeyName(keyName)
        return keyAliases[normalized] ?: keyboardNameToCode[normalized]
    }

    private fun registerBindingCommands() {
        Cmd.AddCommand("bind", true) { args ->
            if (args.size < 2) {
                Com.Printf("bind <key> [command] : attach a command to a key\n")
                return@AddCommand
            }

            val keyName = args[1]
            val keyCode = parseKeyName(keyName)
            if (keyCode == null) {
                Com.Printf("\"$keyName\" isn't a valid keaaaaay\n")
                return@AddCommand
            }

            if (args.size == 2) {
                val binding = bindings[keyCode]
                if (binding != null) {
                    Com.Printf("\"$keyName\" = \"$binding\"\n")
                } else {
                    Com.Printf("\"$keyName\" is not bound\n")
                }
                return@AddCommand
            }

            val command = Cmd.getArguments(args, 2)
            setBinding(keyCode, command)
        }

        Cmd.AddCommand("unbind", true) { args ->
            if (args.size != 2) {
                Com.Printf("unbind <key> : remove commands from a key\n")
                return@AddCommand
            }

            val keyCode = parseKeyName(args[1])
            if (keyCode == null) {
                Com.Printf("\"${args[1]}\" isn't a valid key\n")
                return@AddCommand
            }

            setBinding(keyCode, null)
        }

        Cmd.AddCommand("unbindall", true) {
            clearBindings()
        }

        Cmd.AddCommand("bindlist", true) {
            for ((key, binding) in listBindings()) {
                Com.Printf("$key \"$binding\"\n")
            }
        }
    }
}
