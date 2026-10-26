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
// $Id: SV_MAIN.java,v 1.16 2006-01-20 22:44:07 salomo Exp $
package jake2.server;

import jake2.qcommon.*;
import jake2.qcommon.exec.Cbuf;
import jake2.qcommon.exec.Cmd;
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.exec.CvarTypes;
import jake2.qcommon.exec.cvar_t;
import jake2.qcommon.vfs.EngineVfs;
import jake2.qcommon.vfs.EngineWriteRoot;
import jake2.qcommon.network.NET;
import jake2.qcommon.network.NetAddrType;
import jake2.qcommon.network.Netchan;
import jake2.qcommon.network.messages.ConnectionlessCommand;
import jake2.qcommon.network.messages.NetworkMessage;
import jake2.qcommon.network.messages.NetworkPacket;
import jake2.qcommon.network.messages.client.*;
import jake2.qcommon.network.messages.server.*;
import jake2.qcommon.network.netadr_t;
import jake2.qcommon.util.Lib;
import jake2.server.save.ServerSaveJsonStore;
import jake2.server.save.ServerSaveFiles;

import java.util.*;
import java.util.stream.Collectors;

import static jake2.qcommon.Defines.*;

@SuppressWarnings("deprecation")
public class SV_MAIN implements AutoCloseable {

    // Recreating an embedded manager must reuse the same choice declaration.
    private static final CvarTypes.ChoiceType<Integer> NEEDPASS_TYPE =
            new CvarTypes.ChoiceType<>(List.of(0, 1, 2, 3));

    private final ServerCommands commands = new ServerCommands();
    private final ServerCommands gameCommands = new ServerCommands();

    public void registerGameCommand(String name, jake2.qcommon.exec.Command command) {
        gameCommands.add(name, true, command);
    }

    public boolean isRunning() {
        return !games.isEmpty();
    }

    /** Typed entrypoint for an embedded host; uses the same map path as the console. */
    public boolean startMap(String mapName) {
        GameImportsImpl previous = games.get("default");
        SV_Map_f(List.of("map", mapName));
        return games.get("default") != null && games.get("default") != previous;
    }

    /** Release this manager's console hooks after ending its last session. */
    @Override
    public void close() {
        try {
            SV_Shutdown("Server stopped\n", false);
        } finally {
            commands.close();
        }
    }

    public List<ClientNetworkInfo> getClients() {
        return clients;
    }

    private static final int MAX_STRINGCMDS = 8;

    final Map<String, GameImportsImpl> games = new HashMap<>();

    public List<ClientNetworkInfo> getClientsForInstance(final String gameName) {
        if (!games.containsKey(gameName)) {
            // happens during the game instance creation, while the instance has random name
            Com.Printf("Game " + gameName + " is not found!\n");
        }
        // nullify unrelated clients
        return clients.stream().map(c -> c.gameName != null && c.gameName.equals(gameName) ? c : null).collect(Collectors.toList());
    }


    // prevent invalid IPs from connecting
    private final challenge_t[] challenges = new challenge_t[Defines.MAX_CHALLENGES]; // to

    private List<ClientNetworkInfo> clients = new ArrayList<>();

    private long realtime = 0;
    private int frameNum = 0;
    private int fixedtime = 1000;
    private int gameCounter;

    String allocateGameName() {
        return "jake2_" + gameCounter++;
    }

    boolean isPaused() {
        return sv_paused.getTypedValue();
    }

    final cvar_t<Boolean> sv_paused;

    final cvar_t<Boolean> sv_timedemo;

    final cvar_t<Boolean> sv_enforcetime;

    final cvar_t<Float> timeout; // seconds without any message

    final cvar_t<Float> zombietime; // seconds to sink messages after
                                     // disconnect

    final cvar_t<Boolean> allow_download;

    final cvar_t<Boolean> allow_download_players;

    final cvar_t<Boolean> allow_download_models;

    final cvar_t<Boolean> allow_download_sounds;

    final cvar_t<Boolean> allow_download_maps;

    final cvar_t<Float> sv_airaccelerate;

    final cvar_t<Boolean> sv_showclamp;

    final cvar_t<String> hostname;

    final cvar_t<Boolean> public_server; // should heartbeats be sent

    final cvar_t<Float> sv_reconnect_limit; // minimum seconds between connect
                                             // messages


    /**
     * Builds the string that is sent as heartbeats and status replies.
     */
    private String SV_StatusString() {

        String status = Cvar.getInstance().Serverinfo() + "\n";

        for (ClientNetworkInfo cl: clients) {
            if (cl.state == ClientStates.CS_CONNECTED || cl.state == ClientStates.CS_SPAWNED) {
                String player = "" + cl.edict.getClient().getPlayerState().stats[Defines.STAT_FRAGS] + " " + cl.ping + "\"" + cl.name + "\"\n";

                int playerLength = player.length();
                int statusLength = status.length();

                if (statusLength + playerLength >= 1024)
                    break; // can't hold any more

                status += player;
            }
        }

        return status;
    }

    /**
     * SVC_Info, responds with short info for broadcast scans The second parameter should
     * be the current protocol version number.
     */
    private String SVC_Info(List<String> args, netadr_t from) {

        if (clients.size() == 1)
            return null; // ignore in single player

        int version = args.size() < 2 ? 0 : Lib.atoi(args.get(1));

        String info;
        if (version != Defines.PROTOCOL_VERSION)
            info = hostname.getTypedValue() + ": wrong version\n";
        else {
            int players = 0;
            for (ClientNetworkInfo cl: clients)
                if (cl.state == ClientStates.CS_CONNECTED || cl.state == ClientStates.CS_SPAWNED)
                    players++;

            info = hostname.getTypedValue() + " " + games.get("default").mapName + " " + players + "/" + clients.size() + "\n";
        }

        return info;
    }

    /**
     * Returns a challenge number that can be used in a subsequent
     * client_connect command. We do this to prevent denial of service attacks
     * that flood the server with invalid connection IPs. With a challenge, they
     * must give a valid IP address.
     */
    private int SVC_GetChallenge(netadr_t from) {

        int oldest = 0;
        int oldestTime = 0x7fffffff;

        int i;
        for (i = 0; i < Defines.MAX_CHALLENGES; i++) {
            if (from.CompareBaseAdr(challenges[i].adr))
                break;
            if (challenges[i].time < oldestTime) {
                oldestTime = challenges[i].time;
                oldest = i;
            }
        }

        if (i == Defines.MAX_CHALLENGES) {
            // overwrite the oldest
            challenges[oldest].challenge = Lib.rand() & 0x7fff;
            challenges[oldest].adr = from;
            challenges[oldest].time = (int) Globals.curtime;
            i = oldest;
        }

        // send it back
        return challenges[i].challenge;
    }

    /**
     * A connection request that did not come from the master.
     */
    private void SVC_DirectConnect(List<String> args, netadr_t adr) {

        Com.DPrintf("SVC_DirectConnect ()\n");

        int version = args.size() >= 2 ? Lib.atoi(args.get(1)) : 0;

        if (version != Defines.PROTOCOL_VERSION) {
            Netchan.sendConnectionlessPacket(Defines.NS_SERVER, adr, ConnectionlessCommand.print,
                    "\nServer is version " + Globals.VERSION + "\n");
            Com.DPrintf("    rejected connect from version " + version + "\n");
            return;
        }

        int qport = args.size() >= 3 ? Lib.atoi(args.get(2)) : 0;
        int challenge = args.size() >= 4 ? Lib.atoi(args.get(3)) : 0;
        String userinfo = args.size() >= 5 ? args.get(4) : "";

        // force the IP key/value pair so the game can filter based on ip
        userinfo = Info.Info_SetValueForKey(userinfo, "ip", adr.toString());


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
        int j;
        if (!adr.IsLocalAddress()) {
            for (j = 0; j < Defines.MAX_CHALLENGES; j++) {
                if (adr.CompareBaseAdr(challenges[j].adr)) {
                    if (challenge == challenges[j].challenge)
                        break; // good
                    Netchan.sendConnectionlessPacket(Defines.NS_SERVER, adr, ConnectionlessCommand.print, "\nBad challenge.\n");
                    return;
                }
            }
            if (j == Defines.MAX_CHALLENGES) {
                Netchan.sendConnectionlessPacket(Defines.NS_SERVER, adr, ConnectionlessCommand.print, "\nNo challenge for address.\n");
                return;
            }
        }

        // if there is already a slot for this ip, reuse it
        int i = 0;
        for (ClientNetworkInfo cl: clients) {
            if (cl.state == ClientStates.CS_FREE)
                continue;
            if (adr.CompareBaseAdr(cl.netchan.remote_address)
                    && (cl.netchan.qport == qport || adr.port == cl.netchan.remote_address.port)) {
                if (!adr.IsLocalAddress() && (realtime - cl.lastconnect) < (sv_reconnect_limit.getTypedValue() * 1000)) {
                    Com.DPrintf(adr.toString() + ":reconnect rejected : too soon\n");
                    return;
                }
                Com.Printf(adr + ":reconnect\n");

                gotnewcl(i, challenge, userinfo, adr, qport);
                return;
            }
            i++;
        }

        // find a client slot
        int index = -1;
        i = 0;
        for (ClientNetworkInfo cl: clients) {
            if (cl.state == ClientStates.CS_FREE) {
                index = i;
                break;
            }
            i++;
        }
        if (index == -1) {
            Netchan.sendConnectionlessPacket(Defines.NS_SERVER, adr, ConnectionlessCommand.print, "\nServer is full.\n");
            Com.DPrintf("Rejected a connection.\n");
            return;
        }
        gotnewcl(index, challenge, userinfo, adr, qport);
    }

    /**
     * Initializes player structures after successfull connection.
     */
    private void gotnewcl(int i, int challenge, String userinfo, netadr_t adr, int qport) {
        // build a new connection
        // accept the new client
        // this is the only place a ClientNetworkInfo is ever initialized

        final ClientNetworkInfo client = clients.get(i);
        GameImportsImpl gameImports = games.get("default");
        client.gameName = "default";

        ServerEntity ent = gameImports.gameExports.getEdict(i + 1);
        client.edict = ent; // fixme: why? isn't it already set?

        // save challenge for checksumming
        client.challenge = challenge;

        // get the game a chance to reject this connection or modify the
        // userinfo
        if (!(gameImports.gameExports.ClientConnect(ent, userinfo))) {
            if (Info.Info_ValueForKey(userinfo, "rejmsg") != null) {
                Netchan.sendConnectionlessPacket(Defines.NS_SERVER, adr, ConnectionlessCommand.print, "\n" + Info.Info_ValueForKey(userinfo, "rejmsg") + "\nConnection refused.\n");
            } else {
                Netchan.sendConnectionlessPacket(Defines.NS_SERVER, adr, ConnectionlessCommand.print, "\nConnection refused.\n");
            }
            Com.DPrintf("Game rejected a connection.\n");
            return;
        }

        // parse some info from the info strings
        client.userinfo = userinfo;
        SV_UserinfoChanged(client);

        Netchan.sendConnectionlessPacket(Defines.NS_SERVER, adr, ConnectionlessCommand.client_connect, "");

        client.netchan.setup(Defines.NS_SERVER, adr, qport);

        client.state = ClientStates.CS_CONNECTED;

        client.lastmessage = gameImports.realtime; // don't timeout
        client.lastconnect = gameImports.realtime;
        Com.DPrintf("new client added.\n");
    }

    /** 
     * Checks if the rcon password is corect.
     */
    private static boolean Rcon_Validate(List<String> args) {
        String rconPassword = Cvar.getInstance().find("rcon_password", String.class).getTypedValue();
        if (rconPassword.isEmpty())
            return false;

        return args.size() >= 2 && args.get(1).equals(rconPassword);
    }

    /**
     * A client issued an rcon command. Shift down the remaining args Redirect
     * all printfs from the server to the client.
     */
    private static void SVC_RemoteCommand(List<String> args, final netadr_t from) {

        boolean rconIsValid = Rcon_Validate(args);

        if (rconIsValid) {
            Com.Printf("Rcon from " + from + ":\n" + args + "\n");
        } else {
            Com.Printf("Bad rcon from " + from + ":\n" + args + "\n");
        }


        Com.BeginRedirect((buffer) -> Netchan.sendConnectionlessPacket(NS_SERVER, from, ConnectionlessCommand.print, "\n" + buffer.toString()));
        if (rconIsValid) {
            Cmd.ExecuteString(Cmd.getArguments(args, 2));
        } else {
            // redirected to client
            Com.Printf("Bad rcon_password.\n");
        }

        Com.EndRedirect();
    }

    /**
     * A connectionless packet has four leading 0xff characters to distinguish
     * it from a game channel. Clients that are in the game can still send
     * connectionless packets. It is used also by rcon commands.
     */
    void SV_ConnectionlessPacket(netadr_t from, String messageLine) {

        List<String> args = Cmd.TokenizeString(messageLine, false);

        if (args.isEmpty()) {
            Com.Printf("empty connectionless packet: " + messageLine);
            return;
        }

        String c = args.get(0);
        ConnectionlessCommand cmd = ConnectionlessCommand.fromString(c);

        switch (cmd) {
            case ping:
                // SVC_Ping, Just responds with an acknowledgement.
                Netchan.sendConnectionlessPacket(Defines.NS_SERVER, from, ConnectionlessCommand.ack, "");
                break;
            case ack:
                //SVC_Ack
                Com.Printf("Ping acknowledge from " + from + "\n");
                break;
            case status:
                // SVC_Status, Responds with all the info that qplug or qspy can see
                Netchan.sendConnectionlessPacket(Defines.NS_SERVER, from, ConnectionlessCommand.print, "\n" + SV_StatusString());
                break;
            case info:
                String info = SVC_Info(args, from);
                if (info != null)
                    Netchan.sendConnectionlessPacket(Defines.NS_SERVER, from, ConnectionlessCommand.info, "\n" + info);
                break;
            case getchallenge:
                int challenge = SVC_GetChallenge(from);
                Netchan.sendConnectionlessPacket(Defines.NS_SERVER, from, ConnectionlessCommand.challenge, " " + challenge);
                break;
            case connect:
                SVC_DirectConnect(args, from);
                break;
            case rcon:
                SVC_RemoteCommand(args, from);
                break;
            default:
                Com.Printf("Unknown ClientConnectionlessCommand: " + c + '\n');
                break;
        }
    }

    /**
     * SV_Frame.
     */
    public void update(long msec) {
        // if server is not active, do nothing
        // like when connected to another server
        if (games.isEmpty())
            return;

        realtime += msec;
        for (GameImportsImpl game: games.values()) {
            game.realtime += msec;
        }

        // keep the random time dependent
        Lib.rand();

        // check timeouts
        SV_CheckTimeouts();

        // get packets from clients
        SV_ReadPackets();

        // identify if we need to update the game or it's too early (sleep or skip this update).
        // fixedtime - the time of the next scheduled game update.
        long delay = fixedtime - realtime;
        if (delay > 0) {
            // never let the time get too far off
            if (delay > 100) { // how is it even possible?
                for (GameImportsImpl game: games.values())
                    game.realtime = (int) (fixedtime - 100);
                delay = 100;
            }
            NET.Sleep((int) delay);
            return;
        }
        // move autonomous things around if enough time has passed

        frameNum++;
        fixedtime = 100 * frameNum;

        // update ping based on the last known frame from all clients
        SV_CalcPings();

        // give the clients some timeslices
        SV_GiveMsec();

        // let everything in the world think and move
        for (GameImportsImpl game : games.values()) {
            game.SV_RunGameFrame(fixedtime);
        }

        // send messages back to the clients that had packets read this frame
        SV_SendClientMessages();


        // send a heartbeat to the master if needed
        //Master_Heartbeat();

        // clear teleport flags, etc for next frame
        for (GameImportsImpl game : games.values()) {
            game.clearEntityStateEvents();
        }
    }

    void SV_SendClientMessages() {

        // send a message to each connected client
        for (ClientNetworkInfo c : clients) {

            if (c.state == ClientStates.CS_FREE)
                continue;

            GameImportsImpl game = games.get(c.gameName);
            // if the reliable message overflowed,
            // drop the client
            int pendingReliableSize = c.netchan.reliablePending.stream().mapToInt(NetworkMessage::getSize).sum();
            if (pendingReliableSize > Defines.MAX_MSGLEN - 16) {
                c.netchan.reliablePending.clear();
                c.unreliable.clear();
                SV_BroadcastPrintf(Defines.PRINT_HIGH, c.name + " overflowed\n", c.gameName);
                SV_DropClient(c);
            }

            if (game.state == ServerStates.SS_CINEMATIC || game.state == ServerStates.SS_DEMO || game.state == ServerStates.SS_PIC) {
                // leftover from demo code
                c.netchan.transmit(null);
            } else if (c.state == ClientStates.CS_SPAWNED) {
                // don't overrun bandwidth
                if (SV_RateDrop(c))
                    continue;

                sendClientDatagram(c, game);
            } else {
                // just update reliable	if needed
                if (c.netchan.reliablePending.size() != 0 || Globals.curtime - c.netchan.last_sent > 1000)
                    c.netchan.transmit(null);
            }
        }
    }

    /**
     * Called when the player is totally leaving the server, either willingly or
     * unwillingly. This is NOT called if the entire server is quiting or
     * crashing.
     */
    void SV_DropClient(ClientNetworkInfo client) {
        // add the disconnect
        client.netchan.reliablePending.add(new DisconnectMessage());

        if (client.state == ClientStates.CS_SPAWNED) {
            // call the prog function for removing a client
            // this will remove the body, among other things
            games.get(client.gameName).gameExports.ClientDisconnect(client.edict);
        }

        if (client.download != null) {
            client.download = null;
        }

        client.state = ClientStates.CS_ZOMBIE; // become free in a few seconds
        client.name = "";
    }

    /**
     * Sends text to all active clients
     */
    public void SV_BroadcastPrintf(int level, String message, String gameName) {

        // echo to console
        if (Globals.dedicated.getTypedValue()) {
            Com.Printf(message);
        }

        for (ClientNetworkInfo cl: getClientsForInstance(gameName)) {
            if (cl == null || level < cl.messagelevel)
                continue;
            if (cl.state != ClientStates.CS_SPAWNED)
                continue;
            cl.netchan.reliablePending.add(new PrintMessage(level, message));
        }
    }

    /**
     * SV_RateDrop
     * <p>
     * Returns true if the client is over its current
     * bandwidth estimation and should not be sent another packet
     */
    boolean SV_RateDrop(ClientNetworkInfo c) {

        // never drop over the loopback
        if (c.netchan.remote_address.type == NetAddrType.NA_LOOPBACK)
            return false;

        int total = 0;

        for (int i = 0; i < Defines.RATE_MESSAGES; i++) {
            total += c.message_size[i];
        }

        if (total > c.rate) {
            c.surpressCount++;
            c.message_size[frameNum % Defines.RATE_MESSAGES] = 0;
            return true;
        }

        return false;
    }

    /**
     * Reads packets from the network or loopback.
     */
    void SV_ReadPackets() {
        while (true) {
            NetworkPacket networkPacket = NET.receiveNetworkPacket(
                    NET.ip_sockets[NS_SERVER],
                    NET.ip_channels[NS_SERVER],
                    NET.loopbacks[NS_SERVER],
                    true);

            if (networkPacket == null)
                break;

            if (networkPacket.isConnectionless()) {
                SV_ConnectionlessPacket(networkPacket.from, networkPacket.connectionlessMessage);
                continue;
            }

            // Match incoming packet with the existing client.
            // todo: get client by address (hashmap?)
            for (ClientNetworkInfo cl: clients) {
                if (cl.state == ClientStates.CS_FREE)
                    continue;
                if (!networkPacket.from.CompareBaseAdr(cl.netchan.remote_address))
                    continue;
                if (cl.netchan.qport != networkPacket.qport)
                    continue;
                if (cl.netchan.remote_address.port != networkPacket.from.port) {
                    Com.Printf("SV_ReadPackets: fixing up a translated port\n");
                    cl.netchan.remote_address.port = networkPacket.from.port;
                }

                if (cl.netchan.accept(networkPacket)) {
                    // this is a valid, sequenced packet, so process it
                    if (cl.state != ClientStates.CS_ZOMBIE) {
                        cl.lastmessage = games.get(cl.gameName).realtime; // don't timeout
                        Collection<ClientMessage> body = networkPacket.parseBodyFromClient(cl.netchan.incoming_sequence);
                        if (body == null)
                            SV_DropClient(cl);
                        else
                            SV_ExecuteClientMessage(cl, body);
                    }
                }
                break;
            }

        }
    }

    /*
     * =================== SV_ExecuteClientMessage
     *
     * The current net_message is parsed for the given client
     * ===================
     */
    void SV_ExecuteClientMessage(ClientNetworkInfo cl, Collection<ClientMessage> clientMessages) {

        // only allow one move command
        boolean move_issued = false;
        int stringCmdCount = 0;


        for (ClientMessage msg : clientMessages) {
            if (msg instanceof EndOfClientPacketMessage) {
                break;
            } else if (msg instanceof StringCmdMessage) {
                StringCmdMessage m = (StringCmdMessage) msg;
                // malicious users may try using too many string commands
                if (++stringCmdCount < MAX_STRINGCMDS) {
                    SV_ExecuteUserCommand(cl, m.command);
                }

                if (cl.state == ClientStates.CS_ZOMBIE) {
                    return; // disconnect command
                }
            } else if (msg instanceof UserInfoMessage) {
                UserInfoMessage m = (UserInfoMessage) msg;
                cl.userinfo = m.userInfo;
                SV_UserinfoChanged(cl);
            } else if (msg instanceof MoveMessage) {
                MoveMessage m = (MoveMessage) msg;
                if (move_issued)
                    return; // someone is trying to cheat...

                move_issued = true;
                int lastReceivedFrame = m.lastReceivedFrame;

                if (lastReceivedFrame != cl.lastReceivedFrame) {
                    cl.lastReceivedFrame = lastReceivedFrame;
                    if (cl.lastReceivedFrame > 0) {
                        final int framaLatency = games.get(cl.gameName).realtime - cl.frames[cl.lastReceivedFrame & UPDATE_MASK].senttime;
                        cl.frame_latency[cl.lastReceivedFrame & (Defines.LATENCY_COUNTS - 1)] = framaLatency;
                    }
                }

                if (cl.state != ClientStates.CS_SPAWNED) {
                    cl.lastReceivedFrame = -1;
                    continue;
                }

                // if the checksum fails, ignore the rest of the packet
                if (!m.valid) {
                    Com.Printf("Invalid crc\n");
                    return;
                }


                if (!sv_paused.getTypedValue()) {
                    int net_drop = cl.netchan.dropped;
                    if (net_drop < 20) {

                        //if (net_drop > 2)

                        //	Com.Printf ("drop %i\n", net_drop);
                        while (net_drop > 2) {
                            SV_ClientThink(cl, cl.lastcmd);

                            net_drop--;
                        }
                        if (net_drop > 1)
                            SV_ClientThink(cl, m.oldestCmd);

                        if (net_drop > 0)
                            SV_ClientThink(cl, m.oldCmd);

                    }
                    SV_ClientThink(cl, m.newCmd);
                }

                // copy.
                cl.lastcmd.set(m.newCmd);
            } else {
                Com.Printf("SV_ReadClientMessage: unknown command: " + msg.type + "\n");
                SV_DropClient(cl);
            }
        }
    }

    void SV_ClientThink(ClientNetworkInfo cl, usercmd_t cmd) {
        cl.commandMsec -= cmd.msec & 0xFF;

        if (cl.commandMsec < 0 && sv_enforcetime.getTypedValue()) {
            Com.DPrintf("commandMsec underflow from " + cl.name + "\n");
            return;
        }

        games.get(cl.gameName).gameExports.ClientThink(cl.edict, cmd);
    }

    /**
     * Pull specific info from a newly changed userinfo string into a more C
     * freindly form.
     */
    void SV_UserinfoChanged(ClientNetworkInfo cl) {
        // call prog code to allow overrides
        games.get(cl.gameName).gameExports.ClientUserinfoChanged(cl.edict, cl.userinfo);

        // name for C code
        cl.name = Info.Info_ValueForKey(cl.userinfo, "name");

        // mask off high bit
        //TODO: masking for german umlaute
        //for (i=0 ; i<sizeof(cl.name) ; i++)
        //	cl.name[i] &= 127;

        // rate command
        String val = Info.Info_ValueForKey(cl.userinfo, "rate");
        if (val.length() > 0) {
            cl.rate = Lib.atoi(val);
            if (cl.rate < 100)
                cl.rate = 100;
            if (cl.rate > 15000)
                cl.rate = 15000;
        } else
            cl.rate = 5000;

        // msg command
        val = Info.Info_ValueForKey(cl.userinfo, "msg");
        if (val.length() > 0) {
            cl.messagelevel = Lib.atoi(val);
        }

    }

    void SV_ExecuteUserCommand(ClientNetworkInfo cl, String command) {

        Com.dprintln("SV_ExecuteUserCommand:" + command );

        List<String> args = Cmd.TokenizeString(command, true);
        if (args.isEmpty())
            return;

        final GameImportsImpl game = games.get(cl.gameName);
        if (userCommands.containsKey(args.get(0))) {
            userCommands.get(args.get(0)).execute(args, game, cl);
            return;
        }

        if (game.state == ServerStates.SS_GAME)
            game.gameExports.ClientCommand(cl.edict, args);
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
    void SV_CheckTimeouts() {

        for (ClientNetworkInfo cl : clients) {
            if (cl.state == ClientStates.CS_FREE)
                continue;

            final GameImportsImpl game = games.get(cl.gameName);
            int droppoint = (int) (game.realtime - 1000 * timeout.getTypedValue());
            int zombiepoint = (int) (game.realtime - 1000 * zombietime.getTypedValue());

            // message times may be wrong across a changelevel
            if (cl.lastmessage > game.realtime)
                cl.lastmessage = game.realtime;

            if (cl.state == ClientStates.CS_ZOMBIE && cl.lastmessage < zombiepoint) {
                cl.state = ClientStates.CS_FREE; // can now be reused
                continue;
            }
            if ((cl.state == ClientStates.CS_CONNECTED || cl.state == ClientStates.CS_SPAWNED) && cl.lastmessage < droppoint) {
                SV_BroadcastPrintf(Defines.PRINT_HIGH, cl.name + " timed out\n", cl.gameName);
                SV_DropClient(cl);
                cl.state = ClientStates.CS_FREE; // don't bother with zombie state
            }
        }
    }

    /**
     * Updates the cl.ping variables.
     */
    void SV_CalcPings() {

        for (ClientNetworkInfo cl: clients) {
            if (cl.state != ClientStates.CS_SPAWNED)
                continue;

            int total = 0;
            int count = 0;
            for (int j = 0; j < Defines.LATENCY_COUNTS; j++) {
                if (cl.frame_latency[j] > 0) {
                    count++;
                    total += cl.frame_latency[j];
                }
            }
            if (0 == count)
                cl.ping = 0;
            else
                cl.ping = total / count;

            // let the jake2.game dll know about the ping
            cl.edict.getClient().setPing(cl.ping);
        }
    }

    /**
     * Every few frames, gives all clients an allotment of milliseconds for
     * their command moves. If they exceed it, assume cheating.
     */
    void SV_GiveMsec() {
        // why *1111
        if ((frameNum & 15) != 0)
            return;

        for (ClientNetworkInfo cl: clients) {
            if (cl.state == ClientStates.CS_FREE)
                continue;

            if ((games.get(cl.gameName).frameNum & 15) != 0)
                continue;

            cl.commandMsec = 1800; // 1600 + some slop
        }
    }

    /**
     * Used by SV_Shutdown to send a final message to all connected clients
     * before the server goes down. The messages are sent immediately, not just
     * stuck on the outgoing message list, because the server is going to
     * totally exit after returning from this function.
     */
    private void SV_FinalMessage(String message, boolean reconnect) {

        Collection<NetworkMessage> msgs = new ArrayList<>();
        msgs.add(new PrintMessage(Defines.PRINT_HIGH, message));

        if (reconnect)
            msgs.add(new ReconnectMessage());
        else
            msgs.add(new DisconnectMessage());

        // send it twice
        // stagger the packets to crutch operating system limited buffers
        for (ClientNetworkInfo cl : clients) {
            if (cl.state == ClientStates.CS_CONNECTED || cl.state == ClientStates.CS_SPAWNED)
                cl.netchan.transmit(msgs);
        }
        for (ClientNetworkInfo cl : clients) {
            if (cl.state == ClientStates.CS_CONNECTED || cl.state == ClientStates.CS_SPAWNED)
                cl.netchan.transmit(msgs);
        }
    }

    /**
     * Called when each game quits, before Sys_Quit or Sys_Error.
     * todo: shutdown a particular instance, not the whole server
     */
    public void SV_Shutdown(String finalmsg, boolean reconnect) {
        try {
            if (!games.isEmpty()) {
                if (!clients.isEmpty()) SV_FinalMessage(finalmsg, reconnect);
                Com.Printf("==== ShutdownGame ====\n");
            }
        } finally {
            // Also clean commands installed by a partially constructed game.
            gameCommands.close();
            games.clear();
            clients.clear();
            Globals.server_state = ServerStates.SS_DEAD;
            realtime = 0;
            frameNum = 0;
            fixedtime = 100;
        }
    }

    /**
     * Resets the clients when maxclients changes
     */
    void resetClients() {
        SV_FinalMessage("Server restarted", true);
        final int max = Cvar.getInstance().find("maxclients", Integer.class).getTypedValue();

        // Clear all clients
        clients = new ArrayList<>(max);
        for (int i = 0; i < max; i++) {
            ClientNetworkInfo cl = new ClientNetworkInfo();
            cl.lastcmd = new usercmd_t();
            clients.add(cl);
        }
    }

    /**
     * One manager per dedicated process or embedded session; map changes reuse it.
     */
    public SV_MAIN() {

        for (int n = 0; n < Defines.MAX_CHALLENGES; n++) {
            challenges[n] = new challenge_t();
        }

        Cvar cvars = Cvar.getInstance();
        cvars.register("spectator_password", CvarTypes.STRING, "", Defines.CVAR_USERINFO,
                "Spectator admission password; empty or none allows access");
        cvars.register("needpass", NEEDPASS_TYPE, 0, Defines.CVAR_SERVERINFO,
                "Required passwords: 0 none, 1 player, 2 spectator, 3 both");
        cvars.register("deathmatch", CvarTypes.BOOLEAN, false, Defines.CVAR_SERVERINFO | Defines.CVAR_LATCH);
        cvars.register("coop", CvarTypes.BOOLEAN, false, Defines.CVAR_SERVERINFO | Defines.CVAR_LATCH);
        cvars.register("dmflags", CvarTypes.INTEGER, Defines.DF_INSTANT_ITEMS, Defines.CVAR_SERVERINFO, "Deathmatch rule bitmask; combine DF_* flags");
        cvars.register("fraglimit", new CvarTypes.IntegerType(0, null), 0, Defines.CVAR_SERVERINFO, "End a deathmatch level at this score; 0 disables the limit");
        cvars.register("timelimit", new CvarTypes.FloatType(0f, null), 0f, Defines.CVAR_SERVERINFO, "Deathmatch level time limit in minutes; 0 disables the limit");
        cvars.register("cheats", CvarTypes.BOOLEAN, false, Defines.CVAR_SERVERINFO | Defines.CVAR_LATCH);
        cvars.register("protocol", CvarTypes.INTEGER, Defines.PROTOCOL_VERSION, Defines.CVAR_SERVERINFO | Defines.CVAR_NOSET);
        cvars.register("mapname", CvarTypes.STRING, "", Defines.CVAR_SERVERINFO | Defines.CVAR_NOSET);
        cvars.register("nextserver", CvarTypes.STRING, "", 0, "Console command to execute after a cinematic or picture");
        cvars.register("maxclients", new CvarTypes.IntegerType(1, Defines.MAX_CLIENTS), 1, Defines.CVAR_SERVERINFO | Defines.CVAR_LATCH, "Client slot limit; single-player and coop mode also restrict the count");

        hostname = cvars.register("hostname", CvarTypes.STRING, "noname", Defines.CVAR_SERVERINFO | Defines.CVAR_ARCHIVE);
        timeout = cvars.register("timeout", new CvarTypes.FloatType(0f, null), 125f, 0, "Seconds without a client message before disconnection");
        zombietime = cvars.register("zombietime", new CvarTypes.FloatType(0f, null), 2f, 0, "Seconds to retain a disconnected client slot for final reliable messages");
        sv_showclamp = cvars.register("showclamp", CvarTypes.BOOLEAN, false, 0, "Legacy frame-time clamp diagnostics; unused by the current server and Cake");
        sv_paused = cvars.register("paused", CvarTypes.BOOLEAN, false, 0);
        sv_timedemo = cvars.register("timedemo", CvarTypes.BOOLEAN, false, 0, "Legacy demo benchmarking switch; unused by the current server and Cake");
        sv_enforcetime = cvars.register("sv_enforcetime", CvarTypes.BOOLEAN, false, 0, "Ignore movement commands that exceed the client time budget");

        // come from MainCommon
        allow_download = cvars.find("allow_download", Boolean.class);
        allow_download_players = cvars.find("allow_download_players", Boolean.class);
        allow_download_models = cvars.find("allow_download_models", Boolean.class);
        allow_download_sounds = cvars.find("allow_download_sounds", Boolean.class);
        allow_download_maps = cvars.find("allow_download_maps", Boolean.class);

        cvars.register("sv_noreload", CvarTypes.BOOLEAN, false, 0, "Start revisited levels fresh instead of restoring their saved state");
        sv_airaccelerate = cvars.register("sv_airaccelerate", CvarTypes.FLOAT, 0f, Defines.CVAR_LATCH, "Controls air acceleration (currently only for deathmatch)");
        public_server = cvars.register("public", CvarTypes.BOOLEAN, false, 0, "Legacy master-server advertising switch; currently unused");
        sv_reconnect_limit = cvars.register("sv_reconnect_limit", new CvarTypes.FloatType(0f, null), 3f, Defines.CVAR_ARCHIVE, "Minimum seconds between reconnect attempts from the same remote client");
        resetClients();

        // Publish callbacks only after initialization succeeds. Game operator commands
        // are registered separately when a map starts.
        commands.add("killserver", args -> {
            SV_Shutdown("Server was killed.\n", false);
            NET.configureServer(false);
        });
        commands.add("map", this::SV_Map_f);
        commands.add("gamemap", this::SV_GameMap_f);
        commands.add("load", this::SV_Loadgame_f);
        commands.add("sv_shutdown", args -> {
            String reason;
            if (args.size() > 1) {
                reason = args.get(1);
            } else {
                reason = "Server is shut down";
            }

            SV_Shutdown(reason + "\n", args.size() > 2 && Boolean.parseBoolean(args.get(2)));
        });

        commands.add("listmaps", (List<String> args) -> {
            byte[] bytes = EngineVfs.loadBytes("maps.lst");
            if (bytes == null) {
                Com.Error(ERR_DROP, "Could not read maps.lst");
                return;
            }
            for (String line : new String(bytes).split("\n")) {
                Com.Printf(PRINT_ALL, line.trim() + "\n");
            }
        });
        commands.add("jvm_memory", args -> {
            Runtime runtime = Runtime.getRuntime();
            Com.Printf("vm memory:" + (runtime.totalMemory() - runtime.freeMemory()) + "\n");
        });
        commands.add("games", args -> {
            for (GameImportsImpl game : games.values()) {
                Com.Printf(game.name + " - " + game.mapcmd + "\n");
            }
            Com.Printf("Total " + games.size() + " games\n");
        });
        commands.add("spamgames", args -> {
            int total = Integer.parseInt(args.get(1));
            for (int i = 0; i < total; i++) {
                SV_GameMap_f(List.of("gamemap", "base1", "1"));
            }
        });
    }

    /**
     * SV_InitGame.
     *
     * A brand new game has been started.
     */
    GameImportsImpl createGameInstance(ChangeMapInfo changeMapInfo, GameImportsImpl previous) {
        return GameImportsImpl.create(this, changeMapInfo, previous);
    }

    /**
     * @return if the game is multiplayer or not
     */
    private boolean initializeServerCvars() {
        Cvar cvars = Cvar.getInstance();
        cvar_t<Boolean> coop = cvars.find("coop", Boolean.class);
        cvar_t<Boolean> deathmatch = cvars.find("deathmatch", Boolean.class);
        cvar_t<Integer> maxclients = cvars.find("maxclients", Integer.class);

        // get any latched variable changes (maxclients, etc)
        cvars.updateLatchedVars();

        if (coop.getTypedValue() && deathmatch.getTypedValue()) {
            Com.Printf("Deathmatch and Coop both set, disabling Coop\n");
            coop.forceSet(false);
        }

        // dedicated servers are can't be single player and are usually DM
        // so unless they explicity set coop, force it to deathmatch
        if (Globals.dedicated.getTypedValue()) {
            if (!coop.getTypedValue())
                deathmatch.forceSet(true);
        }


        // set max clients based on game mode

        if (deathmatch.getTypedValue()) {
            if (maxclients.getTypedValue() <= 1)
                maxclients.forceSet(8);
        } else if (coop.getTypedValue()) {
            if (maxclients.getTypedValue() <= 1 || maxclients.getTypedValue() > 4)
                maxclients.forceSet(4);

        } else {
            // non-deathmatch, non-coop is one player
            maxclients.forceSet(1);
        }
        return maxclients.getTypedValue() > 1;
    }

    void SV_Loadgame_f(List<String> args) {

        if (args.size() != 2) {
            Com.Printf("USAGE: load <directory>\n");
            return;
        }

        Com.Printf("Loading game...\n");

        String saveGame;
        try {
            saveGame = ServerSaveJsonStore.normalizeSlot(args.get(1));
        } catch (IllegalArgumentException e) {
            Com.Printf("Bad save name.\n");
            return;
        }
        if ("current".equals(saveGame)) {
            Com.Printf("Can't load from 'current'\n");
            return;
        }

        // make sure the server metadata file exists
        ServerSaveJsonStore store = ServerSaveJsonStore.forWriteDir(EngineWriteRoot.pathString());
        if (!store.hasMapCommand(saveGame)) {
            String name = EngineWriteRoot.resolve("save/" + saveGame + "/server_mapcmd.ssv.json").toString();
            Com.Printf("No such savegame: " + name + "\n");
            return;
        }

        new ServerSaveFiles(EngineWriteRoot.path()).copy(saveGame, "current");

        final String mapCommand = SV_ReadMapCommand();

        // go to the map
        GameImportsImpl loaded = spawnServerInstance(new ChangeMapInfo(mapCommand, false, true), 0);
        loaded.name = "default";
        games.put("default", loaded);
    }

    private String SV_ReadMapCommand() {
        Com.DPrintf("SV_ReadMapCommand()\n");

        try {
            ServerSaveJsonStore store = ServerSaveJsonStore.forWriteDir(EngineWriteRoot.pathString());
            ServerSaveJsonStore.ServerMapCommandSnapshot snapshot = store.readMapCommand("current");
            // read the comment field
            Com.DPrintf("SV_ReadMapCommand: Loading save: " + snapshot.comment() + "\n");

            // read the mapcmd
            return snapshot.mapCommand();
        } catch (Exception e) {
            throw new RuntimeException(e);
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
    void SV_GameMap_f(List<String> args) {

        if (args.size() < 2) {
            Com.Printf("USAGE: gamemap <map>\n <client>");
            return;
        }

        String mapName = args.get(1);

        final int clientIndex;
        if (args.size() > 2 && !args.get(2).isBlank()) {
            try {
                clientIndex = Integer.parseInt(args.get(2));
            } catch (NumberFormatException e) {
                Com.Printf("USAGE: gamemap <map>\n <client>\n");
                return;
            }
        } else
            clientIndex = 0;

        // default.cfg may start a session with "killserver; gamemap *...".
        // Shutdown clears the client slots, so gamemap must initialize a fresh
        // server just as map does, while preserving clients on level transitions.
        if (games.isEmpty()) {
            initializeServerSession();
        }

        Com.DPrintf("SV_GameMap(" + mapName + ")\n");

        EngineWriteRoot.ensureParentDirectories(EngineWriteRoot.resolve("save/current/.keep"));

        // check for clearing the current savegame
        if (mapName.charAt(0) == '*') {
            // wipe all the *.sav files
            new ServerSaveFiles(EngineWriteRoot.path()).wipe("current");
        } else { // save the map just exited
            // note: only applicable to the default instance (subject to change in future)
            var gameImports = games.get("default");
            if (gameImports != null && gameImports.state == ServerStates.SS_GAME) {
                // clear all the client inuse flags before saving so that
                // when the level is re-entered, the clients will spawn
                // at spawn points instead of occupying body shells
                // todo: only relevant to this gameImports clients
                boolean[] savedInuse = new boolean[clients.size()];
                int i = 0;
                for (ClientNetworkInfo cl : clients) {
                    if (cl.edict != null) { // free client slots will have null values
                        savedInuse[i] = cl.edict.inuse;
                        cl.edict.inuse = false;
                    }
                    i++;
                }

                gameImports.SV_WriteLevelFile();

                // we must restore these for clients to transfer over correctly
                i = 0;
                for (ClientNetworkInfo cl: clients) {
                    if (cl.edict != null) { // free client slots will have null values
                        cl.edict.inuse = savedInuse[i];
                    }
                    i++;
                }
            }
        }

        final GameImportsImpl game = spawnServerInstance(new ChangeMapInfo(mapName, false, false), clientIndex);

        if (clientIndex == 0) {
            // server just started, `map` cmd was used
            // start up the next map
            game.name = "default";
            games.put(game.name, game);
            for (ClientNetworkInfo cl : clients) {
                cl.gameName = "default";
            }

        } else {
            // preview of the multi instance mode
            final ClientNetworkInfo cl = clients.get(clientIndex - 1);
            GameImportsImpl previousGame = games.get(cl.gameName);
            previousGame.gameExports.ClientDisconnect(cl.edict);
            games.put(game.name, game);
            cl.gameName = game.name;
        }

        // copy off the level to the autosave slot
        if (!Globals.dedicated.getTypedValue()) {
            game.SV_WriteServerFile(true);
            new ServerSaveFiles(EngineWriteRoot.path()).copy("current", "save0");
        }

        // multi instance preview:
        // when playing the regular flow, multi instances are not requried
        if (clientIndex == 0) {
            games.clear();
            games.put("default", game);
        }
    }

    /**
     * SV_Map
     */
    GameImportsImpl spawnServerInstance(ChangeMapInfo changeMapInfo, int clientIndex) {
        Globals.server_state = ServerStates.SS_DEAD; //fixme: used by the client code

        GameImportsImpl previous = clientIndex != 0
                ? games.get(clients.get(clientIndex - 1).gameName)
                : games.get("default");
        if (previous != null)
            previous.prepareLevelTransition();

        GameImportsImpl gameImports = createGameInstance(changeMapInfo, previous);

        Cvar.getInstance().find("nextserver", String.class).set("gamemap \"" + changeMapInfo.nextServer + "\"");

        Cmd.ExecuteFunction("loading"); // for local system

        if (clientIndex == 0) {
            // leave slots at start for clients only
            for (ClientNetworkInfo cl : clients) { //todo
                // needs to reconnect
                if (cl.state == ClientStates.CS_SPAWNED)
                    cl.state = ClientStates.CS_CONNECTED;
                cl.lastReceivedFrame = -1;
            }
            gameImports.SV_BroadcastCommand("changing");

        } else {
            final ClientNetworkInfo cl = clients.get(clientIndex - 1);
            if (cl.state == ClientStates.CS_SPAWNED)
                cl.state = ClientStates.CS_CONNECTED;
            cl.lastReceivedFrame = -1;
            gameImports.unicastMessage(clientIndex, new StuffTextMessage("changing"), true);
        }

        SV_SendClientMessages();


        // precache and static commands can be issued during
        // map initialization
        Globals.server_state = gameImports.state; // LOADING

        gameImports.initializeLevel(changeMapInfo, previous);
        Globals.server_state = gameImports.state;


        // set serverinfo variable
        Cvar.getInstance().find("mapname", String.class).forceSet(gameImports.mapName);
        Com.Println("mapname: " + gameImports.mapName);

        if (changeMapInfo.state == ServerStates.SS_GAME)
            Cbuf.CopyToDefer();

        if (clientIndex == 0) {
            gameImports.SV_BroadcastCommand("reconnect"); // doesn't really reconnect but rather issues 'NEW' command
        } else {
            gameImports.unicastMessage(clientIndex, new StuffTextMessage("reconnect"), true);
        }
        return gameImports;
    }

    /*
==================
SV_Map_f

Goes directly to a given map without any savegame archiving.
For development work
==================

Resets server to host a given map, moves all clients to it (via reconnect)
*/
    void SV_Map_f(List<String> args) {
        //char expanded[MAX_QPATH];
        if (args.size() < 2 || args.get(1).isBlank()) {
            Com.Printf("usage: map <map_name>\n");
            return;
        }

        // if not a pcx, demo, or cinematic, check to make sure the level exists
        String mapName = args.get(1);
        ChangeMapInfo target = new ChangeMapInfo(mapName, false, false);
        if (target.state == ServerStates.SS_GAME) {
            String mapPath = "maps/" + target.mapName + ".bsp";
            if (!EngineVfs.exists(mapPath)) {
                Com.Printf("Can't find " + mapPath + "\n");
                return;
            }
        }
        var game = games.get("default");

        if (game != null)
            game.state = ServerStates.SS_DEAD; // don't save current level when changing

        new ServerSaveFiles(EngineWriteRoot.path()).wipe("current");

        // A running server needs its clients reset for map. For a fresh server,
        // gamemap performs this initialization itself.
        if (!games.isEmpty()) {
            initializeServerSession();
        }
        SV_GameMap_f(args);
    }

    private void initializeServerSession() {
        if (games.isEmpty()) {
            realtime = 0;
            frameNum = 0;
            fixedtime = 100;
        }
        boolean multiplayer = initializeServerCvars();
        if (!NET.configureServer(multiplayer)) {
            Com.Error(ERR_DROP, "Could not open the server UDP port");
            return;
        }
        resetClients();
    }

    /** Send this client's Game snapshot followed by its queued events, recording rate usage. */
    private void sendClientDatagram(ClientNetworkInfo client, GameImportsImpl gameImports) {
        // send over all the relevant entity_state_t
        // and the player_state_t
        Collection<NetworkMessage> unreliable = new ArrayList<>();
        unreliable.addAll(gameImports.buildClientFrameMessages(client)); // 3 messages

        // copy the accumulated multicast datagram
        // for this client out to the message
        // it is necessary for this to be after the buildClientFrameMessages (SV_BuildClientFrame)
        // so that entity references will be current
        unreliable.addAll(client.unreliable);
        client.unreliable.clear();
        // send the datagram
        client.netchan.transmit(unreliable);

        // record the size for rate estimation
        client.message_size[gameImports.frameNum % Defines.RATE_MESSAGES] = unreliable.stream().mapToInt(NetworkMessage::getSize).sum();
    }
    private final Map<String, ServerUserCommand> userCommands;

    {
        Map<String, ServerUserCommand> handlers = new HashMap<>();
        // auto issued
        handlers.put(StringCmdMessage.NEW, this::SV_New_f);
        handlers.put(StringCmdMessage.CONFIG_STRINGS, this::SV_Configstrings_f);
        handlers.put(StringCmdMessage.BASELINES, this::SV_Baselines_f);
        handlers.put(StringCmdMessage.BEGIN, this::SV_Begin_f);
        handlers.put(StringCmdMessage.NEXT_SERVER, this::SV_Nextserver_f);
        handlers.put(StringCmdMessage.DISCONNECT, this::SV_Disconnect_f);

        // issued by hand at client consoles
        handlers.put(StringCmdMessage.INFO, this::SV_ShowServerinfo_f);
        handlers.put(StringCmdMessage.DOWNLOAD, this::SV_BeginDownload_f);
        handlers.put(StringCmdMessage.NEXT_DOWNLOAD, this::SV_NextDownload_f);

        userCommands = Map.copyOf(handlers);
    }

    @FunctionalInterface
    private interface ServerUserCommand {
        void execute(List<String> args, GameImportsImpl game, ClientNetworkInfo client);
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
    private void SV_New_f(List<String> args, GameImportsImpl gameImports, ClientNetworkInfo client) {

        Com.DPrintf("New() from " + client.name + "\n");

        if (client.state != ClientStates.CS_CONNECTED) {
            Com.Printf("New not valid -- already spawned\n");
            return;
        }

        //
        // serverdata needs to go over for all types of servers
        // to make sure the protocol is right, and to set the gamedir
        //
        String gamedir = Cvar.getInstance().find("gamedir", String.class).getTypedValue();

        // send the serverdata
        // send full levelname

        final int clientIndex;
        if (gameImports.state == ServerStates.SS_CINEMATIC || gameImports.state == ServerStates.SS_PIC)
            clientIndex = -1;
        else
            clientIndex = client.edict.index - 1;


        client.netchan.reliablePending.add(
                new ServerDataMessage(
                        Defines.PROTOCOL_VERSION,
                        gameImports.spawncount,
                        gameImports.isDemo,
                        gamedir,
                        clientIndex,
                        gameImports.configstrings[Defines.CS_NAME]
                ));
        //
        // game server
        //
        if (gameImports.state == ServerStates.SS_GAME) {
            // set up the entity for the client
            ServerEntity ent = gameImports.gameExports.getEdict(clientIndex + 1);
            ent.s.index = clientIndex + 1;
            client.edict = ent;
            client.lastcmd = new usercmd_t();

            // begin fetching configstrings
            client.netchan.reliablePending.add(new StuffTextMessage(String.format("cmd %s %d 0", StringCmdMessage.CONFIG_STRINGS, gameImports.spawncount)));
        }
    }

    /*
     * ================== SV_Configstrings_f ==================
     */
    private void SV_Configstrings_f(List<String> args, GameImportsImpl gameImports, ClientNetworkInfo client) {

        Com.DPrintf("Configstrings() from " + client.name + "\n");

        if (client.state != ClientStates.CS_CONNECTED) {
            Com.Printf("configstrings not valid -- already spawned\n");
            return;
        }

        // handle the case of a level changing while a client was connecting
        int spawnCount = args.size() >= 2 ? Lib.atoi(args.get(1)) : 0;
        if (spawnCount != gameImports.spawncount) {
            Com.Printf("SV_Configstrings_f from different level\n");
            SV_New_f(args, gameImports, client);
            return;
        }

        int start = args.size() >= 3 ? Lib.atoi(args.get(2)) : 0;

        // write a packet full of data
        int currentReliableSize = client.netchan.reliablePending.stream().mapToInt(NetworkMessage::getSize).sum();
        while (currentReliableSize < Defines.MAX_MSGLEN / 2 && start < Defines.MAX_CONFIGSTRINGS) {
            if (gameImports.configstrings[start] != null && gameImports.configstrings[start].length() != 0) {
                final ConfigStringMessage config = new ConfigStringMessage(start, gameImports.configstrings[start]);
                currentReliableSize += config.getSize();
                client.netchan.reliablePending.add(config);
            }
            start++;
        }

        // send next command
        final String nextCmd;

        if (start == Defines.MAX_CONFIGSTRINGS) {
            nextCmd = String.format("cmd %s %d 0", StringCmdMessage.BASELINES, gameImports.spawncount);
        } else {
            nextCmd = String.format("cmd %s %d %d", StringCmdMessage.CONFIG_STRINGS, gameImports.spawncount, start);
        }
        client.netchan.reliablePending.add(new StuffTextMessage(nextCmd));
    }

    /*
     * ================== SV_Baselines_f ==================
     */
    private void SV_Baselines_f(List<String> args, GameImportsImpl gameImports, ClientNetworkInfo client) {

        Com.DPrintf("Baselines() from " + client.name + "\n");

        if (client.state != ClientStates.CS_CONNECTED) {
            Com.Printf("baselines not valid -- already spawned\n");
            return;
        }

        // handle the case of a level changing while a client was connecting
        int spawnCount = args.size() >= 2 ? Lib.atoi(args.get(1)) : 0;
        if (spawnCount != gameImports.spawncount) {
            Com.Printf("SV_Baselines_f from different level\n");
            SV_New_f(args, gameImports, client);
            return;
        }

        // todo: validate argument
        int start = args.size() >= 3 ? Lib.atoi(args.get(2)) : 0;

        // write a packet full of data
        int currentReliableSize = client.netchan.reliablePending.stream().mapToInt(NetworkMessage::getSize).sum();
        while (currentReliableSize < Defines.MAX_MSGLEN / 2 && start < Defines.MAX_EDICTS) {
            entity_state_t base = gameImports.baselines[start];
            if (base.modelindex != 0 || base.sound != 0 || base.effects != 0) {
                final SpawnBaselineMessage spawn = new SpawnBaselineMessage(base);
                currentReliableSize += spawn.getSize();
                client.netchan.reliablePending.add(spawn);
            }
            start++;
        }

        // send next command
        final String nextCmd;
        if (start == Defines.MAX_EDICTS) {
            // finished sending baselines
            nextCmd = String.format("%s %d", StringCmdMessage.PRECACHE,gameImports.spawncount);
        } else {
            // continue from where we finished
            nextCmd = String.format("cmd %s %d %d", StringCmdMessage.BASELINES, gameImports.spawncount, start);
        }
        client.netchan.reliablePending.add(new StuffTextMessage(nextCmd));
    }

    /*
     * ================== SV_Begin_f ==================
     */
    private void SV_Begin_f(List<String> args, GameImportsImpl gameImports, ClientNetworkInfo client) {
        Com.DPrintf("Begin() from " + client.name + "\n");

        // handle the case of a level changing while a client was connecting
        int spawnCount = args.size() >= 2 ? Lib.atoi(args.get(1)) : 0;
        if (spawnCount != gameImports.spawncount) {
            Com.Printf("SV_Begin_f from different level\n");
            SV_New_f(args, gameImports, client);
            return;
        }

        client.state = ClientStates.CS_SPAWNED;

        // call the jake2.game begin function
        gameImports.gameExports.ClientBegin(client.edict);

        Cbuf.InsertFromDefer();
    }

    //=============================================================================

    /*
     * ================== SV_NextDownload_f ==================
     */
    private void SV_NextDownload_f(List<String> args, GameImportsImpl gameImports, ClientNetworkInfo client) {

        if (client.download == null)
            return;

        int packet = client.downloadsize - client.downloadcount;
        if (packet > 1024)
            packet = 1024;

        client.downloadcount += packet;
        int size = client.downloadsize;
        if (size == 0)
            size = 1;
        byte percent = (byte) (client.downloadcount * 100 / size);

        byte[] data = new byte[packet];
        System.arraycopy(client.download, client.downloadcount - packet, data, 0, packet);
        client.netchan.reliablePending.add(new DownloadMessage(data, percent));

        if (client.downloadcount == client.downloadsize) {
            client.download = null;
        }
    }

    /*
     * ================== SV_BeginDownload_f ==================
     */
    private void SV_BeginDownload_f(List<String> args, GameImportsImpl gameImports, ClientNetworkInfo client) {
        int offset = 0;

        if (args.size() < 2)
            return;

        String name = args.get(1);

        if (args.size() > 2)
            offset = Lib.atoi(args.get(2)); // downloaded offset

        // hacked by zoid to allow more conrol over download
        // first off, no .. or global allow check

        if (name.contains("..")
                || !allow_download.getTypedValue() // leading dot is no good
                || name.charAt(0) == '.' // leading slash bad as well, must be
                                         // in subdir
                || name.charAt(0) == '/' // next up, skin check
                || (name.startsWith("players/") && !allow_download_players.getTypedValue()) // now
                                                                                              // models
                || (name.startsWith("models/") && !allow_download_models.getTypedValue()) // now
                                                                                            // sounds
                || (name.startsWith("sound/") && !allow_download_sounds.getTypedValue())
                // now maps (note special case for maps, must not be in pak)
                || (name.startsWith("maps/") && !allow_download_maps.getTypedValue()) // MUST
                                                                                        // be
                                                                                        // in a
                                                                                        // subdirectory
                || name.indexOf('/') == -1) { // don't allow anything with ..
                                              // path

            // refuse
            client.netchan.reliablePending.add(new DownloadMessage());
            return;
        }

        client.download = EngineVfs.loadBytes(name);

        // The client waits for either data or an explicit refusal. Silently
        // returning here leaves precache stuck waiting for a download forever.
        if (client.download == null) {
            client.netchan.reliablePending.add(new DownloadMessage());
            return;
        }

        client.downloadsize = client.download.length;
        client.downloadcount = offset;

        if (offset > client.downloadsize)
            client.downloadcount = client.downloadsize;

        // special check for maps, if it came from a pak file, don't allow download ZOID
        if (name.startsWith("maps/") && EngineVfs.isFromPack(name)) {
            Com.DPrintf("Couldn't download " + name + " to " + client.name + "\n");
            if (client.download != null) {
                client.download = null;
            }

            // refuse
            client.netchan.reliablePending.add(new DownloadMessage());
            return;
        }

        SV_NextDownload_f(args, gameImports, client);
        Com.DPrintf("Downloading " + name + " to " + client.name + "\n");
    }

    //============================================================================

    /*
     * ================= SV_Disconnect_f
     *
     * The client is going to disconnect, so remove the connection immediately
     * =================
     */
    private void SV_Disconnect_f(List<String> args, GameImportsImpl gameImports, ClientNetworkInfo client) {
        //	SV_EndRedirect ();
        SV_DropClient(client);
    }

    /*
     * ================== SV_ShowServerinfo_f
     *
     * Dumps the serverinfo info string ==================
     */
    private void SV_ShowServerinfo_f(List<String> args, GameImportsImpl gameImports, ClientNetworkInfo client) {
        Info.Print(Cvar.getInstance().Serverinfo());
    }

    private void SV_Nextserver(GameImportsImpl gameImports) {

        //ZOID, ss_pic can be nextserver'd in coop mode
        if (gameImports.state == ServerStates.SS_GAME
                || (gameImports.state == ServerStates.SS_PIC && !Cvar.getInstance().find("coop", Boolean.class).getTypedValue()))
            return; // can't nextserver while playing a normal game

        gameImports.spawncount++; // make sure another doesn't sneak in
        String v = Cvar.getInstance().find("nextserver", String.class).getTypedValue();
        //if (!v[0])
        if (v.length() == 0)
            Cbuf.AddText("killserver\n");
        else {
            Cbuf.AddText(v);
            Cbuf.AddText("\n");
        }
        Cvar.getInstance().find("nextserver", String.class).set("");
    }

    /*
     * ================== SV_Nextserver_f
     *
     * A cinematic has completed or been aborted by a client, so move to the
     * next server, ==================
     */
    private void SV_Nextserver_f(List<String> args, GameImportsImpl gameImports, ClientNetworkInfo client) {
        int spawnCount = args.size() >= 2 ? Lib.atoi(args.get(1)) : 0;
        if (spawnCount != gameImports.spawncount) {
            Com.DPrintf("Nextserver() from wrong level, from " + client.name + "\n");
            return; // leftover from last server
        }

        Com.DPrintf("Nextserver() from " + client.name + "\n");
        SV_Nextserver(gameImports);
    }

}
