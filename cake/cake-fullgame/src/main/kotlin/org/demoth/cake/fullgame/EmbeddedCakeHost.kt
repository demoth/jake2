package org.demoth.cake.fullgame

import jake2.qcommon.Defines
import jake2.qcommon.Com
import jake2.qcommon.exec.Cbuf
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.CvarTypes
import jake2.qcommon.exec.find
import jake2.qcommon.longjmpException
import jake2.qcommon.network.transport.LoopbackLink
import jake2.qcommon.network.runtime.NetworkTransport
import jake2.server.TransportServerNetwork
import org.demoth.cake.network.TransportClientNetwork
import jake2.qcommon.vfs.EngineVfs
import jake2.qcommon.vfs.EngineWriteRoot
import jake2.qcommon.vfs.VfsConfig
import jake2.server.ServerHost
import jake2.server.ServerSaveCatalog
import jake2.server.SavedGameEntry
import jake2.server.SavedGameMode
import jake2.server.save.ServerSaveJsonStore
import jake2.server.save.deleteSaveSlot
import org.demoth.cake.hosting.*
import org.demoth.cake.profile.CakeGameProfile
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.util.UUID

/** Application-owned embedded Host; games may stop while commands and profile remain active. */
class EmbeddedCakeHost(
    private val profilesRoot: Path = Path.of(System.getProperty("user.home"), ".cake"),
    private val localLink: LoopbackLink = LoopbackLink(),
) : CakeHost {
    private val clientTransport = NetworkTransport(localLink.first, localLink.second.localAddress)
    private val serverTransport = NetworkTransport(localLink.second, localLink.first.localAddress)
    override val clientNetwork = TransportClientNetwork(clientTransport)

    internal fun udpEndpoint(role: Int) =
        if (role == Defines.NS_CLIENT) clientTransport.udpEndpoint else serverTransport.udpEndpoint

    private fun clearLocalTraffic() {
        localLink.first.clearIncoming()
        localLink.second.clearIncoming()
    }

    internal var server: ServerHost? = null
        private set
    private var starting = false
    private var ownerThread: Thread? = null

    private var previousWriteRoot: Path? = null
    private var activeProfile: CakeGameProfile? = null
    private var startupRejection: () -> String? = { null }
    private var disposed = false

    override fun initialize(startupRejection: () -> String?) {
        checkThread()
        check(!disposed) { "Host has been disposed" }
        check(server == null) { "Host is already initialized" }
        this.startupRejection = startupRejection
        previousWriteRoot = EngineWriteRoot.path()
        val rejection = {
            when {
                disposed -> "Host has been disposed"
                activeProfile == null -> "Select a game profile before hosting."
                else -> this.startupRejection()
            }
        }
        server = ServerHost(TransportServerNetwork(serverTransport), rejection)
    }

    override fun activateProfile(profile: CakeGameProfile?) {
        checkThread()
        check(server != null && !disposed) { "Host is not initialized" }
        check(state == HostState.STOPPED) { "Stop the local game before changing profile" }
        startupRejection()?.let { throw IllegalStateException(it) }
        val normalized = profile?.normalized()
        if (normalized == activeProfile) return
        if (normalized != null) {
            validateProfile(normalized)
            configureFilesystem(normalized)
        } else {
            previousWriteRoot?.let(EngineWriteRoot::setRoot)
        }
        activeProfile = normalized
    }

    override val availability = HostAvailability.AVAILABLE
    override val state: HostState
        get() = when {
            starting -> HostState.STARTING
            server?.isRunning == true -> HostState.RUNNING
            else -> HostState.STOPPED
        }

    override fun start(request: HostRequest) = start(request, prepareOnly = false)

    override fun prepareNewGame(profile: CakeGameProfile, skill: Int) =
        start(HostRequest(profile, "", HostMode.SINGLEPLAYER, skill), prepareOnly = true)

    private fun start(request: HostRequest, prepareOnly: Boolean): HostStartResult {
        checkThread()
        if (disposed || server == null) return HostStartResult.Rejected("Host is not initialized")
        startupRejection()?.let { return HostStartResult.Rejected(it) }
        try {
            validate(request, prepareOnly)
        } catch (error: IllegalArgumentException) {
            return HostStartResult.Rejected(error.message ?: "Invalid host request")
        }
        stop("Starting a new game")
        try {
            activateProfile(request.profile)
            starting = true
            if (!prepareOnly && !EngineVfs.exists("maps/${request.map}.bsp")) {
                stop("Map not found")
                return HostStartResult.Rejected("Map not found: ${request.map}")
            }
            val cvars = Cvar.getInstance()
            val manager = checkNotNull(server)
            cvars.find<Boolean>("deathmatch").forceSet(request.mode == HostMode.DEATHMATCH)
            cvars.find<Boolean>("coop").forceSet(request.mode == HostMode.COOP)
            cvars.find<Int>("maxclients").forceSet(request.maxClients)
            cvars.find<String>("hostname").forceSet(request.hostname)
            cvars.find<Boolean>("paused").forceSet(false)
            // Consumed by the new game instance's existing early-cvar transfer.
            cvars.setResult("skill", request.skill.toString())
            if (prepareOnly) return HostStartResult.Started
            when (val result = manager.map(request.map)) {
                ServerHost.OperationResult.Completed -> Unit
                is ServerHost.OperationResult.Rejected -> {
                    stop("Map did not start")
                    return HostStartResult.Rejected(result.reason)
                }
                is ServerHost.OperationResult.Failed -> {
                    stop("Map startup failed")
                    return HostStartResult.Rejected(result.reason)
                }
            }
            if (request.mode != HostMode.SINGLEPLAYER && udpEndpoint(Defines.NS_SERVER) == null) {
                stop("Server port unavailable")
                return HostStartResult.Rejected("Could not open the server UDP port")
            }
            starting = false
            return HostStartResult.Started
        } catch (error: longjmpException) {
            stop("Server dropped during startup")
            return HostStartResult.Rejected(error.message ?: "Server startup failed")
        } catch (error: IOException) {
            stop("Filesystem setup failed")
            return HostStartResult.Rejected("Could not prepare game files: ${error.message}")
        } catch (error: RuntimeException) {
            stop("Server startup failed")
            throw error
        }
    }

    override fun update(elapsedMillis: Long) {
        checkThread()
        val manager = server ?: return
        if (starting) {
            if (manager.isRunning) starting = false
            else if (Cbuf.contents().isNotBlank()) return // Alias may contain wait.
            else {
                stop("New Game did not start a map")
                throw HostFailureException("The newgame alias did not start a map. Check the console for details.",
                    IllegalStateException("No map after newgame"))
            }
        }
        if (!manager.isRunning) return // An idle Host retains commands and profile context.
        try {
            manager.update(elapsedMillis.coerceAtLeast(0))
        } catch (error: longjmpException) {
            stop("Server dropped")
            throw HostFailureException(error.message ?: "Server dropped", error)
        } catch (error: RuntimeException) {
            stop("Server failed")
            throw error
        }
    }

    override fun stop(reason: String) {
        checkThread()
        starting = false
        try {
            server?.stop("$reason\n", false)
        } finally {
            // The adapter also owns disposal of the local client's queued traffic.
            if (server != null) clearLocalTraffic()
        }
    }

    override fun dispose() {
        checkThread()
        if (disposed) return
        disposed = true
        starting = false
        val manager = server
        server = null
        try {
            manager?.close()
        } finally {
            if (manager != null) clearLocalTraffic()
            try { clientNetwork.close() } finally { localLink.close() }
            previousWriteRoot?.let(EngineWriteRoot::setRoot)
            previousWriteRoot = null
            activeProfile = null
        }
    }

    override fun setPaused(paused: Boolean) {
        checkThread()
        if (server?.isRunning != true) return
        val cvars = Cvar.getInstance()
        val game = server?.games?.get("default") ?: return
        val singlePlayer = !game.isDeathmatch && !game.isCoop && server?.clients?.size == 1
        cvars.find<Boolean>("paused").forceSet(paused && singlePlayer)
    }

    override fun saveAccess(): HostSaveAccess {
        checkThread()
        val manager = server
        val game = manager?.games?.get("default")
        val loadReason = saveContextRejection() ?: when {
            starting -> "Wait for the game to finish starting."
            manager?.isRunning == true && (manager.games.size != 1 || game == null ||
                game.isCoop || game.isDeathmatch || manager.clients.size != 1) ->
                "Save/load menus are available only in singleplayer."
            else -> null
        }
        val saveReason = loadReason ?: game?.saveRejectionReason() ?: if (game == null) {
            "Start a singleplayer game before saving."
        } else null
        return HostSaveAccess(loadReason, saveReason)
    }

    override fun listSaves(): HostSaveCatalogResult {
        checkThread()
        saveContextRejection()?.let { return HostSaveCatalogResult.Rejected(it) }
        val profile = checkNotNull(activeProfile)
        return try {
            val entries = ServerSaveCatalog(profileRoot(profile)).list().map(::toHostSaveEntry)
            HostSaveCatalogResult.Available(profile.id, entries)
        } catch (failure: Exception) {
            HostSaveCatalogResult.Failed(failure.message ?: "Could not read saved games.", failure)
        }
    }

    override fun saveGame(slot: String?, overwrite: Boolean, previewPng: ByteArray?): HostSaveResult {
        checkThread()
        saveAccess().saveRejectionReason?.let { return HostSaveResult.Rejected(it) }
        saveStorageRejection()?.let { return HostSaveResult.Rejected(it) }
        val destination = normalizedSaveSlot(slot ?: "manual-${UUID.randomUUID()}")
            ?: return HostSaveResult.Rejected("Invalid save slot.")
        if (destination == "current" || destination == "autosave") {
            return HostSaveResult.Rejected("This slot is reserved for the game.")
        }
        val path = profileRoot(checkNotNull(activeProfile)).resolve("save").resolve(destination)
        if (Files.exists(path, NOFOLLOW_LINKS)) {
            if (!Files.isDirectory(path, NOFOLLOW_LINKS)) return HostSaveResult.Rejected("Invalid save directory.")
            if (!overwrite) return HostSaveResult.Rejected("Confirm replacing this saved game.")
        }
        val result = checkNotNull(server).saveGame(destination).toHostSaveResult(destination)
        if (result is HostSaveResult.Completed && previewPng != null) {
            try {
                SavePreviewStore(profileRoot(checkNotNull(activeProfile))).write(destination, previewPng)
            } catch (failure: Exception) {
                Com.Warn("Saved checkpoint, but preview failed: ${failure.message}\n")
                return result.copy(warning = "Preview could not be saved.")
            }
        }
        return result
    }

    override fun readSavePreview(slot: String): ByteArray? {
        checkThread()
        if (saveContextRejection() != null) return null
        return SavePreviewStore(profileRoot(checkNotNull(activeProfile))).read(slot)
    }

    override fun loadGame(slot: String): HostSaveResult {
        checkThread()
        saveAccess().loadRejectionReason?.let { return HostSaveResult.Rejected(it) }
        saveStorageRejection()?.let { return HostSaveResult.Rejected(it) }
        val source = normalizedSaveSlot(slot) ?: return HostSaveResult.Rejected("Invalid save slot.")
        if (source == "current") return HostSaveResult.Rejected("The working save is internal.")
        val entry = toHostSaveEntry(ServerSaveCatalog(profileRoot(checkNotNull(activeProfile))).inspect(source))
        entry.loadRejectionReason?.let { return HostSaveResult.Rejected(it) }
        return checkNotNull(server).loadGame(source).toHostSaveResult(source)
    }

    override fun deleteSave(slot: String): HostSaveResult {
        checkThread()
        saveAccess().loadRejectionReason?.let { return HostSaveResult.Rejected(it) }
        saveStorageRejection()?.let { return HostSaveResult.Rejected(it) }
        val target = normalizedSaveSlot(slot) ?: return HostSaveResult.Rejected("Invalid save slot.")
        return try {
            deleteSaveSlot(profileRoot(checkNotNull(activeProfile)), target)
            HostSaveResult.Completed(target)
        } catch (failure: IllegalArgumentException) {
            HostSaveResult.Rejected(failure.message ?: "Invalid save directory.")
        } catch (failure: Exception) {
            HostSaveResult.Failed(failure.message ?: "Could not delete saved game.", failure)
        }
    }

    private fun saveContextRejection(): String? = when {
        disposed || server == null -> "Host is not initialized."
        activeProfile == null -> "Select a game profile first."
        else -> startupRejection()
    }

    // Browsing uses the profile directly. Backend operations still use the shared write root.
    private fun saveStorageRejection(): String? = if (
        EngineWriteRoot.path().toAbsolutePath().normalize() != profileRoot(checkNotNull(activeProfile))
    ) "The active save directory does not match the selected profile." else null

    private fun profileRoot(profile: CakeGameProfile): Path =
        profilesRoot.resolve(profile.id).toAbsolutePath().normalize()

    private fun normalizedSaveSlot(slot: String): String? = try {
        ServerSaveJsonStore.normalizeSlot(slot).takeIf { it == slot }
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun toHostSaveEntry(entry: SavedGameEntry): HostSaveEntry {
        val mode = when (entry.mode) {
            SavedGameMode.SINGLEPLAYER -> HostMode.SINGLEPLAYER
            SavedGameMode.COOP -> HostMode.COOP
            SavedGameMode.DEATHMATCH -> HostMode.DEATHMATCH
            null -> null
        }
        return HostSaveEntry(entry.slot, entry.comment, entry.mapName, entry.savedAtMillis, mode,
            entry.problem ?: if (mode != HostMode.SINGLEPLAYER) "This save is not a singleplayer game." else null,
            entry.mapTitle)
    }

    private fun ServerHost.OperationResult.toHostSaveResult(slot: String): HostSaveResult = when (this) {
        ServerHost.OperationResult.Completed -> HostSaveResult.Completed(slot)
        is ServerHost.OperationResult.Rejected -> HostSaveResult.Rejected(reason)
        is ServerHost.OperationResult.Failed -> HostSaveResult.Failed(reason, cause)
    }

    private fun configureFilesystem(profile: CakeGameProfile) {
        // Validate writable storage before changing process-wide content selection.
        val root = profilesRoot.resolve(profile.id)
        Files.createDirectories(root)
        if (!Files.isWritable(root)) throw IOException("Save directory is not writable: $root")
        val cvars = Cvar.getInstance()
        val gameMod = profile.gamemod?.takeUnless { it == "baseq2" }
        EngineVfs.configure(VfsConfig(
            Path.of(profile.basedir), "baseq2", gameMod, true, false,
            cvars.isRegistered("fs_casesensitive") && cvars.find<Boolean>("fs_casesensitive").typedValue,
            emptyList(), setOf("pak", "pk2", "pk3", "pkz", "zip"),
        ))
        cvars.register("game", CvarTypes.STRING, "", Defines.CVAR_LATCH or Defines.CVAR_SERVERINFO)
            .forceSet(gameMod.orEmpty())
        cvars.register("gamedir", CvarTypes.STRING, "", Defines.CVAR_SERVERINFO or Defines.CVAR_NOSET)
            .forceSet(gameMod.orEmpty())
        EngineWriteRoot.setRoot(root)
    }

    private fun validateProfile(profile: CakeGameProfile) {
        fun safeDirectory(value: String) = value.isNotBlank() && value != "." &&
            !value.contains("..") && value.none { it == '/' || it == '\\' || it == ':' }
        require(safeDirectory(profile.id)) { "Invalid profile id" }
        require(profile.basedir.isNotBlank() && Files.isDirectory(Path.of(profile.basedir))) {
            "Profile game directory does not exist"
        }
        require(profile.gamemod?.let(::safeDirectory) != false) { "Invalid mod directory" }
        require(profile.gamemod == null || Files.isDirectory(Path.of(profile.basedir).resolve(profile.gamemod))) {
            "Profile mod directory does not exist: ${profile.gamemod}"
        }
    }

    private fun validate(request: HostRequest, prepareOnly: Boolean) {
        validateProfile(request.profile.normalized())
        require(prepareOnly || (request.map.matches(Regex("[A-Za-z0-9_/-]+")) && !request.map.startsWith('/'))) {
            "Map must be a relative map name without an extension"
        }
        require(request.skill in 0..3) { "Difficulty must be between 0 and 3" }
        require(request.maxClients in 1..Defines.MAX_CLIENTS) { "Invalid client count" }
        require(request.hostname.isNotBlank() && request.hostname.none { it == '\\' || it == '"' || it == ';' }) {
            "Invalid hostname"
        }
    }

    private fun checkThread() {
        val thread = Thread.currentThread()
        check(ownerThread == null || ownerThread === thread) { "Host operations must run on the application thread" }
        ownerThread = thread
    }
}
