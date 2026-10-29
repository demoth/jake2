package jake2.server

import jake2.qcommon.Defines
import jake2.qcommon.exec.CvarTypes
import jake2.server.save.ServerSaveJsonStore
import jake2.server.save.ServerLevelJsonStore
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

enum class SavedGameMode { SINGLEPLAYER, COOP, DEATHMATCH }

data class SavedGameEntry(
    val slot: String,
    val comment: String?,
    val mapName: String?,
    val savedAtMillis: Long?,
    val mode: SavedGameMode?,
    val problem: String?,
    val mapTitle: String? = null,
)

/** Cheap, read-only browsing of an explicit root. Full restoration checks remain in SavePreflight. */
class ServerSaveCatalog(private val root: Path) {
    fun list(): List<SavedGameEntry> {
        val directory = root.resolve("save")
        if (Files.notExists(directory)) return emptyList()
        return Files.newDirectoryStream(directory).use { paths ->
            paths.filter { it.fileName.toString() != "current" && Files.isDirectory(it, NOFOLLOW_LINKS) }
                .map { inspect(it.fileName.toString()) }
                .sortedWith(compareByDescending<SavedGameEntry> { it.savedAtMillis ?: Long.MIN_VALUE }.thenBy { it.slot })
        }
    }

    fun inspect(slot: String): SavedGameEntry {
        var comment: String? = null
        var mapName: String? = null
        var timestamp: Long? = null
        var mode: SavedGameMode? = null
        var mapTitle: String? = null
        val problem = try {
            require(ServerSaveJsonStore.normalizeSlot(slot) == slot) { "Invalid save slot" }
            require(slot != "current") { "The working save is internal" }
            val directory = root.resolve("save").resolve(slot)
            require(Files.isDirectory(directory, NOFOLLOW_LINKS)) { "Save directory is missing or is a link" }
            val metadata = directory.resolve("server_mapcmd.ssv.json")
            require(Files.isRegularFile(metadata, NOFOLLOW_LINKS)) { "Missing map metadata" }
            timestamp = Files.getLastModifiedTime(metadata).toMillis()
            val store = ServerSaveJsonStore.forWriteDir(root.toString())
            val map = store.readMapCommand(slot)
            require(map.schemaVersion() == ServerSaveJsonStore.SCHEMA_VERSION) { "Unsupported map metadata version" }
            require(!map.mapCommand().isNullOrBlank()) { "Missing saved map command" }
            val target = ChangeMapInfo(map.mapCommand(), false, true)
            require(target.mapName.isNotBlank() && !target.mapName.contains("..") &&
                !target.mapName.contains('/') && !target.mapName.contains('\\')) { "Invalid saved map name" }
            comment = map.comment()
            mapName = target.mapName
            mapTitle = map.mapTitle()?.takeIf { it.isNotBlank() }
                ?: archivedTitle(slot, target.mapName)
                ?: comment?.takeIf { it.startsWith("Autosave in ") }?.removePrefix("Autosave in ")
            require(Files.isRegularFile(directory.resolve("server_latched_cvars.ssv.json"), NOFOLLOW_LINKS)) {
                "Missing saved settings"
            }
            val settings = store.readLatchedCvars(slot)
            require(settings.schemaVersion() == ServerSaveJsonStore.SCHEMA_VERSION) { "Unsupported saved settings version" }
            val values = settings.cvars().associate { it.name() to it.value() }
            require(values.size == settings.cvars().size) { "Duplicate saved settings" }
            val deathmatch = CvarTypes.BOOLEAN.parse(values.getValue("deathmatch")).value
            val coop = CvarTypes.BOOLEAN.parse(values.getValue("coop")).value
            val capacity = values.getValue("maxclients").toInt()
            require(capacity in 1..Defines.MAX_CLIENTS) { "Invalid saved maxclients" }
            mode = when {
                deathmatch -> SavedGameMode.DEATHMATCH
                coop -> SavedGameMode.COOP
                else -> SavedGameMode.SINGLEPLAYER
            }
            require(mode != SavedGameMode.SINGLEPLAYER || capacity == 1) { "Invalid singleplayer client capacity" }
            require(Files.isRegularFile(directory.resolve("game.ssv.json"), NOFOLLOW_LINKS)) { "Missing saved game state" }
            null
        } catch (failure: Exception) {
            failure.message ?: "Unreadable save"
        }
        return SavedGameEntry(slot, comment, mapName, timestamp, mode, problem, mapTitle)
    }

    // Existing manual checkpoints already contain the display title in the archived configstrings.
    private fun archivedTitle(slot: String, map: String): String? = try {
        if (Files.isRegularFile(root.resolve("save/$slot/$map.sv2.json"), NOFOLLOW_LINKS)) {
            ServerLevelJsonStore.forWriteDir(root.toString()).readLevelState(slot, map)
                .configstrings.getOrNull(Defines.CS_NAME)?.takeIf { it.isNotBlank() }
        } else null
    } catch (_: Exception) {
        null // Missing title must not turn metadata browsing into a full restoration check.
    }
}
