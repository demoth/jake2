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
import jake2.server.SV_MAIN
import org.demoth.cake.hosting.*
import org.demoth.cake.profile.CakeGameProfile
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** A local server session, created only after Cake has applied its client profile. */
class EmbeddedCakeHost(
    private val profilesRoot: Path = Path.of(System.getProperty("user.home"), ".cake"),
) : CakeHost {
    internal var server: SV_MAIN? = null
        private set
    private var starting = false
    private var ownerThread: Thread? = null

    /**
     * EngineWriteRoot is process-wide. Hosting temporarily redirects it to the active
     * profile so existing server save code writes there. Retain the prior root to
     * restore it on stop or failed startup, so later engine writes do not keep using
     * the ended session's profile. Null means this host has no root to restore.
     */
    private var previousWriteRoot: Path? = null

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
        try {
            validate(request, prepareOnly)
        } catch (error: IllegalArgumentException) {
            return HostStartResult.Rejected(error.message ?: "Invalid host request")
        }
        stop("Starting a new game")
        starting = true
        try {
            configureFilesystem(request)
            if (!prepareOnly && !EngineVfs.exists("maps/${request.map}.bsp")) {
                stop("Map not found")
                return HostStartResult.Rejected("Map not found: ${request.map}")
            }
            val cvars = Cvar.getInstance()
            val manager = SV_MAIN()
            server = manager
            cvars.find<Boolean>("deathmatch").forceSet(request.mode == HostMode.DEATHMATCH)
            cvars.find<Boolean>("coop").forceSet(request.mode == HostMode.COOP)
            cvars.find<Int>("maxclients").forceSet(request.maxClients)
            cvars.find<String>("hostname").forceSet(request.hostname)
            cvars.find<Boolean>("paused").forceSet(false)
            // Consumed by the new game instance's existing early-cvar transfer.
            cvars.setResult("skill", request.skill.toString())
            if (prepareOnly) return HostStartResult.Started
            if (!manager.startMap(request.map)) {
                stop("Map did not start")
                return HostStartResult.Rejected("Could not start map: ${request.map}")
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
        if (!manager.isRunning) {
            stop("Server stopped") // e.g. killserver or sv_shutdown from the console
            return
        }
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
        val manager = server
        val ownedSession = manager != null || starting
        server = null
        starting = false
        try {
            manager?.SV_Shutdown("$reason\n", false)
        } finally {
            try {
                manager?.close()
            } finally {
                if (ownedSession) {
                    // Shutdown notification is sent before sockets close.
                    NET.configureServer(false)
                    NET.clearLoopbacks()
                    Cbuf.ClearDeferred()
                    val cvars = Cvar.getInstance()
                    if (cvars.isRegistered("paused")) cvars.forceSetResult("paused", "0")
                }
                previousWriteRoot?.let(EngineWriteRoot::setRoot)
                previousWriteRoot = null
            }
        }
    }

    override fun setPaused(paused: Boolean) {
        checkThread()
        if (server?.isRunning != true) return
        val cvars = Cvar.getInstance()
        val singlePlayer = !cvars.find<Boolean>("deathmatch").typedValue &&
            !cvars.find<Boolean>("coop").typedValue && cvars.find<Int>("maxclients").typedValue == 1
        cvars.find<Boolean>("paused").forceSet(paused && singlePlayer)
    }

    private fun configureFilesystem(request: HostRequest) {
        val profile = request.profile.normalized()
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
        val root = profilesRoot.resolve(profile.id)
        Files.createDirectories(root)
        if (!Files.isWritable(root)) throw IOException("Save directory is not writable: $root")
        previousWriteRoot = EngineWriteRoot.path()
        EngineWriteRoot.setRoot(root)
    }

    private fun validate(request: HostRequest, prepareOnly: Boolean) {
        val profile = request.profile.normalized()
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
