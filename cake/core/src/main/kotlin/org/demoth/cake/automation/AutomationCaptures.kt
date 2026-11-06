package org.demoth.cake.automation

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import jake2.qcommon.Globals
import org.demoth.cake.ui.writeScreenshot
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture

enum class CaptureLayer { PRESENTATION, FINAL }

/** GL-thread capture requests shared by Cake and standalone stage fixtures.
 * Poll completion; never block the render thread waiting for a result.
 */
class AutomationCaptures {
    private data class Pending(
        val file: FileHandle,
        val layer: CaptureLayer,
        val earliestFrame: Long,
        val result: CompletableFuture<FileHandle>,
    )

    private val pending = mutableListOf<Pending>()
    private var frame = 0L

    /** Requests made during a frame wait for the next frame's updates and drawing. */
    fun request(file: FileHandle, layer: CaptureLayer = CaptureLayer.PRESENTATION): CompletableFuture<FileHandle> {
        check(Globals.developer?.typedValue == true) { "Capture automation requires developer = 1" }
        val result = CompletableFuture<FileHandle>()
        pending += Pending(file, layer, frame + 1, result)
        return result
    }

    /** Call once before application updates, including on frames that cannot draw. */
    fun beginFrame() {
        frame++
        pending.removeAll { it.result.isDone }
        cancelIfDisabled()
    }

    /** Call after drawing this composition layer, before presenting/swapping the framebuffer. */
    fun afterDraw(layer: CaptureLayer) {
        if (cancelIfDisabled()) return
        if (Gdx.graphics.backBufferWidth <= 0 || Gdx.graphics.backBufferHeight <= 0) return
        // Snapshot before completing futures: completion handlers may enqueue more captures.
        val ready = pending.filter { it.layer == layer && it.earliestFrame <= frame }
        for (capture in ready) {
            pending.remove(capture)
            if (capture.result.isDone) continue
            try {
                writeScreenshot(capture.file)
            } catch (error: Exception) {
                capture.result.completeExceptionally(error)
                throw error
            }
            capture.result.complete(capture.file)
        }
    }

    /** Used by the caller on shutdown/cancellation. Individual requests can also be cancelled. */
    fun cancelAll(reason: String) {
        val cancelled = pending.toList()
        pending.clear()
        cancelled.forEach { it.result.completeExceptionally(CancellationException(reason)) }
    }

    private fun cancelIfDisabled(): Boolean {
        if (Globals.developer?.typedValue == true) return false
        cancelAll("Capture automation disabled: developer = 0")
        return true
    }
}
