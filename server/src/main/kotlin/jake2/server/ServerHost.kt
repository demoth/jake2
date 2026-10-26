/*
 * Copyright (C) 1997-2001 Id Software, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.
 *
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */
// Created on 13.01.2004 by RST.
// former SV_MAIN
package jake2.server

import jake2.qcommon.*
import jake2.qcommon.exec.*
import jake2.qcommon.exec.CvarTypes.ChoiceType
import jake2.qcommon.network.NET
import jake2.qcommon.network.NetAddrType
import jake2.qcommon.network.Netchan
import jake2.qcommon.network.messages.ConnectionlessCommand
import jake2.qcommon.network.messages.NetworkMessage
import jake2.qcommon.network.messages.client.*
import jake2.qcommon.network.messages.server.*
import jake2.qcommon.network.netadr_t
import jake2.qcommon.util.Lib
import jake2.qcommon.vfs.EngineVfs
import jake2.qcommon.vfs.EngineWriteRoot
import jake2.server.save.ServerSaveFiles
import jake2.server.save.ServerSaveJsonStore

/** Owns client connections, Game routing, protocol and commands for one server Host. */
class ServerHost : AutoCloseable {
    // region Host state and command dispatch

    private val commands = ServerCommands()
    val isRunning: Boolean
        get() = games.isNotEmpty()

    @JvmField
    val games: MutableMap<String, GameImportsImpl> = HashMap()

    private val challenges = Array(Defines.MAX_CHALLENGES) { challenge_t() }

    var clients: MutableList<ClientNetworkInfo> = mutableListOf()
        private set

    private var realtime: Long = 0
    private var frameNum = 0
    private var nextFrameTime = 1000
    private var gameCounter = 0

    val isPaused: Boolean
        get() = paused.typedValue

    private val paused: cvar_t<Boolean>

    private val enforceMoveTime: cvar_t<Boolean>

    @JvmField
    val timeout: cvar_t<Float> // seconds without any message

    private val zombieTimeout: cvar_t<Float> // seconds to sink messages after

    private val allowDownload: cvar_t<Boolean>

    private val allowPlayerDownloads: cvar_t<Boolean>

    private val allowModelDownloads: cvar_t<Boolean>

    private val allowSoundDownloads: cvar_t<Boolean>

    private val allowMapDownloads: cvar_t<Boolean>

    private val hostname: cvar_t<String>

    private val reconnectLimit: cvar_t<Float> // minimum seconds between connect

    private val userCommands = mapOf(
        StringCmdMessage.NEW to ServerUserCommand { _, game, client -> sendInitialGameData(game, client) },
        StringCmdMessage.CONFIG_STRINGS to ServerUserCommand(::sendConfigstrings),
        StringCmdMessage.BASELINES to ServerUserCommand(::sendBaselines),
        StringCmdMessage.BEGIN to ServerUserCommand(::beginClient),
        StringCmdMessage.NEXT_SERVER to ServerUserCommand(::finishCinematic),
        StringCmdMessage.DISCONNECT to ServerUserCommand { _, _, client -> dropClient(client) },
        StringCmdMessage.INFO to ServerUserCommand { _, _, _ -> Info.Print(Cvar.getInstance().Serverinfo()) },
        StringCmdMessage.DOWNLOAD to ServerUserCommand { args, _, client -> beginDownload(args, client) },
        StringCmdMessage.NEXT_DOWNLOAD to ServerUserCommand { _, _, client -> sendNextDownloadChunk(client) },
    )

    private fun interface ServerUserCommand {
        fun execute(args: List<String>, game: GameImportsImpl, client: ClientNetworkInfo)
    }

    // endregion

    // region Initialization and lifecycle

    /**
     * One manager per dedicated process or embedded session; map changes reuse it.
     */
    init {
        val cvars = Cvar.getInstance()
        cvars.register(
            "spectator_password", CvarTypes.STRING, "", Defines.CVAR_USERINFO,
            "Spectator admission password; empty or none allows access"
        )
        cvars.register(
            "needpass", NEEDPASS_TYPE, 0, Defines.CVAR_SERVERINFO,
            "Required passwords: 0 none, 1 player, 2 spectator, 3 both"
        )
        cvars.register("deathmatch", CvarTypes.BOOLEAN, false, Defines.CVAR_SERVERINFO or Defines.CVAR_LATCH)
        cvars.register("coop", CvarTypes.BOOLEAN, false, Defines.CVAR_SERVERINFO or Defines.CVAR_LATCH)
        cvars.register(
            "dmflags",
            CvarTypes.INTEGER,
            Defines.DF_INSTANT_ITEMS,
            Defines.CVAR_SERVERINFO,
            "Deathmatch rule bitmask; combine DF_* flags"
        )
        cvars.register(
            "fraglimit",
            CvarTypes.IntegerType(0, null),
            0,
            Defines.CVAR_SERVERINFO,
            "End a deathmatch level at this score; 0 disables the limit"
        )
        cvars.register(
            "timelimit",
            CvarTypes.FloatType(0f, null),
            0f,
            Defines.CVAR_SERVERINFO,
            "Deathmatch level time limit in minutes; 0 disables the limit"
        )
        cvars.register("cheats", CvarTypes.BOOLEAN, false, Defines.CVAR_SERVERINFO or Defines.CVAR_LATCH)
        cvars.register(
            "protocol",
            CvarTypes.INTEGER,
            Defines.PROTOCOL_VERSION,
            Defines.CVAR_SERVERINFO or Defines.CVAR_NOSET
        )
        cvars.register("mapname", CvarTypes.STRING, "", Defines.CVAR_SERVERINFO or Defines.CVAR_NOSET)
        cvars.register(
            "nextserver",
            CvarTypes.STRING,
            "",
            0,
            "Console command to execute after a cinematic or picture"
        )
        cvars.register(
            "maxclients",
            CvarTypes.IntegerType(1, Defines.MAX_CLIENTS),
            1,
            Defines.CVAR_SERVERINFO or Defines.CVAR_LATCH,
            "Client slot limit; single-player and coop mode also restrict the count"
        )

        hostname = cvars.register(
            "hostname",
            CvarTypes.STRING,
            "noname",
            Defines.CVAR_SERVERINFO or Defines.CVAR_ARCHIVE
        )
        timeout = cvars.register(
            "timeout",
            CvarTypes.FloatType(0f, null),
            125f,
            0,
            "Seconds without a client message before disconnection"
        )
        zombieTimeout = cvars.register(
            "zombietime",
            CvarTypes.FloatType(0f, null),
            2f,
            0,
            "Seconds to retain a disconnected client slot for final reliable messages"
        )
        cvars.register(
            "showclamp",
            CvarTypes.BOOLEAN,
            false,
            0,
            "Legacy frame-time clamp diagnostics; unused by the current server and Cake"
        )
        paused = cvars.register("paused", CvarTypes.BOOLEAN, false, 0)
        cvars.register(
            "timedemo",
            CvarTypes.BOOLEAN,
            false,
            0,
            "Legacy demo benchmarking switch; unused by the current server and Cake"
        )
        enforceMoveTime = cvars.register(
            "sv_enforcetime",
            CvarTypes.BOOLEAN,
            false,
            0,
            "Ignore movement commands that exceed the client time budget"
        )

        // come from MainCommon
        allowDownload = cvars.find<Boolean>("allow_download")
        allowPlayerDownloads = cvars.find<Boolean>("allow_download_players")
        allowModelDownloads = cvars.find<Boolean>("allow_download_models")
        allowSoundDownloads = cvars.find<Boolean>("allow_download_sounds")
        allowMapDownloads = cvars.find<Boolean>("allow_download_maps")

        cvars.register(
            "sv_noreload",
            CvarTypes.BOOLEAN,
            false,
            0,
            "Start revisited levels fresh instead of restoring their saved state"
        )
        cvars.register(
            "sv_airaccelerate",
            CvarTypes.FLOAT,
            0f,
            Defines.CVAR_LATCH,
            "Controls air acceleration (currently only for deathmatch)"
        )
        cvars.register(
            "public",
            CvarTypes.BOOLEAN,
            false,
            0,
            "Legacy master-server advertising switch; currently unused"
        )
        reconnectLimit = cvars.register(
            "sv_reconnect_limit",
            CvarTypes.FloatType(0f, null),
            3f,
            Defines.CVAR_ARCHIVE,
            "Minimum seconds between reconnect attempts from the same remote client"
        )
        resetClients()

        // Publish Host callbacks only after initialization succeeds. They survive Game replacement.
        // Retained for console compatibility; master-server advertising is not implemented.
        commands.add("heartbeat", true) {}
        commands.add("kick", true, Command(::kickClient))
        commands.add("status", true) { printStatus() }
        commands.add("serverinfo", true) { printServerInfo() }
        commands.add("dumpuser", true, Command(::dumpUser))
        commands.add("say", true, Command(::say))
        commands.add("save", true, Command(::saveGame))

        commands.add("killserver") {
            stop("Server was killed.\n", false)
            NET.configureServer(false)
        }
        commands.add("map", Command(::map))
        commands.add("gamemap", Command(::changeMap))
        commands.add("load", Command(::loadGame))
        commands.add("sv_shutdown") { args ->
            val reason: String? = if (args.size > 1) {
                args[1]
            } else {
                "Server is shut down"
            }
            stop(reason + "\n", args.size > 2 && args[2].toBoolean())
        }

        commands.add("listmaps") {
            val bytes = EngineVfs.loadBytes("maps.lst")
            if (bytes == null) {
                Com.Error(Defines.ERR_DROP, "Could not read maps.lst")
                return@add
            }
            for (line in String(bytes).split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
                Com.Printf(Defines.PRINT_ALL, line.trim { it <= ' ' } + "\n")
            }
        }
        commands.add("jvm_memory") {
            val runtime = Runtime.getRuntime()
            Com.Printf("vm memory:" + (runtime.totalMemory() - runtime.freeMemory()) + "\n")
        }
        commands.add("games") {
            for (game in games.values) {
                Com.Printf(game.name + " - " + game.mapcmd + "\n")
            }
            Com.Printf("Total " + games.size + " games\n")
        }
        commands.add("spamgames") { args ->
            val total = args[1].toInt()
            repeat(total) {
                changeMap(listOf("gamemap", "base1", "1"))
            }
        }
    }

    /** Typed entrypoint for an embedded host; uses the same map path as the console.  */
    fun startMap(mapName: String): Boolean {
        val previous = games["default"]
        map(listOf("map", mapName))
        return games["default"] != null && games["default"] !== previous
    }

    /** Release this manager's console hooks after ending its last session.  */
    override fun close() {
        commands.use {
            stop("Server stopped\n", false)
        }
    }

    /**
     * Called when each game quits, before Sys_Quit or Sys_Error.
     * todo: shutdown a particular instance, not the whole server
     */
    fun stop(finalmsg: String?, reconnect: Boolean) {
        try {
            if (games.isNotEmpty()) {
                if (clients.isNotEmpty()) sendFinalMessage(finalmsg, reconnect)
                Com.Printf("==== ShutdownGame ====\n")
            }
        } finally {
            games.clear()
            clients.clear()
            publishLegacyServerState(ServerStates.SS_DEAD)
            realtime = 0
            frameNum = 0
            nextFrameTime = 100
        }
    }

    /**
     * Resets the clients when maxclients changes
     */
    fun resetClients() {
        sendFinalMessage("Server restarted", true)
        val max = Cvar.getInstance().find<Int>("maxclients").typedValue

        // Clear all clients
        clients = MutableList(max) {
            ClientNetworkInfo().apply { lastcmd = usercmd_t() }
        }
    }

    private fun initializeServerSession() {
        if (games.isEmpty()) {
            realtime = 0
            frameNum = 0
            nextFrameTime = 100
        }
        val multiplayer = initializeServerCvars()
        if (!NET.configureServer(multiplayer)) {
            Com.Error(Defines.ERR_DROP, "Could not open the server UDP port")
            return
        }
        resetClients()
    }

    /**
     * @return if the game is multiplayer or not
     */
    private fun initializeServerCvars(): Boolean {
        val cvars = Cvar.getInstance()
        val coop = cvars.find<Boolean>("coop")
        val deathmatch = cvars.find<Boolean>("deathmatch")
        val maxclients = cvars.find<Int>("maxclients")

        // get any latched variable changes (maxclients, etc)
        cvars.updateLatchedVars()

        if (coop.typedValue && deathmatch.typedValue) {
            Com.Printf("Deathmatch and Coop both set, disabling Coop\n")
            coop.forceSet(false)
        }

        // dedicated servers are can't be single player and are usually DM
        // so unless they explicity set coop, force it to deathmatch
        if (Globals.dedicated.typedValue) {
            if (!coop.typedValue) deathmatch.forceSet(true)
        }

        // set max clients based on game mode
        if (deathmatch.typedValue) {
            if (maxclients.typedValue <= 1) maxclients.forceSet(8)
        } else if (coop.typedValue) {
            if (maxclients.typedValue !in 2..4) maxclients.forceSet(4)
        } else {
            // non-deathmatch, non-coop is one player
            maxclients.forceSet(1)
        }
        return maxclients.typedValue > 1
    }

    /** Bridge for client loading state and cvar latching until shared-global cleanup. */
    private fun publishLegacyServerState(state: ServerStates) {
        Globals.server_state = state
    }

    // endregion

    // region Game routing and map/save transitions

    fun getClientsForInstance(gameName: String?): MutableList<ClientNetworkInfo?> {
        if (!games.containsKey(gameName)) {
            // happens during the game instance creation, while the instance has random name
            Com.Printf("Game $gameName is not found!\n")
        }
        // nullify unrelated clients
        return clients.map { client ->
            if (client.gameName != null && client.gameName == gameName) client else null
        }.toMutableList()
    }

    // prevent invalid IPs from connecting

    fun allocateGameName(): String {
        return "jake2_${gameCounter++}"
    }

    /**
     * SV_InitGame.
     *
     * A brand new game has been started.
     */
    fun createGameInstance(changeMapInfo: ChangeMapInfo, previous: GameImportsImpl?): GameImportsImpl {
        return GameImportsImpl.create(this, changeMapInfo, previous)
    }

    fun loadGame(args: List<String>) {
        if (args.size != 2) {
            Com.Printf("USAGE: load <directory>\n")
            return
        }

        Com.Printf("Loading game...\n")

        val saveGame: String
        try {
            saveGame = ServerSaveJsonStore.normalizeSlot(args[1])
        } catch (_: IllegalArgumentException) {
            Com.Printf("Bad save name.\n")
            return
        }
        if ("current" == saveGame) {
            Com.Printf("Can't load from 'current'\n")
            return
        }

        // make sure the server metadata file exists
        val store = ServerSaveJsonStore.forWriteDir(EngineWriteRoot.pathString())
        if (!store.hasMapCommand(saveGame)) {
            val name = EngineWriteRoot.resolve("save/$saveGame/server_mapcmd.ssv.json").toString()
            Com.Printf("No such savegame: $name\n")
            return
        }

        ServerSaveFiles(EngineWriteRoot.path()).copy(saveGame, "current")

        val mapCommand = readSavedMapCommand()

        // go to the map
        val loaded = spawnServerInstance(ChangeMapInfo(mapCommand, false, true), 0)
        loaded.name = "default"
        games["default"] = loaded
    }

    private fun readSavedMapCommand(): String {
        Com.DPrintf("SV_ReadMapCommand()\n")

        try {
            val store = ServerSaveJsonStore.forWriteDir(EngineWriteRoot.pathString())
            val snapshot = store.readMapCommand("current")
            // read the comment field
            Com.DPrintf("SV_ReadMapCommand: Loading save: " + snapshot.comment + "\n")

            // read the mapcmd
            return snapshot.mapCommand
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    /*
==================
SV_GameMap_f

Saves the state of the map just being exited and goes to a new map.

If the initial character of the map string is '*', the next map is
in a new unit, so the current savegame directory is cleared of
map files.

Example:

*inter.cin+jail

Clears the archived maps, plays the inter.cin cinematic, then
goes to map jail.bsp.
==================

Spins up a new game instance, to be used with either `map` or `join` (TBD)
*/
    fun changeMap(args: List<String>) {
        if (args.size < 2) {
            Com.Printf("USAGE: gamemap <map>\n <client>")
            return
        }

        val mapName = args[1]
        val clientIndex: Int = if (args.size > 2 && args[2].isNotBlank()) {
            try {
                args[2].toInt()
            } catch (_: NumberFormatException) {
                Com.Printf("USAGE: gamemap <map>\n <client>\n")
                return
            }
        } else 0

        // default.cfg may start a session with "killserver; gamemap *...".
        // Shutdown clears the client slots, so gamemap must initialize a fresh
        // server just as map does, while preserving clients on level transitions.
        if (games.isEmpty()) {
            initializeServerSession()
        }

        Com.DPrintf("SV_GameMap($mapName)\n")

        EngineWriteRoot.ensureParentDirectories(EngineWriteRoot.resolve("save/current/.keep"))

        // check for clearing the current savegame
        if (mapName[0] == '*') {
            // wipe all the *.sav files
            ServerSaveFiles(EngineWriteRoot.path()).wipe("current")
        } else { // save the map just exited
            // note: only applicable to the default instance (subject to change in future)
            val gameImports = games["default"]
            if (gameImports != null && gameImports.state == ServerStates.SS_GAME) {
                gameImports.archiveLevelForTransition()
            }
        }

        val game = spawnServerInstance(ChangeMapInfo(mapName, false, false), clientIndex)

        if (clientIndex == 0) {
            // server just started, `map` cmd was used
            // start up the next map
            game.name = "default"
            games[game.name] = game
            for (cl in clients) {
                cl.gameName = "default"
            }
        } else {
            // preview of the multi instance mode
            val cl = clients[clientIndex - 1]
            val previousGame: GameImportsImpl = games[cl.gameName]!!
            previousGame.gameExports.ClientDisconnect(cl.edict)
            games[game.name] = game
            cl.gameName = game.name
        }

        // copy off the level to the autosave slot
        if (!Globals.dedicated.typedValue) {
            game.SV_WriteServerFile(true)
            ServerSaveFiles(EngineWriteRoot.path()).copy("current", "save0")
        }

        // multi instance preview:
        // when playing the regular flow, multi instances are not requried
        if (clientIndex == 0) {
            games.clear()
            games["default"] = game
        }
    }

    /**
     * SV_Map
     */
    fun spawnServerInstance(changeMapInfo: ChangeMapInfo, clientIndex: Int): GameImportsImpl {
        publishLegacyServerState(ServerStates.SS_DEAD)

        val previous = if (clientIndex != 0)
            games[clients[clientIndex - 1].gameName]
        else
            games["default"]
        previous?.prepareLevelTransition()

        val gameImports = createGameInstance(changeMapInfo, previous)

        Cvar.getInstance().find<String>("nextserver")
            .set("gamemap \"" + changeMapInfo.nextServer + "\"")

        Cmd.ExecuteFunction("loading") // for local system

        if (clientIndex == 0) {
            // leave slots at start for clients only
            for (cl in clients) { //todo
                // needs to reconnect
                if (cl.state == ClientStates.CS_SPAWNED) cl.state = ClientStates.CS_CONNECTED
                cl.lastReceivedFrame = -1
            }
            gameImports.SV_BroadcastCommand("changing")
        } else {
            val cl = clients[clientIndex - 1]
            if (cl.state == ClientStates.CS_SPAWNED) cl.state = ClientStates.CS_CONNECTED
            cl.lastReceivedFrame = -1
            gameImports.unicastMessage(clientIndex, StuffTextMessage("changing"), true)
        }

        sendClientMessages()

        // precache and static commands can be issued during
        // map initialization
        publishLegacyServerState(gameImports.state)

        gameImports.initializeLevel(changeMapInfo, previous)
        publishLegacyServerState(gameImports.state)

        // set serverinfo variable
        Cvar.getInstance().find<String>("mapname").forceSet(gameImports.mapName)
        Com.Println("mapname: " + gameImports.mapName)

        if (changeMapInfo.state == ServerStates.SS_GAME) Cbuf.CopyToDefer()

        if (clientIndex == 0) {
            gameImports.SV_BroadcastCommand("reconnect") // doesn't really reconnect but rather issues 'NEW' command
        } else {
            gameImports.unicastMessage(clientIndex, StuffTextMessage("reconnect"), true)
        }
        return gameImports
    }

    /*
==================
SV_Map_f

Goes directly to a given map without any savegame archiving.
For development work
==================

Resets server to host a given map, moves all clients to it (via reconnect)
*/
    fun map(args: List<String>) {

        if (args.size < 2 || args[1].isBlank()) {
            Com.Printf("usage: map <map_name>\n")
            return
        }

        // if not a pcx, demo, or cinematic, check to make sure the level exists
        val mapName = args[1]
        val target = ChangeMapInfo(mapName, false, false)
        if (target.state == ServerStates.SS_GAME) {
            val mapPath = "maps/" + target.mapName + ".bsp"
            if (!EngineVfs.exists(mapPath)) {
                Com.Printf("Can't find $mapPath\n")
                return
            }
        }
        val game = games["default"]

        if (game != null) game.state = ServerStates.SS_DEAD // don't save current level when changing

        ServerSaveFiles(EngineWriteRoot.path()).wipe("current")

        // A running server needs its clients reset for map. For a fresh server,
        // gamemap performs this initialization itself.
        if (games.isNotEmpty()) {
            initializeServerSession()
        }
        changeMap(args)
    }

    /** Resolve a Game at execution time; creating another Game never replaces these callbacks.  */
    private fun saveGame(args: List<String>) {
        var target = "default"
        var command = args
        if (args.size > 1 && args[1] == "--game") {
            if (args.size < 4) {
                Com.Printf("Usage: " + args[0] + " --game <id> <arguments>\n")
                return
            }
            target = args[2]
            command = listOf(args[0]) + args.drop(3)
        }
        val game = games[target]
        if (game == null) {
            Com.Printf("No running game: $target\n")
            return
        }
        game.saveGame(command)
    }

    // endregion

    // region Frame scheduling and client time accounting

    /**
     * SV_Frame.
     */
    fun update(msec: Long) {
        // if server is not active, do nothing
        // like when connected to another server
        if (games.isEmpty()) return

        realtime += msec
        for (game in games.values) {
            game.realtime += msec.toInt()
        }

        // keep the random time dependent
        Lib.rand()

        // check timeouts
        checkTimeouts()

        // get packets from clients
        readPackets()

        // identify if we need to update the game or it's too early (sleep or skip this update).
        // fixedtime - the time of the next scheduled game update.
        var delay = nextFrameTime - realtime
        if (delay > 0) {
            // never let the time get too far off
            if (delay > 100) { // how is it even possible?
                for (game in games.values) game.realtime = (nextFrameTime - 100)
                delay = 100
            }
            NET.Sleep(delay.toInt())
            return
        }

        // move autonomous things around if enough time has passed
        frameNum++
        nextFrameTime = 100 * frameNum

        // update ping based on the last known frame from all clients
        updateClientPings()

        // give the clients some timeslices
        replenishClientMoveBudgets()

        // let everything in the world think and move
        for (game in games.values) {
            game.SV_RunGameFrame(nextFrameTime.toLong())
        }

        // send messages back to the clients that had packets read this frame
        sendClientMessages()

        // send a heartbeat to the master if needed

        // clear teleport flags, etc for next frame
        for (game in games.values) {
            game.clearEntityStateEvents()
        }
    }

    /**
     * If a packet has not been received from a client for the configured timeout
     * in seconds, drop the connection. All connection timestamps use Host elapsed
     * time, independent of Game replacement and simulation clocks.
     *
     * When a client is normally dropped, the ClientNetworkInfo goes into a zombie state
     * for a few seconds to make sure any final reliable message gets resent if
     * necessary.
     */
    fun checkTimeouts() {
        for (cl in clients) {
            if (cl.state == ClientStates.CS_FREE) continue

            val droppoint = realtime - (1000 * timeout.typedValue).toLong()
            val zombiepoint = realtime - (1000 * zombieTimeout.typedValue).toLong()

            if (cl.state == ClientStates.CS_ZOMBIE && cl.lastmessage < zombiepoint) {
                cl.state = ClientStates.CS_FREE // can now be reused
                continue
            }
            if ((cl.state == ClientStates.CS_CONNECTED || cl.state == ClientStates.CS_SPAWNED) && cl.lastmessage < droppoint) {
                broadcastPrint(Defines.PRINT_HIGH, cl.name + " timed out\n", cl.gameName)
                dropClient(cl)
                cl.state = ClientStates.CS_FREE // don't bother with zombie state
            }
        }
    }

    /**
     * Updates the cl.ping variables.
     */
    fun updateClientPings() {
        for (cl in clients) {
            if (cl.state != ClientStates.CS_SPAWNED) continue

            var total = 0
            var count = 0
            for (j in 0..<Defines.LATENCY_COUNTS) {
                if (cl.frame_latency[j] > 0) {
                    count++
                    total += cl.frame_latency[j]
                }
            }
            if (0 == count) cl.ping = 0
            else cl.ping = total / count

            // let the jake2.game dll know about the ping
            cl.edict.getClient().setPing(cl.ping)
        }
    }

    /**
     * Every few frames, gives all clients an allotment of milliseconds for
     * their command moves. If they exceed it, assume cheating.
     */
    fun replenishClientMoveBudgets() {
        // why *1111
        if ((frameNum and 15) != 0) return

        for (cl in clients) {
            if (cl.state == ClientStates.CS_FREE) continue

            cl.commandMsec = 1800 // 1600 + some slop
        }
    }

    // endregion

    // region Connection admission and disconnection

    /**
     * Builds the string that is sent as heartbeats and status replies.
     */
    private fun buildStatusResponse(): String {
        var status = Cvar.getInstance().Serverinfo() + "\n"

        for (cl in clients) {
            if (cl.state == ClientStates.CS_CONNECTED || cl.state == ClientStates.CS_SPAWNED) {
                val player = "" + cl.edict.getClient()
                    .getPlayerState().stats[Defines.STAT_FRAGS] + " " + cl.ping + "\"" + cl.name + "\"\n"

                val playerLength = player.length
                val statusLength = status.length

                if (statusLength + playerLength >= 1024) break // can't hold any more

                status += player
            }
        }

        return status
    }

    /**
     * SVC_Info, responds with short info for broadcast scans The second parameter should
     * be the current protocol version number.
     */
    private fun buildInfoResponse(args: List<String>): String? {
        if (clients.size == 1) return null // ignore in single player

        val version = if (args.size < 2) 0 else Lib.atoi(args[1])

        val info: String
        if (version != Defines.PROTOCOL_VERSION) info = hostname.typedValue + ": wrong version\n"
        else {
            var players = 0
            for (cl in clients) if (cl.state == ClientStates.CS_CONNECTED || cl.state == ClientStates.CS_SPAWNED) players++

            info =
                hostname.typedValue + " " + games["default"]!!.mapName + " " + players + "/" + clients.size + "\n"
        }

        return info
    }

    /**
     * Returns a challenge number that can be used in a subsequent
     * client_connect command. We do this to prevent denial of service attacks
     * that flood the server with invalid connection IPs. With a challenge, they
     * must give a valid IP address.
     */
    private fun getChallenge(from: netadr_t): Int {
        var oldest = 0
        var oldestTime = 0x7fffffff

        var i: Int
        i = 0
        while (i < Defines.MAX_CHALLENGES) {
            if (from.CompareBaseAdr(challenges[i].adr)) break
            if (challenges[i].time < oldestTime) {
                oldestTime = challenges[i].time
                oldest = i
            }
            i++
        }

        if (i == Defines.MAX_CHALLENGES) {
            // overwrite the oldest
            challenges[oldest].challenge = Lib.rand().toInt() and 0x7fff
            challenges[oldest].adr = from
            challenges[oldest].time = Globals.curtime
            i = oldest
        }

        // send it back
        return challenges[i].challenge
    }

    /**
     * A connection request that did not come from the master.
     */
    private fun connectClient(args: List<String>, adr: netadr_t) {
        Com.DPrintf("SVC_DirectConnect ()\n")

        val version = if (args.size >= 2) Lib.atoi(args[1]) else 0

        if (version != Defines.PROTOCOL_VERSION) {
            Netchan.sendConnectionlessPacket(
                Defines.NS_SERVER, adr, ConnectionlessCommand.print,
                "\nServer is version " + Globals.VERSION + "\n"
            )
            Com.DPrintf("    rejected connect from version $version\n")
            return
        }

        val qport = if (args.size >= 3) Lib.atoi(args[2]) else 0
        val challenge = if (args.size >= 4) Lib.atoi(args[3]) else 0
        var userinfo = if (args.size >= 5) args[4] else ""

        // force the IP key/value pair so the game can filter based on ip
        userinfo = Info.Info_SetValueForKey(userinfo, "ip", adr.toString())

        // see if the challenge is valid
        var j: Int
        if (!adr.IsLocalAddress()) {
            j = 0
            while (j < Defines.MAX_CHALLENGES) {
                if (adr.CompareBaseAdr(challenges[j].adr)) {
                    if (challenge == challenges[j].challenge) break // good

                    Netchan.sendConnectionlessPacket(
                        Defines.NS_SERVER,
                        adr,
                        ConnectionlessCommand.print,
                        "\nBad challenge.\n"
                    )
                    return
                }
                j++
            }
            if (j == Defines.MAX_CHALLENGES) {
                Netchan.sendConnectionlessPacket(
                    Defines.NS_SERVER,
                    adr,
                    ConnectionlessCommand.print,
                    "\nNo challenge for address.\n"
                )
                return
            }
        }

        // if there is already a slot for this ip, reuse it
        var i = 0
        for (cl in clients) {
            if (cl.state == ClientStates.CS_FREE) continue
            if (adr.CompareBaseAdr(cl.netchan.remote_address)
                && (cl.netchan.qport == qport || adr.port == cl.netchan.remote_address.port)
            ) {
                if (!adr.IsLocalAddress() && (realtime - cl.lastconnect) < (reconnectLimit.typedValue * 1000)) {
                    Com.DPrintf("$adr:reconnect rejected : too soon\n")
                    return
                }
                Com.Printf("$adr:reconnect\n")

                initializeClientConnection(i, challenge, userinfo, adr, qport)
                return
            }
            i++
        }

        // find a client slot
        var index = -1
        i = 0
        for (cl in clients) {
            if (cl.state == ClientStates.CS_FREE) {
                index = i
                break
            }
            i++
        }
        if (index == -1) {
            Netchan.sendConnectionlessPacket(Defines.NS_SERVER, adr, ConnectionlessCommand.print, "\nServer is full.\n")
            Com.DPrintf("Rejected a connection.\n")
            return
        }
        initializeClientConnection(index, challenge, userinfo, adr, qport)
    }

    /**
     * Initializes player structures after successfull connection.
     */
    private fun initializeClientConnection(i: Int, challenge: Int, userinfo: String?, adr: netadr_t, qport: Int) {
        // build a new connection
        // accept the new client
        // this is the only place a ClientNetworkInfo is ever initialized

        val client = clients[i]
        val gameImports: GameImportsImpl = games["default"]!!
        client.gameName = "default"

        val ent = gameImports.gameExports.getEdict(i + 1)
        client.edict = ent // fixme: why? isn't it already set?

        // save challenge for checksumming
        client.challenge = challenge

        // get the game a chance to reject this connection or modify the
        // userinfo
        if (!(gameImports.gameExports.ClientConnect(ent, userinfo))) {
            if (Info.Info_ValueForKey(userinfo, "rejmsg") != null) {
                Netchan.sendConnectionlessPacket(
                    Defines.NS_SERVER,
                    adr,
                    ConnectionlessCommand.print,
                    "\n" + Info.Info_ValueForKey(userinfo, "rejmsg") + "\nConnection refused.\n"
                )
            } else {
                Netchan.sendConnectionlessPacket(
                    Defines.NS_SERVER,
                    adr,
                    ConnectionlessCommand.print,
                    "\nConnection refused.\n"
                )
            }
            Com.DPrintf("Game rejected a connection.\n")
            return
        }

        // parse some info from the info strings
        client.userinfo = userinfo
        updateClientUserinfo(client)

        Netchan.sendConnectionlessPacket(Defines.NS_SERVER, adr, ConnectionlessCommand.client_connect, "")

        client.netchan.setup(Defines.NS_SERVER, adr, qport)

        client.state = ClientStates.CS_CONNECTED

        client.lastmessage = realtime // don't timeout
        client.lastconnect = realtime
        Com.DPrintf("new client added.\n")
    }

    /**
     * A connectionless packet has four leading 0xff characters to distinguish
     * it from a game channel. Clients that are in the game can still send
     * connectionless packets. It is used also by rcon commands.
     */
    fun handleConnectionlessPacket(from: netadr_t, messageLine: String?) {
        val args = Cmd.TokenizeString(messageLine, false)

        if (args.isEmpty()) {
            Com.Printf("empty connectionless packet: $messageLine")
            return
        }

        val c = args[0]
        val cmd = ConnectionlessCommand.fromString(c)

        when (cmd) {
            ConnectionlessCommand.ping ->                 // SVC_Ping, Just responds with an acknowledgement.
                Netchan.sendConnectionlessPacket(Defines.NS_SERVER, from, ConnectionlessCommand.ack, "")

            ConnectionlessCommand.ack ->                 //SVC_Ack
                Com.Printf("Ping acknowledge from $from\n")

            ConnectionlessCommand.status ->                 // SVC_Status, Responds with all the info that qplug or qspy can see
                Netchan.sendConnectionlessPacket(
                    Defines.NS_SERVER,
                    from,
                    ConnectionlessCommand.print,
                    "\n" + buildStatusResponse()
                )

            ConnectionlessCommand.info -> {
                val info = buildInfoResponse(args)
                if (info != null) Netchan.sendConnectionlessPacket(
                    Defines.NS_SERVER,
                    from,
                    ConnectionlessCommand.info,
                    "\n" + info
                )
            }

            ConnectionlessCommand.getchallenge -> {
                val challenge = getChallenge(from)
                Netchan.sendConnectionlessPacket(
                    Defines.NS_SERVER,
                    from,
                    ConnectionlessCommand.challenge,
                    " $challenge"
                )
            }

            ConnectionlessCommand.connect -> connectClient(args, from)
            ConnectionlessCommand.rcon -> executeRemoteCommand(args, from)
            else -> Com.Printf("Unknown ClientConnectionlessCommand: $c\n")
        }
    }

    /**
     * Called when the player is totally leaving the server, either willingly or
     * unwillingly. This is NOT called if the entire server is quiting or
     * crashing.
     */
    fun dropClient(client: ClientNetworkInfo) {
        // add the disconnect
        client.netchan.reliablePending.add(DisconnectMessage())

        if (client.state == ClientStates.CS_SPAWNED) {
            // call the prog function for removing a client
            // this will remove the body, among other things
            games[client.gameName]!!.gameExports.ClientDisconnect(client.edict)
        }

        if (client.download != null) {
            client.download = null
        }

        client.lastmessage = realtime // start the zombie grace period on the Host clock
        client.state = ClientStates.CS_ZOMBIE // become free in a few seconds
        client.name = ""
    }

    // endregion

    // region Incoming packets and client commands

    /**
     * Reads packets from the network or loopback.
     */
    fun readPackets() {
        while (true) {
            val networkPacket = NET.receiveNetworkPacket(
                NET.ip_sockets[Defines.NS_SERVER],
                NET.ip_channels[Defines.NS_SERVER],
                NET.loopbacks[Defines.NS_SERVER],
                true
            )

            if (networkPacket == null) break

            if (networkPacket.isConnectionless) {
                handleConnectionlessPacket(networkPacket.from, networkPacket.connectionlessMessage)
                continue
            }

            // Match incoming packet with the existing client.
            // todo: get client by address (hashmap?)
            for (cl in clients) {
                if (cl.state == ClientStates.CS_FREE) continue
                if (!networkPacket.from.CompareBaseAdr(cl.netchan.remote_address)) continue
                if (cl.netchan.qport != networkPacket.qport) continue
                if (cl.netchan.remote_address.port != networkPacket.from.port) {
                    Com.Printf("SV_ReadPackets: fixing up a translated port\n")
                    cl.netchan.remote_address.port = networkPacket.from.port
                }

                if (cl.netchan.accept(networkPacket)) {
                    // this is a valid, sequenced packet, so process it
                    if (cl.state != ClientStates.CS_ZOMBIE) {
                        cl.lastmessage = realtime // don't timeout
                        val body = networkPacket.parseBodyFromClient(cl.netchan.incoming_sequence)
                        if (body == null) dropClient(cl)
                        else handleClientMessages(cl, body)
                    }
                }
                break
            }
        }
    }

    fun handleClientMessages(cl: ClientNetworkInfo, clientMessages: Collection<ClientMessage>) {
        // only allow one move command

        var moveIssued = false
        var stringCmdCount = 0

        for (msg in clientMessages) {
            if (msg is EndOfClientPacketMessage) {
                break
            } else if (msg is StringCmdMessage) {
                // malicious users may try using too many string commands
                if (++stringCmdCount < MAX_STRINGCMDS) {
                    executeClientCommand(cl, msg.command)
                }

                if (cl.state == ClientStates.CS_ZOMBIE) {
                    return  // disconnect command
                }
            } else if (msg is UserInfoMessage) {
                cl.userinfo = msg.userInfo
                updateClientUserinfo(cl)
            } else if (msg is MoveMessage) {
                if (moveIssued) return  // someone is trying to cheat...

                moveIssued = true
                val lastReceivedFrame = msg.lastReceivedFrame

                if (lastReceivedFrame != cl.lastReceivedFrame) {
                    cl.lastReceivedFrame = lastReceivedFrame
                    if (cl.lastReceivedFrame > 0) {
                        val frameLatency =
                            (realtime - cl.frames[cl.lastReceivedFrame and Defines.UPDATE_MASK].senttime)
                                .coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
                        cl.frame_latency[cl.lastReceivedFrame and (Defines.LATENCY_COUNTS - 1)] = frameLatency
                    }
                }

                if (cl.state != ClientStates.CS_SPAWNED) {
                    cl.lastReceivedFrame = -1
                    continue
                }

                // if the checksum fails, ignore the rest of the packet
                if (!msg.valid) {
                    Com.Printf("Invalid crc\n")
                    return
                }

                if (!paused.typedValue) {
                    var droppedPackets = cl.netchan.dropped
                    if (droppedPackets < 20) {
                        //if (droppedPackets > 2)

                        //	Com.Printf ("drop %i\n", droppedPackets);

                        while (droppedPackets > 2) {
                            processClientMove(cl, cl.lastcmd)

                            droppedPackets--
                        }
                        if (droppedPackets > 1) processClientMove(cl, msg.oldestCmd)

                        if (droppedPackets > 0) processClientMove(cl, msg.oldCmd)
                    }
                    processClientMove(cl, msg.newCmd)
                }

                // copy.
                cl.lastcmd.set(msg.newCmd)
            } else {
                Com.Printf("SV_ReadClientMessage: unknown command: " + msg.type + "\n")
                dropClient(cl)
            }
        }
    }

    fun processClientMove(cl: ClientNetworkInfo, cmd: usercmd_t) {
        cl.commandMsec -= cmd.msec.toInt() and 0xFF

        if (cl.commandMsec < 0 && enforceMoveTime.typedValue) {
            Com.DPrintf("commandMsec underflow from " + cl.name + "\n")
            return
        }

        games[cl.gameName]!!.gameExports.ClientThink(cl.edict, cmd)
    }

    /**
     * Pull specific info from a newly changed userinfo string into a more C
     * freindly form.
     */
    fun updateClientUserinfo(cl: ClientNetworkInfo) {
        // call prog code to allow overrides
        games[cl.gameName]!!.gameExports.ClientUserinfoChanged(cl.edict, cl.userinfo)

        // name for C code
        cl.name = Info.Info_ValueForKey(cl.userinfo, "name")

        // mask off high bit

        // rate command
        var userinfoValue = Info.Info_ValueForKey(cl.userinfo, "rate")
        if (userinfoValue.isNotEmpty()) {
            cl.rate = Lib.atoi(userinfoValue)
            if (cl.rate < 100) cl.rate = 100
            if (cl.rate > 15000) cl.rate = 15000
        } else cl.rate = 5000

        // msg command
        userinfoValue = Info.Info_ValueForKey(cl.userinfo, "msg")
        if (userinfoValue.isNotEmpty()) {
            cl.messagelevel = Lib.atoi(userinfoValue)
        }
    }

    fun executeClientCommand(cl: ClientNetworkInfo, command: String?) {
        Com.dprintln("SV_ExecuteUserCommand:$command")

        val args = Cmd.TokenizeString(command, true)
        if (args.isEmpty()) return

        val game: GameImportsImpl = games[cl.gameName]!!
        if (userCommands.containsKey(args[0])) {
            userCommands[args[0]]!!.execute(args, game, cl)
            return
        }

        if (game.state == ServerStates.SS_GAME) game.gameExports.ClientCommand(cl.edict, args)
    }

    // endregion

    // region Game handshake and cinematic progression

    /*
     * ================ SV_New_f
     *
     * Sends the first message from the server to a connected client. This will
     * be sent on the initial connection and upon each server load.
     * ================
     */
    private fun sendInitialGameData(gameImports: GameImportsImpl, client: ClientNetworkInfo) {
        Com.DPrintf("New() from " + client.name + "\n")

        if (client.state != ClientStates.CS_CONNECTED) {
            Com.Printf("New not valid -- already spawned\n")
            return
        }

        //
        // serverdata needs to go over for all types of servers
        // to make sure the protocol is right, and to set the gamedir
        //
        val gamedir = Cvar.getInstance().find<String>("gamedir").typedValue

        // send the serverdata
        // send full levelname
        val clientIndex: Int = if (gameImports.state == ServerStates.SS_CINEMATIC || gameImports.state == ServerStates.SS_PIC) {
            -1
        } else {
            client.edict.index - 1
        }

        client.netchan.reliablePending.add(
            ServerDataMessage(
                Defines.PROTOCOL_VERSION,
                gameImports.spawncount,
                gameImports.isDemo,
                gamedir,
                clientIndex,
                gameImports.configstrings[Defines.CS_NAME]
            )
        )
        //
        // game server
        //
        if (gameImports.state == ServerStates.SS_GAME) {
            // set up the entity for the client
            val ent = gameImports.gameExports.getEdict(clientIndex + 1)
            ent.s.index = clientIndex + 1
            client.edict = ent
            client.lastcmd = usercmd_t()

            // begin fetching configstrings
            client.netchan.reliablePending.add(
                StuffTextMessage(
                    String.format(
                        "cmd %s %d 0",
                        StringCmdMessage.CONFIG_STRINGS,
                        gameImports.spawncount
                    )
                )
            )
        }
    }

    private fun sendConfigstrings(
        args: List<String>,
        gameImports: GameImportsImpl,
        client: ClientNetworkInfo
    ) {
        Com.DPrintf("Configstrings() from " + client.name + "\n")

        if (client.state != ClientStates.CS_CONNECTED) {
            Com.Printf("configstrings not valid -- already spawned\n")
            return
        }

        // handle the case of a level changing while a client was connecting
        val spawnCount = if (args.size >= 2) Lib.atoi(args[1]) else 0
        if (spawnCount != gameImports.spawncount) {
            Com.Printf("SV_Configstrings_f from different level\n")
            sendInitialGameData(gameImports, client)
            return
        }

        var start = if (args.size >= 3) Lib.atoi(args[2]) else 0

        // write a packet full of data
        var currentReliableSize =
            client.netchan.reliablePending.sumOf { it.size }
        while (currentReliableSize < Defines.MAX_MSGLEN / 2 && start < Defines.MAX_CONFIGSTRINGS) {
            if (gameImports.configstrings[start] != null && gameImports.configstrings[start].isNotEmpty()) {
                val config = ConfigStringMessage(start, gameImports.configstrings[start])
                currentReliableSize += config.size
                client.netchan.reliablePending.add(config)
            }
            start++
        }

        // send next command
        val nextCmd: String = if (start == Defines.MAX_CONFIGSTRINGS) {
            String.format("cmd %s %d 0", StringCmdMessage.BASELINES, gameImports.spawncount)
        } else {
            String.format("cmd %s %d %d", StringCmdMessage.CONFIG_STRINGS, gameImports.spawncount, start)
        }
        client.netchan.reliablePending.add(StuffTextMessage(nextCmd))
    }

    private fun sendBaselines(args: List<String>, gameImports: GameImportsImpl, client: ClientNetworkInfo) {
        Com.DPrintf("Baselines() from " + client.name + "\n")

        if (client.state != ClientStates.CS_CONNECTED) {
            Com.Printf("baselines not valid -- already spawned\n")
            return
        }

        // handle the case of a level changing while a client was connecting
        val spawnCount = if (args.size >= 2) Lib.atoi(args[1]) else 0
        if (spawnCount != gameImports.spawncount) {
            Com.Printf("SV_Baselines_f from different level\n")
            sendInitialGameData(gameImports, client)
            return
        }

        // todo: validate argument
        var start = if (args.size >= 3) Lib.atoi(args[2]) else 0

        // write a packet full of data
        var currentReliableSize =
            client.netchan.reliablePending.sumOf { it.size }
        while (currentReliableSize < Defines.MAX_MSGLEN / 2 && start < Defines.MAX_EDICTS) {
            val base = gameImports.baselines[start]
            if (base.modelindex != 0 || base.sound != 0 || base.effects != 0) {
                val spawn = SpawnBaselineMessage(base)
                currentReliableSize += spawn.size
                client.netchan.reliablePending.add(spawn)
            }
            start++
        }

        // send next command
        val nextCmd: String = if (start == Defines.MAX_EDICTS) {
            // finished sending baselines
            String.format("%s %d", StringCmdMessage.PRECACHE, gameImports.spawncount)
        } else {
            // continue from where we finished
            String.format("cmd %s %d %d", StringCmdMessage.BASELINES, gameImports.spawncount, start)
        }
        client.netchan.reliablePending.add(StuffTextMessage(nextCmd))
    }

    private fun beginClient(args: List<String>, gameImports: GameImportsImpl, client: ClientNetworkInfo) {
        Com.DPrintf("Begin() from " + client.name + "\n")

        // handle the case of a level changing while a client was connecting
        val spawnCount = if (args.size >= 2) Lib.atoi(args[1]) else 0
        if (spawnCount != gameImports.spawncount) {
            Com.Printf("SV_Begin_f from different level\n")
            sendInitialGameData(gameImports, client)
            return
        }

        client.state = ClientStates.CS_SPAWNED

        // call the jake2.game begin function
        gameImports.gameExports.ClientBegin(client.edict)

        Cbuf.InsertFromDefer()
    }

    private fun advanceCinematic(gameImports: GameImportsImpl) {
        //ZOID, ss_pic can be nextserver'd in coop mode

        if (gameImports.state == ServerStates.SS_GAME
            || (gameImports.state == ServerStates.SS_PIC && !Cvar.getInstance()
                .find<Boolean>("coop").typedValue)
        ) return  // can't nextserver while playing a normal game

        gameImports.spawncount++ // make sure another doesn't sneak in
        val v = Cvar.getInstance().find<String>("nextserver").typedValue

        if (v.isEmpty()) Cbuf.AddText("killserver\n")
        else {
            Cbuf.AddText(v)
            Cbuf.AddText("\n")
        }
        Cvar.getInstance().find<String>("nextserver").set("")
    }

    /*
     * ================== SV_Nextserver_f
     *
     * A cinematic has completed or been aborted by a client, so move to the
     * next server, ==================
     */
    private fun finishCinematic(args: List<String>, gameImports: GameImportsImpl, client: ClientNetworkInfo) {
        val spawnCount = if (args.size >= 2) Lib.atoi(args[1]) else 0
        if (spawnCount != gameImports.spawncount) {
            Com.DPrintf("Nextserver() from wrong level, from " + client.name + "\n")
            return  // leftover from last server
        }

        Com.DPrintf("Nextserver() from " + client.name + "\n")
        advanceCinematic(gameImports)
    }

    // endregion

    // region Downloads

    private fun sendNextDownloadChunk(client: ClientNetworkInfo) {
        if (client.download == null) return

        var packet = client.downloadsize - client.downloadcount
        if (packet > 1024) packet = 1024

        client.downloadcount += packet
        var size = client.downloadsize
        if (size == 0) size = 1
        val percent = (client.downloadcount * 100 / size).toByte()

        val data = ByteArray(packet)
        System.arraycopy(client.download, client.downloadcount - packet, data, 0, packet)
        client.netchan.reliablePending.add(DownloadMessage(data, percent))

        if (client.downloadcount == client.downloadsize) {
            client.download = null
        }
    }

    private fun beginDownload(
        args: List<String>,
        client: ClientNetworkInfo
    ) {
        var offset = 0

        if (args.size < 2) return

        val name = args[1]

        if (args.size > 2) offset = Lib.atoi(args[2]) // downloaded offset

        // hacked by zoid to allow more conrol over download
        // first off, no .. or global allow check
        if (name.contains("..")
            || !allowDownload.typedValue // leading dot is no good
            || name[0] == '.' // leading slash bad as well, must be
            // in subdir
            || name[0] == '/' // next up, skin check
            || (name.startsWith("players/") && !allowPlayerDownloads.typedValue) // now
            // models
            || (name.startsWith("models/") && !allowModelDownloads.typedValue) // now
            // sounds
            || (name.startsWith("sound/") && !allowSoundDownloads.typedValue) // now maps (note special case for maps, must not be in pak)
            || (name.startsWith("maps/") && !allowMapDownloads.typedValue) // MUST
            // be
            // in a
            // subdirectory
            || name.indexOf('/') == -1
        ) { // don't allow anything with ..
            // path

            // refuse

            client.netchan.reliablePending.add(DownloadMessage())
            return
        }

        client.download = EngineVfs.loadBytes(name)

        // The client waits for either data or an explicit refusal. Silently
        // returning here leaves precache stuck waiting for a download forever.
        if (client.download == null) {
            client.netchan.reliablePending.add(DownloadMessage())
            return
        }

        client.downloadsize = client.download.size
        client.downloadcount = offset

        if (offset > client.downloadsize) client.downloadcount = client.downloadsize

        // special check for maps, if it came from a pak file, don't allow download ZOID
        if (name.startsWith("maps/") && EngineVfs.isFromPack(name)) {
            Com.DPrintf("Couldn't download " + name + " to " + client.name + "\n")
            if (client.download != null) {
                client.download = null
            }

            // refuse
            client.netchan.reliablePending.add(DownloadMessage())
            return
        }

        sendNextDownloadChunk(client)
        Com.DPrintf("Downloading " + name + " to " + client.name + "\n")
    }

    // endregion

    // region Outgoing messages and rate limiting

    fun sendClientMessages() {
        // send a message to each connected client

        for (c in clients) {
            if (c.state == ClientStates.CS_FREE) continue

            val game: GameImportsImpl = games[c.gameName]!!
            // if the reliable message overflowed,
            // drop the client
            val pendingReliableSize =
                c.netchan.reliablePending.sumOf { it.size }
            if (pendingReliableSize > Defines.MAX_MSGLEN - 16) {
                c.netchan.reliablePending.clear()
                c.unreliable.clear()
                broadcastPrint(Defines.PRINT_HIGH, c.name + " overflowed\n", c.gameName)
                dropClient(c)
            }

            if (game.state == ServerStates.SS_CINEMATIC || game.state == ServerStates.SS_DEMO || game.state == ServerStates.SS_PIC) {
                // leftover from demo code
                c.netchan.transmit(null)
            } else if (c.state == ClientStates.CS_SPAWNED) {
                // don't overrun bandwidth
                if (shouldRateLimit(c)) continue

                sendClientDatagram(c, game)
            } else {
                // just update reliable	if needed
                if (c.netchan.reliablePending.isNotEmpty() || Globals.curtime - c.netchan.last_sent > 1000) c.netchan.transmit(
                    null
                )
            }
        }
    }

    /**
     * Sends text to all active clients
     */
    fun broadcastPrint(level: Int, message: String?, gameName: String?) {
        // echo to console

        if (Globals.dedicated.typedValue) {
            Com.Printf(message)
        }

        for (cl in getClientsForInstance(gameName)) {
            if (cl == null || level < cl.messagelevel) continue
            if (cl.state != ClientStates.CS_SPAWNED) continue
            cl.netchan.reliablePending.add(PrintMessage(level, message))
        }
    }

    /**
     * SV_RateDrop
     *
     *
     * Returns true if the client is over its current
     * bandwidth estimation and should not be sent another packet
     */
    fun shouldRateLimit(c: ClientNetworkInfo): Boolean {
        // never drop over the loopback

        if (c.netchan.remote_address.type == NetAddrType.NA_LOOPBACK) return false

        var total = 0

        for (i in 0..<Defines.RATE_MESSAGES) {
            total += c.message_size[i]
        }

        if (total > c.rate) {
            c.surpressCount++
            c.message_size[frameNum % Defines.RATE_MESSAGES] = 0
            return true
        }

        return false
    }

    /**
     * Used by stop to send a final message to all connected clients
     * before the server goes down. The messages are sent immediately, not just
     * stuck on the outgoing message list, because the server is going to
     * totally exit after returning from this function.
     */
    private fun sendFinalMessage(message: String?, reconnect: Boolean) {
        val msgs: MutableCollection<NetworkMessage> = ArrayList()
        msgs.add(PrintMessage(Defines.PRINT_HIGH, message))

        if (reconnect) msgs.add(ReconnectMessage())
        else msgs.add(DisconnectMessage())

        // send it twice
        // stagger the packets to crutch operating system limited buffers
        for (cl in clients) {
            if (cl.state == ClientStates.CS_CONNECTED || cl.state == ClientStates.CS_SPAWNED) cl.netchan.transmit(msgs)
        }
        for (cl in clients) {
            if (cl.state == ClientStates.CS_CONNECTED || cl.state == ClientStates.CS_SPAWNED) cl.netchan.transmit(msgs)
        }
    }

    /** Send this client's Game snapshot followed by its queued events, recording rate usage.  */
    private fun sendClientDatagram(client: ClientNetworkInfo, gameImports: GameImportsImpl) {
        // send over all the relevant entity_state_t
        // and the player_state_t
        val unreliable: MutableList<NetworkMessage> = mutableListOf()
        unreliable.addAll(gameImports.buildClientFrameMessages(client)) // 3 messages

        // copy the accumulated multicast datagram
        // for this client out to the message
        // it is necessary for this to be after the buildClientFrameMessages (SV_BuildClientFrame)
        // so that entity references will be current
        unreliable.addAll(client.unreliable)
        client.unreliable.clear()
        // send the datagram
        client.frames[gameImports.frameNum and Defines.UPDATE_MASK].senttime = realtime
        client.netchan.transmit(unreliable)

        // record the size for rate estimation
        client.message_size[frameNum % Defines.RATE_MESSAGES] =
            unreliable.sumOf { it.size }
    }

    // endregion

    // region Operator commands and diagnostics

    private fun kickClient(args: List<String>) {
        if (args.size != 2) {
            Com.Printf("Usage: kick <userid>\n")
            return
        }
        val client = findClient(args) ?: return

        broadcastPrint(Defines.PRINT_HIGH, client.name + " was kicked\n", client.gameName)
        // print directly, because the dropped client won't get the
        // SV_BroadcastPrintf message
        client.queuePrint(Defines.PRINT_HIGH, "You were kicked from the game\n")
        dropClient(client)
    }

    private fun printStatus() {
        // todo: check
        if (games.isEmpty()) {
            Com.Printf("No server running.\n")
            return
        }
        for (game in games.values) Com.Printf("game " + game.name + " : " + game.mapName + "\n")

        Com.Printf("num score ping name            lastmsg address               qport \n")
        Com.Printf("--- ----- ---- --------------- ------- --------------------- ------\n")

        for (i in clients.indices) {
            val cl = clients[i]
            if (ClientStates.CS_FREE == cl.state) continue

            Com.Printf(i.toString().padStart(3) + " ")
            Com.Printf(cl.edict.getClient().getPlayerState().stats[Defines.STAT_FRAGS].toString().padStart(5) + " ")

            when (cl.state) {
                ClientStates.CS_CONNECTED -> Com.Printf("CNCT ")
                ClientStates.CS_ZOMBIE -> Com.Printf("ZMBI ")
                else -> {
                    val ping = if (cl.ping < 9999) cl.ping else 9999
                    Com.Printf(ping.toString().padStart(4) + " ")
                }
            }

            Com.Printf(cl.name.padEnd(16))
            val lastMessageAge = realtime - cl.lastmessage
            Com.Printf(lastMessageAge.toString().padStart(7) + " ")
            Com.Printf(cl.netchan.remote_address.toString().padEnd(22))
            Com.Printf(cl.netchan.qport.toString().padStart(5))

            Com.Printf("\n")
        }
        Com.Printf("\n")
    }

    private fun say(args: List<String>) {
        if (args.size < 2) return

        // char[1024];
        var text = "console: "
        var p = Cmd.getArguments(args)

        if (p.isNotEmpty() && p[0] == '"') {
            p = p.substring(1, p.length - 1)
        }

        text += p

        for (client in clients) {
            if (client.state != ClientStates.CS_SPAWNED) continue
            client.queuePrint(Defines.PRINT_CHAT, text + "\n")
        }
    }

    private fun printServerInfo() {
        Com.Printf("Server info settings:\n")
        Info.Print(Cvar.getInstance().Serverinfo())
    }

    private fun dumpUser(args: List<String>) {
        if (args.size != 2) {
            Com.Printf("Usage: info <userid>\n")
            return
        }
        val client = findClient(args) ?: return

        Com.Printf("userinfo\n")
        Com.Printf("--------\n")
        Info.Print(client.userinfo)
    }

    /**
     * Sets sv_client and sv_player to the player with idnum Cmd.Argv(1)
     */
    private fun findClient(args: List<String>): ClientNetworkInfo? {
        if (args.size < 2) return null

        val idOrName = args[1]

        // numeric values are just slot numbers
        // fixme: player name cannot start with a number?
        if (idOrName.isNotEmpty() && idOrName[0] >= '0' && idOrName[0] <= '9') {
            val id = Lib.atoi(idOrName)
            if (id < 0 || id >= clients.size) {
                Com.Printf("Bad client slot: $id\n")
                return null
            }

            val result = clients[id]
            if (ClientStates.CS_FREE == result.state) {
                Com.Printf("Client $id is not active\n")
                return null
            }
            return result
        }

        // check for a name match
        for (cl in clients) {
            if (ClientStates.CS_FREE == cl.state) continue
            if (idOrName == cl.name) {
                return cl
            }
        }

        Com.Printf("Userid $idOrName is not on the server\n")
        return null
    }

    // endregion

    companion object {
        // Recreating an embedded manager must reuse the same choice declaration.
        private val NEEDPASS_TYPE = ChoiceType(listOf(0, 1, 2, 3))

        private const val MAX_STRINGCMDS = 8

        /**
         * Checks if the rcon password is corect.
         */
        private fun isRconAuthorized(args: List<String>): Boolean {
            val rconPassword = Cvar.getInstance().find<String>("rcon_password").typedValue
            if (rconPassword.isEmpty()) return false

            return args.size >= 2 && args[1] == rconPassword
        }

        /**
         * A client issued an rcon command. Shift down the remaining args Redirect
         * all printfs from the server to the client.
         */
        private fun executeRemoteCommand(args: List<String>, from: netadr_t?) {
            val rconIsValid: Boolean = isRconAuthorized(args)

            if (rconIsValid) {
                Com.Printf("Rcon from $from:\n$args\n")
            } else {
                Com.Printf("Bad rcon from $from:\n$args\n")
            }

            Com.BeginRedirect { buffer ->
                Netchan.sendConnectionlessPacket(
                    Defines.NS_SERVER,
                    from,
                    ConnectionlessCommand.print,
                    "\n" + buffer.toString()
                )
            }
            if (rconIsValid) {
                Cmd.ExecuteString(Cmd.getArguments(args, 2))
            } else {
                // redirected to client
                Com.Printf("Bad rcon_password.\n")
            }

            Com.EndRedirect()
        }
    }
}
