package org.demoth.cake

import jake2.qcommon.exec.Cmd

// Quake2 servers can send `svc_stufftext`, but in practice the client only expects a very small
// set of control commands during connect/map-change flow. Filter at this boundary so malformed or
// mod-specific arbitrary commands do not get executed through the generic local command buffer.
internal data class ServerStuffTextFilterResult(
    val acceptedCommands: List<String>,
    val rejectedCommands: List<String>,
)

internal fun filterServerStuffText(text: String): ServerStuffTextFilterResult {
    val accepted = mutableListOf<String>()
    val rejected = mutableListOf<String>()

    splitStuffTextCommands(text).forEach { command ->
        if (isExpectedServerStuffTextCommand(command)) {
            accepted += command
        } else {
            rejected += command
        }
    }

    return ServerStuffTextFilterResult(
        acceptedCommands = accepted,
        rejectedCommands = rejected,
    )
}

private fun isExpectedServerStuffTextCommand(command: String): Boolean {
    val args = Cmd.TokenizeString(command, false)
    if (args.isEmpty()) {
        return true
    }
    // Validate exact command shape, not just prefixes, because some expected commands are
    // single-token (`changing`) while others require numeric arguments (`precache <spawncount>`).
    return when (args[0]) {
        "changing", "reconnect" -> args.size == 1
        "cmd" -> args.size > 1
        "precache" -> args.size == 2 && args[1].toIntOrNull() != null
        "spectator" -> args.size == 2 && (args[1] == "0" || args[1] == "1")
        else -> false
    }
}

private fun splitStuffTextCommands(text: String): List<String> {
    val result = mutableListOf<String>()
    // `stufftext` follows command-buffer splitting rules: newline-separated commands and
    // unquoted semicolons, with quoted semicolons preserved inside a single command.
    text.split('\n', '\r').forEach { line ->
        appendSplitCommands(line, result)
    }
    return result.map { it.trim() }.filter { it.isNotEmpty() }
}

private fun appendSplitCommands(line: String, result: MutableList<String>) {
    if (!line.contains(';')) {
        result += line
        return
    }

    val current = StringBuilder()
    var inQuotes = false
    line.forEach { char ->
        when {
            char == ';' && !inQuotes -> {
                result += current.toString()
                current.setLength(0)
            }
            char == '"' -> inQuotes = !inQuotes
            else -> current.append(char)
        }
    }
    if (current.isNotEmpty()) {
        result += current.toString()
    }
}
