package org.demoth.cake.profile

import jake2.qcommon.vfs.DefaultWritableFileSystem
import jake2.qcommon.vfs.VfsOpenOptions
import jake2.qcommon.vfs.VfsWriteOptions
import jake2.qcommon.vfs.WritableFileSystem
import java.nio.charset.StandardCharsets
import java.nio.file.Path

class CakeConsoleHistoryStore(
    private val writableFactory: (String) -> WritableFileSystem = { profileId ->
        val home = Path.of(System.getProperty("user.home"))
        DefaultWritableFileSystem(home.resolve(".cake").resolve(profileId))
    },
) {
    fun readHistory(profileId: String): List<String> {
        val writable = writableFactory(profileId)
        val opened = writable.openReadReal(HISTORY_FILE_NAME, VfsOpenOptions.DEFAULT)
        if (!opened.success || opened.value == null) {
            return emptyList()
        }
        opened.value.use { handle ->
            handle.inputStream().use { input ->
                return input
                    .bufferedReader(StandardCharsets.UTF_8)
                    .readLines()
            }
        }
    }

    fun writeHistory(profileId: String, commands: List<String>): String {
        val writable = writableFactory(profileId)
        val opened = writable.openWrite(HISTORY_FILE_NAME, VfsWriteOptions.TRUNCATE)
        require(opened.success && opened.value != null) { "Failed to open console history for write" }

        opened.value.use { handle ->
            handle.outputStream().use { output ->
                commands.forEach { command ->
                    output.write(command.toByteArray(StandardCharsets.UTF_8))
                    output.write('\n'.code)
                }
            }
        }

        return writable.resolveWritePath(HISTORY_FILE_NAME)
            ?: throw IllegalStateException("Failed to resolve console history path")
    }

    companion object {
        const val HISTORY_FILE_NAME: String = "console-history.txt"
    }
}
