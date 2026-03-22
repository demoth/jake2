package org.demoth.cake.download

import jake2.qcommon.vfs.DefaultWritableFileSystem
import jake2.qcommon.vfs.WritableFileSystem
import java.nio.file.Path

class CakeDownloadCache(
    private val root: Path = defaultRoot(),
) {
    fun resolveRoot(gamemod: String?): Path {
        return root.resolve(normalizeGameDirectory(gamemod))
    }

    fun writable(gamemod: String?): WritableFileSystem {
        return DefaultWritableFileSystem(resolveRoot(gamemod))
    }

    companion object {
        const val DEFAULT_GAME_DIRECTORY: String = "baseq2"

        fun defaultRoot(): Path {
            val home = Path.of(System.getProperty("user.home"))
            return home.resolve(".cake").resolve("downloads")
        }

        private fun normalizeGameDirectory(gamemod: String?): String {
            val normalized = gamemod?.trim()?.takeIf { it.isNotEmpty() } ?: return DEFAULT_GAME_DIRECTORY
            require(isSafeDirectoryToken(normalized)) { "Game directory must be a single safe token" }
            return normalized
        }

        private fun isSafeDirectoryToken(value: String): Boolean {
            if (value.contains("..")) return false
            if (value.contains('/')) return false
            if (value.contains('\\')) return false
            if (value.contains(':')) return false
            return true
        }
    }
}
