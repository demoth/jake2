package jake2.server.save

import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

/** Remove one complete checkpoint, including future sidecar assets, without following directory links. */
fun deleteSaveSlot(root: Path, slot: String) {
    val name = ServerSaveJsonStore.normalizeSlot(slot)
    require(name == slot) { "Invalid save slot." }
    require(name != "current") { "The working save is internal." }
    val saves = root.resolve("save")
    val directory = saves.resolve(name)
    require(Files.isDirectory(saves, NOFOLLOW_LINKS) && Files.isDirectory(directory, NOFOLLOW_LINKS)) {
        "Save directory is missing or is a link."
    }
    Files.walk(directory).use { paths ->
        paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) }
    }
}
