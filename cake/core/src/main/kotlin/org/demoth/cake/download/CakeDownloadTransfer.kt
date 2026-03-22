package org.demoth.cake.download

import jake2.qcommon.network.messages.server.DownloadMessage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

sealed interface CakeDownloadTransferResult {
    data class Continue(val request: CakeDownloadRequest, val percent: Int) : CakeDownloadTransferResult
    data class Completed(val request: CakeDownloadRequest) : CakeDownloadTransferResult
    data class MissingOnServer(val request: CakeDownloadRequest) : CakeDownloadTransferResult
}

class CakeDownloadTransfer {
    private var activeRequest: CakeDownloadRequest? = null

    fun begin(request: CakeDownloadRequest): Long {
        check(activeRequest == null) { "A download is already active" }
        activeRequest = request
        ensureParentDirectories(request)
        return currentOffset(request)
    }

    fun activeRequest(): CakeDownloadRequest? = activeRequest

    fun handle(message: DownloadMessage): CakeDownloadTransferResult {
        val request = requireNotNull(activeRequest) { "No active download request" }

        if (message.data == null) {
            activeRequest = null
            return CakeDownloadTransferResult.MissingOnServer(request)
        }

        ensureParentDirectories(request)
        Files.write(
            request.tempPath,
            message.data,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.APPEND,
        )

        val percent = message.percentage.toInt() and 0xFF
        if (percent >= 100) {
            Files.move(request.tempPath, request.finalPath, StandardCopyOption.REPLACE_EXISTING)
            activeRequest = null
            return CakeDownloadTransferResult.Completed(request)
        }

        return CakeDownloadTransferResult.Continue(request, percent)
    }

    private fun currentOffset(request: CakeDownloadRequest): Long {
        return if (Files.isRegularFile(request.tempPath)) Files.size(request.tempPath) else 0L
    }

    private fun ensureParentDirectories(request: CakeDownloadRequest) {
        request.tempPath.parent?.let { Files.createDirectories(it) }
        request.finalPath.parent?.let { Files.createDirectories(it) }
    }
}
