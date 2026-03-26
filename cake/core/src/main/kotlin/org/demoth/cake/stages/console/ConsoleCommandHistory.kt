package org.demoth.cake.stages.console

class ConsoleCommandHistory(
    private val maxSize: Int = DEFAULT_MAX_SIZE,
) {
    private val entries = ArrayDeque<String>(maxSize)
    private var cursor = 0
    private var draft = ""

    init {
        require(maxSize > 0) { "maxSize must be positive" }
    }

    fun entries(): List<String> = entries.toList()

    fun replaceEntries(commands: Iterable<String>) {
        entries.clear()
        commands
            .filter(::shouldPersist)
            .takeLast(maxSize)
            .forEach(entries::addLast)
        resetNavigation()
    }

    fun recordSubmitted(command: String): Boolean {
        val shouldPersist = shouldPersist(command)
        if (shouldPersist) {
            if (entries.size == maxSize) {
                entries.removeFirst()
            }
            entries.addLast(command)
        }
        resetNavigation()
        return shouldPersist
    }

    fun navigate(direction: Int, currentInput: String): String? {
        if (entries.isEmpty()) {
            return null
        }

        if (cursor == entries.size) {
            draft = currentInput
        }

        cursor = (cursor + direction).coerceIn(0, entries.size)
        return if (cursor == entries.size) {
            draft
        } else {
            entries.elementAt(cursor)
        }
    }

    private fun resetNavigation() {
        cursor = entries.size
        draft = ""
    }

    companion object {
        const val DEFAULT_MAX_SIZE: Int = 500

        fun shouldPersist(command: String): Boolean =
            command.isNotBlank() && !command.startsWith(' ')
    }
}
