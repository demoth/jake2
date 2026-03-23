package org.demoth.cake

import com.badlogic.gdx.*
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.profiling.GLProfiler
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.viewport.StretchViewport
import jake2.qcommon.Com
import jake2.qcommon.Defines.*
import jake2.qcommon.Globals
import jake2.qcommon.exec.Cbuf
import jake2.qcommon.exec.Cmd
import jake2.qcommon.exec.Cmd.getArguments
import jake2.qcommon.exec.Cvar
import jake2.qcommon.network.NET
import jake2.qcommon.network.Netchan
import jake2.qcommon.network.messages.ConnectionlessCommand
import jake2.qcommon.network.messages.NetworkMessage
import jake2.qcommon.network.messages.NetworkPacket
import jake2.qcommon.network.messages.client.StringCmdMessage
import jake2.qcommon.network.messages.client.UserInfoMessage
import jake2.qcommon.network.messages.server.*
import jake2.qcommon.network.netadr_t
import jake2.qcommon.network.netchan_t
import jake2.qcommon.vfs.DefaultWritableFileSystem
import jake2.qcommon.vfs.VfsDebugCommands
import ktx.app.KtxApplicationAdapter
import ktx.app.KtxInputAdapter
import ktx.assets.TextAssetLoader
import ktx.scene2d.Scene2DSkin
import org.demoth.cake.ClientNetworkState.*
import org.demoth.cake.assets.*
import org.demoth.cake.download.CakeDownloadManager
import org.demoth.cake.download.CakeDownloadQueueResult
import org.demoth.cake.download.CakeDownloadRejectReason
import org.demoth.cake.download.CakeDownloadTransfer
import org.demoth.cake.download.CakeDownloadTransferResult
import org.demoth.cake.input.InputManager
import org.demoth.cake.profile.CakeGameProfile
import org.demoth.cake.profile.CakeGameProfileStore
import org.demoth.cake.profile.CakeProfileConfigStore
import org.demoth.cake.stages.DebugGraphStage
import org.demoth.cake.stages.JoinGameStage
import org.demoth.cake.stages.MainMenuStage
import org.demoth.cake.stages.MultiplayerMenuStage
import org.demoth.cake.stages.OptionsMenuStage
import org.demoth.cake.stages.OptionsSectionStage
import org.demoth.cake.stages.PlayerSetupStage
import org.demoth.cake.stages.ProfileEditStage
import org.demoth.cake.stages.console.ConsoleStage
import org.demoth.cake.stages.ingame.Game3dScreen
import org.demoth.cake.ui.menu.*
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private enum class ClientNetworkState {
    DISCONNECTED,
    CONNECTING, // started the connection procedure
    CONNECTED, // connection established, preparing resources
    ACTIVE // game is running
}

private enum class MenuView {
    MAIN,
    PROFILE_EDIT,
    MULTIPLAYER,
    JOIN_GAME,
    PLAYER_SETUP,
    OPTIONS,
    OPTIONS_SECTION,
}

/**
 * Entrypoint for the client application
 *
 */
class Cake(
    startupContext: CakeStartupContext? = null,
) : KtxApplicationAdapter, KtxInputAdapter {
    companion object {
        private const val CONNECT_RETRY_TIMEOUT_SECONDS = 1f
        private const val CONNECTED_KEEPALIVE_TIMEOUT_MS = 1000
        private const val MIN_CLIENT_COMMAND_HZ = 10f
        private const val MAX_CLIENT_COMMAND_HZ = 125f
        private const val MAX_CLIENT_COMMAND_CATCHUP_STEPS = 4
        private const val PROFILE_BACKGROUND_PATH = "pics/conback.pcx"
        private val SCREENSHOT_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")
        // Cross-layout console toggle fallbacks.
        // `GRAVE` covers the common physical key; some macOS/international layouts report printable chars instead.
        private val CONSOLE_TOGGLE_CHARS = setOf('`', '§')
    }

    private lateinit var menuStage: MainMenuStage
    private lateinit var profileEditStage: ProfileEditStage
    private lateinit var multiplayerMenuStage: MultiplayerMenuStage
    private lateinit var joinGameStage: JoinGameStage
    private lateinit var playerSetupStage: PlayerSetupStage
    private lateinit var optionsMenuStage: OptionsMenuStage
    private lateinit var optionsSectionStage: OptionsSectionStage
    private lateinit var consoleStage: ConsoleStage
    private lateinit var debugGraphStage: DebugGraphStage
    private lateinit var glProfiler: GLProfiler
    private var glProfilerActive: Boolean = false
    private var physicalConsoleToggleKeyDown: Boolean = false
    private lateinit var viewport: StretchViewport
    private var profileBackgroundBatch: SpriteBatch? = null
    private var profileBackgroundTexture: Texture? = null
    private var transitionBackdropBatch: SpriteBatch? = null
    private var transitionBackdropTexture: Texture? = null
    private var transitionBackdropRegion: TextureRegion? = null
    private var transitionBackdropActive = false

    // whenever these are changed, input handlers should be updated
    private var consoleVisible = false
        set(value) {
            field = value
            updateInputHandlers(field, menuVisible)
        }

    private var menuVisible = true
        set(value) {
            field = value
            updateInputHandlers(consoleVisible, field)
        }
    private var menuView = MenuView.MAIN

    // network
    private var networkState = DISCONNECTED
    private var servername = "localhost"
    private var challenge = 0
    private var reconnectTimeout = 0f // fire first connect attempt immediately
    private val netchan = netchan_t()

    private var game3dScreen: Game3dScreen? = null
        set(value) {
            field = value
            // allow the game screen to receive the input
            updateInputHandlers(consoleVisible, menuVisible)
        }

    // During map change, the previous screen is disposed first but its config assets are kept alive
    // until the new screen finishes precache. This avoids unload->reload churn for shared assets.
    private var deferredConfigUnloadScreen: Game3dScreen? = null

    // Session-wide runtime bindings. Kept at app scope so reconnect/map transitions do not reset binds.
    // Per-mod binding persistence is not implemented yet.
    private val startup = startupContext ?: CakeStartupBootstrap.bootstrap()
    private val clientBindings = startup.clientBindings
    private val gameProfileStore = CakeGameProfileStore()
    private val profileConfigStore = CakeProfileConfigStore()
    private val downloadManager = CakeDownloadManager()
    private val downloadTransfer = CakeDownloadTransfer()
    private val networkDebugSampler = NetworkDebugSampler()
    private var clientCommandExtraMs = 0f
    private var pendingPrecacheSpawnCount: Int? = null
    private var lastDownloadProgressPercent: Int = -1
    private var activeGameProfile: CakeGameProfile? = null
    private var fileResolver = CakeFileResolver()
    private var cachedPlayerSetupCatalog: PlayerSetupCatalog? = null
    private val menuEventBus = MenuEventBus()
    private lateinit var menuController: MenuController

    private val assetManager = AssetManager(fileResolver).apply {
        // for loading shaders and other text files
        setLoader(String::class.java, TextAssetLoader(fileResolver))
        // for loading binary files
        setLoader(Any::class.java, ObjectLoader(fileResolver))
        // for loading binary blobs from the filesystem (e.g. BSP maps)
        setLoader(ByteArray::class.java, ByteArrayLoader(fileResolver))
        setLoader(Sound::class.java, ConvertingSoundLoader(fileResolver))
        setLoader(Texture::class.java, "pcx", PcxLoader(fileResolver))
        setLoader(Texture::class.java, "wal", WalLoader(fileResolver))
        setLoader(BspMapAsset::class.java, "bsp", BspLoader(fileResolver))
        setLoader(Md2Asset::class.java, "md2", Md2Loader(fileResolver))
        setLoader(Sp2Asset::class.java, "sp2", Sp2Loader(fileResolver))
        setLoader(Model::class.java, "sky", SkyLoader(fileResolver))

    }
    private var backgroundColor = Color.BLACK
    private val clDebugStufftext = Cvar.getInstance().Get("cl_debug_stufftext", "0", 0, "Log raw server stufftext commands")
    private val clMaxfps = Cvar.getInstance().Get("cl_maxfps", "62", 0)

    init {
        CakeStartupBootstrap.ensureInitialized()
        Cbuf.AddText("set thinclient 1")
        Netchan.Netchan_Init()
    }

    override fun create() {
        initializeShaderCompatibility()
        applyGameProfile(startup.activeProfile)

        // load sync resources - required immediately
        assetManager.load(cakeSkin, Skin::class.java)

        // load async resources (will be used later in the game)

        assetManager.load("q2palette.bin", Any::class.java) // todo: use original baseq2/pics/colormap.pcx
        assetManager.load(md2VertexShader, String::class.java)
        assetManager.load(md2FragmentShader, String::class.java)
        assetManager.load(postProcessVertexShader, String::class.java)
        assetManager.load(postProcessFragmentShader, String::class.java)
        assetManager.finishLoading() // these assets are necessary anyway

        Scene2DSkin.defaultSkin = assetManager.get(cakeSkin, Skin::class.java)
        backgroundColor = Scene2DSkin.defaultSkin.getColor("background")
        // doesn't really stretch because we don't yet allow the window to freely resize
        viewport = StretchViewport(Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        menuController = MenuController(
            backend = createMenuBackend(),
            bus = menuEventBus,
        )
        menuStage = MainMenuStage(
            viewport = viewport,
            menuEventBus = menuEventBus,
        ) // fixme: cvar
        profileEditStage = ProfileEditStage(
            viewport = viewport,
            menuEventBus = menuEventBus,
        )
        multiplayerMenuStage = MultiplayerMenuStage(
            viewport = viewport,
            menuEventBus = menuEventBus,
        )
        joinGameStage = JoinGameStage(
            viewport = viewport,
            menuEventBus = menuEventBus,
        )
        playerSetupStage = PlayerSetupStage(
            viewport = viewport,
            menuEventBus = menuEventBus,
        )
        optionsMenuStage = OptionsMenuStage(
            viewport = viewport,
            menuEventBus = menuEventBus,
        )
        optionsSectionStage = OptionsSectionStage(
            viewport = viewport,
            menuEventBus = menuEventBus,
        )
        // todo: gather all early logging (which is generated before the console is created)
        // and put into the console when it's ready
        consoleStage = ConsoleStage(viewport)
        debugGraphStage = DebugGraphStage(viewport)
        profileBackgroundBatch = SpriteBatch()
        transitionBackdropBatch = SpriteBatch()
        glProfiler = GLProfiler(Gdx.graphics).apply {
            disable()
            reset()
        }
        menuController.initialize()
        openStartupProfileEditorIfNeeded()

        updateInputHandlers(false, true)

        VfsDebugCommands.register(object : VfsDebugCommands.Provider {
            override fun isInitialized(): Boolean = fileResolver.isVfsInitialized()

            override fun resolvedFiles(): List<String> = fileResolver.debugResolvedFiles()

            override fun mounts(): List<String> = fileResolver.debugMounts()

            override fun overrides(): List<String> = fileResolver.debugOverrides()
        })

        // region COMMANDS

        Cmd.AddCommand("quit", "Exit Cake") {
            saveActiveProfileConfig()
            disconnect()
            Gdx.app.exit()
        }

        /**
         * Changing_f
         *
         * Just sent as a hint to the client that they should drop to full console.
         */
        Cmd.AddCommand("changing", "(internal) Enter map-change transition state") {
            networkState = CONNECTED
            // todo: indicate somehow about the map changing (loading screen or spinner)
            // SCR.BeginLoadingPlaque();
            Com.Printf("\nChanging map...\n")

            beginMapTransitionRetainingConfigAssets()
        }

        // Important: like a svc_reconnect but more lightweight
        Cmd.AddCommand("reconnect", "Reconnect to the current server") {
            Com.Printf("Reconnecting..\n")

            if (networkState == CONNECTED) { // set on "changing" command
                Com.Printf("Sending 'new' to server\n")
                netchan.reliablePending.add(StringCmdMessage(StringCmdMessage.NEW))

            } else if (servername.isNotEmpty()) { // todo: check this condition
                disconnect()
                // CheckForResend() will fire immediately
                reconnectTimeout = 0f
                networkState = CONNECTING
            }

        }

        Cmd.AddCommand("connect", "Connect to a remote server") {
            val server = sanitizeConnectTarget(it)
            if (server == null) {
                Com.Printf("usage: connect <server>\n")
                return@AddCommand
            }
            Com.Printf("Connecting to $server...\n")
            // first disconnect
            disconnect()

            NET.Config(true) // allow remote
            servername = server
            networkState = CONNECTING
            reconnectTimeout = 0f
            // picked up later in the CheckForResend() // fixme: why not connect immediately?
        }

        /*
         * Rcon_f
         *
         * Send the rest of the command line over as an unconnected command.
         */
        Cmd.AddCommand("rcon", "Send a remote console command (requires rcon_password)") { args ->
            val rconPassword = Cvar.getInstance().VariableString("rcon_password")
            if (rconPassword.isEmpty()) {
                Com.Printf("You must set 'rcon_password' before\nissuing an rcon command.\n")
                return@AddCommand
            }

            // allow remote
            NET.Config(true)

            // assemble password and arguments into a string and send
            val message = buildString {
                append(" ")
                append(rconPassword)
                if (args.size > 1) {
                    append(" ")
                    append(getArguments(args))
                }
            }

            val to = if (networkState == CONNECTED || networkState == ACTIVE) {
                netchan.remote_address
            } else {
                val rconAddress = Cvar.getInstance().VariableString("rcon_address")
                if (rconAddress.isEmpty()) {
                    Com.Printf("You must either be connected,\nor set the 'rcon_address' cvar\nto issue rcon commands\n")
                    return@AddCommand
                }
                netadr_t.fromString(rconAddress, PORT_SERVER) ?: run {
                    Com.Warn("Bad rcon_address\n")
                    return@AddCommand
                }
            }

            Netchan.sendConnectionlessPacket(NS_CLIENT, to, ConnectionlessCommand.rcon, message)
        }

        Cmd.AddCommand("disconnect", "Disconnect from the current server") { disconnect() }

        Cmd.AddCommand("userinfo", "Print the current userinfo string") {
            val userInfo = Cvar.getInstance().Userinfo()
            Com.Println("Userinfo: $userInfo")
        }

        Cmd.AddCommand("jvm_info", "Print JVM version and memory information") {
            Com.Println("Version: ${Runtime.version()}")
            val rt = Runtime.getRuntime()
            Com.Println("Free: ${rt.freeMemory() / 1024 / 1024} MB")
            Com.Println("Used: ${(rt.totalMemory() - rt.freeMemory()) / 1024 / 1024} MB")
            Com.Println("Total: ${rt.totalMemory() / 1024 / 1024} MB")
            Com.Println("Max: ${rt.maxMemory() / 1024 / 1024} MB")
        }

        Cmd.AddCommand("print_cbuf", "Print current command buffer contents") {
            Com.Println(Cbuf.contents())
        }

        Cmd.AddCommand("screenshot", "Save a screenshot to the active profile") {
            takeScreenshot()
        }

        Cmd.AddCommand("cake_profile_set", "Switch the active Cake game profile") { args ->
            setActiveGameProfile(args)
        }

        Cmd.AddCommand("writeconfig", "Write profile-local config and bindings") {
            saveActiveProfileConfig()
        }

        /*
         * Adds the current command line as a clc_stringcmd to the client message.
         * things like godmode, noclip, etc, are commands directed to the server, so
         * when they are typed in at the console, they will need to be forwarded.
         *
         * see jake2.server.SV_MAIN#SV_ExecuteUserCommand(jake2.server.client_t, java.lang.String)
         */
        Cmd.AddCommand("cmd", "(internal) Forward a command to the connected server") {
            if (networkState != CONNECTED && networkState != ACTIVE) {
                Com.Warn("Cannot cmd '${it}', not connected")
            } else {
                if (it.size > 1)
                    netchan.reliablePending.add(StringCmdMessage(getArguments(it)))
                else
                    Com.Warn("Empty cmd") // todo: warning
            }
        }

        Cmd.AddCommand("precache", "(internal) Finish client precache for the active map") {
            if (networkState == ACTIVE) {
                // tothink: do we ignore it or restart the map?
                Com.Warn("precache - during the game!\n") // todo: warning
                return@AddCommand
            }
            val precache_spawncount = it[1].toInt()

            val activeScreen = game3dScreen
            if (activeScreen == null) {
                Com.Warn("precache called without an active game screen")
                return@AddCommand
            }
            try {
                activeScreen.precache()
            } catch (error: RuntimeException) {
                val missingResource = findMissingResourceFailure(error)
                if (missingResource != null && beginMissingResourceDownload(missingResource, precache_spawncount)) {
                    return@AddCommand
                }
                throw error
            }
            releaseDeferredConfigUnload()
            pendingPrecacheSpawnCount = null

            // we are ready to start the game!
            netchan.reliablePending.add(StringCmdMessage(StringCmdMessage.BEGIN + " " + precache_spawncount + "\n"))
        }

        // endregion

    }

    private fun disconnect() {
        Com.Printf("Disconnecting from server...\n")
        // todo: clear the game state and release resources
        game3dScreen?.stopAudio()
        disposeGame3dScreen()
        releaseDeferredConfigUnload()
        clearTransitionBackdrop()

        // send a disconnect message to the server
        transmitAndRecord(listOf(StringCmdMessage(StringCmdMessage.DISCONNECT)))

        // reset network state
        NET.Config(false)
        networkState = DISCONNECTED
        challenge = 0
        clientCommandExtraMs = 0f
        resetDownloadState()
    }

    private fun beginMissingResourceDownload(error: MissingResourceException, spawnCount: Int): Boolean {
        pendingPrecacheSpawnCount = spawnCount
        return when (val result = downloadManager.enqueue(error.path, fileResolver.gamemod)) {
            is CakeDownloadQueueResult.Enqueued -> {
                startQueuedDownloadIfIdle()
                true
            }

            is CakeDownloadQueueResult.Rejected -> {
                when (result.reason) {
                    CakeDownloadRejectReason.ALREADY_QUEUED -> true
                    CakeDownloadRejectReason.DOWNLOADS_DISABLED,
                    CakeDownloadRejectReason.CATEGORY_DISABLED -> {
                        Com.Warn("Autodownload disabled for missing resource '${error.path}'\n")
                        false
                    }

                    CakeDownloadRejectReason.INVALID_PATH -> {
                        Com.Warn("Refusing to autodownload invalid path '${error.path}'\n")
                        false
                    }

                    CakeDownloadRejectReason.UNSUPPORTED_CATEGORY -> {
                        Com.Warn("No Cake autodownload support yet for '${error.path}'\n")
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
            netchan.reliablePending.add(StringCmdMessage("${StringCmdMessage.DOWNLOAD} ${request.logicalPath} $offset"))
        } else {
            Com.Printf("Downloading ${request.logicalPath}\n")
            netchan.reliablePending.add(StringCmdMessage("${StringCmdMessage.DOWNLOAD} ${request.logicalPath}"))
        }
        return true
    }

    private fun processDownloadMessage(message: DownloadMessage) {
        if (downloadTransfer.activeRequest() == null) {
            Com.Warn("Received download data without an active request\n")
            return
        }

        when (val result = downloadTransfer.handle(message)) {
            is CakeDownloadTransferResult.Continue -> {
                maybeLogDownloadProgress(result.request.logicalPath, result.percent)
                netchan.reliablePending.add(StringCmdMessage(StringCmdMessage.NEXT_DOWNLOAD))
            }

            is CakeDownloadTransferResult.Completed -> {
                maybeLogDownloadProgress(result.request.logicalPath, 100)
                Com.Printf("Downloaded ${result.request.logicalPath}\n")
                if (!startQueuedDownloadIfIdle()) {
                    pendingPrecacheSpawnCount?.let { spawnCount ->
                        Cbuf.AddText("${StringCmdMessage.PRECACHE} $spawnCount\n")
                    }
                }
            }

            is CakeDownloadTransferResult.MissingOnServer -> {
                Com.Warn("Server does not have ${result.request.logicalPath}\n")
                dropToConsole()
            }
        }
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

    private fun transmitAndRecord(unreliable: Collection<NetworkMessage>?) {
        netchan.transmit(unreliable)
        networkDebugSampler.recordOutbound(netchan.last_sent_size, Globals.curtime)
    }

    private fun handleMissingResourceFailure(error: MissingResourceException) {
        Com.Warn("${error.message}\n")
        dropToConsole()
    }

    private fun findMissingResourceFailure(error: Throwable): MissingResourceException? {
        var current: Throwable? = error
        while (current != null) {
            if (current is MissingResourceException) {
                return current
            }
            current = current.cause
        }
        return null
    }

    private fun dropToConsole() {
        disconnect()
        menuVisible = false
        consoleVisible = true
        consoleStage.focus()
        updateInputHandlers(consoleVisible, menuVisible)
    }

    private fun clearProfileBackground() {
        profileBackgroundTexture = null
        if (assetManager.isLoaded(PROFILE_BACKGROUND_PATH, Texture::class.java)) {
            assetManager.unload(PROFILE_BACKGROUND_PATH)
        }
    }

    private fun reloadProfileBackground() {
        clearProfileBackground()

        if (fileResolver.tryResolve(PROFILE_BACKGROUND_PATH) == null) {
            return
        }

        try {
            assetManager.load(PROFILE_BACKGROUND_PATH, Texture::class.java)
            assetManager.finishLoadingAsset<Texture>(PROFILE_BACKGROUND_PATH)
            profileBackgroundTexture = assetManager.get(PROFILE_BACKGROUND_PATH, Texture::class.java)
        } catch (error: RuntimeException) {
            clearProfileBackground()
            Com.Warn("Failed to load profile background '$PROFILE_BACKGROUND_PATH': ${error.message}\n")
        }
    }

    private fun renderProfileBackground() {
        val texture = profileBackgroundTexture ?: return
        val batch = profileBackgroundBatch ?: return
        batch.begin()
        batch.draw(texture, 0f, 0f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        batch.end()
    }

    // whenever we change the visibility of the console or the menu, we should update the set of input handlers
    // (in other words, which components receive the input events and which don't)
    private fun updateInputHandlers(consoleVisible: Boolean, menuVisible: Boolean) {
        Gdx.input.isCursorCatched = !menuVisible && !consoleVisible
        game3dScreen?.clearInputState()

        val inputProcessor: InputProcessor = when {
            consoleVisible -> consoleStage
            menuVisible -> when (menuView) {
                MenuView.MAIN -> menuStage
                MenuView.PROFILE_EDIT -> profileEditStage
                MenuView.MULTIPLAYER -> multiplayerMenuStage
                MenuView.JOIN_GAME -> joinGameStage
                MenuView.PLAYER_SETUP -> playerSetupStage
                MenuView.OPTIONS -> optionsMenuStage
                MenuView.OPTIONS_SECTION -> optionsSectionStage
            }

            else -> {
                // delegate to the game screen
                game3dScreen?.let { screen -> object : InputProcessor by screen {} }
                    ?: InputAdapter() // or to an empty stub when no game is running
            }
        }
        // delegate the rest to the current 3d screen
        Gdx.input.inputProcessor = InputMultiplexer(
            this, // global input processor to control console and menu
            inputProcessor
        )
    }

    override fun render() {
        try {
            updateGlProfilerState()
            menuController.pumpIntents()
            menuController.refreshExternalState()
            syncMenuViewFromBusState()
            assetManager.update() // todo: 1000/fps millis
            val deltaSeconds = Gdx.graphics.deltaTime
            Globals.curtime += (deltaSeconds * 1000f).toInt() // todo: get rid of globals!
            game3dScreen?.deltaTime = deltaSeconds
            ScreenUtils.clear(backgroundColor.r, backgroundColor.g, backgroundColor.b, 1f, true)
            if (game3dScreen == null) {
                renderProfileBackground()
            }

            // Match the legacy client ordering: process incoming packets and any stuffed commands first,
            // then react in the same frame by sending reliable/new packets or a reconnect challenge.
            CL_ReadPackets()
            Cbuf.Execute()
            game3dScreen?.updateLocalInput(deltaSeconds)
            runClientCommandSteps(deltaSeconds)
            CheckForResend(deltaSeconds)

            if (game3dScreen != null) {
                // Keep prediction state aligned with netchan before render-time replay.
                // Cross-reference: old client reads `cls.netchan.incoming_acknowledged/outgoing_sequence`
                // in `CL_pred.PredictMovement`.
                game3dScreen?.updatePredictionNetworkState(
                    netchan.incoming_acknowledged,
                    netchan.outgoing_sequence,
                    Globals.curtime
                )
                game3dScreen?.render(deltaSeconds)
            }

            val activeScreen = game3dScreen
            if (transitionBackdropActive) {
                if (activeScreen != null && activeScreen.canRenderPresentationFrame()) {
                    clearTransitionBackdrop()
                } else {
                    renderTransitionBackdrop()
                }
            }

            if (menuVisible) {
                when (menuView) {
                    MenuView.MAIN -> {
                        menuStage.act()
                        menuStage.draw()
                    }

                    MenuView.PROFILE_EDIT -> {
                        profileEditStage.act(deltaSeconds)
                        profileEditStage.draw()
                    }

                    MenuView.MULTIPLAYER -> {
                        multiplayerMenuStage.act(deltaSeconds)
                        multiplayerMenuStage.draw()
                    }

                    MenuView.JOIN_GAME -> {
                        joinGameStage.act(deltaSeconds)
                        joinGameStage.draw()
                    }

                    MenuView.PLAYER_SETUP -> {
                        playerSetupStage.act(deltaSeconds)
                        playerSetupStage.draw()
                    }
                    MenuView.OPTIONS -> {
                        optionsMenuStage.act(deltaSeconds)
                        optionsMenuStage.draw()
                    }
                    MenuView.OPTIONS_SECTION -> {
                        optionsSectionStage.act(deltaSeconds)
                        optionsSectionStage.draw()
                    }
                }
            }

            if (consoleVisible) {
                consoleStage.act()
                consoleStage.draw()
            }

            if (debugGraphStage.hasEnabledMetrics()) {
                val profiler = if (glProfilerActive) glProfiler else null
                val networkSnapshot = networkDebugSampler.snapshot(Globals.curtime, netchan.smoothed_ping_ms)
                debugGraphStage.collectMetrics(profiler, networkSnapshot)
                debugGraphStage.act(deltaSeconds)
                debugGraphStage.draw()
                if (glProfilerActive) {
                    glProfiler.reset()
                }
            }
        } catch (error: RuntimeException) {
            val missingResource = findMissingResourceFailure(error)
            if (missingResource != null) {
                handleMissingResourceFailure(missingResource)
                return
            }
            throw error
        }
    }

    @Suppress("GDXKotlinProfilingCode")
    private fun updateGlProfilerState() {
        val shouldEnableProfiler = debugGraphStage.hasEnabledGlMetrics()
        if (shouldEnableProfiler == glProfilerActive) {
            return
        }
        if (shouldEnableProfiler) {
            glProfiler.enable()
            glProfiler.reset()
        } else {
            glProfiler.disable()
        }
        glProfilerActive = shouldEnableProfiler
    }

    /**
     * SendCommands
     */
    private fun sendUpdates() {
        game3dScreen?.pollCinematicSkipCommand(Globals.curtime)?.let { skipCommand ->
            netchan.reliablePending.add(skipCommand)
        }

        when (networkState) {
            CONNECTING, DISCONNECTED -> return
            CONNECTED -> {
                if (netchan.reliablePending.isNotEmpty() || Globals.curtime - netchan.last_sent > CONNECTED_KEEPALIVE_TIMEOUT_MS) {
                    // send pending reliable messages (e.g. "new") immediately
                    transmitAndRecord(null)
                }
            }

            ACTIVE -> {
                queueUserInfoUpdateIfNeeded()
                game3dScreen?.buildMoveMessage(netchan.outgoing_sequence, currentClientCommandMsec())?.let {
                    transmitAndRecord(listOf(it))
                }
            }
        }

    }

    private fun runClientCommandSteps(deltaSeconds: Float) {
        if (networkState != CONNECTED && networkState != ACTIVE) {
            clientCommandExtraMs = 0f
            return
        }

        clientCommandExtraMs += deltaSeconds * 1000f
        val stepMs = currentClientCommandStepMs()
        if (clientCommandExtraMs > stepMs * MAX_CLIENT_COMMAND_CATCHUP_STEPS) {
            clientCommandExtraMs = stepMs
        }

        var steps = 0
        while (clientCommandExtraMs >= stepMs && steps < MAX_CLIENT_COMMAND_CATCHUP_STEPS) {
            sendUpdates()
            clientCommandExtraMs -= stepMs
            steps++
        }
    }

    private fun currentClientCommandStepMs(): Float {
        val hz = clMaxfps.value.coerceIn(MIN_CLIENT_COMMAND_HZ, MAX_CLIENT_COMMAND_HZ)
        return 1000f / hz
    }

    private fun currentClientCommandMsec(): Int =
        currentClientCommandStepMs().toInt().coerceAtLeast(1)

    @Suppress("DEPRECATION")
    private fun queueUserInfoUpdateIfNeeded() {
        if (Globals.userinfo_modified) {
            netchan.reliablePending.add(UserInfoMessage(Cvar.getInstance().Userinfo()))
            Globals.userinfo_modified = false
        }
    }

    override fun keyDown(keycode: Int): Boolean {
        if (keycode == Input.Keys.GRAVE) {
            physicalConsoleToggleKeyDown = true
        }
        return false
    }

    /**
     * Global input handling: Control Console and Menu
     * Hardwired controls.
     */
    override fun keyUp(keycode: Int): Boolean {
        when (keycode) {
            Input.Keys.GRAVE -> {
                physicalConsoleToggleKeyDown = false
                toggleConsoleVisibility()
                return true
            }

            Input.Keys.F12 -> {
                takeScreenshot()
            }

            Input.Keys.ESCAPE -> {
                if (!consoleVisible && !menuVisible && game3dScreen != null) {
                    menuVisible = true
                    menuEventBus.postIntent(MenuIntent.OpenMainMenu)
                    menuController.pumpIntents()
                    syncMenuViewFromBusState()
                    updateInputHandlers(consoleVisible, menuVisible)
                    return true
                }
                return false
            }

            else -> return false
        }
        return true
    }

    override fun keyTyped(character: Char): Boolean {
        if (character in CONSOLE_TOGGLE_CHARS) {
            if (physicalConsoleToggleKeyDown) {
                return true
            }
            toggleConsoleVisibility()
            return true
        }
        return false
    }

    private fun toggleConsoleVisibility() {
        if (consoleVisible) {
            consoleVisible = false
            if (game3dScreen == null) {
                menuVisible = true
            }
        } else {
            menuVisible = false
            consoleVisible = true
            consoleStage.focus()
        }
    }

    // fixme is it called from somewhere?
    override fun dispose() {
        saveActiveProfileConfig()
        menuStage.dispose()
        profileEditStage.dispose()
        multiplayerMenuStage.dispose()
        joinGameStage.dispose()
        playerSetupStage.dispose()
        optionsMenuStage.dispose()
        optionsSectionStage.dispose()
        consoleStage.dispose()
        debugGraphStage.dispose()
        clearProfileBackground()
        profileBackgroundBatch?.dispose()
        profileBackgroundBatch = null
        clearTransitionBackdrop()
        transitionBackdropBatch?.dispose()
        transitionBackdropBatch = null
        if (glProfilerActive) {
            glProfiler.disable()
            glProfilerActive = false
        }
        disposeGame3dScreen()
        releaseDeferredConfigUnload()
    }

    /**
     * Dispose the active game screen. Config asset unloading is optional to support map transition
     * handover where old assets stay alive until new precache completes.
     */
    private fun disposeGame3dScreen(unloadConfigAssets: Boolean = true): Game3dScreen? {
        val screen = game3dScreen
        if (screen != null) {
            if (unloadConfigAssets) {
                screen.unloadConfigAssets()
            }
            screen.dispose()
        }
        game3dScreen = null
        return screen
    }

    /**
     * Finalize deferred old-screen retirement by unloading its configuration-owned assets.
     */
    private fun releaseDeferredConfigUnload() {
        deferredConfigUnloadScreen?.unloadConfigAssets()
        deferredConfigUnloadScreen = null
    }

    /**
     * CheckForResend
     *
     * Resend a connect message if the last one has timed out.
     */
    private fun CheckForResend(deltaSeconds: Float) {
        // resend if we haven't gotten a reply yet
        if (networkState != CONNECTING)
            return

        val adr = netadr_t.fromString(servername, PORT_SERVER)
        if (adr == null) {
            Com.Warn("Bad server address\n")
            networkState = DISCONNECTED
            reconnectTimeout = 0f
            return
        }
        if (reconnectTimeout <= 0f) {
            Com.Printf("${"CheckForResend ${networkState}: Connecting to $servername"}...\n")
            Netchan.sendConnectionlessPacket(NS_CLIENT, adr, ConnectionlessCommand.getchallenge, "\n")
            reconnectTimeout = CONNECT_RETRY_TIMEOUT_SECONDS
        } else {
            reconnectTimeout -= deltaSeconds
        }
    }

    @Suppress("DEPRECATION")
    private fun SendConnectPacket() {
        val adr = netadr_t.fromString(servername, PORT_SERVER)
        if (adr == null) {
            Com.Warn("Bad server address\n")
            networkState = DISCONNECTED
            reconnectTimeout = 0f
            return
        }

        val port = Cvar.getInstance().VariableValue("qport").toInt()
        Globals.userinfo_modified = false

        Netchan.sendConnectionlessPacket(
            NS_CLIENT,
            adr,
            ConnectionlessCommand.connect,
            " $PROTOCOL_VERSION $port $challenge \"${Cvar.getInstance().Userinfo()}\"\n"
        )
    }

    private fun CL_ReadPackets() {
        while (true) {
            val networkPacket = NET.receiveNetworkPacket(
                NET.ip_sockets[NS_CLIENT],
                NET.ip_channels[NS_CLIENT],
                NET.loopbacks[NS_CLIENT],
                false
            )

            if (networkPacket == null) break

            if (networkPacket.isConnectionless) {
                CL_ConnectionlessPacket(networkPacket)
                continue
            }

            if (networkState == CONNECTING || networkState == DISCONNECTED) {
                // dump it if not connected
                continue
            }
            //
            // packet from server
            //
            if (!networkPacket.from.compareIp(netchan.remote_address)) {
                Com.Warn(networkPacket.from.toString() + ": sequenced packet without connection\n")
                continue
            }

            if (netchan.accept(networkPacket)) {
                networkDebugSampler.recordInbound(netchan.last_received_size, Globals.curtime)
                parseServerMessage(networkPacket.parseBodyFromServer())
            } //else wasn't accepted for some reason


        }
    }

    /**
     * CL_ParseServerMessage
     */
    private fun parseServerMessage(messages: Collection<ServerMessage>) {
        // Important ordering quirk: accept() updates netchan ack from packet header before parsing
        // payload messages. We push those values into prediction first, mirroring old `CL_ReadPackets`
        // + `CL_pred.CheckPredictionError/PredictMovement` data flow.
        game3dScreen?.updatePredictionNetworkState(
            netchan.incoming_acknowledged,
            netchan.outgoing_sequence,
            Globals.curtime
        )
        messages.forEach { msg ->
            when (msg) {
                is DisconnectMessage -> {
                    Com.Printf("Server disconnected\n")
                    dropToConsole()
                    return
                }

                // NB: it is not sent during map change (instead - StuffTextMessage("reconnect"))
                // sent on server restart
                is ReconnectMessage -> {
                    Com.Printf("ReconnectMessage: Reconnecting..\n")
                    beginMapTransitionRetainingConfigAssets()
                    networkState = CONNECTING
                    // CheckForResend() will fire immediately
                    reconnectTimeout = 0f
                }

                is StuffTextMessage -> {
                    if (clDebugStufftext.value != 0f) {
                        Com.Printf("svc_stufftext: ${Com.makePrintable(msg.text)}\n")
                    }
                    val filtered = filterServerStuffText(msg.text)
                    filtered.rejectedCommands.forEach {
                        Com.Warn("Ignoring unexpected server stufftext command: '${Com.makePrintable(it)}'")
                    }
                    if (filtered.acceptedCommands.isNotEmpty()) {
                        Cbuf.AddText(filtered.acceptedCommands.joinToString(separator = "\n", postfix = "\n"))
                    }
                }

                is ServerDataMessage -> {
                    Com.Printf("joining ${msg.levelString}\n")

                    // new game is starting
                    Cbuf.Execute()
                    resetClientStateForServerData()
                    netchan.reliablePending.clear()

                    if (!msg.gameName.isNullOrBlank()) {
                        fileResolver.gamemod = msg.gameName
                    }

                    game3dScreen?.processServerDataMessage(msg)
                    // networkState = CONNECTED // fixme: required?
                    consoleVisible = false
                    menuVisible = false

                }

                // delegate all game-related updates to the game screen
                is FrameHeaderMessage -> {
                    game3dScreen?.processServerFrameHeader(msg)
                }

                is ConfigStringMessage -> {
                    game3dScreen?.processConfigStringMessage(msg)
                }

                is DownloadMessage -> {
                    msg.data?.size?.takeIf { it > 0 }?.let { bytes ->
                        networkDebugSampler.recordDownload(bytes, Globals.curtime)
                    }
                    processDownloadMessage(msg)
                }

                is SoundMessage -> {
                    game3dScreen?.processSoundMessage(msg)
                }

                is SpawnBaselineMessage -> {
                    game3dScreen?.processBaselineMessage(msg)
                }

                is WeaponSoundMessage -> {
                    game3dScreen?.processWeaponSoundMessage(msg)
                }

                is MuzzleFlash2Message -> {
                    game3dScreen?.processMuzzleFlash2Message(msg)
                }

                is TEMessage -> {
                    game3dScreen?.processTempEntityMessage(msg)
                }

                is PacketEntitiesMessage -> {
                    if (game3dScreen?.processPacketEntitiesMessage(msg) == true) {
                        if (networkState != ACTIVE) {
                            networkState = ACTIVE
                            Com.Printf("Game started!\n")

                            // todo: check the rest of the player view related code from jake2.client.CL_ents#parsePacketEntities
                        }
                    }
                }

                is PlayerInfoMessage -> {
                    game3dScreen?.processPlayerInfoMessage(msg)
                }

                is PrintMessage -> {
                    game3dScreen?.processPrintMessage(msg)
                }

                is PrintCenterMessage -> {
                    game3dScreen?.processPrintCenterMessage(msg)
                }

                is LayoutMessage -> {
                    game3dScreen?.processLayoutMessage(msg)
                }

                is InventoryMessage -> {
                    game3dScreen?.processInventoryMessage(msg)
                }

            }
        }
    }

    /**
     * Reset local client state for a fresh serverdata sequence.
     *
     * Mirrors legacy CL_ParseServerData -> CL.ClearState behavior, which is required
     * for reconnect-based map changes (e.g. `rcon map`).
     */
    private fun resetClientStateForServerData() {
        resetDownloadState()
        game3dScreen?.stopAudio()
        // If serverdata arrives while a screen is still active, stage transition first.
        if (game3dScreen != null) {
            beginMapTransitionRetainingConfigAssets()
        }
        if (game3dScreen == null) {
            game3dScreen = Game3dScreen(assetManager, InputManager(bindings = clientBindings))
            debugGraphStage.resetMetrics()
        }
    }

    private fun resetDownloadState() {
        downloadManager.clear()
        downloadTransfer.reset()
        networkDebugSampler.clear()
        pendingPrecacheSpawnCount = null
        lastDownloadProgressPercent = -1
    }

    /**
     * Stage a map transition while keeping old config-owned resources alive until the next map finishes precache.
     */
    private fun beginMapTransitionRetainingConfigAssets() {
        // if a prior transition was still deferred, retire it first
        releaseDeferredConfigUnload()
        captureTransitionBackdropFromActiveScreen()
        game3dScreen?.stopAudio()
        deferredConfigUnloadScreen = disposeGame3dScreen(unloadConfigAssets = false)
    }

    /**
     * Captures one frozen frame from the active gameplay screen.
     *
     * This is used as a light-weight visual bridge during level transitions.
     */
    private fun captureTransitionBackdropFromActiveScreen() {
        val scenePixmap = game3dScreen?.captureScenePixmapForTransition() ?: return
        transitionBackdropTexture?.dispose()
        val snapshotTexture = Texture(scenePixmap)
        scenePixmap.dispose()

        transitionBackdropTexture = snapshotTexture
        transitionBackdropRegion = TextureRegion(snapshotTexture).also { region ->
            // Framebuffer snapshots are upside-down in screen space.
            region.flip(false, true)
        }
        transitionBackdropActive = true
    }

    /**
     * Draws the frozen transition backdrop while the next gameplay screen is not ready yet.
     */
    private fun renderTransitionBackdrop() {
        val backdropRegion = transitionBackdropRegion ?: return
        val batch = transitionBackdropBatch ?: return
        batch.begin()
        batch.draw(
            backdropRegion,
            0f,
            0f,
            Gdx.graphics.width.toFloat(),
            Gdx.graphics.height.toFloat(),
        )
        batch.end()
    }

    /**
     * Releases transition backdrop resources.
     */
    private fun clearTransitionBackdrop() {
        transitionBackdropRegion = null
        transitionBackdropTexture?.dispose()
        transitionBackdropTexture = null
        transitionBackdropActive = false
    }


    private fun CL_ConnectionlessPacket(packet: NetworkPacket) {
        val args = Cmd.TokenizeString(packet.connectionlessMessage, false)
        val c = args[0]
        val cmd = ConnectionlessCommand.fromString(c)

        when (cmd) {
            ConnectionlessCommand.challenge -> {
                challenge = args[1].toInt()
                SendConnectPacket()
            }

            ConnectionlessCommand.client_connect -> {
                if (networkState == CONNECTED) {
                    Com.Warn("Dup connect received.  Ignored.\n")

                } else {
                    networkState = CONNECTED // Defines.ca_connected
                    netchan.setup(
                        NS_CLIENT,
                        packet.from,
                        packet.qport
                    ) // fixme: port isn't needed? should it be Netchan.qport?
                    netchan.reliablePending.add(StringCmdMessage(StringCmdMessage.NEW))
                    Com.Println("Connected!")
                }
            }

            ConnectionlessCommand.print -> {
                if (!packet.connectionlessParameters.isNullOrEmpty()) {
                    Com.Printf(packet.connectionlessParameters)
                }
            }

            else -> {
                println("not yet implemented, no need")
            }

        }
    }

    private fun takeScreenshot() {
        val pixmap = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.width, Gdx.graphics.height)
        val flippedPixmap = Pixmap(pixmap.width, pixmap.height, pixmap.format)
        try {
            for (y in 0 until pixmap.height) {
                flippedPixmap.drawPixmap(
                    pixmap,
                    0,
                    y,
                    0,
                    pixmap.height - y - 1,
                    pixmap.width,
                    1
                )
            }
            val filename = "cake_${LocalDateTime.now().format(SCREENSHOT_TIMESTAMP_FORMATTER)}.png"
            val writable = profileWritable()
            val screenshotPath = writable.resolveWritePath("scrnshot/$filename")
            if (screenshotPath == null) {
                throw IllegalStateException("Failed to resolve screenshot write path")
            }
            val screenshotFile = Gdx.files.absolute(screenshotPath)
            PixmapIO.writePNG(screenshotFile, flippedPixmap)
            Com.Println("Screenshot saved: ${screenshotFile.file().absolutePath}")
        } catch (e: Exception) {
            Com.Warn("Failed to save screenshot: ${e.message}\n")
        } finally {
            flippedPixmap.dispose()
            pixmap.dispose()
        }
    }

    private fun activeProfileId(): String =
        persistedSelectedProfileId() ?: activeGameProfile?.id ?: CakeGameProfileStore.DEFAULT_PROFILE_ID

    private fun currentConfigProfileId(): String? =
        persistedSelectedProfileId()?.takeIf { it.isNotBlank() }
            ?: activeGameProfile?.id?.takeIf { it.isNotBlank() }

    private fun persistedSelectedProfileId(): String? = try {
        gameProfileStore.readSelectedProfileId()
    } catch (_: Exception) {
        null
    }

    private fun profileWritable(): DefaultWritableFileSystem {
        val home = Path.of(System.getProperty("user.home"))
        return DefaultWritableFileSystem(home.resolve(".cake").resolve(activeProfileId()))
    }

    private fun loadActiveProfileConfig() {
        val profileId = currentConfigProfileId() ?: return
        try {
            val configText = profileConfigStore.readConfig(profileId) ?: return
            Cbuf.AddAndExecuteScript(configText)
            Cvar.getInstance().updateLatchedVars()
            Com.Printf("Loaded Cake profile config for '$profileId'\n")
        } catch (e: Exception) {
            Com.Warn("Failed to load Cake profile config for '$profileId': ${e.message}\n")
        }
    }

    private fun saveActiveProfileConfig() {
        val profileId = currentConfigProfileId() ?: return
        try {
            val path = profileConfigStore.writeConfig(
                profileId = profileId,
                bindings = clientBindings.listBindings(),
            )
            Com.Printf("Saved Cake profile config: $path\n")
        } catch (e: Exception) {
            Com.Warn("Failed to save Cake profile config for '$profileId': ${e.message}\n")
        }
    }

    private fun playerSetupCatalog(): PlayerSetupCatalog {
        cachedPlayerSetupCatalog?.let { return it }
        val catalog = if (fileResolver.isVfsInitialized()) {
            PlayerSetupCatalog.fromResolvedFiles(fileResolver.debugResolvedFiles())
        } else {
            PlayerSetupCatalog.fallback()
        }
        cachedPlayerSetupCatalog = catalog
        return catalog
    }

    private fun currentPlayerSetupForm(): PlayerSetupFormState {
        val name = Cvar.getInstance().VariableString("name").ifBlank { "unnamed" }
        val password = Cvar.getInstance().VariableString("password")
        val skinValue = Cvar.getInstance().VariableString("skin").ifBlank { "male/grunt" }
        val split = skinValue.split('/', limit = 2)
        val rawModel = split.firstOrNull().orEmpty().ifBlank { "male" }
        val rawSkin = split.getOrNull(1).orEmpty().ifBlank { "grunt" }
        val hand = Cvar.getInstance().VariableString("hand").toIntOrNull()?.coerceIn(0, 2) ?: 0
        return playerSetupCatalog().normalize(
            PlayerSetupFormState(
                name = name,
                password = password,
                model = rawModel,
                skin = rawSkin,
                hand = hand,
            ),
        )
    }

    private fun savePlayerSetup(form: PlayerSetupFormState): String {
        val normalized = playerSetupCatalog().normalize(
            form.copy(
                name = form.name.trim().ifBlank { "unnamed" },
                password = form.password.trim(),
            ),
        )
        val skinValue = "${normalized.model}/${normalized.skin}"
        val genderValue = deriveGenderFromSkin(skinValue)

        Cvar.getInstance().Set("name", normalized.name)
        Cvar.getInstance().Set("password", normalized.password)
        Cvar.getInstance().Set("skin", skinValue)
        Cvar.getInstance().Set("hand", normalized.hand.toString())
        Cvar.getInstance().Set("gender", genderValue)
        saveActiveProfileConfig()
        return "Saved player setup for ${normalized.name}"
    }

    private fun optionSections(): List<OptionsSectionSummary> {
        val cvars = Cvar.getInstance()
        return DEFAULT_OPTIONS_SECTIONS.map { section ->
            OptionsSectionSummary(
                title = section.title,
                prefix = section.prefix,
                optionCount = cvars.listByPrefixAndFlags(section.prefix, CVAR_OPTIONS).size,
            )
        }
    }

    private fun optionEntries(prefix: String): List<OptionEntryState> {
        return Cvar.getInstance().listByPrefixAndFlags(prefix, CVAR_OPTIONS).map { cvar ->
            OptionEntryState(
                name = cvar.name,
                description = cvar.description.orEmpty(),
                value = cvar.string.orEmpty(),
                latchedValue = cvar.latched_string,
            )
        }
    }

    private fun saveOptionEntries(prefix: String, values: List<OptionEditValue>): String {
        val cvars = Cvar.getInstance()
        var updatedCount = 0
        var latchedCount = 0
        values.forEach { update ->
            val cvar = cvars.FindVar(update.name) ?: return@forEach
            if (!update.name.startsWith(prefix) || (cvar.flags and CVAR_OPTIONS) == 0) {
                return@forEach
            }
            cvars.Set(update.name, update.value)
            updatedCount++
            if (!cvar.latched_string.isNullOrBlank()) {
                latchedCount++
            }
        }
        saveActiveProfileConfig()
        return if (latchedCount > 0) {
            "Saved $updatedCount option(s). $latchedCount pending restart."
        } else {
            "Saved $updatedCount option(s)."
        }
    }

    private fun deriveGenderFromSkin(skinValue: String): String {
        return when {
            skinValue.startsWith("male", ignoreCase = true) || skinValue.startsWith(
                "cyborg",
                ignoreCase = true
            ) -> "male"

            skinValue.startsWith("female", ignoreCase = true) || skinValue.startsWith(
                "crackhor",
                ignoreCase = true
            ) -> "female"

            else -> "none"
        }
    }

    private fun loadProfileById(profileId: String): CakeGameProfile? {
        val normalizedId = profileId.trim()
        if (normalizedId.isBlank()) return null
        return try {
            gameProfileStore.readConfig()?.profiles?.firstOrNull { it.id == normalizedId }
        } catch (e: Exception) {
            Com.Warn("Failed to load profile '$profileId': ${e.message}\n")
            null
        }
    }

    private fun listProfileIdsForMenu(): List<String> = try {
        gameProfileStore.readConfig()?.profiles?.map { it.id }.orEmpty()
    } catch (e: Exception) {
        Com.Warn("Failed to list Cake profiles: ${e.message}\n")
        emptyList()
    }

    private fun isProfileSwitchAllowed(): Boolean = networkState == DISCONNECTED

    private fun selectProfileForEditor(profileId: String): CakeGameProfile? {
        selectProfileForMenu(profileId)
        return activeGameProfile?.takeIf { it.id == profileId }
    }

    private fun createNewProfileDraft(): CakeGameProfile {
        val base = activeGameProfile?.basedir ?: autodetectSteamBasedir().orEmpty()
        val draftId = nextDraftProfileId()
        return CakeGameProfile(
            id = draftId,
            basedir = base,
            gamemod = null,
        )
    }

    private fun nextDraftProfileId(): String {
        val existing = listProfileIdsForMenu()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        val base = "newprofile"
        if (!existing.contains(base)) {
            return base
        }
        var suffix = 2
        while (existing.contains("$base$suffix")) {
            suffix++
        }
        return "$base$suffix"
    }

    private fun saveProfileFromEditor(profile: CakeGameProfile): String {
        if (!isProfileSwitchAllowed()) {
            return "Disconnect first to edit profiles"
        }
        val normalized = profile.normalized()
        if (normalized.id.isBlank()) {
            return "Profile ID must not be blank"
        }
        if (normalized.basedir.isBlank()) {
            return "Basedir must not be blank"
        }
        return try {
            saveActiveProfileConfig()
            gameProfileStore.upsertProfile(normalized, select = true)
            applyGameProfile(normalized)
            loadActiveProfileConfig()
            "Saved profile '${normalized.id}'"
        } catch (e: Exception) {
            "Failed to save profile: ${e.message}"
        }
    }

    // when switching profiles, we expect no active game, therefore, no loaded game resources
    private fun selectProfileForMenu(profileId: String) {
        if (!isProfileSwitchAllowed()) {
            Com.Warn("Profile switching is only allowed while disconnected.\n")
            return
        }
        val selected = try {
            val config = gameProfileStore.readConfig() ?: return
            config.profiles.firstOrNull { it.id == profileId } ?: return
        } catch (e: Exception) {
            Com.Warn("Failed to load profiles config: ${e.message}\n")
            return
        }

        try {
            saveActiveProfileConfig()
            gameProfileStore.selectProfile(profileId)
            applyGameProfile(selected)
            loadActiveProfileConfig()
            Com.Printf("Active profile switched to '$profileId'\n")
        } catch (e: Exception) {
            Com.Warn("Failed to switch profile '$profileId': ${e.message}\n")
        }
    }

    private fun autodetectSteamBasedir(): String? = CakeStartupBootstrap.autodetectSteamBasedir()

    private fun applyGameProfile(profile: CakeGameProfile?) {
        val normalized = profile?.normalized()
        activeGameProfile = normalized
        fileResolver.basedir = normalized?.basedir
        fileResolver.gamemod = normalized?.gamemod
        cachedPlayerSetupCatalog = null
        reloadProfileBackground()
    }

    private fun openStartupProfileEditorIfNeeded() {
        if (activeGameProfile != null) {
            return
        }
        menuEventBus.postIntent(
            MenuIntent.CreateProfileDraft(
                "Create a profile to tell Cake where your Quake II installation is located.",
            ),
        )
        menuController.pumpIntents()
        syncMenuViewFromBusState()
    }

    private fun setActiveGameProfile(args: List<String>) {
        if (!isProfileSwitchAllowed()) {
            Com.Warn("Profile switching is only allowed while disconnected.\n")
            return
        }
        if (args.size < 3) {
            Com.Printf("usage: cake_profile_set <id> <basedir> [gamemod]\n")
            return
        }
        val profile = CakeGameProfile(
            id = args[1],
            basedir = args[2],
            gamemod = args.getOrNull(3),
        ).normalized()

        saveActiveProfileConfig()
        applyGameProfile(profile)
        try {
            val path = gameProfileStore.upsertProfile(profile, select = true)
            loadActiveProfileConfig()
            Com.Printf("Cake profile saved: $path\n")
        } catch (e: Exception) {
            Com.Warn("Failed to save Cake profile: ${e.message}\n")
        }
    }

    private fun createMenuBackend(): MenuBackend = object : MenuBackend {
        override fun activeProfileId(): String = this@Cake.activeProfileId()

        override fun canDisconnect(): Boolean = networkState != DISCONNECTED

        override fun disconnect() {
            this@Cake.disconnect()
        }

        override fun listProfileIds(): List<String> = listProfileIdsForMenu()

        override fun selectedProfileId(): String? = persistedSelectedProfileId() ?: activeGameProfile?.id

        override fun profileFormById(profileId: String): ProfileFormState? =
            loadProfileById(profileId)?.toProfileFormState()

        override fun selectProfile(profileId: String): ProfileFormState? =
            selectProfileForEditor(profileId)?.toProfileFormState()

        override fun createProfileDraft(): ProfileFormState =
            createNewProfileDraft().toProfileFormState()

        override fun autodetectBasedir(): String? = autodetectSteamBasedir()

        override fun saveProfile(form: ProfileFormState): String =
            saveProfileFromEditor(form.toCakeGameProfile())

        override fun joinServer(address: String): String {
            Cbuf.AddText("connect $address")
            menuVisible = false
            consoleVisible = false
            return "Joining $address..."
        }

        override fun playerSetupCatalog(): PlayerSetupCatalog = this@Cake.playerSetupCatalog()

        override fun currentPlayerSetupForm(): PlayerSetupFormState = this@Cake.currentPlayerSetupForm()

        override fun savePlayerSetup(form: PlayerSetupFormState): String = this@Cake.savePlayerSetup(form)

        override fun optionSections(): List<OptionsSectionSummary> = this@Cake.optionSections()

        override fun optionEntries(prefix: String): List<OptionEntryState> = this@Cake.optionEntries(prefix)

        override fun saveOptionEntries(prefix: String, values: List<OptionEditValue>): String =
            this@Cake.saveOptionEntries(prefix, values)
    }

    private fun CakeGameProfile.toProfileFormState(): ProfileFormState = ProfileFormState(
        id = id,
        basedir = basedir,
        gamemod = gamemod.orEmpty(),
    )

    private fun ProfileFormState.toCakeGameProfile(): CakeGameProfile = CakeGameProfile(
        id = id,
        basedir = basedir,
        gamemod = gamemod.takeIf { it.isNotBlank() },
    )

    private fun syncMenuViewFromBusState() {
        val targetView = when (menuEventBus.latestState().activeScreen) {
            MenuScreen.MAIN -> MenuView.MAIN
            MenuScreen.PROFILE_EDIT -> MenuView.PROFILE_EDIT
            MenuScreen.MULTIPLAYER -> MenuView.MULTIPLAYER
            MenuScreen.JOIN_GAME -> MenuView.JOIN_GAME
            MenuScreen.PLAYER_SETUP -> MenuView.PLAYER_SETUP
            MenuScreen.OPTIONS -> MenuView.OPTIONS
            MenuScreen.OPTIONS_SECTION -> MenuView.OPTIONS_SECTION
        }
        if (targetView == menuView) return
        menuView = targetView
        if (menuVisible) {
            updateInputHandlers(consoleVisible, menuVisible)
        }
    }
}
