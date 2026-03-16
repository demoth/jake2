package org.demoth.cake.stages.console

import jake2.qcommon.Com

data class ConsoleEntry(
    val severity: Com.ConsoleLevel,
    val text: String,
)

class ConsoleBuffer(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    private val entries = ArrayDeque<ConsoleEntry>(maxEntries)

    fun append(severity: Com.ConsoleLevel, text: String) {
        if (entries.size == maxEntries) {
            entries.removeFirst()
        }
        entries.addLast(ConsoleEntry(severity, text))
    }

    fun clear() {
        entries.clear()
    }

    fun entries(): List<ConsoleEntry> = entries.toList()

    companion object {
        const val DEFAULT_MAX_ENTRIES = 2048
    }
}
