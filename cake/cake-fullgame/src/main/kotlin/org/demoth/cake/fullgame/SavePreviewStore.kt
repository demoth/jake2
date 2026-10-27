package org.demoth.cake.fullgame

import jake2.server.save.ServerSaveJsonStore
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

/** Optional image sidecars, separate from the gameplay working save and its copy/restore flow. */
internal class SavePreviewStore(private val root: Path) {
    private val maxBytes = 1024 * 1024L

    fun write(slot: String, png: ByteArray) {
        val image = path(slot)
        require(slot != "autosave") { "Autosave capture is not supported." }
        require(png.isNotEmpty() && png.size <= maxBytes) { "Invalid preview size." }
        require(Files.isDirectory(image.parent, NOFOLLOW_LINKS)) { "Checkpoint no longer exists." }
        try {
            Files.write(image, png)
        } catch (failure: Exception) {
            try { Files.deleteIfExists(image) } catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
            throw failure
        }
    }

    fun read(slot: String): ByteArray? = try {
        val image = path(slot)
        if (Files.isDirectory(image.parent, NOFOLLOW_LINKS) && Files.isRegularFile(image, NOFOLLOW_LINKS) &&
            Files.size(image) in 1..maxBytes) Files.readAllBytes(image) else null
    } catch (_: Exception) { null }

    private fun path(slot: String): Path {
        require(slot == ServerSaveJsonStore.normalizeSlot(slot) && slot != "current") { "Invalid checkpoint." }
        return root.resolve("save").resolve(slot).resolve("preview.png")
    }
}
