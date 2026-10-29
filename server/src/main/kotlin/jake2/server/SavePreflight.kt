package jake2.server

import jake2.qcommon.Defines
import jake2.qcommon.ServerStates
import jake2.qcommon.exec.Cvar
import jake2.qcommon.save.SaveJson
import jake2.qcommon.vfs.EngineVfs
import jake2.server.save.ServerLevelJsonStore
import jake2.server.save.ServerSaveJsonStore
import java.nio.file.Files
import java.nio.file.Path

/** Read-only checks before a slot replaces the working save. Gameplay reference resolution stays in Game. */
internal class SavePreflight(private val root: Path) {
    fun inspect(slot: String): PreparedSave {
        val store = ServerSaveJsonStore.forWriteDir(root.toString())
        val map = store.readMapCommand(slot)
        require(map.schemaVersion() == ServerSaveJsonStore.SCHEMA_VERSION) { "Unsupported map metadata version" }
        require(!map.mapCommand().isNullOrBlank()) { "Missing saved map command" }
        val target = ChangeMapInfo(map.mapCommand(), false, true)
        require(target.mapName.isNotBlank() && !target.mapName.contains("..") &&
            !target.mapName.contains('/') && !target.mapName.contains('\\')) { "Invalid saved map name" }
        if (target.state == ServerStates.SS_GAME) {
            require(EngineVfs.exists("maps/${target.mapName}.bsp")) { "Missing map: ${target.mapName}" }
        }

        val settings = store.readLatchedCvars(slot)
        require(settings.schemaVersion() == ServerSaveJsonStore.SCHEMA_VERSION) { "Unsupported saved settings version" }
        val values = settings.cvars().associate { entry ->
            require(!entry.name().isNullOrBlank() && entry.value() != null) { "Invalid saved setting" }
            val registry = Cvar.getInstance()
            registry.find(entry.name())?.type?.parse(entry.value())
            entry.name() to entry.value()
        }
        require(values.size == settings.cvars().size) { "Duplicate saved settings" }
        val capacity = values.getValue("maxclients").toInt()
        require(capacity in 1..Defines.MAX_CLIENTS) { "Invalid saved maxclients" }

        val directory = root.resolve("save").resolve(ServerSaveJsonStore.normalizeSlot(slot))
        // Only inspect the gameplay envelope here: the server must not depend on gameplay DTOs.
        val gameFile = readEnvelope(directory.resolve("game.ssv.json"))
        val game = gameFile["game"] as? Map<*, *> ?: error("Missing saved game state")
        require(game["maxClients"] == capacity) { "Saved client capacities disagree" }
        val clients = gameFile["clients"] as? List<*> ?: error("Missing saved clients")
        require(clients.size == capacity && clients.all { it is Map<*, *> }) { "Incomplete saved clients" }
        val autoSaved = game["autoSaved"] as? Boolean ?: error("Missing autosave marker")

        val archives = linkedSetOf<String>()
        Files.newDirectoryStream(directory).use { files ->
            for (file in files) {
                val name = file.fileName.toString()
                if (name.endsWith(".sav.json")) archives.add(name.removeSuffix(".sav.json"))
                if (name.endsWith(".sv2.json")) archives.add(name.removeSuffix(".sv2.json"))
            }
        }
        // Level-entry autosaves intentionally have no archive for the new map yet.
        if (!autoSaved && target.state == ServerStates.SS_GAME) archives.add(target.mapName)
        for (name in archives) {
            // Gameplay level v2 includes the component state required by restored callbacks.
            val level = readEnvelope(directory.resolve("$name.sav.json"), version = 2)
            require(level["level"] is Map<*, *> && level["entities"] is List<*>) { "Incomplete level: $name" }
            val server = ServerLevelJsonStore.forWriteDir(root.toString()).readLevelState(slot, name)
            require(server.schemaVersion == ServerLevelJsonStore.SCHEMA_VERSION) { "Unsupported server level version" }
            require(server.configstrings.size == Defines.MAX_CONFIGSTRINGS) { "Incomplete configstrings: $name" }
        }
        return PreparedSave(target, capacity)
    }

    private fun readEnvelope(path: Path, version: Int = 1): Map<*, *> = Files.newInputStream(path).use {
        val value = SaveJson.read(it, Map::class.java)
        require(value["schemaVersion"] == version) { "Unsupported save version: ${path.fileName}" }
        value
    }
}

internal data class PreparedSave(val target: ChangeMapInfo, val maxClients: Int)
