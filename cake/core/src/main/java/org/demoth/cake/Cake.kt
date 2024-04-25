package org.demoth.cake

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.viewport.StretchViewport
import jake2.qcommon.Com
import jake2.qcommon.Defines
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
import jake2.qcommon.sizebuf_t
import ktx.app.KtxApplicationAdapter
import ktx.app.KtxInputAdapter
import ktx.scene2d.Scene2DSkin
import org.demoth.cake.ClientNetworkState.*
import org.demoth.cake.stages.ConsoleStage
import org.demoth.cake.stages.MainMenuStage

enum class ClientNetworkState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ACTIVE
}

/**
 * Entrypoint for the client application
 *
 */
class Cake : KtxApplicationAdapter, KtxInputAdapter {
    private lateinit var menuStage: MainMenuStage
    private lateinit var consoleStage: ConsoleStage
    private var consoleVisible = false
    private var menuVisible = true

    // network
    private var networkState = DISCONNECTED
    private var servername = "localhost"
    private var challenge = 0
    private var reconnectTimeout = 1f // todo: use proper timer
    private val netchan = netchan_t()

    // game state
    private var gameName: String = "baseq2"
    private var spawnCount = 0
    private var playercount = 1
    private var levelString: String = ""
    private var refresh_prepped: Boolean = false

    private val configStrings = Array(MAX_CONFIGSTRINGS) {""}




    init {
        Cmd.Init()
        Cvar.Init()
        Cbuf.AddText("set thinclient 1")
        initUserInfoCvars()
        Netchan.Netchan_Init()
    }

    override fun create() {
        Scene2DSkin.defaultSkin = Skin(Gdx.files.internal("ui/uiskin.json"))
        // doesn't really stretch because we don't yet allow the window to freely resize
        val viewport = StretchViewport(Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        menuStage = MainMenuStage(viewport) // fixme: cvar
        // todo: gather all early logging (which is generated before the console is created)
        // and put into the console when it's ready
        consoleStage = ConsoleStage(viewport)

        Gdx.input.inputProcessor = InputMultiplexer(
            this, // global input processor to control console and menu
            consoleStage,
            menuStage
        )

        Cmd.AddCommand("quit") {
            Gdx.app.exit()
        }

        Cmd.AddCommand("connect") {
            NET.Config(true) // allow remote
            servername = it[1]
            networkState = CONNECTING
        }

        Cmd.AddCommand("disconnect") {
            // send a disconnect message to the server
            val buf = sizebuf_t(128)
            StringCmdMessage(StringCmdMessage.DISCONNECT).writeTo(buf)
            netchan.transmit(listOf(StringCmdMessage(StringCmdMessage.DISCONNECT)))

            NET.Config(false)
            networkState = DISCONNECTED
            challenge = 0

        }

        Cmd.AddCommand("userinfo") {
            val userInfo = Cvar.getInstance().Userinfo()
            Com.Println("Userinfo: $userInfo")
        }

        Cmd.AddCommand("cbuf") {
            Com.Println(Cbuf.contents())
        }

        Cmd.AddCommand("cmd") {
            if (networkState != CONNECTED && networkState != ACTIVE) {
                Com.Println("Cannot cmd '${it}', not connected")
            } else {
                if (it.size > 1)
                    netchan.reliablePending.add(StringCmdMessage(getArguments(it)))
                else
                    Com.Println("Empty cmd")
            }
        }
    }

    override fun render() {
        val deltaSeconds = Gdx.graphics.deltaTime
        Globals.curtime += (deltaSeconds * 1000f).toInt() // todo: get rid of globals!
        ScreenUtils.clear(0.15f, 0.15f, 0.2f, 1f)

        if (consoleVisible) {
            consoleStage.act()
            consoleStage.draw()
        } else if (menuVisible) {
            menuStage.act()
            menuStage.draw()
        } // todo: else draw IngameScreen

        CheckForResend(deltaSeconds)
        CL_ReadPackets()
        SendCommands()

        Cbuf.Execute()
        if (!refresh_prepped) {
            // todo: load level and other resources into refresher/renderer
        }
    }

    private fun SendCommands() {
        if (networkState == CONNECTING || networkState == DISCONNECTED )
            return

        if (networkState == CONNECTED){
            if (netchan.reliablePending.isNotEmpty()) {
                if (Globals.curtime - netchan.last_sent > 1000) // fixme: proper timers
                    netchan.transmit(null)
            }
            return
        }
        // todo: assemble the inputs and commands, then transmit them

    }

    // handle ESC for menu and F1 for console
    override fun keyUp(keycode: Int): Boolean {
        when (keycode) {
            Input.Keys.F1 -> {
                menuVisible = false
                consoleVisible = !consoleVisible
                if (consoleVisible) {
                    consoleStage.focus()
                }
                return true
            }
            Input.Keys.ESCAPE -> {
                consoleVisible = false
                menuVisible = !menuVisible
                return true
            }
            else -> return false
        }
    }

    override fun dispose() {
        menuStage.dispose()
        consoleStage.dispose()
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
            Com.Printf("Bad server address\n")
            networkState = DISCONNECTED
            reconnectTimeout = 0f
            return
        }
        if (reconnectTimeout < 0) {
            Com.Printf("${"Connecting to $servername"}...\n")
            Netchan.sendConnectionlessPacket(NS_CLIENT, adr, ConnectionlessCommand.getchallenge, "\n")
            reconnectTimeout = 1f
        } else {
            reconnectTimeout -= deltaSeconds
        }
    }

    private fun SendConnectPacket() {
        val adr = netadr_t.fromString(servername, PORT_SERVER)
        if (adr == null) {
            Com.Printf("Bad server address\n")
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
                Com.Printf(networkPacket.from.toString() + ": sequenced packet without connection\n")
                continue
            }

            if (netchan.accept(networkPacket)) {
                parseServerMessage(networkPacket.parseBodyFromServer())
            } //else wasn't accepted for some reason


        }
    }

    /*
     * ===================== CL_ParseServerMessage =====================
     */
    private fun parseServerMessage(messages: Collection<ServerMessage>) {
        messages.forEach { msg ->
            Com.Printf("Received ${msg}\n")
            when (msg) {
                is DisconnectMessage -> {
                    Com.Error(ERR_DISCONNECT, "Server disconnected\n")
                }
                is ReconnectMessage -> {
                    networkState = CONNECTING
                    // CheckForResend() will fire immediately
                    reconnectTimeout = 0f
                }

                is ServerDataMessage -> {
                    Cbuf.Execute()
                    parseServerDataMessage(msg)
                }

                is StuffTextMessage -> {
                    Cbuf.AddText(msg.text)
                }

                is ConfigStringMessage -> {
                    configStrings[msg.index] = msg.config
                }

                else -> {
                    Com.Printf("Received ${msg.javaClass.name} message\n")
                }
            }
        }
    }

    /*
     * ================== CL_ParseServerData ==================
     */
    private fun parseServerDataMessage(msg: ServerDataMessage) {
        //
        //	   wipe the client_state_t struct
        //
        clearState()
        gameName = msg.gameName
        levelString = msg.levelString
        playercount = msg.playerNumber
        spawnCount = msg.spawnCount
        refresh_prepped = false // force reloading of all "refresher" (visual) resources, most importantly the level
    }

    private fun clearState() {
        // todo: stop all effects
        // todo: clear all client entities
        netchan.reliablePending.clear()
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
                    Com.Printf("Dup connect received.  Ignored.\n")

                } else {
                    networkState = CONNECTED // Defines.ca_connected
                    netchan.setup(Defines.NS_CLIENT, packet.from, packet.qport) // fixme: port isn't needed? should it be Netchan.qport?
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
        Cvar.getInstance().Get("password", "", CVAR_USERINFO)
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