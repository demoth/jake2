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
package jake2.server

import jake2.qcommon.*
import jake2.qcommon.Com.RD_Flusher
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
import jake2.qcommon.util.Vargs
import jake2.qcommon.vfs.EngineVfs
import jake2.qcommon.vfs.EngineWriteRoot
import jake2.server.save.ServerSaveFiles
import jake2.server.save.ServerSaveJsonStore

/** Owns client connections, Game routing, protocol and commands for one server Host. */
@Suppress("deprecation")
class ServerHost : AutoCloseable {
    private val commands = ServerCommands()
    val isRunning: Boolean
        get() = !games.isEmpty()

    /** Typed entrypoint for an embedded host; uses the same map path as the console.  */
    fun startMap(mapName: String): Boolean {
        val previous = games["default"]
        SV_Map_f(java.util.List.of<String>("map", mapName))
        return games.get("default") != null && games.get("default") !== previous
    }

    /** Release this manager's console hooks after ending its last session.  */
    override fun close() {
        try {
            stop("Server stopped\n", false)
        } finally {
            commands.close()
        }
    }

    @JvmField
    val games: MutableMap<String, GameImportsImpl> = HashMap()

    fun getClientsForInstance(gameName: String?): MutableList<ClientNetworkInfo?> {
        if (!games.containsKey(gameName)) {
            // happens during the game instance creation, while the instance has random name
            Com.Printf("Game " + gameName + " is not found!\n")
        }
        // nullify unrelated clients
        return clients.map { client ->
            if (client.gameName != null && client.gameName == gameName) client else null
        }.toMutableList()
    }


    // prevent invalid IPs from connecting
    private val challenges = Array(Defines.MAX_CHALLENGES) { challenge_t() }

    var clients: MutableList<ClientNetworkInfo> = ArrayList<ClientNetworkInfo>()
        private set

    private var realtime: Long = 0
    private var frameNum = 0
    private var fixedtime = 1000
    private var gameCounter = 0

    fun allocateGameName(): String {
        return "jake2_" + gameCounter++
    }

    val isPaused: Boolean
        get() = sv_paused.getTypedValue()

    private val sv_paused: cvar_t<Boolean>

    private val sv_timedemo: cvar_t<Boolean>

    private val sv_enforcetime: cvar_t<Boolean>

    @JvmField
    val timeout: cvar_t<Float> // seconds without any message

    private val zombietime: cvar_t<Float> // seconds to sink messages after

    // disconnect
    private val allow_download: cvar_t<Boolean>

    private val allow_download_players: cvar_t<Boolean>

    private val allow_download_models: cvar_t<Boolean>

    private val allow_download_sounds: cvar_t<Boolean>

    private val allow_download_maps: cvar_t<Boolean>

    private val sv_airaccelerate: cvar_t<Float>

    private val sv_showclamp: cvar_t<Boolean>

    private val hostname: cvar_t<String>

    private val public_server: cvar_t<Boolean> // should heartbeats be sent

    private val sv_reconnect_limit: cvar_t<Float> // minimum seconds between connect


    // messages
    /**
     * Builds the string that is sent as heartbeats and status replies.
     */
    private fun SV_StatusString(): String {
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
    private fun SVC_Info(args: MutableList<String>, from: netadr_t?): String? {
        if (clients.size == 1) return null // ignore in single player


        val version = if (args.size < 2) 0 else Lib.atoi(args.get(1))

        val info: String
        if (version != Defines.PROTOCOL_VERSION) info = hostname.getTypedValue() + ": wrong version\n"
        else {
            var players = 0
            for (cl in clients) if (cl.state == ClientStates.CS_CONNECTED || cl.state == ClientStates.CS_SPAWNED) players++

            info =
                hostname.getTypedValue() + " " + games.get("default")!!.mapName + " " + players + "/" + clients.size + "\n"
        }

        return info
    }

    /**
     * Returns a challenge number that can be used in a subsequent
     * client_connect command. We do this to prevent denial of service attacks
     * that flood the server with invalid connection IPs. With a challenge, they
     * must give a valid IP address.
     */
    private fun SVC_GetChallenge(from: netadr_t): Int {
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
    private fun SVC_DirectConnect(args: MutableList<String>, adr: netadr_t) {
        Com.DPrintf("SVC_DirectConnect ()\n")

        val version = if (args.size >= 2) Lib.atoi(args.get(1)) else 0

        if (version != Defines.PROTOCOL_VERSION) {
            Netchan.sendConnectionlessPacket(
                Defines.NS_SERVER, adr, ConnectionlessCommand.print,
                "\nServer is version " + Globals.VERSION + "\n"
            )
            Com.DPrintf("    rejected connect from version " + version + "\n")
            return
        }

        val qport = if (args.size >= 3) Lib.atoi(args.get(2)) else 0
        val challenge = if (args.size >= 4) Lib.atoi(args.get(3)) else 0
        var userinfo = if (args.size >= 5) args.get(4) else ""

        // force the IP key/value pair so the game can filter based on ip
        userinfo = Info.Info_SetValueForKey(userinfo, "ip", adr.toString())


        /*
        if (gameImports.isDemo) {
            if (!adr.IsLocalAddress()) {
                Com.Printf("Remote connect in attract loop.  Ignored.\n");
                Netchan.sendConnectionlessPacket(Defines.NS_SERVER, adr, ConnectionlessCommand.print, "\nConnection refused.\n");
                return;
            }
        }
*/


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
                if (!adr.IsLocalAddress() && (realtime - cl.lastconnect) < (sv_reconnect_limit.getTypedValue() * 1000)) {
                    Com.DPrintf(adr.toString() + ":reconnect rejected : too soon\n")
                    return
                }
                Com.Printf(adr.toString() + ":reconnect\n")

                gotnewcl(i, challenge, userinfo, adr, qport)
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
        gotnewcl(index, challenge, userinfo, adr, qport)
    }

    /**
     * Initializes player structures after successfull connection.
     */
    private fun gotnewcl(i: Int, challenge: Int, userinfo: String?, adr: netadr_t, qport: Int) {
        // build a new connection
        // accept the new client
        // this is the only place a ClientNetworkInfo is ever initialized

        val client = clients.get(i)
        val gameImports: GameImportsImpl = games.get("default")!!
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
        SV_UserinfoChanged(client)

        Netchan.sendConnectionlessPacket(Defines.NS_SERVER, adr, ConnectionlessCommand.client_connect, "")

        client.netchan.setup(Defines.NS_SERVER, adr, qport)

        client.state = ClientStates.CS_CONNECTED

        client.lastmessage = gameImports.realtime // don't timeout
        client.lastconnect = gameImports.realtime
        Com.DPrintf("new client added.\n")
    }

    /**
     * A connectionless packet has four leading 0xff characters to distinguish
     * it from a game channel. Clients that are in the game can still send
     * connectionless packets. It is used also by rcon commands.
     */
    fun SV_ConnectionlessPacket(from: netadr_t, messageLine: String?) {
        val args = Cmd.TokenizeString(messageLine, false)

        if (args.isEmpty()) {
            Com.Printf("empty connectionless packet: " + messageLine)
            return
        }

        val c = args.get(0)
        val cmd = ConnectionlessCommand.fromString(c)

        when (cmd) {
            ConnectionlessCommand.ping ->                 // SVC_Ping, Just responds with an acknowledgement.
                Netchan.sendConnectionlessPacket(Defines.NS_SERVER, from, ConnectionlessCommand.ack, "")

            ConnectionlessCommand.ack ->                 //SVC_Ack
                Com.Printf("Ping acknowledge from " + from + "\n")

            ConnectionlessCommand.status ->                 // SVC_Status, Responds with all the info that qplug or qspy can see
                Netchan.sendConnectionlessPacket(
                    Defines.NS_SERVER,
                    from,
                    ConnectionlessCommand.print,
                    "\n" + SV_StatusString()
                )

            ConnectionlessCommand.info -> {
                val info = SVC_Info(args, from)
                if (info != null) Netchan.sendConnectionlessPacket(
                    Defines.NS_SERVER,
                    from,
                    ConnectionlessCommand.info,
                    "\n" + info
                )
            }

            ConnectionlessCommand.getchallenge -> {
                val challenge = SVC_GetChallenge(from)
                Netchan.sendConnectionlessPacket(
                    Defines.NS_SERVER,
                    from,
                    ConnectionlessCommand.challenge,
                    " " + challenge
                )
            }

            ConnectionlessCommand.connect -> SVC_DirectConnect(args, from)
            ConnectionlessCommand.rcon -> SVC_RemoteCommand(args, from)
            else -> Com.Printf("Unknown ClientConnectionlessCommand: " + c + '\n')
        }
    }

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
        SV_CheckTimeouts()

        // get packets from clients
        SV_ReadPackets()

        // identify if we need to update the game or it's too early (sleep or skip this update).
        // fixedtime - the time of the next scheduled game update.
        var delay = fixedtime - realtime
        if (delay > 0) {
            // never let the time get too far off
            if (delay > 100) { // how is it even possible?
                for (game in games.values) game.realtime = (fixedtime - 100)
                delay = 100
            }
            NET.Sleep(delay.toInt())
            return
        }

        // move autonomous things around if enough time has passed
        frameNum++
        fixedtime = 100 * frameNum

        // update ping based on the last known frame from all clients
        SV_CalcPings()

        // give the clients some timeslices
        SV_GiveMsec()

        // let everything in the world think and move
        for (game in games.values) {
            game.SV_RunGameFrame(fixedtime.toLong())
        }

        // send messages back to the clients that had packets read this frame
        SV_SendClientMessages()


        // send a heartbeat to the master if needed
        //Master_Heartbeat();

        // clear teleport flags, etc for next frame
        for (game in games.values) {
            game.clearEntityStateEvents()
        }
    }

    fun SV_SendClientMessages() {
        // send a message to each connected client

        for (c in clients) {
            if (c.state == ClientStates.CS_FREE) continue

            val game: GameImportsImpl = games.get(c.gameName)!!
            // if the reliable message overflowed,
            // drop the client
            val pendingReliableSize =
                c.netchan.reliablePending.stream().mapToInt { obj: NetworkMessage? -> obj!!.getSize() }.sum()
            if (pendingReliableSize > Defines.MAX_MSGLEN - 16) {
                c.netchan.reliablePending.clear()
                c.unreliable.clear()
                SV_BroadcastPrintf(Defines.PRINT_HIGH, c.name + " overflowed\n", c.gameName)
                SV_DropClient(c)
            }

            if (game.state == ServerStates.SS_CINEMATIC || game.state == ServerStates.SS_DEMO || game.state == ServerStates.SS_PIC) {
                // leftover from demo code
                c.netchan.transmit(null)
            } else if (c.state == ClientStates.CS_SPAWNED) {
                // don't overrun bandwidth
                if (SV_RateDrop(c)) continue

                sendClientDatagram(c, game)
            } else {
                // just update reliable	if needed
                if (c.netchan.reliablePending.size != 0 || Globals.curtime - c.netchan.last_sent > 1000) c.netchan.transmit(
                    null
                )
            }
        }
    }

    /**
     * Called when the player is totally leaving the server, either willingly or
     * unwillingly. This is NOT called if the entire server is quiting or
     * crashing.
     */
    fun SV_DropClient(client: ClientNetworkInfo) {
        // add the disconnect
        client.netchan.reliablePending.add(DisconnectMessage())

        if (client.state == ClientStates.CS_SPAWNED) {
            // call the prog function for removing a client
            // this will remove the body, among other things
            games.get(client.gameName)!!.gameExports.ClientDisconnect(client.edict)
        }

        if (client.download != null) {
            client.download = null
        }

        client.state = ClientStates.CS_ZOMBIE // become free in a few seconds
        client.name = ""
    }

    /**
     * Sends text to all active clients
     */
    fun SV_BroadcastPrintf(level: Int, message: String?, gameName: String?) {
        // echo to console

        if (Globals.dedicated.getTypedValue()) {
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
    fun SV_RateDrop(c: ClientNetworkInfo): Boolean {
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
     * Reads packets from the network or loopback.
     */
    fun SV_ReadPackets() {
        while (true) {
            val networkPacket = NET.receiveNetworkPacket(
                NET.ip_sockets[Defines.NS_SERVER],
                NET.ip_channels[Defines.NS_SERVER],
                NET.loopbacks[Defines.NS_SERVER],
                true
            )

            if (networkPacket == null) break

            if (networkPacket.isConnectionless()) {
                SV_ConnectionlessPacket(networkPacket.from, networkPacket.connectionlessMessage)
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
                        cl.lastmessage = games.get(cl.gameName)!!.realtime // don't timeout
                        val body = networkPacket.parseBodyFromClient(cl.netchan.incoming_sequence)
                        if (body == null) SV_DropClient(cl)
                        else SV_ExecuteClientMessage(cl, body)
                    }
                }
                break
            }
        }
    }

    /*
     * =================== SV_ExecuteClientMessage
     *
     * The current net_message is parsed for the given client
     * ===================
     */
    fun SV_ExecuteClientMessage(cl: ClientNetworkInfo, clientMessages: MutableCollection<ClientMessage>) {
        // only allow one move command

        var move_issued = false
        var stringCmdCount = 0


        for (msg in clientMessages) {
            if (msg is EndOfClientPacketMessage) {
                break
            } else if (msg is StringCmdMessage) {
                val m = msg
                // malicious users may try using too many string commands
                if (++stringCmdCount < MAX_STRINGCMDS) {
                    SV_ExecuteUserCommand(cl, m.command)
                }

                if (cl.state == ClientStates.CS_ZOMBIE) {
                    return  // disconnect command
                }
            } else if (msg is UserInfoMessage) {
                val m = msg
                cl.userinfo = m.userInfo
                SV_UserinfoChanged(cl)
            } else if (msg is MoveMessage) {
                val m = msg
                if (move_issued) return  // someone is trying to cheat...


                move_issued = true
                val lastReceivedFrame = m.lastReceivedFrame

                if (lastReceivedFrame != cl.lastReceivedFrame) {
                    cl.lastReceivedFrame = lastReceivedFrame
                    if (cl.lastReceivedFrame > 0) {
                        val framaLatency =
                            games.get(cl.gameName)!!.realtime - cl.frames[cl.lastReceivedFrame and Defines.UPDATE_MASK].senttime
                        cl.frame_latency[cl.lastReceivedFrame and (Defines.LATENCY_COUNTS - 1)] = framaLatency
                    }
                }

                if (cl.state != ClientStates.CS_SPAWNED) {
                    cl.lastReceivedFrame = -1
                    continue
                }

                // if the checksum fails, ignore the rest of the packet
                if (!m.valid) {
                    Com.Printf("Invalid crc\n")
                    return
                }


                if (!sv_paused.getTypedValue()) {
                    var net_drop = cl.netchan.dropped
                    if (net_drop < 20) {
                        //if (net_drop > 2)

                        //	Com.Printf ("drop %i\n", net_drop);

                        while (net_drop > 2) {
                            SV_ClientThink(cl, cl.lastcmd)

                            net_drop--
                        }
                        if (net_drop > 1) SV_ClientThink(cl, m.oldestCmd)

                        if (net_drop > 0) SV_ClientThink(cl, m.oldCmd)
                    }
                    SV_ClientThink(cl, m.newCmd)
                }

                // copy.
                cl.lastcmd.set(m.newCmd)
            } else {
                Com.Printf("SV_ReadClientMessage: unknown command: " + msg.type + "\n")
                SV_DropClient(cl)
            }
        }
    }

    fun SV_ClientThink(cl: ClientNetworkInfo, cmd: usercmd_t) {
        cl.commandMsec -= cmd.msec.toInt() and 0xFF

        if (cl.commandMsec < 0 && sv_enforcetime.getTypedValue()) {
            Com.DPrintf("commandMsec underflow from " + cl.name + "\n")
            return
        }

        games.get(cl.gameName)!!.gameExports.ClientThink(cl.edict, cmd)
    }

    /**
     * Pull specific info from a newly changed userinfo string into a more C
     * freindly form.
     */
    fun SV_UserinfoChanged(cl: ClientNetworkInfo) {
        // call prog code to allow overrides
        games.get(cl.gameName)!!.gameExports.ClientUserinfoChanged(cl.edict, cl.userinfo)

        // name for C code
        cl.name = Info.Info_ValueForKey(cl.userinfo, "name")

        // mask off high bit
        //TODO: masking for german umlaute
        //for (i=0 ; i<sizeof(cl.name) ; i++)
        //	cl.name[i] &= 127;

        // rate command
        var `val` = Info.Info_ValueForKey(cl.userinfo, "rate")
        if (`val`.length > 0) {
            cl.rate = Lib.atoi(`val`)
            if (cl.rate < 100) cl.rate = 100
            if (cl.rate > 15000) cl.rate = 15000
        } else cl.rate = 5000

        // msg command
        `val` = Info.Info_ValueForKey(cl.userinfo, "msg")
        if (`val`.length > 0) {
            cl.messagelevel = Lib.atoi(`val`)
        }
    }

    fun SV_ExecuteUserCommand(cl: ClientNetworkInfo, command: String?) {
        Com.dprintln("SV_ExecuteUserCommand:" + command)

        val args = Cmd.TokenizeString(command, true)
        if (args.isEmpty()) return

        val game: GameImportsImpl = games.get(cl.gameName)!!
        if (userCommands.containsKey(args.get(0))) {
            userCommands.get(args.get(0))!!.execute(args, game, cl)
            return
        }

        if (game.state == ServerStates.SS_GAME) game.gameExports.ClientCommand(cl.edict, args)
    }

    /**
     * If a packet has not been received from a client for the configured timeout
     * in seconds, drop the connection. Server frames are used instead of realtime
     * to avoid dropping the local client while debugging.
     *
     * When a client is normally dropped, the ClientNetworkInfo goes into a zombie state
     * for a few seconds to make sure any final reliable message gets resent if
     * necessary.
     */
    fun SV_CheckTimeouts() {
        for (cl in clients) {
            if (cl.state == ClientStates.CS_FREE) continue

            val game: GameImportsImpl = games.get(cl.gameName)!!
            val droppoint = (game.realtime - 1000 * timeout.getTypedValue()).toInt()
            val zombiepoint = (game.realtime - 1000 * zombietime.getTypedValue()).toInt()

            // message times may be wrong across a changelevel
            if (cl.lastmessage > game.realtime) cl.lastmessage = game.realtime

            if (cl.state == ClientStates.CS_ZOMBIE && cl.lastmessage < zombiepoint) {
                cl.state = ClientStates.CS_FREE // can now be reused
                continue
            }
            if ((cl.state == ClientStates.CS_CONNECTED || cl.state == ClientStates.CS_SPAWNED) && cl.lastmessage < droppoint) {
                SV_BroadcastPrintf(Defines.PRINT_HIGH, cl.name + " timed out\n", cl.gameName)
                SV_DropClient(cl)
                cl.state = ClientStates.CS_FREE // don't bother with zombie state
            }
        }
    }

    /**
     * Updates the cl.ping variables.
     */
    fun SV_CalcPings() {
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
    fun SV_GiveMsec() {
        // why *1111
        if ((frameNum and 15) != 0) return

        for (cl in clients) {
            if (cl.state == ClientStates.CS_FREE) continue

            if ((games.get(cl.gameName)!!.frameNum and 15) != 0) continue

            cl.commandMsec = 1800 // 1600 + some slop
        }
    }

    /**
     * Used by stop to send a final message to all connected clients
     * before the server goes down. The messages are sent immediately, not just
     * stuck on the outgoing message list, because the server is going to
     * totally exit after returning from this function.
     */
    private fun SV_FinalMessage(message: String?, reconnect: Boolean) {
        val msgs: MutableCollection<NetworkMessage> = ArrayList<NetworkMessage>()
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

    /**
     * Called when each game quits, before Sys_Quit or Sys_Error.
     * todo: shutdown a particular instance, not the whole server
     */
    fun stop(finalmsg: String?, reconnect: Boolean) {
        try {
            if (!games.isEmpty()) {
                if (!clients.isEmpty()) SV_FinalMessage(finalmsg, reconnect)
                Com.Printf("==== ShutdownGame ====\n")
            }
        } finally {
            games.clear()
            clients.clear()
            Globals.server_state = ServerStates.SS_DEAD
            realtime = 0
            frameNum = 0
            fixedtime = 100
        }
    }

    /**
     * Resets the clients when maxclients changes
     */
    fun resetClients() {
        SV_FinalMessage("Server restarted", true)
        val max = Cvar.getInstance().find<Int>("maxclients", Int::class.java).getTypedValue()

        // Clear all clients
        clients = ArrayList<ClientNetworkInfo>(max)
        for (i in 0..<max) {
            val cl = ClientNetworkInfo()
            cl.lastcmd = usercmd_t()
            clients.add(cl)
        }
    }

    /**
     * SV_InitGame.
     *
     * A brand new game has been started.
     */
    fun createGameInstance(changeMapInfo: ChangeMapInfo, previous: GameImportsImpl?): GameImportsImpl {
        return GameImportsImpl.create(this, changeMapInfo, previous)
    }

    /**
     * @return if the game is multiplayer or not
     */
    private fun initializeServerCvars(): Boolean {
        val cvars = Cvar.getInstance()
        val coop = cvars.find<Boolean>("coop", Boolean::class.java)
        val deathmatch = cvars.find<Boolean>("deathmatch", Boolean::class.java)
        val maxclients = cvars.find<Int>("maxclients", Int::class.java)

        // get any latched variable changes (maxclients, etc)
        cvars.updateLatchedVars()

        if (coop.getTypedValue() && deathmatch.getTypedValue()) {
            Com.Printf("Deathmatch and Coop both set, disabling Coop\n")
            coop.forceSet(false)
        }

        // dedicated servers are can't be single player and are usually DM
        // so unless they explicity set coop, force it to deathmatch
        if (Globals.dedicated.getTypedValue()) {
            if (!coop.getTypedValue()) deathmatch.forceSet(true)
        }


        // set max clients based on game mode
        if (deathmatch.getTypedValue()) {
            if (maxclients.getTypedValue() <= 1) maxclients.forceSet(8)
        } else if (coop.getTypedValue()) {
            if (maxclients.getTypedValue() <= 1 || maxclients.getTypedValue() > 4) maxclients.forceSet(4)
        } else {
            // non-deathmatch, non-coop is one player
            maxclients.forceSet(1)
        }
        return maxclients.getTypedValue() > 1
    }

    fun SV_Loadgame_f(args: MutableList<String>) {
        if (args.size != 2) {
            Com.Printf("USAGE: load <directory>\n")
            return
        }

        Com.Printf("Loading game...\n")

        val saveGame: String
        try {
            saveGame = ServerSaveJsonStore.normalizeSlot(args.get(1))
        } catch (e: IllegalArgumentException) {
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
            val name = EngineWriteRoot.resolve("save/" + saveGame + "/server_mapcmd.ssv.json").toString()
            Com.Printf("No such savegame: " + name + "\n")
            return
        }

        ServerSaveFiles(EngineWriteRoot.path()).copy(saveGame, "current")

        val mapCommand = SV_ReadMapCommand()

        // go to the map
        val loaded = spawnServerInstance(ChangeMapInfo(mapCommand, false, true), 0)
        loaded.name = "default"
        games.put("default", loaded)
    }

    private fun SV_ReadMapCommand(): String {
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
    fun SV_GameMap_f(args: MutableList<String>) {
        if (args.size < 2) {
            Com.Printf("USAGE: gamemap <map>\n <client>")
            return
        }

        val mapName = args.get(1)

        val clientIndex: Int
        if (args.size > 2 && !args.get(2).isBlank()) {
            try {
                clientIndex = args.get(2).toInt()
            } catch (e: NumberFormatException) {
                Com.Printf("USAGE: gamemap <map>\n <client>\n")
                return
            }
        } else clientIndex = 0

        // default.cfg may start a session with "killserver; gamemap *...".
        // Shutdown clears the client slots, so gamemap must initialize a fresh
        // server just as map does, while preserving clients on level transitions.
        if (games.isEmpty()) {
            initializeServerSession()
        }

        Com.DPrintf("SV_GameMap(" + mapName + ")\n")

        EngineWriteRoot.ensureParentDirectories(EngineWriteRoot.resolve("save/current/.keep"))

        // check for clearing the current savegame
        if (mapName.get(0) == '*') {
            // wipe all the *.sav files
            ServerSaveFiles(EngineWriteRoot.path()).wipe("current")
        } else { // save the map just exited
            // note: only applicable to the default instance (subject to change in future)
            val gameImports = games.get("default")
            if (gameImports != null && gameImports.state == ServerStates.SS_GAME) {
                // clear all the client inuse flags before saving so that
                // when the level is re-entered, the clients will spawn
                // at spawn points instead of occupying body shells
                // todo: only relevant to this gameImports clients
                val savedInuse = BooleanArray(clients.size)
                var i = 0
                for (cl in clients) {
                    if (cl.edict != null) { // free client slots will have null values
                        savedInuse[i] = cl.edict.inuse
                        cl.edict.inuse = false
                    }
                    i++
                }

                gameImports.SV_WriteLevelFile()

                // we must restore these for clients to transfer over correctly
                i = 0
                for (cl in clients) {
                    if (cl.edict != null) { // free client slots will have null values
                        cl.edict.inuse = savedInuse[i]
                    }
                    i++
                }
            }
        }

        val game = spawnServerInstance(ChangeMapInfo(mapName, false, false), clientIndex)

        if (clientIndex == 0) {
            // server just started, `map` cmd was used
            // start up the next map
            game.name = "default"
            games.put(game.name, game)
            for (cl in clients) {
                cl.gameName = "default"
            }
        } else {
            // preview of the multi instance mode
            val cl = clients.get(clientIndex - 1)
            val previousGame: GameImportsImpl = games.get(cl.gameName)!!
            previousGame.gameExports.ClientDisconnect(cl.edict)
            games.put(game.name, game)
            cl.gameName = game.name
        }

        // copy off the level to the autosave slot
        if (!Globals.dedicated.getTypedValue()) {
            game.SV_WriteServerFile(true)
            ServerSaveFiles(EngineWriteRoot.path()).copy("current", "save0")
        }

        // multi instance preview:
        // when playing the regular flow, multi instances are not requried
        if (clientIndex == 0) {
            games.clear()
            games.put("default", game)
        }
    }

    /**
     * SV_Map
     */
    fun spawnServerInstance(changeMapInfo: ChangeMapInfo, clientIndex: Int): GameImportsImpl {
        Globals.server_state = ServerStates.SS_DEAD //fixme: used by the client code

        val previous = if (clientIndex != 0)
            games.get(clients.get(clientIndex - 1).gameName)
        else
            games.get("default")
        if (previous != null) previous.prepareLevelTransition()

        val gameImports = createGameInstance(changeMapInfo, previous)

        Cvar.getInstance().find<String>("nextserver", String::class.java)
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
            val cl = clients.get(clientIndex - 1)
            if (cl.state == ClientStates.CS_SPAWNED) cl.state = ClientStates.CS_CONNECTED
            cl.lastReceivedFrame = -1
            gameImports.unicastMessage(clientIndex, StuffTextMessage("changing"), true)
        }

        SV_SendClientMessages()


        // precache and static commands can be issued during
        // map initialization
        Globals.server_state = gameImports.state // LOADING

        gameImports.initializeLevel(changeMapInfo, previous)
        Globals.server_state = gameImports.state


        // set serverinfo variable
        Cvar.getInstance().find<String>("mapname", String::class.java).forceSet(gameImports.mapName)
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
    fun SV_Map_f(args: MutableList<String>) {
        //char expanded[MAX_QPATH];
        if (args.size < 2 || args.get(1).isBlank()) {
            Com.Printf("usage: map <map_name>\n")
            return
        }

        // if not a pcx, demo, or cinematic, check to make sure the level exists
        val mapName = args.get(1)
        val target = ChangeMapInfo(mapName, false, false)
        if (target.state == ServerStates.SS_GAME) {
            val mapPath = "maps/" + target.mapName + ".bsp"
            if (!EngineVfs.exists(mapPath)) {
                Com.Printf("Can't find " + mapPath + "\n")
                return
            }
        }
        val game = games.get("default")

        if (game != null) game.state = ServerStates.SS_DEAD // don't save current level when changing


        ServerSaveFiles(EngineWriteRoot.path()).wipe("current")

        // A running server needs its clients reset for map. For a fresh server,
        // gamemap performs this initialization itself.
        if (!games.isEmpty()) {
            initializeServerSession()
        }
        SV_GameMap_f(args)
    }

    private fun initializeServerSession() {
        if (games.isEmpty()) {
            realtime = 0
            frameNum = 0
            fixedtime = 100
        }
        val multiplayer = initializeServerCvars()
        if (!NET.configureServer(multiplayer)) {
            Com.Error(Defines.ERR_DROP, "Could not open the server UDP port")
            return
        }
        resetClients()
    }

    /** Send this client's Game snapshot followed by its queued events, recording rate usage.  */
    private fun sendClientDatagram(client: ClientNetworkInfo, gameImports: GameImportsImpl) {
        // send over all the relevant entity_state_t
        // and the player_state_t
        val unreliable: MutableCollection<NetworkMessage?> = ArrayList<NetworkMessage?>()
        unreliable.addAll(gameImports.buildClientFrameMessages(client)) // 3 messages

        // copy the accumulated multicast datagram
        // for this client out to the message
        // it is necessary for this to be after the buildClientFrameMessages (SV_BuildClientFrame)
        // so that entity references will be current
        unreliable.addAll(client.unreliable)
        client.unreliable.clear()
        // send the datagram
        client.netchan.transmit(unreliable)

        // record the size for rate estimation
        client.message_size[gameImports.frameNum % Defines.RATE_MESSAGES] =
            unreliable.stream().mapToInt { obj: NetworkMessage? -> obj!!.getSize() }.sum()
    }

    private val userCommands = mapOf(
        StringCmdMessage.NEW to ServerUserCommand(::SV_New_f),
        StringCmdMessage.CONFIG_STRINGS to ServerUserCommand(::SV_Configstrings_f),
        StringCmdMessage.BASELINES to ServerUserCommand(::SV_Baselines_f),
        StringCmdMessage.BEGIN to ServerUserCommand(::SV_Begin_f),
        StringCmdMessage.NEXT_SERVER to ServerUserCommand(::SV_Nextserver_f),
        StringCmdMessage.DISCONNECT to ServerUserCommand(::SV_Disconnect_f),
        StringCmdMessage.INFO to ServerUserCommand(::SV_ShowServerinfo_f),
        StringCmdMessage.DOWNLOAD to ServerUserCommand(::SV_BeginDownload_f),
        StringCmdMessage.NEXT_DOWNLOAD to ServerUserCommand(::SV_NextDownload_f),
    )

    /**
     * One manager per dedicated process or embedded session; map changes reuse it.
     */
    init {
        val cvars = Cvar.getInstance()
        cvars.register<String>(
            "spectator_password", CvarTypes.STRING, "", Defines.CVAR_USERINFO,
            "Spectator admission password; empty or none allows access"
        )
        cvars.register<Int>(
            "needpass", NEEDPASS_TYPE, 0, Defines.CVAR_SERVERINFO,
            "Required passwords: 0 none, 1 player, 2 spectator, 3 both"
        )
        cvars.register<Boolean>("deathmatch", CvarTypes.BOOLEAN, false, Defines.CVAR_SERVERINFO or Defines.CVAR_LATCH)
        cvars.register<Boolean>("coop", CvarTypes.BOOLEAN, false, Defines.CVAR_SERVERINFO or Defines.CVAR_LATCH)
        cvars.register<Int>(
            "dmflags",
            CvarTypes.INTEGER,
            Defines.DF_INSTANT_ITEMS,
            Defines.CVAR_SERVERINFO,
            "Deathmatch rule bitmask; combine DF_* flags"
        )
        cvars.register<Int>(
            "fraglimit",
            CvarTypes.IntegerType(0, null),
            0,
            Defines.CVAR_SERVERINFO,
            "End a deathmatch level at this score; 0 disables the limit"
        )
        cvars.register<Float>(
            "timelimit",
            CvarTypes.FloatType(0f, null),
            0f,
            Defines.CVAR_SERVERINFO,
            "Deathmatch level time limit in minutes; 0 disables the limit"
        )
        cvars.register<Boolean>("cheats", CvarTypes.BOOLEAN, false, Defines.CVAR_SERVERINFO or Defines.CVAR_LATCH)
        cvars.register<Int>(
            "protocol",
            CvarTypes.INTEGER,
            Defines.PROTOCOL_VERSION,
            Defines.CVAR_SERVERINFO or Defines.CVAR_NOSET
        )
        cvars.register<String>("mapname", CvarTypes.STRING, "", Defines.CVAR_SERVERINFO or Defines.CVAR_NOSET)
        cvars.register<String>(
            "nextserver",
            CvarTypes.STRING,
            "",
            0,
            "Console command to execute after a cinematic or picture"
        )
        cvars.register<Int>(
            "maxclients",
            CvarTypes.IntegerType(1, Defines.MAX_CLIENTS),
            1,
            Defines.CVAR_SERVERINFO or Defines.CVAR_LATCH,
            "Client slot limit; single-player and coop mode also restrict the count"
        )

        hostname = cvars.register<String>(
            "hostname",
            CvarTypes.STRING,
            "noname",
            Defines.CVAR_SERVERINFO or Defines.CVAR_ARCHIVE
        )
        timeout = cvars.register<Float>(
            "timeout",
            CvarTypes.FloatType(0f, null),
            125f,
            0,
            "Seconds without a client message before disconnection"
        )
        zombietime = cvars.register<Float>(
            "zombietime",
            CvarTypes.FloatType(0f, null),
            2f,
            0,
            "Seconds to retain a disconnected client slot for final reliable messages"
        )
        sv_showclamp = cvars.register<Boolean>(
            "showclamp",
            CvarTypes.BOOLEAN,
            false,
            0,
            "Legacy frame-time clamp diagnostics; unused by the current server and Cake"
        )
        sv_paused = cvars.register<Boolean>("paused", CvarTypes.BOOLEAN, false, 0)
        sv_timedemo = cvars.register<Boolean>(
            "timedemo",
            CvarTypes.BOOLEAN,
            false,
            0,
            "Legacy demo benchmarking switch; unused by the current server and Cake"
        )
        sv_enforcetime = cvars.register<Boolean>(
            "sv_enforcetime",
            CvarTypes.BOOLEAN,
            false,
            0,
            "Ignore movement commands that exceed the client time budget"
        )

        // come from MainCommon
        allow_download = cvars.find<Boolean>("allow_download", Boolean::class.java)
        allow_download_players = cvars.find<Boolean>("allow_download_players", Boolean::class.java)
        allow_download_models = cvars.find<Boolean>("allow_download_models", Boolean::class.java)
        allow_download_sounds = cvars.find<Boolean>("allow_download_sounds", Boolean::class.java)
        allow_download_maps = cvars.find<Boolean>("allow_download_maps", Boolean::class.java)

        cvars.register<Boolean>(
            "sv_noreload",
            CvarTypes.BOOLEAN,
            false,
            0,
            "Start revisited levels fresh instead of restoring their saved state"
        )
        sv_airaccelerate = cvars.register<Float>(
            "sv_airaccelerate",
            CvarTypes.FLOAT,
            0f,
            Defines.CVAR_LATCH,
            "Controls air acceleration (currently only for deathmatch)"
        )
        public_server = cvars.register<Boolean>(
            "public",
            CvarTypes.BOOLEAN,
            false,
            0,
            "Legacy master-server advertising switch; currently unused"
        )
        sv_reconnect_limit = cvars.register<Float>(
            "sv_reconnect_limit",
            CvarTypes.FloatType(0f, null),
            3f,
            Defines.CVAR_ARCHIVE,
            "Minimum seconds between reconnect attempts from the same remote client"
        )
        resetClients()

        // Publish Host callbacks only after initialization succeeds. They survive Game replacement.
        commands.add("heartbeat", true, Command { agrs: MutableList<String> -> this.SV_Heartbeat_f(agrs) })
        commands.add("kick", true, Command { args: MutableList<String> -> this.SV_Kick_f(args) })
        commands.add("status", true, Command { args: MutableList<String> -> this.SV_Status_f(args) })
        commands.add("serverinfo", true, Command { args: MutableList<String> -> this.SV_Serverinfo_f(args) })
        commands.add("dumpuser", true, Command { args: MutableList<String> -> this.SV_DumpUser_f(args) })
        commands.add("say", true, Command { args: MutableList<String> -> this.SV_ConSay_f(args) })
        commands.add("save", true, Command { args: MutableList<String> -> this.saveGame(args) })

        commands.add("killserver", Command { args: MutableList<String> ->
            stop("Server was killed.\n", false)
            NET.configureServer(false)
        })
        commands.add("map", Command { args: MutableList<String> -> this.SV_Map_f(args) })
        commands.add("gamemap", Command { args: MutableList<String> -> this.SV_GameMap_f(args) })
        commands.add("load", Command { args: MutableList<String> -> this.SV_Loadgame_f(args) })
        commands.add("sv_shutdown", Command { args: MutableList<String> ->
            val reason: String?
            if (args.size > 1) {
                reason = args.get(1)
            } else {
                reason = "Server is shut down"
            }
            stop(reason + "\n", args.size > 2 && args.get(2).toBoolean())
        })

        commands.add("listmaps", Command { args: MutableList<String> ->
            val bytes = EngineVfs.loadBytes("maps.lst")
            if (bytes == null) {
                Com.Error(Defines.ERR_DROP, "Could not read maps.lst")
                return@Command
            }
            for (line in String(bytes).split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
                Com.Printf(Defines.PRINT_ALL, line.trim { it <= ' ' } + "\n")
            }
        })
        commands.add("jvm_memory", Command { args: MutableList<String> ->
            val runtime = Runtime.getRuntime()
            Com.Printf("vm memory:" + (runtime.totalMemory() - runtime.freeMemory()) + "\n")
        })
        commands.add("games", Command { args: MutableList<String> ->
            for (game in games.values) {
                Com.Printf(game.name + " - " + game.mapcmd + "\n")
            }
            Com.Printf("Total " + games.size + " games\n")
        })
        commands.add("spamgames", Command { args: MutableList<String> ->
            val total = args.get(1).toInt()
            for (i in 0..<total) {
                SV_GameMap_f(mutableListOf<String>("gamemap", "base1", "1"))
            }
        })
    }

    private fun interface ServerUserCommand {
        fun execute(args: MutableList<String>, game: GameImportsImpl, client: ClientNetworkInfo)
    }

    /*
     * ============================================================
     *
     * USER STRINGCMD EXECUTION
     *
     * sv_client and sv_player will be valid.
     * ============================================================
     */
    /*
     * ================ SV_New_f
     *
     * Sends the first message from the server to a connected client. This will
     * be sent on the initial connection and upon each server load.
     * ================
     */
    private fun SV_New_f(args: MutableList<String>, gameImports: GameImportsImpl, client: ClientNetworkInfo) {
        Com.DPrintf("New() from " + client.name + "\n")

        if (client.state != ClientStates.CS_CONNECTED) {
            Com.Printf("New not valid -- already spawned\n")
            return
        }

        //
        // serverdata needs to go over for all types of servers
        // to make sure the protocol is right, and to set the gamedir
        //
        val gamedir = Cvar.getInstance().find<String>("gamedir", String::class.java).getTypedValue()

        // send the serverdata
        // send full levelname
        val clientIndex: Int
        if (gameImports.state == ServerStates.SS_CINEMATIC || gameImports.state == ServerStates.SS_PIC) clientIndex = -1
        else clientIndex = client.edict.index - 1


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

    /*
     * ================== SV_Configstrings_f ==================
     */
    private fun SV_Configstrings_f(
        args: MutableList<String>,
        gameImports: GameImportsImpl,
        client: ClientNetworkInfo
    ) {
        Com.DPrintf("Configstrings() from " + client.name + "\n")

        if (client.state != ClientStates.CS_CONNECTED) {
            Com.Printf("configstrings not valid -- already spawned\n")
            return
        }

        // handle the case of a level changing while a client was connecting
        val spawnCount = if (args.size >= 2) Lib.atoi(args.get(1)) else 0
        if (spawnCount != gameImports.spawncount) {
            Com.Printf("SV_Configstrings_f from different level\n")
            SV_New_f(args, gameImports, client)
            return
        }

        var start = if (args.size >= 3) Lib.atoi(args.get(2)) else 0

        // write a packet full of data
        var currentReliableSize =
            client.netchan.reliablePending.stream().mapToInt { obj: NetworkMessage? -> obj!!.getSize() }.sum()
        while (currentReliableSize < Defines.MAX_MSGLEN / 2 && start < Defines.MAX_CONFIGSTRINGS) {
            if (gameImports.configstrings[start] != null && gameImports.configstrings[start].length != 0) {
                val config = ConfigStringMessage(start, gameImports.configstrings[start])
                currentReliableSize += config.getSize()
                client.netchan.reliablePending.add(config)
            }
            start++
        }

        // send next command
        val nextCmd: String

        if (start == Defines.MAX_CONFIGSTRINGS) {
            nextCmd = String.format("cmd %s %d 0", StringCmdMessage.BASELINES, gameImports.spawncount)
        } else {
            nextCmd = String.format("cmd %s %d %d", StringCmdMessage.CONFIG_STRINGS, gameImports.spawncount, start)
        }
        client.netchan.reliablePending.add(StuffTextMessage(nextCmd))
    }

    /*
     * ================== SV_Baselines_f ==================
     */
    private fun SV_Baselines_f(args: MutableList<String>, gameImports: GameImportsImpl, client: ClientNetworkInfo) {
        Com.DPrintf("Baselines() from " + client.name + "\n")

        if (client.state != ClientStates.CS_CONNECTED) {
            Com.Printf("baselines not valid -- already spawned\n")
            return
        }

        // handle the case of a level changing while a client was connecting
        val spawnCount = if (args.size >= 2) Lib.atoi(args.get(1)) else 0
        if (spawnCount != gameImports.spawncount) {
            Com.Printf("SV_Baselines_f from different level\n")
            SV_New_f(args, gameImports, client)
            return
        }

        // todo: validate argument
        var start = if (args.size >= 3) Lib.atoi(args.get(2)) else 0

        // write a packet full of data
        var currentReliableSize =
            client.netchan.reliablePending.stream().mapToInt { obj: NetworkMessage? -> obj!!.getSize() }.sum()
        while (currentReliableSize < Defines.MAX_MSGLEN / 2 && start < Defines.MAX_EDICTS) {
            val base = gameImports.baselines[start]
            if (base.modelindex != 0 || base.sound != 0 || base.effects != 0) {
                val spawn = SpawnBaselineMessage(base)
                currentReliableSize += spawn.getSize()
                client.netchan.reliablePending.add(spawn)
            }
            start++
        }

        // send next command
        val nextCmd: String
        if (start == Defines.MAX_EDICTS) {
            // finished sending baselines
            nextCmd = String.format("%s %d", StringCmdMessage.PRECACHE, gameImports.spawncount)
        } else {
            // continue from where we finished
            nextCmd = String.format("cmd %s %d %d", StringCmdMessage.BASELINES, gameImports.spawncount, start)
        }
        client.netchan.reliablePending.add(StuffTextMessage(nextCmd))
    }

    /*
     * ================== SV_Begin_f ==================
     */
    private fun SV_Begin_f(args: MutableList<String>, gameImports: GameImportsImpl, client: ClientNetworkInfo) {
        Com.DPrintf("Begin() from " + client.name + "\n")

        // handle the case of a level changing while a client was connecting
        val spawnCount = if (args.size >= 2) Lib.atoi(args.get(1)) else 0
        if (spawnCount != gameImports.spawncount) {
            Com.Printf("SV_Begin_f from different level\n")
            SV_New_f(args, gameImports, client)
            return
        }

        client.state = ClientStates.CS_SPAWNED

        // call the jake2.game begin function
        gameImports.gameExports.ClientBegin(client.edict)

        Cbuf.InsertFromDefer()
    }

    //=============================================================================
    /*
     * ================== SV_NextDownload_f ==================
     */
    private fun SV_NextDownload_f(
        args: MutableList<String>,
        gameImports: GameImportsImpl?,
        client: ClientNetworkInfo
    ) {
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

    /*
     * ================== SV_BeginDownload_f ==================
     */
    private fun SV_BeginDownload_f(
        args: MutableList<String>,
        gameImports: GameImportsImpl?,
        client: ClientNetworkInfo
    ) {
        var offset = 0

        if (args.size < 2) return

        val name = args.get(1)

        if (args.size > 2) offset = Lib.atoi(args.get(2)) // downloaded offset


        // hacked by zoid to allow more conrol over download
        // first off, no .. or global allow check
        if (name.contains("..")
            || !allow_download.getTypedValue() // leading dot is no good
            || name.get(0) == '.' // leading slash bad as well, must be
            // in subdir
            || name.get(0) == '/' // next up, skin check
            || (name.startsWith("players/") && !allow_download_players.getTypedValue()) // now
            // models
            || (name.startsWith("models/") && !allow_download_models.getTypedValue()) // now
            // sounds
            || (name.startsWith("sound/") && !allow_download_sounds.getTypedValue()) // now maps (note special case for maps, must not be in pak)
            || (name.startsWith("maps/") && !allow_download_maps.getTypedValue()) // MUST
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

        SV_NextDownload_f(args, gameImports, client)
        Com.DPrintf("Downloading " + name + " to " + client.name + "\n")
    }

    //============================================================================
    /*
     * ================= SV_Disconnect_f
     *
     * The client is going to disconnect, so remove the connection immediately
     * =================
     */
    private fun SV_Disconnect_f(args: MutableList<String>, gameImports: GameImportsImpl?, client: ClientNetworkInfo) {
        //	SV_EndRedirect ();
        SV_DropClient(client)
    }

    /*
     * ================== SV_ShowServerinfo_f
     *
     * Dumps the serverinfo info string ==================
     */
    private fun SV_ShowServerinfo_f(
        args: MutableList<String>,
        gameImports: GameImportsImpl?,
        client: ClientNetworkInfo?
    ) {
        Info.Print(Cvar.getInstance().Serverinfo())
    }

    private fun SV_Nextserver(gameImports: GameImportsImpl) {
        //ZOID, ss_pic can be nextserver'd in coop mode

        if (gameImports.state == ServerStates.SS_GAME
            || (gameImports.state == ServerStates.SS_PIC && !Cvar.getInstance()
                .find<Boolean>("coop", Boolean::class.java).getTypedValue())
        ) return  // can't nextserver while playing a normal game


        gameImports.spawncount++ // make sure another doesn't sneak in
        val v = Cvar.getInstance().find<String>("nextserver", String::class.java).getTypedValue()
        //if (!v[0])
        if (v.length == 0) Cbuf.AddText("killserver\n")
        else {
            Cbuf.AddText(v)
            Cbuf.AddText("\n")
        }
        Cvar.getInstance().find<String>("nextserver", String::class.java).set("")
    }

    /*
     * ================== SV_Nextserver_f
     *
     * A cinematic has completed or been aborted by a client, so move to the
     * next server, ==================
     */
    private fun SV_Nextserver_f(args: MutableList<String>, gameImports: GameImportsImpl, client: ClientNetworkInfo) {
        val spawnCount = if (args.size >= 2) Lib.atoi(args.get(1)) else 0
        if (spawnCount != gameImports.spawncount) {
            Com.DPrintf("Nextserver() from wrong level, from " + client.name + "\n")
            return  // leftover from last server
        }

        Com.DPrintf("Nextserver() from " + client.name + "\n")
        SV_Nextserver(gameImports)
    }

    /** Resolve a Game at execution time; creating another Game never replaces these callbacks.  */
    private fun saveGame(args: MutableList<String>) {
        var target = "default"
        var command: MutableList<String> = args
        if (args.size > 1 && args.get(1) == "--game") {
            if (args.size < 4) {
                Com.Printf("Usage: " + args.get(0) + " --game <id> <arguments>\n")
                return
            }
            target = args.get(2)
            command = ArrayList<String>()
            command.add(args.get(0))
            command.addAll(args.subList(3, args.size))
        }
        val game = games.get(target)
        if (game == null) {
            Com.Printf("No running game: " + target + "\n")
            return
        }
        game.saveGame(command)
    }

    /*
	==================
	SV_Kick_f

	Kick a user off of the server
	==================
	*/
    private fun SV_Kick_f(args: MutableList<String>) {
        if (args.size != 2) {
            Com.Printf("Usage: kick <userid>\n")
            return
        }
        val client = SV_SetPlayer(args)
        if (client == null) return

        SV_BroadcastPrintf(Defines.PRINT_HIGH, client.name + " was kicked\n", client.gameName)
        // print directly, because the dropped client won't get the
        // SV_BroadcastPrintf message
        client.queuePrint(Defines.PRINT_HIGH, "You were kicked from the game\n")
        SV_DropClient(client)
        client.lastmessage = games.get(client.gameName)!!.realtime // min case there is a funny zombie
    }

    /*
    ================
    SV_Status_f
    ================
    */
    private fun SV_Status_f(args: MutableList<String>) {
        // todo: check
        if (games.isEmpty()) {
            Com.Printf("No server running.\n")
            return
        }
        for (game in games.values) Com.Printf("game " + game.name + " : " + game.mapName + "\n")

        Com.Printf("num score ping name            lastmsg address               qport \n")
        Com.Printf("--- ----- ---- --------------- ------- --------------------- ------\n")

        for (i in clients.indices) {
            val cl = clients.get(i)
            if (ClientStates.CS_FREE == cl.state) continue

            Com.Printf("%3i ", Vargs().add(i))
            Com.Printf("%5i ", Vargs().add(cl.edict.getClient().getPlayerState().stats[Defines.STAT_FRAGS]))

            if (cl.state == ClientStates.CS_CONNECTED) Com.Printf("CNCT ")
            else if (cl.state == ClientStates.CS_ZOMBIE) Com.Printf("ZMBI ")
            else {
                val ping = if (cl.ping < 9999) cl.ping else 9999
                Com.Printf("%4i ", Vargs().add(ping))
            }

            Com.Printf("%s", Vargs().add(cl.name))
            var l = 16 - cl.name.length
            var j: Int
            j = 0
            while (j < l) {
                Com.Printf(" ")
                j++
            }

            Com.Printf("%7i ", Vargs().add(games.get(cl.gameName)!!.realtime - cl.lastmessage))

            val s = cl.netchan.remote_address.toString()
            Com.Printf(s)
            l = 22 - s.length
            j = 0
            while (j < l) {
                Com.Printf(" ")
                j++
            }

            Com.Printf("%5i", Vargs().add(cl.netchan.qport))

            Com.Printf("\n")
        }
        Com.Printf("\n")
    }

    /*
    ==================
    SV_ConSay_f
    ==================
    */
    private fun SV_ConSay_f(args: MutableList<String>) {
        if (args.size < 2) return

        // char[1024];
        var text = "console: "
        var p = Cmd.getArguments(args)

        if (!p.isEmpty() && p.get(0) == '"') {
            p = p.substring(1, p.length - 1)
        }

        text += p

        for (client in clients) {
            if (client.state != ClientStates.CS_SPAWNED) continue
            client.queuePrint(Defines.PRINT_CHAT, text + "\n")
        }
    }

    /*
    ==================
    SV_Heartbeat_f
    ==================
    */
    private fun SV_Heartbeat_f(agrs: MutableList<String>) {
    }

    /*
    ===========
    SV_Serverinfo_f

      Examine or change the serverinfo string
    ===========
    */
    private fun SV_Serverinfo_f(args: MutableList<String>) {
        Com.Printf("Server info settings:\n")
        Info.Print(Cvar.getInstance().Serverinfo())
    }

    /*
    ===========
    SV_DumpUser_f

    Examine all a users info strings
    ===========
    */
    private fun SV_DumpUser_f(args: MutableList<String>) {
        if (args.size != 2) {
            Com.Printf("Usage: info <userid>\n")
            return
        }
        val client = SV_SetPlayer(args)
        if (client == null) return

        Com.Printf("userinfo\n")
        Com.Printf("--------\n")
        Info.Print(client.userinfo)
    }

    /**
     * Sets sv_client and sv_player to the player with idnum Cmd.Argv(1)
     */
    private fun SV_SetPlayer(args: MutableList<String>): ClientNetworkInfo? {
        if (args.size < 2) return null

        val idOrName = args.get(1)

        // numeric values are just slot numbers
        // fixme: player name cannot start with a number?
        if (!idOrName.isEmpty() && idOrName.get(0) >= '0' && idOrName.get(0) <= '9') {
            val id = Lib.atoi(idOrName)
            if (id < 0 || id >= clients.size) {
                Com.Printf("Bad client slot: " + id + "\n")
                return null
            }

            val result = clients.get(id)
            if (ClientStates.CS_FREE == result.state) {
                Com.Printf("Client " + id + " is not active\n")
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

        Com.Printf("Userid " + idOrName + " is not on the server\n")
        return null
    }

    companion object {
        // Recreating an embedded manager must reuse the same choice declaration.
        private val NEEDPASS_TYPE = ChoiceType<Int>(mutableListOf<Int>(0, 1, 2, 3))

        private const val MAX_STRINGCMDS = 8

        /**
         * Checks if the rcon password is corect.
         */
        private fun Rcon_Validate(args: MutableList<String>): Boolean {
            val rconPassword = Cvar.getInstance().find<String>("rcon_password", String::class.java).getTypedValue()
            if (rconPassword.isEmpty()) return false

            return args.size >= 2 && args.get(1) == rconPassword
        }

        /**
         * A client issued an rcon command. Shift down the remaining args Redirect
         * all printfs from the server to the client.
         */
        private fun SVC_RemoteCommand(args: MutableList<String>, from: netadr_t?) {
            val rconIsValid: Boolean = Rcon_Validate(args)

            if (rconIsValid) {
                Com.Printf("Rcon from " + from + ":\n" + args + "\n")
            } else {
                Com.Printf("Bad rcon from " + from + ":\n" + args + "\n")
            }


            Com.BeginRedirect(RD_Flusher { buffer: StringBuilder? ->
                Netchan.sendConnectionlessPacket(
                    Defines.NS_SERVER,
                    from,
                    ConnectionlessCommand.print,
                    "\n" + buffer.toString()
                )
            })
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
