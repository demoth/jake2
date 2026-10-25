package org.demoth.cake.download

import jake2.qcommon.Com
import jake2.qcommon.network.messages.client.StringCmdMessage
import jake2.qcommon.network.messages.server.DownloadMessage

/** Coordinates download protocol commands and precache retries on the application thread.
 * The caller queues reliable commands and handles retry/failure results; this owner does not
 * execute the command buffer, disconnect, or control presentation.
 */
class CakeDownloadSession(
    private val sendReliable: (String) -> Unit,
    private val downloadManager: CakeDownloadManager = CakeDownloadManager(),
) {
    sealed interface Result {
        data object Continue : Result
        data class RetryPrecache(val spawnCount: Int) : Result
        data object MissingOnServer : Result
    }

    private val downloadTransfer = CakeDownloadTransfer()
    private var pendingPrecacheSpawnCount: Int? = null
    private var lastDownloadProgressPercent = -1

    /** Called after successful precache, once deferred configuration assets have been released. */
    fun precacheCompleted() {
        pendingPrecacheSpawnCount = null
    }

    /** Cancel session state while preserving partial files for a future resume. */
    fun reset() {
        downloadManager.clear()
        downloadTransfer.reset()
        pendingPrecacheSpawnCount = null
        lastDownloadProgressPercent = -1
    }

    fun requestMissingResource(path: String, gamemod: String?, spawnCount: Int): Boolean {
        pendingPrecacheSpawnCount = spawnCount
        return when (val result = downloadManager.enqueue(path, gamemod)) {
            is CakeDownloadQueueResult.Enqueued -> {
                startQueuedDownloadIfIdle()
                true
            }

            is CakeDownloadQueueResult.Rejected -> {
                when (result.reason) {
                    CakeDownloadRejectReason.ALREADY_QUEUED -> true
                    CakeDownloadRejectReason.DOWNLOADS_DISABLED,
                    CakeDownloadRejectReason.CATEGORY_DISABLED -> {
                        Com.Warn("Autodownload disabled for missing resource '${path}'\n")
                        false
                    }

                    CakeDownloadRejectReason.INVALID_PATH -> {
                        Com.Warn("Refusing to autodownload invalid path '${path}'\n")
                        false
                    }

                    CakeDownloadRejectReason.UNSUPPORTED_CATEGORY -> {
                        Com.Warn("No Cake autodownload support yet for '${path}'\n")
                        false
                    }
                }
            }
        }
    }

    private fun startQueuedDownloadIfIdle(): Boolean {
        if (downloadTransfer.activeRequest() != null) {
            return false
        }
        val request = downloadManager.pollPending() ?: return false
        val offset = downloadTransfer.begin(request)
        lastDownloadProgressPercent = -1
        if (offset > 0L) {
            Com.Printf("Resuming ${request.logicalPath}\n")
            sendReliable("${StringCmdMessage.DOWNLOAD} ${request.logicalPath} $offset")
        } else {
            Com.Printf("Downloading ${request.logicalPath}\n")
            sendReliable("${StringCmdMessage.DOWNLOAD} ${request.logicalPath}")
        }
        return true
    }

    fun handle(message: DownloadMessage): Result {
        if (downloadTransfer.activeRequest() == null) {
            Com.Warn("Received download data without an active request\n")
            return Result.Continue
        }

        when (val result = downloadTransfer.handle(message)) {
            is CakeDownloadTransferResult.Continue -> {
                maybeLogDownloadProgress(result.request.logicalPath, result.percent)
                sendReliable(StringCmdMessage.NEXT_DOWNLOAD)
            }

            is CakeDownloadTransferResult.Completed -> {
                maybeLogDownloadProgress(result.request.logicalPath, 100)
                Com.Printf("Downloaded ${result.request.logicalPath}\n")
                if (!startQueuedDownloadIfIdle()) {
                    pendingPrecacheSpawnCount?.let { spawnCount ->
                        return Result.RetryPrecache(spawnCount)
                    }
                }
            }

            is CakeDownloadTransferResult.MissingOnServer -> {
                Com.Warn("Server does not have ${result.request.logicalPath}\n")
                return Result.MissingOnServer
            }
        }
        return Result.Continue
    }

    private fun maybeLogDownloadProgress(logicalPath: String, percent: Int) {
        if (percent <= lastDownloadProgressPercent) {
            return
        }
        val crossedBucket = lastDownloadProgressPercent < 0 || percent / 5 > lastDownloadProgressPercent / 5
        if (crossedBucket || percent == 100) {
            Com.Printf("Downloading ${logicalPath}... ${percent}%\n")
        }
        lastDownloadProgressPercent = percent
    }

}
