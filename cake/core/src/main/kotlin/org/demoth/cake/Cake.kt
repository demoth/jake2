package org.demoth.cake

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.SoundLoader
import com.badlogic.gdx.audio.Sound
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
import jake2.qcommon.network.messages.NetworkPacket
import jake2.qcommon.network.messages.client.StringCmdMessage
import jake2.qcommon.network.messages.server.*
import jake2.qcommon.network.netadr_t
import jake2.qcommon.network.netchan_t
import ktx.app.KtxApplicationAdapter
import ktx.app.KtxInputAdapter
import ktx.assets.TextAssetLoader
import ktx.scene2d.Scene2DSkin
import org.demoth.cake.ClientNetworkState.*
import org.demoth.cake.assets.CakeFileResolver
import org.demoth.cake.stages.ConsoleStage
import org.demoth.cake.stages.Game3dScreen
import org.demoth.cake.stages.MainMenuStage

private enum class ClientNetworkState {
    DISCONNECTED,
    CONNECTING, // started the connection procedure
    CONNECTED, // connection established, preparing resources
    ACTIVE // game is running
}

/**
 * Entrypoint for the client application
 *
 */
class Cake : KtxApplicationAdapter, KtxInputAdapter {
    private lateinit var menuStage: MainMenuStage
    private lateinit var consoleStage: ConsoleStage
    private lateinit var viewport: StretchViewport

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

    // network
    private var networkState = DISCONNECTED
    private var servername = "localhost"
    private var challenge = 0
    private var reconnectTimeout = 1f // todo: use proper timer
    private val netchan = netchan_t()

    private var game3dScreen: Game3dScreen? = null
        set(value) {
            field = value
            // allow the game screen to receive the input
            updateInputHandlers(consoleVisible, menuVisible)
        }

    private var fileResolver = CakeFileResolver()

    private val assetManager = AssetManager().apply {
        // for loading shaders and other text files
        setLoader(String::class.java, TextAssetLoader(fileResolver))
        // for loading binary files
        setLoader(Any::class.java, ObjectLoader(fileResolver))
        // for loading binary blobs from the filesystem (e.g. BSP maps)
        setLoader(ByteArray::class.java, ByteArrayLoader(fileResolver))
        // for loading sounds from the filesystem
        setLoader(Sound::class.java, SoundLoader(fileResolver))

    }

    init {
        Cmd.Init()
        Cvar.Init()
        Cbuf.AddText("set thinclient 1")
        initUserInfoCvars()
        Netchan.Netchan_Init()
    }

    override fun create() {
        // load sync resources - required immediately
        assetManager.load(cakeSkin, Skin::class.java)

        // load async resources (will be used later in the game)

        // todo: make an pluggable system for queueing resource loading which will be required during the game
        assetManager.load("q2palette.bin", Any::class.java) // todo: use original baseq2/pics/colormap.pcx
        assetManager.load(vatShader, String::class.java)
        assetManager.finishLoading() // these assets are necessary anyway

        Scene2DSkin.defaultSkin = assetManager.get(cakeSkin, Skin::class.java)
        // doesn't really stretch because we don't yet allow the window to freely resize
        viewport = StretchViewport(Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        menuStage = MainMenuStage(viewport) // fixme: cvar
        // todo: gather all early logging (which is generated before the console is created)
        // and put into the console when it's ready
        consoleStage = ConsoleStage(viewport)

        updateInputHandlers(false, true)


        Cmd.AddCommand("quit") {
            disconnect()
            Gdx.app.exit()
        }

        /**
         * Changing_f
         *
         * Just sent as a hint to the client that they should drop to full console.
         */
        Cmd.AddCommand("changing") {
            networkState = CONNECTED
            // todo: indicate somehow about the map changing (loading screen or spinner)
            // SCR.BeginLoadingPlaque();
            Com.Printf("\nChanging map...\n")

            // free unused resources
            game3dScreen?.dispose()
            game3dScreen = Game3dScreen(assetManager)
        }

        // like a connect but more lightweight
        // fixme: not same as svc_reconnect?
        Cmd.AddCommand("reconnect") {
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

        Cmd.AddCommand("connect") {
            Com.Printf("Connecting to ${it[1]}...\n")
            // first disconnect
            disconnect()

            NET.Config(true) // allow remote
            servername = it[1]
            networkState = CONNECTING
            game3dScreen = Game3dScreen(assetManager)
            // picked up later in the CheckForResend() // fixme: why not connect immediately?
        }

        Cmd.AddCommand("disconnect") { disconnect() }

        Cmd.AddCommand("userinfo") {
            val userInfo = Cvar.getInstance().Userinfo()
            Com.Println("Userinfo: $userInfo")
        }

        Cmd.AddCommand("jvm_info") {
            Com.Println("Version: ${Runtime.version()}")
            val rt = Runtime.getRuntime()
            Com.Println("Free: ${rt.freeMemory() / 1024 / 1024} MB")
            Com.Println("Used: ${(rt.totalMemory() - rt.freeMemory()) / 1024 / 1024} MB")
            Com.Println("Total: ${rt.totalMemory() / 1024 / 1024} MB")
            Com.Println("Max: ${rt.maxMemory() / 1024 / 1024} MB")
        }


        Cmd.AddCommand("print_cbuf") {
            Com.Println(Cbuf.contents())
        }

        /*
         * Adds the current command line as a clc_stringcmd to the client message.
         * things like godmode, noclip, etc, are commands directed to the server, so
         * when they are typed in at the console, they will need to be forwarded.
         *
         * see jake2.server.SV_MAIN#SV_ExecuteUserCommand(jake2.server.client_t, java.lang.String)
         */
        Cmd.AddCommand("cmd") {
            if (networkState != CONNECTED && networkState != ACTIVE) {
                Com.Warn("Cannot cmd '${it}', not connected")
            } else {
                if (it.size > 1)
                    netchan.reliablePending.add(StringCmdMessage(getArguments(it)))
                else
                    Com.Warn("Empty cmd") // todo: warning
            }
        }

        Cmd.AddCommand("precache") {
            if (networkState == ACTIVE) {
                // tothink: do we ignore it or restart the map?
                Com.Warn("precache - during the game!\n") // todo: warning
                return@AddCommand
            }
            val precache_spawncount = it[1].toInt()
            // no udp downloads anymore!!

            game3dScreen?.precache()

            // we are ready to start the game!
            netchan.reliablePending.add(StringCmdMessage(StringCmdMessage.BEGIN + " " + precache_spawncount + "\n"));
        }

    }

    private fun disconnect() {
        Com.Printf("Disconnecting from server...\n")
        // todo: clear the game state and release resources
        game3dScreen?.dispose()
        game3dScreen = null

        // send a disconnect message to the server
        netchan.transmit(listOf(StringCmdMessage(StringCmdMessage.DISCONNECT)))

        // reset network state
        NET.Config(false)
        networkState = DISCONNECTED
        challenge = 0
    }

    // whenever we change the visibility of the console or the menu, we should update the set of input handlers
    // (in other words, which components receive the input events and which don't)
    private fun updateInputHandlers(consoleVisible: Boolean, menuVisible: Boolean) {
        Gdx.input.isCursorCatched = !menuVisible && !consoleVisible

        val inputProcessor: InputProcessor = when {
            consoleVisible -> consoleStage
            menuVisible -> menuStage
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
        assetManager.update() // todo: 1000/fps millis
        val deltaSeconds = Gdx.graphics.deltaTime
        Globals.curtime += (deltaSeconds * 1000f).toInt() // todo: get rid of globals!
        game3dScreen?.deltaTime = deltaSeconds
        ScreenUtils.clear(0.15f, 0.15f, 0.2f, 1f, true)

        CheckForResend(deltaSeconds)
        CL_ReadPackets()
        sendUpdates()

        Cbuf.Execute()

        if (game3dScreen != null) {
            game3dScreen?.render(deltaSeconds)
        }

        if (menuVisible) {
            menuStage.act()
            menuStage.draw()
        }

        if (consoleVisible) {
            consoleStage.act()
            consoleStage.draw()
        }
    }

    /**
     * SendCommands
     */
    private fun sendUpdates() {
        when (networkState) {
            CONNECTING, DISCONNECTED -> return
            CONNECTED -> {
                if (netchan.reliablePending.isNotEmpty()) {
                    if (Globals.curtime - netchan.last_sent > 1000) {
                        // fixme: proper timers
                        netchan.transmit(null)
                    }
                }
            }
            ACTIVE -> {
                // fixme: send only at client rate, not every client frame
                game3dScreen?.gatherInput(netchan.outgoing_sequence)?.let {
                    netchan.transmit(listOf(it))
                }
            }
        }

    }

    /**
     * Global input handling: Console and Menu
     */
    override fun keyUp(keycode: Int): Boolean {
        when (keycode) {
            Input.Keys.F1 -> {
                menuVisible = false
                consoleVisible = !consoleVisible
                if (consoleVisible) {
                    consoleStage.focus()
                }
            }
            Input.Keys.ESCAPE -> {
                if (consoleVisible)
                    consoleVisible = false
                else menuVisible = !menuVisible
            }
            else -> return false
        }
        return true
    }

    // fixme is it called from somewhere?
    override fun dispose() {
        menuStage.dispose()
        consoleStage.dispose()
        game3dScreen?.dispose()
        game3dScreen = null
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
        if (reconnectTimeout < 0) {
            Com.Printf("${"CheckForResend ${networkState}: Connecting to $servername"}...\n")
            Netchan.sendConnectionlessPacket(NS_CLIENT, adr, ConnectionlessCommand.getchallenge, "\n")
            reconnectTimeout = 1f
        } else {
            reconnectTimeout -= deltaSeconds
        }
    }

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

    fun CL_ReadPackets() {
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
                parseServerMessage(networkPacket.parseBodyFromServer())
            } //else wasn't accepted for some reason


        }
    }

    /**
     * CL_ParseServerMessage
     */
    private fun parseServerMessage(messages: Collection<ServerMessage>) {
        messages.forEach { msg ->
            when (msg) {
                is DisconnectMessage -> {
                    Com.Error(ERR_DISCONNECT, "Server disconnected\n")
                }

                // NB: it is not sent during map change (instead - StuffTextMessage("reconnect"))
                // sent on server restart
                is ReconnectMessage -> {
                    Com.Printf("ReconnectMessage: Reconnecting..\n")
                    networkState = CONNECTING
                    // CheckForResend() will fire immediately
                    reconnectTimeout = 0f
                    // todo: handle game screen disposal?
                }

                is StuffTextMessage -> {
                    Cbuf.AddText(msg.text)
                }

                is ServerDataMessage -> {
                    Com.Printf("joining ${msg.levelString}\n")

                    // new game is starting
                    Cbuf.Execute()
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
                is SoundMessage -> {
                    game3dScreen?.processSoundMessage(msg)
                }
                is SpawnBaselineMessage -> {
                    game3dScreen?.processBaselineMessage(msg)
                }
                is WeaponSoundMessage -> {
                    game3dScreen?.processWeaponSoundMessage(msg)
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
                    // todo: print in the chat (upper left corner of the screen)
                    Com.Printf("${msg.text}\n")
                }
                is PrintCenterMessage -> {
                    // todo: display on the hud
                    Com.Printf("${msg.text}\n")
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


    private fun CL_ConnectionlessPacket(packet: NetworkPacket) {
        val args = Cmd.TokenizeString(packet.connectionlessMessage, false)
        val c = args[0]
        Com.Println(packet.from.toString() + ": " + c)
        val cmd = ConnectionlessCommand.fromString(c)
        Com.Println("received: $cmd")

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
                    netchan.setup(NS_CLIENT, packet.from, packet.qport) // fixme: port isn't needed? should it be Netchan.qport?
                    netchan.reliablePending.add(StringCmdMessage(StringCmdMessage.NEW));
                    Com.Println("Connected!")
                }
            }
            else -> {
                println("not yet implemented, no need")
            }

        }
    }

    /**
     * populate default userinfo values - required for connecting to the server
     */
    private fun initUserInfoCvars() {
//        Cvar.getInstance().Get("password", "", CVAR_USERINFO)
        Cvar.getInstance().Get("spectator", "0", CVAR_USERINFO)
        Cvar.getInstance().Get("name", "unnamed", CVAR_USERINFO or CVAR_ARCHIVE)
        Cvar.getInstance().Get("skin", "male/grunt", CVAR_USERINFO or CVAR_ARCHIVE)
        Cvar.getInstance().Get("rate", "25000", CVAR_USERINFO or CVAR_ARCHIVE)
        Cvar.getInstance().Get("msg", "1", CVAR_USERINFO or CVAR_ARCHIVE)
        Cvar.getInstance().Get("hand", "0", CVAR_USERINFO or CVAR_ARCHIVE)
        Cvar.getInstance().Get("fov", "90", CVAR_USERINFO or CVAR_ARCHIVE)
        Cvar.getInstance().Get("gender", "male", CVAR_USERINFO or CVAR_ARCHIVE)
    }

}
