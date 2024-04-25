package org.demoth.cake

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.viewport.StretchViewport
import jake2.qcommon.Com
import jake2.qcommon.Defines.*
import jake2.qcommon.Globals
import jake2.qcommon.exec.Cbuf
import jake2.qcommon.exec.Cmd
import jake2.qcommon.exec.Cvar
import jake2.qcommon.network.NET
import jake2.qcommon.network.Netchan
import jake2.qcommon.network.messages.ConnectionlessCommand
import jake2.qcommon.network.messages.NetworkPacket
import jake2.qcommon.network.netadr_t
import ktx.app.KtxApplicationAdapter
import ktx.app.KtxInputAdapter
import ktx.scene2d.Scene2DSkin
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


    private var networkState = ClientNetworkState.DISCONNECTED
    private var servername = "localhost"
    private var challenge = 0
    private var reconnectTimeout = 1f // todo: use proper timer

    init {
        Cmd.Init()
        Cvar.Init()
        Cbuf.AddText("set thinclient 1")
        initUserInfoCvars()
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
            networkState = ClientNetworkState.CONNECTING
        }

        Cmd.AddCommand("disconnect") {
            NET.Config(false)
            networkState = ClientNetworkState.DISCONNECTED
            challenge = 0

        }

        Cmd.AddCommand("userinfo") {
            val userInfo = Cvar.getInstance().Userinfo()
            Com.Println("Userinfo: $userInfo")
        }
    }

    override fun render() {
        val deltaSeconds = Gdx.graphics.deltaTime
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
        if (networkState != ClientNetworkState.CONNECTING)
            return

        val adr = netadr_t.fromString(servername, PORT_SERVER)
        if (adr == null) {
            Com.Printf("Bad server address\n")
            networkState = ClientNetworkState.DISCONNECTED
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
//            ClientGlobals.cls.connect_time = 0
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

            // else
            // TODO
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
                networkState = ClientNetworkState.CONNECTED
                Com.Println("Connected!")
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