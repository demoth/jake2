package org.demoth.cake.download

import jake2.qcommon.exec.Cvar
import java.nio.file.Path

enum class CakeDownloadCategory(val cvarName: String) {
    MAPS("allow_download_maps"),
    MODELS("allow_download_models"),
    PLAYERS("allow_download_players"),
    SOUNDS("allow_download_sounds"),
}

data class CakeDownloadRequest(
    val gameDirectory: String,
    val logicalPath: String,
    val category: CakeDownloadCategory,
    val finalPath: Path,
    val tempPath: Path,
)

enum class CakeDownloadRejectReason {
    ALREADY_QUEUED,
    DOWNLOADS_DISABLED,
    CATEGORY_DISABLED,
    INVALID_PATH,
    UNSUPPORTED_CATEGORY,
}

sealed interface CakeDownloadQueueResult {
    data class Enqueued(val request: CakeDownloadRequest) : CakeDownloadQueueResult
    data class Rejected(val reason: CakeDownloadRejectReason) : CakeDownloadQueueResult
}

class CakeDownloadManager(
    private val cache: CakeDownloadCache = CakeDownloadCache(),
    private val cvars: Cvar = Cvar.getInstance(),
) {
    private val pending = ArrayDeque<CakeDownloadRequest>()
    private val queuedKeys = linkedSetOf<String>()

    fun enqueue(logicalPath: String, gamemod: String?): CakeDownloadQueueResult {
        val normalizedPath = normalizePath(logicalPath)
            ?: return CakeDownloadQueueResult.Rejected(CakeDownloadRejectReason.INVALID_PATH)

        if (!isEnabled("allow_download")) {
            return CakeDownloadQueueResult.Rejected(CakeDownloadRejectReason.DOWNLOADS_DISABLED)
        }

        val category = categoryFor(normalizedPath)
            ?: return CakeDownloadQueueResult.Rejected(CakeDownloadRejectReason.UNSUPPORTED_CATEGORY)

        if (!isEnabled(category.cvarName)) {
            return CakeDownloadQueueResult.Rejected(CakeDownloadRejectReason.CATEGORY_DISABLED)
        }

        val gameDirectory = gamemod?.trim()?.takeIf { it.isNotEmpty() } ?: CakeDownloadCache.DEFAULT_GAME_DIRECTORY
        val queueKey = "$gameDirectory:$normalizedPath"
        if (!queuedKeys.add(queueKey)) {
            return CakeDownloadQueueResult.Rejected(CakeDownloadRejectReason.ALREADY_QUEUED)
        }

        val finalPath = cache.resolveRoot(gamemod).resolve(normalizedPath)
        val tempPath = cache.resolveRoot(gamemod).resolve(tempLogicalPath(normalizedPath))
        val request = CakeDownloadRequest(
            gameDirectory = gameDirectory,
            logicalPath = normalizedPath,
            category = category,
            finalPath = finalPath,
            tempPath = tempPath,
        )
        pending.addLast(request)
        return CakeDownloadQueueResult.Enqueued(request)
    }

    fun nextPending(): CakeDownloadRequest? = pending.firstOrNull()

    fun pollPending(): CakeDownloadRequest? {
        val request = pending.removeFirstOrNull() ?: return null
        queuedKeys.remove("${request.gameDirectory}:${request.logicalPath}")
        return request
    }

    fun pendingCount(): Int = pending.size

    private fun isEnabled(cvarName: String): Boolean {
        return cvars.VariableValue(cvarName) != 0f
    }

    companion object {
        private val DANGEROUS_EXTENSIONS = listOf(".dll", ".dylib", ".so")

        fun categoryFor(path: String): CakeDownloadCategory? {
            return when {
                path.startsWith("maps/") -> CakeDownloadCategory.MAPS
                path.startsWith("models/") -> CakeDownloadCategory.MODELS
                path.startsWith("players/") -> CakeDownloadCategory.PLAYERS
                path.startsWith("sound/") -> CakeDownloadCategory.SOUNDS
                else -> null
            }
        }

        fun normalizePath(path: String): String? {
            val normalized = path.trim()
            if (normalized.isEmpty()) return null
            if (normalized.contains("..")) return null
            if (normalized.contains('\\')) return null
            if (normalized.contains(':')) return null
            if (normalized.startsWith('.')) return null
            if (normalized.startsWith('/')) return null
            if (!normalized.contains('/')) return null
            if (DANGEROUS_EXTENSIONS.any { normalized.endsWith(it, ignoreCase = true) }) return null
            return normalized
        }

        fun tempLogicalPath(path: String): String {
            val dotIndex = path.lastIndexOf('.')
            return if (dotIndex <= 0) "$path.tmp" else path.substring(0, dotIndex) + ".tmp"
        }
    }
}
