package org.demoth.cake.fullgame

import jake2.qcommon.Defines
import jake2.qcommon.exec.Cbuf
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.CvarTypes
import jake2.qcommon.exec.find
import jake2.qcommon.longjmpException
import jake2.qcommon.network.NET
import jake2.qcommon.vfs.EngineVfs
import jake2.qcommon.vfs.EngineWriteRoot
import jake2.qcommon.vfs.VfsConfig
import jake2.server.ServerHost
import org.demoth.cake.hosting.*
import org.demoth.cake.profile.CakeGameProfile
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** Application-owned embedded Host; games may stop while commands and profile remain active. */
class EmbeddedCakeHost(
    private val profilesRoot: Path = Path.of(System.getProperty("user.home"), ".cake"),
) : CakeHost {
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
        server = ServerHost {
            when {
                disposed -> "Host has been disposed"
                activeProfile == null -> "Select a game profile before hosting."
                else -> this.startupRejection()
            }
        }
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
            if (request.mode != HostMode.SINGLEPLAYER && NET.ip_sockets[Defines.NS_SERVER] == null) {
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
            if (server != null) NET.clearLoopbacks()
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
            if (manager != null) NET.clearLoopbacks()
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
